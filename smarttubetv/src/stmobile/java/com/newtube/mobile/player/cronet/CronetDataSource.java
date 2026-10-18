/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.newtube.mobile.player.cronet;

import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.datasource.HttpUtil.buildRangeRequestHeader;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceException;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.HttpUtil;
import androidx.media3.datasource.TransferListener;
import com.google.common.base.Ascii;
import com.google.common.base.Predicate;
import com.google.common.net.HttpHeaders;
import com.google.common.primitives.Longs;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.NetworkException;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlRequest.Status;
import org.chromium.net.UrlResponseInfo;

/**
 * DataSource without intermediate buffer based on Cronet API set using UrlRequest.
 */
public class CronetDataSource extends BaseDataSource implements HttpDataSource {

  private static final String TAG = "CronetDataSource";

  static {
    MediaLibraryInfo.registerModule("media3.datasource.cronet");
  }

  /** {@link DataSource.Factory} for {@link CronetDataSource} instances. */
  public static final class Factory implements HttpDataSource.Factory {

    @Nullable private final CronetEngine cronetEngine;
    private final Executor executor;
    private final RequestProperties defaultRequestProperties;
    @Nullable private final DefaultHttpDataSource.Factory internalFallbackFactory;

    @Nullable private HttpDataSource.Factory fallbackFactory;
    @Nullable private Predicate<String> contentTypePredicate;
    @Nullable private TransferListener transferListener;
    @Nullable private String userAgent;
    private int requestPriority;
    private int connectTimeoutMs;
    private int readTimeoutMs;
    private int readBufferSize;
    private boolean resetTimeoutOnRedirects;
    private boolean handleSetCookieRequests;
    private boolean keepPostFor302Redirects;

    public Factory(CronetEngine cronetEngine, Executor executor) {
      this.cronetEngine = checkNotNull(cronetEngine);
      this.executor = executor;
      defaultRequestProperties = new RequestProperties();
      internalFallbackFactory = null;
      requestPriority = UrlRequest.Builder.REQUEST_PRIORITY_MEDIUM;
      connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MILLIS;
      readTimeoutMs = DEFAULT_READ_TIMEOUT_MILLIS;
      readBufferSize = DEFAULT_READ_BUFFER_SIZE_BYTES;
    }

    @UnstableApi
    @Override
    public final Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
      this.defaultRequestProperties.clearAndSet(defaultRequestProperties);
      if (internalFallbackFactory != null) {
        internalFallbackFactory.setDefaultRequestProperties(defaultRequestProperties);
      }
      return this;
    }

    public Factory setUserAgent(@Nullable String userAgent) {
      this.userAgent = userAgent;
      if (internalFallbackFactory != null) {
        internalFallbackFactory.setUserAgent(userAgent);
      }
      return this;
    }

    @UnstableApi
    public Factory setRequestPriority(int requestPriority) {
      this.requestPriority = requestPriority;
      return this;
    }

    @UnstableApi
    public Factory setConnectionTimeoutMs(int connectTimeoutMs) {
      this.connectTimeoutMs = connectTimeoutMs;
      if (internalFallbackFactory != null) {
        internalFallbackFactory.setConnectTimeoutMs(connectTimeoutMs);
      }
      return this;
    }

    @UnstableApi
    public Factory setResetTimeoutOnRedirects(boolean resetTimeoutOnRedirects) {
      this.resetTimeoutOnRedirects = resetTimeoutOnRedirects;
      return this;
    }

    @UnstableApi
    public Factory setHandleSetCookieRequests(boolean handleSetCookieRequests) {
      this.handleSetCookieRequests = handleSetCookieRequests;
      return this;
    }

    @UnstableApi
    public Factory setReadTimeoutMs(int readTimeoutMs) {
      this.readTimeoutMs = readTimeoutMs;
      if (internalFallbackFactory != null) {
        internalFallbackFactory.setReadTimeoutMs(readTimeoutMs);
      }
      return this;
    }

    @UnstableApi
    public Factory setContentTypePredicate(@Nullable Predicate<String> contentTypePredicate) {
      this.contentTypePredicate = contentTypePredicate;
      if (internalFallbackFactory != null) {
        internalFallbackFactory.setContentTypePredicate(contentTypePredicate);
      }
      return this;
    }

    @UnstableApi
    public Factory setKeepPostFor302Redirects(boolean keepPostFor302Redirects) {
      this.keepPostFor302Redirects = keepPostFor302Redirects;
      if (internalFallbackFactory != null) {
        internalFallbackFactory.setKeepPostFor302Redirects(keepPostFor302Redirects);
      }
      return this;
    }

    @UnstableApi
    public Factory setTransferListener(@Nullable TransferListener transferListener) {
      this.transferListener = transferListener;
      if (internalFallbackFactory != null) {
        internalFallbackFactory.setTransferListener(transferListener);
      }
      return this;
    }

    @SuppressWarnings("deprecation")
    @UnstableApi
    @Deprecated
    public Factory setFallbackFactory(@Nullable HttpDataSource.Factory fallbackFactory) {
      this.fallbackFactory = fallbackFactory;
      return this;
    }

    @UnstableApi
    public Factory setReadBufferSize(int readBufferSize) {
      this.readBufferSize = readBufferSize;
      return this;
    }

    @UnstableApi
    @Override
    public HttpDataSource createDataSource() {
      if (cronetEngine == null) {
        return (fallbackFactory != null)
            ? fallbackFactory.createDataSource()
            : checkNotNull(internalFallbackFactory).createDataSource();
      }
      CronetDataSource dataSource =
          new CronetDataSource(
              cronetEngine,
              executor,
              requestPriority,
              connectTimeoutMs,
              readTimeoutMs,
              resetTimeoutOnRedirects,
              handleSetCookieRequests,
              userAgent,
              defaultRequestProperties,
              contentTypePredicate,
              keepPostFor302Redirects,
              readBufferSize);
      if (transferListener != null) {
        dataSource.addTransferListener(transferListener);
      }
      return dataSource;
    }
  }

  @UnstableApi
  public static final class OpenException extends HttpDataSourceException {

    public final int cronetConnectionStatus;

    @Deprecated
    public OpenException(IOException cause, DataSpec dataSpec, int cronetConnectionStatus) {
      super(cause, dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, TYPE_OPEN);
      this.cronetConnectionStatus = cronetConnectionStatus;
    }

    public OpenException(
        IOException cause,
        DataSpec dataSpec,
        @PlaybackException.ErrorCode int errorCode,
        int cronetConnectionStatus) {
      super(cause, dataSpec, errorCode, TYPE_OPEN);
      this.cronetConnectionStatus = cronetConnectionStatus;
    }

    @Deprecated
    public OpenException(String errorMessage, DataSpec dataSpec, int cronetConnectionStatus) {
      super(errorMessage, dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, TYPE_OPEN);
      this.cronetConnectionStatus = cronetConnectionStatus;
    }

    public OpenException(
        String errorMessage,
        DataSpec dataSpec,
        @PlaybackException.ErrorCode int errorCode,
        int cronetConnectionStatus) {
      super(errorMessage, dataSpec, errorCode, TYPE_OPEN);
      this.cronetConnectionStatus = cronetConnectionStatus;
    }

    public OpenException(
        DataSpec dataSpec, @PlaybackException.ErrorCode int errorCode, int cronetConnectionStatus) {
      super(dataSpec, errorCode, TYPE_OPEN);
      this.cronetConnectionStatus = cronetConnectionStatus;
    }
  }

  @UnstableApi public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
  @UnstableApi public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

  private static final int DEFAULT_READ_BUFFER_SIZE_BYTES = 32 * 1024;

  private final CronetEngine cronetEngine;
  private final Executor executor;
  private final int requestPriority;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final boolean resetTimeoutOnRedirects;
  private final boolean handleSetCookieRequests;
  @Nullable private final String userAgent;
  @Nullable private final RequestProperties defaultRequestProperties;
  private final RequestProperties requestProperties;
  private final ConditionVariable operation;
  private final Clock clock;
  private final int readBufferSize;

  @Nullable private final Predicate<String> contentTypePredicate;
  private final boolean keepPostFor302Redirects;

  private boolean transferStarted;
  private long bytesRemaining;

  @Nullable private UrlRequest currentUrlRequest;
  @VisibleForTesting @Nullable /* package */ UrlRequestCallback currentUrlRequestCallback;
  @Nullable private DataSpec currentDataSpec;

  @Nullable private ByteBuffer readBuffer;

  @Nullable private UrlResponseInfo responseInfo;
  @Nullable private IOException exception;
  private boolean finished;

  private volatile long currentConnectTimeoutMs;

  @UnstableApi
  protected CronetDataSource(
      CronetEngine cronetEngine,
      Executor executor,
      int requestPriority,
      int connectTimeoutMs,
      int readTimeoutMs,
      boolean resetTimeoutOnRedirects,
      boolean handleSetCookieRequests,
      @Nullable String userAgent,
      @Nullable RequestProperties defaultRequestProperties,
      @Nullable Predicate<String> contentTypePredicate,
      boolean keepPostFor302Redirects,
      int readBufferSize) {
    super(/* isNetwork= */ true);
    this.cronetEngine = checkNotNull(cronetEngine);
    this.executor = checkNotNull(executor);
    this.requestPriority = requestPriority;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
    this.resetTimeoutOnRedirects = resetTimeoutOnRedirects;
    this.handleSetCookieRequests = handleSetCookieRequests;
    this.userAgent = userAgent;
    this.defaultRequestProperties = defaultRequestProperties;
    this.contentTypePredicate = contentTypePredicate;
    this.keepPostFor302Redirects = keepPostFor302Redirects;
    clock = Clock.DEFAULT;
    this.readBufferSize = readBufferSize;
    requestProperties = new RequestProperties();
    operation = new ConditionVariable();
  }

  @UnstableApi
  @Override
  public void setRequestProperty(String name, String value) {
    requestProperties.set(name, value);
  }

  @UnstableApi
  @Override
  public void clearRequestProperty(String name) {
    requestProperties.remove(name);
  }

  @UnstableApi
  @Override
  public void clearAllRequestProperties() {
    requestProperties.clear();
  }

  @UnstableApi
  @Override
  public int getResponseCode() {
    return responseInfo == null || responseInfo.getHttpStatusCode() <= 0
        ? -1
        : responseInfo.getHttpStatusCode();
  }

  @UnstableApi
  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return responseInfo == null ? Collections.emptyMap() : responseInfo.getAllHeaders();
  }

  @UnstableApi
  @Override
  @Nullable
  public Uri getUri() {
    if (responseInfo != null) {
      return Uri.parse(responseInfo.getUrl());
    } else if (currentDataSpec != null) {
      return currentDataSpec.uri;
    } else {
      return null;
    }
  }

  @UnstableApi
  @Override
  public long open(DataSpec dataSpec) throws HttpDataSourceException {
    checkNotNull(dataSpec);
    checkState(!transferStarted);

    operation.close();
    resetConnectTimeout();
    currentDataSpec = dataSpec;
    UrlRequest urlRequest;
    try {
      createCurrentUrlRequestAndCallback(dataSpec);
      urlRequest = currentUrlRequest;
    } catch (IOException e) {
      if (e instanceof HttpDataSourceException) {
        throw (HttpDataSourceException) e;
      } else {
        throw new OpenException(
            e, dataSpec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED, Status.IDLE);
      }
    }
    urlRequest.start();

    transferInitializing(dataSpec);
    try {
      boolean connectionOpened = blockUntilConnectTimeout();
      @Nullable IOException connectionOpenException = exception;
      if (connectionOpenException != null) {
        @Nullable String message = connectionOpenException.getMessage();
        if (message != null && Ascii.toLowerCase(message).contains("err_cleartext_not_permitted")) {
          throw new CleartextNotPermittedException(connectionOpenException, dataSpec);
        }
        throw new OpenException(
            connectionOpenException,
            dataSpec,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            getStatus(urlRequest));
      } else if (!connectionOpened) {
        throw new OpenException(
            new SocketTimeoutException(),
            dataSpec,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            getStatus(urlRequest));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OpenException(
          new InterruptedIOException(),
          dataSpec,
          PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
          Status.INVALID);
    }

    UrlResponseInfo responseInfo = checkNotNull(this.responseInfo);
    int responseCode = responseInfo.getHttpStatusCode();
    Map<String, List<String>> responseHeaders = responseInfo.getAllHeaders();
    if (responseCode < 200 || responseCode > 299) {
      if (responseCode == 416) {
        long documentSize =
            HttpUtil.getDocumentSize(getFirstHeader(responseHeaders, HttpHeaders.CONTENT_RANGE));
        if (dataSpec.position == documentSize) {
          transferStarted = true;
          transferStarted(dataSpec);
          return dataSpec.length != C.LENGTH_UNSET ? dataSpec.length : 0;
        }
      }

      byte[] responseBody;
      try {
        responseBody = readResponseBody();
      } catch (IOException e) {
        responseBody = Util.EMPTY_BYTE_ARRAY;
      }

      @Nullable
      IOException cause =
          responseCode == 416
              ? new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
              : null;
      throw new InvalidResponseCodeException(
          responseCode,
          responseInfo.getHttpStatusText(),
          cause,
          responseHeaders,
          dataSpec,
          responseBody);
    }

    Predicate<String> contentTypePredicate = this.contentTypePredicate;
    if (contentTypePredicate != null) {
      @Nullable String contentType = getFirstHeader(responseHeaders, HttpHeaders.CONTENT_TYPE);
      if (contentType != null && !contentTypePredicate.apply(contentType)) {
        throw new InvalidContentTypeException(contentType, dataSpec);
      }
    }

    long bytesToSkip = responseCode == 200 && dataSpec.position != 0 ? dataSpec.position : 0;

    if (!isCompressed(responseInfo)) {
      if (dataSpec.length != C.LENGTH_UNSET) {
        bytesRemaining = dataSpec.length;
      } else {
        long contentLength =
            HttpUtil.getContentLength(
                getFirstHeader(responseHeaders, HttpHeaders.CONTENT_LENGTH),
                getFirstHeader(responseHeaders, HttpHeaders.CONTENT_RANGE));
        bytesRemaining =
            contentLength != C.LENGTH_UNSET ? (contentLength - bytesToSkip) : C.LENGTH_UNSET;
      }
    } else {
      bytesRemaining = dataSpec.length;
    }

    transferStarted = true;
    transferStarted(dataSpec);

    skipFully(bytesToSkip, dataSpec);
    return bytesRemaining;
  }

  @UnstableApi
  @Override
  public int read(byte[] buffer, int offset, int length) throws HttpDataSourceException {
    checkState(transferStarted);

    if (length == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }

    ByteBuffer readBuffer = getOrCreateReadBuffer();
    if (!readBuffer.hasRemaining()) {
      operation.close();
      readBuffer.clear();

      readInternal(readBuffer, castNonNull(currentDataSpec));

      if (finished) {
        bytesRemaining = 0;
        return C.RESULT_END_OF_INPUT;
      }

      readBuffer.flip();
      checkState(readBuffer.hasRemaining());
    }

    int bytesRead =
        (int)
            Longs.min(
                bytesRemaining != C.LENGTH_UNSET ? bytesRemaining : Long.MAX_VALUE,
                readBuffer.remaining(),
                length);

    readBuffer.get(buffer, offset, bytesRead);

    if (bytesRemaining != C.LENGTH_UNSET) {
      bytesRemaining -= bytesRead;
    }
    bytesTransferred(bytesRead);
    return bytesRead;
  }

  @UnstableApi
  public int read(ByteBuffer buffer) throws HttpDataSourceException {
    checkState(transferStarted);

    if (!buffer.isDirect()) {
      throw new IllegalArgumentException("Passed buffer is not a direct ByteBuffer");
    }
    if (!buffer.hasRemaining()) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }
    int readLength = buffer.remaining();

    if (readBuffer != null) {
      int copyBytes = copyByteBuffer(readBuffer, buffer);
      if (copyBytes != 0) {
        if (bytesRemaining != C.LENGTH_UNSET) {
          bytesRemaining -= copyBytes;
        }
        bytesTransferred(copyBytes);
        return copyBytes;
      }
    }

    operation.close();
    readInternal(buffer, castNonNull(currentDataSpec));

    if (finished) {
      bytesRemaining = 0;
      return C.RESULT_END_OF_INPUT;
    }

    checkState(readLength > buffer.remaining());
    int bytesRead = readLength - buffer.remaining();
    if (bytesRemaining != C.LENGTH_UNSET) {
      bytesRemaining -= bytesRead;
    }
    bytesTransferred(bytesRead);
    return bytesRead;
  }

  @UnstableApi
  @Override
  public synchronized void close() {
    closeCurrentUrlRequestAndCallback();
    if (readBuffer != null) {
      readBuffer.limit(0);
    }
    currentDataSpec = null;
    responseInfo = null;
    exception = null;
    finished = false;
    if (transferStarted) {
      transferStarted = false;
      transferEnded();
    }
  }

  private void closeCurrentUrlRequestAndCallback() {
    if (currentUrlRequest != null) {
      currentUrlRequest.cancel();
      currentUrlRequest = null;
    }

    if (currentUrlRequestCallback != null) {
      currentUrlRequestCallback.close();
      currentUrlRequestCallback = null;
    }
  }

  @UnstableApi
  @Nullable
  protected UrlRequest getCurrentUrlRequest() {
    return currentUrlRequest;
  }

  @UnstableApi
  @Nullable
  protected UrlResponseInfo getCurrentUrlResponseInfo() {
    return responseInfo;
  }

  private void createCurrentUrlRequestAndCallback(DataSpec dataSpec) throws IOException {
    currentUrlRequestCallback = new UrlRequestCallback();
    currentUrlRequest = buildRequestBuilder(dataSpec).build();
  }

  @UnstableApi
  protected UrlRequest.Builder buildRequestBuilder(DataSpec dataSpec) throws IOException {
    UrlRequest.Builder requestBuilder =
        cronetEngine
            .newUrlRequestBuilder(dataSpec.uri.toString(), currentUrlRequestCallback, executor)
            .setPriority(requestPriority)
            .allowDirectExecutor();

    Map<String, String> requestHeaders = new HashMap<>();
    if (defaultRequestProperties != null) {
      requestHeaders.putAll(defaultRequestProperties.getSnapshot());
    }
    requestHeaders.putAll(requestProperties.getSnapshot());
    requestHeaders.putAll(dataSpec.httpRequestHeaders);

    for (Entry<String, String> headerEntry : requestHeaders.entrySet()) {
      String key = headerEntry.getKey();
      String value = headerEntry.getValue();
      requestBuilder.addHeader(key, value);
    }

    if (dataSpec.httpBody != null && !requestHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
      throw new OpenException(
          "HTTP request with non-empty body must set Content-Type",
          dataSpec,
          PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
          Status.IDLE);
    }

    @Nullable String rangeHeader = buildRangeRequestHeader(dataSpec.position, dataSpec.length);
    if (rangeHeader != null) {
      requestBuilder.addHeader(HttpHeaders.RANGE, rangeHeader);
    }
    if (userAgent != null) {
      requestBuilder.addHeader(HttpHeaders.USER_AGENT, userAgent);
    }

    requestBuilder.setHttpMethod(dataSpec.getHttpMethodString());
    if (dataSpec.httpBody != null) {
      requestBuilder.setUploadDataProvider(
          new ByteArrayUploadDataProvider(dataSpec.httpBody), executor);
    }
    return requestBuilder;
  }

  private boolean blockUntilConnectTimeout() throws InterruptedException {
    long now = clock.elapsedRealtime();
    boolean opened = false;
    while (!opened && now < currentConnectTimeoutMs) {
      opened = operation.block(currentConnectTimeoutMs - now + 5);
      now = clock.elapsedRealtime();
    }
    return opened;
  }

  private void resetConnectTimeout() {
    currentConnectTimeoutMs = clock.elapsedRealtime() + connectTimeoutMs;
  }

  private void skipFully(long bytesToSkip, DataSpec dataSpec) throws HttpDataSourceException {
    if (bytesToSkip == 0) {
      return;
    }
    ByteBuffer readBuffer = getOrCreateReadBuffer();

    try {
      while (bytesToSkip > 0) {
        operation.close();
        readBuffer.clear();
        readInternal(readBuffer, dataSpec);
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedIOException();
        }
        if (finished) {
          throw new OpenException(
              dataSpec,
              PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
              Status.READING_RESPONSE);
        } else {
          readBuffer.flip();
          checkState(readBuffer.hasRemaining());
          int bytesSkipped = (int) Math.min(readBuffer.remaining(), bytesToSkip);
          readBuffer.position(readBuffer.position() + bytesSkipped);
          bytesToSkip -= bytesSkipped;
        }
      }
    } catch (IOException e) {
      if (e instanceof HttpDataSourceException) {
        throw (HttpDataSourceException) e;
      } else {
        throw new OpenException(
            e,
            dataSpec,
            e instanceof SocketTimeoutException
                ? PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                : PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            Status.READING_RESPONSE);
      }
    }
  }

  private byte[] readResponseBody() throws IOException {
    byte[] responseBody = Util.EMPTY_BYTE_ARRAY;
    ByteBuffer readBuffer = getOrCreateReadBuffer();
    while (!finished) {
      operation.close();
      readBuffer.clear();
      readInternal(readBuffer, castNonNull(currentDataSpec));
      readBuffer.flip();
      if (readBuffer.remaining() > 0) {
        int existingResponseBodyEnd = responseBody.length;
        responseBody = Arrays.copyOf(responseBody, responseBody.length + readBuffer.remaining());
        readBuffer.get(responseBody, existingResponseBodyEnd, readBuffer.remaining());
      }
    }
    return responseBody;
  }

  @SuppressWarnings("ReferenceEquality")
  private void readInternal(ByteBuffer buffer, DataSpec dataSpec) throws HttpDataSourceException {
    castNonNull(currentUrlRequest).read(buffer);
    try {
      if (!operation.block(readTimeoutMs)) {
        throw new SocketTimeoutException();
      }
    } catch (InterruptedException e) {
      if (buffer == readBuffer) {
        readBuffer = null;
      }
      Thread.currentThread().interrupt();
      exception = new InterruptedIOException();
    } catch (SocketTimeoutException e) {
      if (buffer == readBuffer) {
        readBuffer = null;
      }
      exception =
          new HttpDataSourceException(
              e,
              dataSpec,
              PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
              HttpDataSourceException.TYPE_READ);
    }

    if (exception != null) {
      if (exception instanceof HttpDataSourceException) {
        throw (HttpDataSourceException) exception;
      } else {
        throw HttpDataSourceException.createForIOException(
            exception, dataSpec, HttpDataSourceException.TYPE_READ);
      }
    }
  }

  private ByteBuffer getOrCreateReadBuffer() {
    if (readBuffer == null) {
      readBuffer = ByteBuffer.allocateDirect(readBufferSize);
      readBuffer.limit(0);
    }
    return readBuffer;
  }

  private static boolean isCompressed(UrlResponseInfo info) {
    for (Map.Entry<String, String> entry : info.getAllHeadersAsList()) {
      if (entry.getKey().equalsIgnoreCase("Content-Encoding")) {
        return !entry.getValue().equalsIgnoreCase("identity");
      }
    }
    return false;
  }

  private static int getStatus(UrlRequest request) throws InterruptedException {
    final ConditionVariable conditionVariable = new ConditionVariable();
    final int[] statusHolder = new int[1];
    request.getStatus(
        new UrlRequest.StatusListener() {
          @Override
          public void onStatus(int status) {
            statusHolder[0] = status;
            conditionVariable.open();
          }
        });
    conditionVariable.block();
    return statusHolder[0];
  }

  @Nullable
  private static String getFirstHeader(Map<String, List<String>> allHeaders, String headerName) {
    @Nullable List<String> headers = allHeaders.get(headerName);
    return headers != null && !headers.isEmpty() ? headers.get(0) : null;
  }

  private static int copyByteBuffer(ByteBuffer src, ByteBuffer dst) {
    int remaining = Math.min(src.remaining(), dst.remaining());
    int limit = src.limit();
    src.limit(src.position() + remaining);
    dst.put(src);
    src.limit(limit);
    return remaining;
  }

  @VisibleForTesting
  /* package */ final class UrlRequestCallback extends UrlRequest.Callback {

    private volatile boolean isClosed = false;

    public void close() {
      this.isClosed = true;
    }

    @Override
    public synchronized void onRedirectReceived(
        UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
      if (isClosed) {
        return;
      }
      checkNotNull(currentUrlRequest);
      checkNotNull(currentUrlRequestCallback);
      DataSpec dataSpec = checkNotNull(currentDataSpec);
      int responseCode = info.getHttpStatusCode();
      if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
        if (responseCode == 307 || responseCode == 308) {
          exception =
              new InvalidResponseCodeException(
                  responseCode,
                  info.getHttpStatusText(),
                  null,
                  info.getAllHeaders(),
                  dataSpec,
                  Util.EMPTY_BYTE_ARRAY);
          operation.open();
          return;
        }
      }
      if (resetTimeoutOnRedirects) {
        resetConnectTimeout();
      }

      CookieHandler cookieHandler = CookieHandler.getDefault();
      if (cookieHandler == null && handleSetCookieRequests) {
        cookieHandler = new CookieManager();
      }

      String url = info.getUrl();
      Map<String, List<String>> headers = info.getAllHeaders();
      HttpUtil.storeCookiesFromHeaders(url, headers, cookieHandler);
      String cookieHeaders = HttpUtil.getCookieHeader(url, headers, cookieHandler);

      boolean shouldKeepPost =
          keepPostFor302Redirects
              && dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST
              && responseCode == 302;

      if (!shouldKeepPost) {
        if (!handleSetCookieRequests || TextUtils.isEmpty(cookieHeaders)) {
          request.followRedirect();
          return;
        }
      }

      DataSpec redirectUrlDataSpec;
      if (!shouldKeepPost && dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST) {
        redirectUrlDataSpec =
            dataSpec
                .buildUpon()
                .setUri(newLocationUrl)
                .setHttpMethod(DataSpec.HTTP_METHOD_GET)
                .setHttpBody(null)
                .build();
      } else {
        redirectUrlDataSpec = dataSpec.withUri(Uri.parse(newLocationUrl));
      }

      if (!TextUtils.isEmpty(cookieHeaders)) {
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.putAll(dataSpec.httpRequestHeaders);
        requestHeaders.put(HttpHeaders.COOKIE, cookieHeaders);
        redirectUrlDataSpec =
            redirectUrlDataSpec.buildUpon().setHttpRequestHeaders(requestHeaders).build();
      }

      closeCurrentUrlRequestAndCallback();
      try {
        createCurrentUrlRequestAndCallback(redirectUrlDataSpec);
      } catch (IOException e) {
        exception = e;
        return;
      }

      currentUrlRequest.start();
    }

    @Override
    public synchronized void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
      if (isClosed) {
        return;
      }
      HttpUtil.storeCookiesFromHeaders(
          info.getUrl(), info.getAllHeaders(), CookieHandler.getDefault());
      responseInfo = info;
      operation.open();
    }

    @Override
    public synchronized void onReadCompleted(
        UrlRequest request, UrlResponseInfo info, ByteBuffer buffer) {
      if (isClosed) {
        return;
      }
      operation.open();
    }

    @Override
    public synchronized void onSucceeded(UrlRequest request, UrlResponseInfo info) {
      if (isClosed) {
        return;
      }
      finished = true;
      operation.open();
    }

    @Override
    public synchronized void onFailed(
        UrlRequest request, UrlResponseInfo info, CronetException error) {
      if (isClosed) {
        return;
      }
      if (error instanceof NetworkException
          && ((NetworkException) error).getErrorCode()
              == NetworkException.ERROR_HOSTNAME_NOT_RESOLVED) {
        exception = new UnknownHostException();
      } else {
        exception = error;
      }
      operation.open();
    }
  }
}
