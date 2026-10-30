package com.newtube.mobile.ui.playback;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemStoryboard;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemStoryboard.Size;

import java.util.Objects;

/**
 * NEWTUBE(storyboard): turns YouTube's storyboard spec into the frame under the finger.
 *
 * <p>A storyboard is a set of sprite sheets: each sheet is a {@code cols x rows} grid of
 * {@code durationEachMs} thumbnails, so the frame for a position is "sheet index + row/column".
 * The sheets are fetched (and disk-cached) with Glide at their native size and one of them is kept
 * decoded; the preview view draws the requested tile out of it. A sheet change starts one request;
 * until it lands the view keeps drawing the previous frame, so scrubbing never flashes black.</p>
 *
 * <p>Only the current sheet is kept as a private copy: Glide owns the bitmaps it decodes and can
 * put them back in its pool once a request is cleared, which would paint garbage in a view that
 * still referenced one.</p>
 */
public final class StoryboardPreviewController {
    /** One tile: the sheet it lives in and its column/row inside that sheet. */
    private static final class Tile {
        final String url;
        final int column;
        final int row;

        Tile(String url, int column, int row) {
            this.url = url;
            this.column = column;
            this.row = row;
        }
    }

    private final StoryboardPreviewView mView;

    @Nullable
    private MediaItemStoryboard mStoryboard;
    @Nullable
    private String mVideoId;
    /** The decoded sheet the view is drawing from, owned by this controller. */
    @Nullable
    private Bitmap mSheet;
    @Nullable
    private String mSheetUrl;
    @Nullable
    private String mRequestedUrl;
    /** A sheet whose fetch failed: never retry it for this video (a drag would ask every move). */
    @Nullable
    private String mFailedUrl;
    @Nullable
    private CustomTarget<Bitmap> mTarget;
    /** Guards against a late answer of a request that was superseded. */
    private int mRequest;

    /** The position the finger is on (the last {@link #show} call), -1 = nothing yet. */
    private long mPositionMs = -1;
    /** Where the finger is headed: prefetches the next sheet in that direction. */
    private int mDirection = 1;
    /** The preview should be up (the bar is being dragged). */
    private boolean mVisible;

    public StoryboardPreviewController(@NonNull StoryboardPreviewView view) {
        mView = view;
    }

    /**
     * Attach the storyboard of {@code videoId}: drops the previous video's frame and warms the
     * sheet around {@code positionMs} (the video's current position), so the first drag starts
     * from a nearby frame even on a slow link.
     */
    public void setStoryboard(@Nullable MediaItemStoryboard storyboard, @Nullable String videoId,
            long positionMs) {
        if (mStoryboard == storyboard && Objects.equals(mVideoId, videoId)) {
            return;
        }

        cancelRequest();
        mStoryboard = storyboard;
        mVideoId = videoId;
        mPositionMs = Math.max(0, positionMs);
        mDirection = 1;
        mFailedUrl = null;
        dropSheet();
        mVisible = false;
        mView.setVisibility(View.GONE);

        if (storyboard != null) {
            Tile first = tileAt(mPositionMs);
            if (first != null) {
                requestSheet(first.url);
            }
        }
    }

    /** The bar is being dragged: show the frame at {@code positionMs}. */
    public void show(long positionMs) {
        if (mStoryboard == null) {
            return;
        }

        if (positionMs < mPositionMs) {
            mDirection = -1;
        } else if (positionMs > mPositionMs) {
            mDirection = 1;
        }
        mPositionMs = Math.max(0, positionMs);
        mVisible = true;

        Tile tile = tileAt(mPositionMs);
        if (tile == null) {
            return;
        }

        if (mSheet != null && tile.url.equals(mSheetUrl)) {
            draw(tile);
            return;
        }

        // Keep whatever frame is already up while the new sheet travels; if there is none, the
        // real video stays visible (no black box) until the first tile arrives.
        if (mView.hasFrame()) {
            mView.setVisibility(View.VISIBLE);
        }
        requestSheet(tile.url);
    }

    /** The drag ended: put the video back. The last frame is kept for the next drag. */
    public void hide() {
        mVisible = false;
        // One retry per sheet per drag: a transient failure must not blacklist it for the video,
        // but a drag that keeps asking must not hammer the same failing URL either.
        mFailedUrl = null;
        mView.setVisibility(View.GONE);
    }

    /** A different video: forget the frame and the in-flight request. */
    public void clear() {
        setStoryboard(null, null, -1);
    }

    // ---------------------------------------------------------------------------------
    // Sheet loading
    // ---------------------------------------------------------------------------------

    private void requestSheet(@NonNull String url) {
        if (mSheet != null && url.equals(mSheetUrl)) {
            return;
        }
        if (url.equals(mFailedUrl)) {
            return;
        }
        if (url.equals(mRequestedUrl) && mTarget != null) {
            return;
        }

        cancelRequest();
        mRequestedUrl = url;
        final int request = ++mRequest;

        CustomTarget<Bitmap> target = new CustomTarget<Bitmap>(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL) {
            @Override
            public void onResourceReady(@NonNull Bitmap resource,
                    @Nullable Transition<? super Bitmap> transition) {
                if (request != mRequest) {
                    return;
                }
                mTarget = null;
                mRequestedUrl = null;

                Bitmap copy = copySheet(resource);
                if (copy == null) {
                    return;
                }
                dropSheet();
                mSheet = copy;
                mSheetUrl = url;
                Tile tile = tileAt(mPositionMs);
                if (tile != null && tile.url.equals(url)) {
                    draw(tile);
                }
                prefetchNextSheet();
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (request == mRequest) {
                    mTarget = null;
                    mRequestedUrl = null;
                    mFailedUrl = url;
                }
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                // The decoded bitmap is Glide's; this controller keeps only its own copy.
            }
        };

        mTarget = target;
        Glide.with(mView)
                .asBitmap()
                .load(url)
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(target);
    }

    /** Warm the next sheet in the drag's direction so a continued drag does not wait for it. */
    private void prefetchNextSheet() {
        if (mStoryboard == null || mPositionMs < 0) {
            return;
        }
        long group = mStoryboard.getGroupDurationMS();
        if (group <= 0) {
            return;
        }
        Tile next = tileAt(mPositionMs + mDirection * group);
        if (next == null || next.url.equals(mSheetUrl) || next.url.equals(mRequestedUrl)) {
            return;
        }
        Glide.with(mView)
                .asBitmap()
                .load(next.url)
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .preload();
    }

    private void cancelRequest() {
        mRequest++;
        mRequestedUrl = null;
        if (mTarget != null) {
            try {
                Glide.with(mView).clear(mTarget);
            } catch (IllegalArgumentException ignored) {
                // The activity went away between the last callback and this cleanup.
            }
            mTarget = null;
        }
    }

    private void dropSheet() {
        mView.clear();
        // Deliberately NOT recycled: the view's last recorded display list may still reference the
        // bitmap until the next frame. Dropping both references lets the GC reclaim it safely.
        mSheet = null;
        mSheetUrl = null;
    }

    /**
     * Glide's bitmap may go back to its pool once the request is cleared, so the preview owns a
     * copy. Opaque storyboard JPEGs decode at RGB_565, which also halves the copy's memory.
     */
    @Nullable
    private static Bitmap copySheet(@NonNull Bitmap source) {
        try {
            Bitmap.Config config = source.getConfig() == Bitmap.Config.RGB_565
                    ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
            return source.copy(config, false);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------------------------
    // Position -> tile
    // ---------------------------------------------------------------------------------

    /** The sheet URL and the tile inside it for {@code positionMs}, or null without a spec. */
    @Nullable
    private Tile tileAt(long positionMs) {
        if (mStoryboard == null) {
            return null;
        }
        Size size = mStoryboard.getGroupSize();
        if (size == null) {
            return null;
        }

        int eachMs = size.getDurationEachMS();
        int columns = size.getColCount();
        int rows = size.getRowCount();
        if (eachMs <= 0 || columns <= 0 || rows <= 0) {
            return null;
        }

        long index = Math.max(0, positionMs) / eachMs;
        int perSheet = columns * rows;
        long sheetIndex = index / perSheet;
        if (sheetIndex > Integer.MAX_VALUE) {
            return null;
        }

        int inSheet = (int) (index % perSheet);
        String url = mStoryboard.getGroupUrl((int) sheetIndex);
        if (url == null) {
            return null;
        }
        return new Tile(url, inSheet % columns, inSheet / columns);
    }

    /** Draw {@code tile} out of the current sheet, centre-cropped by the view itself. */
    private void draw(@NonNull Tile tile) {
        Bitmap sheet = mSheet;
        if (sheet == null || sheet.isRecycled() || mStoryboard == null) {
            return;
        }
        Size size = mStoryboard.getGroupSize();
        if (size == null || size.getColCount() <= 0 || size.getRowCount() <= 0) {
            return;
        }

        int tileWidth = sheet.getWidth() / size.getColCount();
        int tileHeight = sheet.getHeight() / size.getRowCount();
        if (tileWidth <= 0 || tileHeight <= 0) {
            return;
        }

        int left = tile.column * tileWidth;
        int top = tile.row * tileHeight;
        Rect src = new Rect(left, top, left + tileWidth, top + tileHeight);
        if (src.isEmpty()) {
            return;
        }

        mView.showTile(sheet, src);
        if (mVisible) {
            mView.setVisibility(View.VISIBLE);
        }
    }
}
