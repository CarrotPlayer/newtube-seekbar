package com.newtube.mobile.ui.playback;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Rational;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.mediaserviceinterfaces.LiveChatService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.rxjava3.disposables.Disposable;

import com.github.vkay94.dtpv3.DoubleTapPlayerView;
import com.github.vkay94.dtpv3.DoubleTapPlayerViewImpl;
import com.github.vkay94.dtpv3.youtube.YouTubeOverlay;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.TimeBar;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.QueuePlaybackMode;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SubtitleSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.SubtitleTrack;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.NetPath;
import com.newtube.mobile.casting.CastPickerLauncher;
import com.newtube.mobile.casting.CastSessionManager;
import com.newtube.mobile.casting.CastTarget;
import com.newtube.mobile.casting.CastVolumeKeys;
import com.newtube.mobile.player.Media3DebugInfoManager;
import com.newtube.mobile.player.Media3PlayerController;
import com.newtube.mobile.player.Media3PlayerInitializer;
import com.newtube.mobile.player.Media3SubtitleManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.common.misc.VideoDownloads;
import com.newtube.mobile.downloads.DownloadItem;
import com.newtube.mobile.downloads.DownloadMenu;
import com.newtube.mobile.downloads.DownloadOption;
import com.newtube.mobile.downloads.DownloadRegistry;
import com.newtube.mobile.SessionWarmup;
import com.newtube.mobile.ui.common.FrameGate;
import com.newtube.mobile.ui.common.Haptics;
import com.newtube.mobile.ui.common.MagneticDrag;
import com.newtube.mobile.ui.common.MobileActivity;
import com.newtube.mobile.ui.common.Motion;
import com.newtube.mobile.ui.common.ThemeMode;
import com.newtube.mobile.ui.common.ThemeRefresh;
import com.newtube.mobile.ui.dialog.MaxHeightRecyclerView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Touch player - PLAYER POLISH wave.
 */
public class MobilePlaybackActivity extends MobileActivity
        implements PlaybackView, PlayerContainerLayout.SwipeListener, LiveChatSheet.Host {

    private static final long AUTO_HIDE_MS = 3_500;
    private static final long PROGRESS_UPDATE_MS = 500;
    private static final long LIVE_EDGE_OFFSET_MS = 15_000;
    private static final long LIVE_EDGE_THRESHOLD_MS = 20_000;
    private static final int SUGGESTIONS_PAGE_THRESHOLD_PX = 800;

    private PlayerContainerLayout mContainer;
    private PinchZoomLayout mVideoArea;
    private TextView mZoomHintView;
    private DoubleTapPlayerViewImpl mPlayerView;
    private YouTubeOverlay mYouTubeOverlay;
    private View mControlsRoot;
    @Nullable private View mTopScrim;
    @Nullable private View mBottomScrim;
    private float mVideoAspect;
    private TextView mTitleView;
    private ImageButton mBackButton;
    private ImageButton mPlayPauseButton;
    private ImageButton mFullscreenButton;
    private TextView mPositionView;
    private TextView mDurationView;
    private TextView mLiveChip;

    private PlayerTimeBar mTimeBar;
    private StoryboardManager mStoryboardManager;
    private ImageView mSeekOverlayPreview;

    @Nullable private View mBottomRow;
    @Nullable private View mTransport;
    @Nullable private View mOptionsRow;
    @Nullable private TextView mTopPill;
    @Nullable private View mLevelPill;
    @Nullable private ImageView mLevelIcon;
    @Nullable private LevelBar mLevelBar;
    private boolean mScrubChromeHidden;
    private ProgressBar mProgressBar;
    private TextView mSetupHint;
    private TextView mNoticeView;
    private ImageButton mSubtitlesButton;
    private ImageButton mMoreButton;
    private ImageButton mPrevButton;
    private ImageButton mNextButton;
    private ViewGroup mDebugViewGroup;

    private ImageButton mCastButton;
    private View mCastOverlay;
    private TextView mCastOverlayTitle;
    private ImageButton mCastPlayPause;
    private TextView mCastLiveChip;
    private View mCastTimeline;
    private TextView mCastPosition;
    private TextView mCastDuration;
    private SeekBar mCastSeekBar;
    private CastSessionManager mCastSessionManager;
    private boolean mCastScrubbing;
    @Nullable private String mCastSubtitleVssId;
    @Nullable private String mCastSubtitleLabel;

    private Media3SubtitleManager mSubtitleManager;
    private Media3DebugInfoManager mDebugInfoManager;

    private View mWatchRoot;
    private NestedScrollView mWatchScroll;
    private View mWatchContent;
    private TextView mWatchTitle;
    private TextView mWatchMeta;
    private View mWatchMetaRow;
    private ImageView mWatchExpand;
    private TextView mWatchDescription;
    private View mWatchLike;
    private ImageView mWatchLikeIcon;
    private TextView mWatchLikeCount;
    private View mWatchDislike;
    private ImageView mWatchDislikeIcon;
    private TextView mWatchDislikeCount;
    private View mWatchShare;
    private View mWatchSave;
    private ImageView mWatchSaveIcon;
    private TextView mWatchSaveLabel;
    private View mWatchDownload;
    private ImageView mWatchDownloadIcon;
    private TextView mWatchDownloadLabel;
    private ImageView mWatchAvatar;
    private TextView mWatchChannelName;
    private TextView mWatchSubs;
    private MaterialButton mWatchSubscribe;
    private TextView mWatchRelatedLabel;
    private View mRelatedSkeleton;
    private RecyclerView mWatchRelated;
    private RelatedVideoAdapter mRelatedAdapter;

    private View mWatchCommentsEntry;
    private TextView mWatchCommentsCount;
    private CommentsPanel mCommentsPanel;
    private static final long COMMENTS_PREFETCH_DELAY_MS = 2_000;
    private final Runnable mPrefetchComments = this::prefetchComments;
    private final List<Video> mChapterVideos = new ArrayList<>();
    private TextView mScrubChapterView;
    private TextView mChapterButton;
    private int mChapterButtonIndex = -1;
    @Nullable private BottomSheetDialog mChaptersSheet;
    private View mWatchChatEntry;
    private String mCommentsKey;
    private String mLiveChatKey;

    private static final int MAX_CHAT_ITEMS = 250;
    private final List<ChatItem> mChatItems = new ArrayList<>();
    private ChatReceiver mChatReceiver;
    private LiveChatSheet.Observer mChatObserver;
    private Disposable mLiveChatAction;
    private final WatchMetadataGate mWatchMetadataGate = new WatchMetadataGate();
    private final DeferredPlaybackUi mRelatedRenderGate = new DeferredPlaybackUi();
    private static final long WATCH_METADATA_TIMEOUT_MS = 6_000;
    private final Runnable mReleaseWatchMetadata = this::releaseWatchMetadata;

    private final LinkedHashMap<Integer, List<Video>> mSuggestionVideos = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, VideoGroup> mSuggestionGroups = new LinkedHashMap<>();
    private final List<Video> mRelatedVideos = new ArrayList<>();
    private Video mLastPagedVideo;

    private View mQueueCard;
    private View mQueueHeader;
    private TextView mQueueTitle;
    private TextView mQueueSubtitle;
    private ImageView mQueueChevron;
    private MaxHeightRecyclerView mQueueList;
    private RelatedVideoAdapter mQueueAdapter;
    private boolean mQueueExpanded;
    private final List<Video> mQueueVideos = new ArrayList<>();

    private final SparseIntArray mButtonStates = new SparseIntArray();

    private Video mWatchVideo;
    private String mWatchVideoId;
    private boolean mDescriptionExpanded;

    private final View.OnLayoutChangeListener mWatchRootLayoutListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int width = right - left;
                if (width > 0
                        && getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_PORTRAIT) {
                    applyPortraitVideoHeight(width);
                    updateInlineViewport(width);
                }
            };

    private PlaybackPresenter mPresenter;
    private Media3PlayerInitializer mPlayerInitializer;
    private Media3PlayerController mExoPlayerController;
    private ExoPlayer mPlayer;
    @Nullable private com.newtube.mobile.player.BenchTicker mBenchTicker;
    private boolean mIsEngineBlocked;

    private boolean mControlsVisible;
    private boolean mScrubbing;
    private boolean mIsEnded;
    private boolean mIsInPip;
    private boolean mIsStopped;
    private boolean mIsResumed;
    private boolean mPipDismissPending;
    private int mPrePipOrientation = ORIENTATION_NONE;
    private static final int FREE_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_USER;
    private final OrientationHandBack mOrientationHandBack = new OrientationHandBack();
    @Nullable private android.view.OrientationEventListener mOrientationListener;
    private int mLastPhoneDegrees = android.view.OrientationEventListener.ORIENTATION_UNKNOWN;
    private final Runnable mOrientationSettleCheck = () -> onPhoneOrientation(mLastPhoneDegrees);
    private boolean mBackgroundAudioMode;

    private MobilePlaybackService mPlaybackService;
    private boolean mServiceBound;

    private static final String ACTION_PIP_TOGGLE = "com.newtube.mobile.action.PIP_TOGGLE";
    private static final int PIP_REQUEST_TOGGLE = 700;
    private static final int ORIENTATION_NONE = Integer.MIN_VALUE;
    private BroadcastReceiver mPipReceiver;

    private final StringBuilder mFormatBuilder = new StringBuilder();
    private final Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());

    private final Runnable mHideControlsRunnable = this::onAutoHideTick;
    private final Runnable mProgressUpdateRunnable = this::onProgressTick;
    private final Runnable mLineUpdateRunnable = this::onLineTick;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SystemPipBridge.attach(this);
        sCurrent = new java.lang.ref.WeakReference<>(this);

        if (PlayerTransitionBridge.hasPending()) {
            overridePendingTransition(0, 0);
        }

        setContentView(R.layout.activity_mobile_playback);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackStarted(@NonNull androidx.activity.BackEventCompat backEvent) {
                onBackGestureStarted();
            }

            @Override
            public void handleOnBackProgressed(@NonNull androidx.activity.BackEventCompat backEvent) {
                onBackGestureProgressed(backEvent.getProgress());
            }

            @Override
            public void handleOnBackCancelled() {
                onBackGestureCancelled();
            }

            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });

        bindViews();
        setupVideoSurface();
        setupControls();
        setupWatchContent();

        int orientation = getResources().getConfiguration().orientation;
        applyWatchLayoutForOrientation(orientation);
        applySystemBarsForOrientation(orientation);
        updateFullscreenIcon(orientation);

        mPresenter = PlaybackPresenter.instance(this);
        mPlayerInitializer = new Media3PlayerInitializer(this);
        mExoPlayerController = new Media3PlayerController(this, mPresenter);

        mPresenter.setView(this);
        mPresenter.onViewInitialized();

        createPlayerObjects();

        registerPipReceiver();

        mCastSessionManager = CastSessionManager.instance(this);
        mCastSessionManager.addListener(mCastListener);
        if (mCastSessionManager.isConnected()) {
            showCastOverlay();
        }
        updateCastIconTint();

        finishIfNothingToPlay(savedInstanceState);
    }

    private void finishIfNothingToPlay(@Nullable Bundle savedInstanceState) {
        if (mPresenter == null) {
            return;
        }

        if (!shouldFinishWithoutVideo(savedInstanceState != null,
                PlayerTransitionBridge.hasPending(), mPresenter.getVideo() != null)) {
            return;
        }

        NetPath.log("playback-activity abort=no-video reason=bare-launch");
        getViewManager().startDefaultView();
        finishReally();
    }

    static boolean shouldFinishWithoutVideo(boolean hasSavedState, boolean hasPendingTransition,
            boolean presenterHasVideo) {
        return !hasSavedState && !hasPendingTransition && !presenterHasVideo;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        cancelClose();
        super.onNewIntent(intent);
        if (PlayerTransitionBridge.hasPending()) {
            overridePendingTransition(0, 0);
        }
        if (routedInWhileLeaving(mIsResumed, mIsInPip, SystemPipBridge.isRestoreIntent(intent))) {
            mRoutedInWhileLeaving = true;
            logPip("routed-in while-leaving");
        }
    }

    private boolean mRoutedInWhileLeaving;
    private int mRoutedInRestoreAttempts;
    private final Runnable mRoutedInRestore = this::restoreRoutedInPip;

    private void restoreRoutedInPip() {
        if (!mIsInPip || isFinishing() || isDestroyed() || mRoutedInRestoreAttempts >= 3) {
            return;
        }
        mRoutedInRestoreAttempts++;
        logPip("restore reason=routed-in-during-pip-entry attempt=" + mRoutedInRestoreAttempts);
        SystemPipBridge.restore(this);
        Utils.postDelayed(mRoutedInRestore, 500);
    }

    private boolean isInPipModeNow() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
    }

    private static java.lang.ref.WeakReference<MobilePlaybackActivity> sCurrent = new java.lang.ref.WeakReference<>(null);

    public static boolean isCoveringScreens() {
        MobilePlaybackActivity player = sCurrent.get();
        return player != null && !player.mIsStopped && !player.isFinishing() && !player.isDestroyed()
                && !player.isInPipModeNow();
    }

    boolean isPinnedForRestore() {
        return pinnedForRestore(isInPipModeNow(), mPipStateStale);
    }

    static boolean pinnedForRestore(boolean platformSaysPinned, boolean stateStale) {
        return platformSaysPinned && !stateStale;
    }

    static boolean routedInWhileLeaving(boolean resumed, boolean inPip, boolean ownRestoreRequest) {
        return !resumed && !inPip && !ownRestoreRequest;
    }

    private void bindViews() {
        mContainer = findViewById(R.id.mobile_player_container);
        mVideoArea = findViewById(R.id.mobile_video_area);
        mZoomHintView = findViewById(R.id.mobile_player_zoom_hint);
        mPlayerView = findViewById(R.id.mobile_player_view);
        mPlayerView.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS);
        mPlayerView.setClipToOutline(true);
        mYouTubeOverlay = findViewById(R.id.mobile_player_yt_overlay);
        mControlsRoot = findViewById(R.id.mobile_controls_root);
        mTopScrim = findViewById(R.id.mobile_player_top_scrim);
        mBottomScrim = findViewById(R.id.mobile_player_bottom_scrim);
        mTitleView = findViewById(R.id.mobile_player_title);
        mBackButton = findViewById(R.id.mobile_player_back);
        mPlayPauseButton = findViewById(R.id.mobile_player_play_pause);
        mFullscreenButton = findViewById(R.id.mobile_player_fullscreen);
        mPositionView = findViewById(R.id.mobile_player_position);
        mDurationView = findViewById(R.id.mobile_player_duration);
        mLiveChip = findViewById(R.id.mobile_player_live);
        mLiveChip.setOnClickListener(v -> jumpToLiveEdge());
        mTimeBar = findViewById(R.id.mobile_player_time_bar);
        mBottomRow = findViewById(R.id.mobile_player_bottom_row);
        mTransport = findViewById(R.id.mobile_player_transport);
        mOptionsRow = findViewById(R.id.mobile_player_options);
        mTopPill = findViewById(R.id.mobile_player_top_pill);
        mLevelPill = findViewById(R.id.mobile_player_level_pill);
        mLevelIcon = findViewById(R.id.mobile_player_level_icon);
        mLevelBar = findViewById(R.id.mobile_player_level_bar);
        mProgressBar = findViewById(R.id.mobile_player_progress);
        mSpinnerShown = mProgressBar != null && mProgressBar.getVisibility() == View.VISIBLE;
        syncPlayPauseWithSpinner();
        mSetupHint = findViewById(R.id.mobile_player_setup_hint);
        mNoticeView = findViewById(R.id.mobile_player_notice);
        mCastButton = findViewById(R.id.mobile_player_cast);
        mCastOverlay = findViewById(R.id.mobile_cast_overlay);
        mCastOverlayTitle = findViewById(R.id.mobile_cast_overlay_title);
        mCastPlayPause = findViewById(R.id.mobile_cast_play_pause);
        mCastLiveChip = findViewById(R.id.mobile_cast_live);
        mCastTimeline = findViewById(R.id.mobile_cast_timeline);
        mCastPosition = findViewById(R.id.mobile_cast_position);
        mCastDuration = findViewById(R.id.mobile_cast_duration);
        mCastSeekBar = findViewById(R.id.mobile_cast_seekbar);
        mSubtitlesButton = findViewById(R.id.mobile_player_subtitles);
        mMoreButton = findViewById(R.id.mobile_player_more);
        mPrevButton = findViewById(R.id.mobile_player_previous);
        mNextButton = findViewById(R.id.mobile_player_next);
        mDebugViewGroup = findViewById(R.id.mobile_player_debug);

        mWatchRoot = findViewById(R.id.mobile_watch_root);
        mWatchRoot.addOnLayoutChangeListener(mWatchRootLayoutListener);
        mWatchScroll = findViewById(R.id.mobile_watch_scroll);
        mWatchContent = findViewById(R.id.mobile_watch_content);
        mWatchTitle = findViewById(R.id.mobile_watch_title);
        mWatchMeta = findViewById(R.id.mobile_watch_meta);
        mWatchMetaRow = findViewById(R.id.mobile_watch_meta_row);
        mWatchExpand = findViewById(R.id.mobile_watch_expand);
        mWatchDescription = findViewById(R.id.mobile_watch_description);
        mWatchLike = findViewById(R.id.mobile_watch_like);
        mWatchLikeIcon = findViewById(R.id.mobile_watch_like_icon);
        mWatchLikeCount = findViewById(R.id.mobile_watch_like_count);
        mWatchDislike = findViewById(R.id.mobile_watch_dislike);
        mWatchDislikeIcon = findViewById(R.id.mobile_watch_dislike_icon);
        mWatchDislikeCount = findViewById(R.id.mobile_watch_dislike_count);
        mWatchShare = findViewById(R.id.mobile_watch_share);
        mWatchSave = findViewById(R.id.mobile_watch_save);
        mWatchSaveIcon = findViewById(R.id.mobile_watch_save_icon);
        mWatchSaveLabel = findViewById(R.id.mobile_watch_save_label);
        mWatchDownload = findViewById(R.id.mobile_watch_download);
        mWatchDownloadIcon = findViewById(R.id.mobile_watch_download_icon);
        mWatchDownloadLabel = findViewById(R.id.mobile_watch_download_label);
        mWatchAvatar = findViewById(R.id.mobile_watch_avatar);
        mWatchChannelName = findViewById(R.id.mobile_watch_channel_name);
        mWatchSubs = findViewById(R.id.mobile_watch_subs);
        mWatchSubscribe = findViewById(R.id.mobile_watch_subscribe);
        mWatchRelatedLabel = findViewById(R.id.mobile_watch_related_label);
        mRelatedSkeleton = findViewById(R.id.mobile_watch_related_skeleton);
        mWatchRelated = findViewById(R.id.mobile_watch_related);
        mQueueCard = findViewById(R.id.mobile_watch_queue_card);
        mQueueHeader = findViewById(R.id.mobile_watch_queue_header);
        mQueueTitle = findViewById(R.id.mobile_watch_queue_title);
        mQueueSubtitle = findViewById(R.id.mobile_watch_queue_subtitle);
        mQueueChevron = findViewById(R.id.mobile_watch_queue_chevron);
        mQueueList = findViewById(R.id.mobile_watch_queue_list);
        mWatchCommentsEntry = findViewById(R.id.mobile_watch_comments_entry);
        mWatchCommentsCount = findViewById(R.id.mobile_watch_comments_count);
        mWatchChatEntry = findViewById(R.id.mobile_watch_chat_entry);
        mScrubChapterView = findViewById(R.id.mobile_player_scrub_chapter);
        mChapterButton = findViewById(R.id.mobile_player_chapter);
        mChapterButton.setOnClickListener(v -> showChaptersSheet());
    }

    private void setupControls() {
        mContainer.setSwipeListener(this);
        mContainer.setDragStartBoundView(mVideoArea);
        mSwipeLevels = new SwipeLevels(this, getWindow(), mContainer, new SwipeLevels.Pill() {
            private boolean mShown;

            @Override
            public void showLevel(int iconRes, float level) {
                if (mLevelIcon != null && mLevelBar != null) {
                    mLevelIcon.setImageResource(iconRes);
                    mLevelBar.setLevel(level);
                }
                if (!mShown) {
                    mShown = true;
                    fadePill(mLevelPill, true);
                }
            }

            @Override
            public void hideLevel() {
                mShown = false;
                fadePill(mLevelPill, false);
            }
        });

        mVideoArea.setPinchListener(this::onPinchZoom);

        ViewCompat.setOnApplyWindowInsetsListener(mControlsRoot, (v, insets) -> {
            applyControlsInsets();
            return insets;
        });
        mControlsRoot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or_, ob) -> {
            if ((r - l) != (or_ - ol) || (b - t) != (ob - ot)) {
                applyControlsInsets();
            }
            fitChapterButton();
        });
        mVideoArea.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or_, ob) -> {
            if ((r - l) != (or_ - ol) || (b - t) != (ob - ot)) {
                applyControlsInsets();
            }
        });

        mPlayerView.setOnClickListener(v -> {
            boolean reveal = !mControlsVisible;
            toggleControls();
            mInstantRevealAt = reveal ? android.os.SystemClock.uptimeMillis() : 0L;
        });
        mPlayerView.setInstantSingleTap(true);
        mPlayerView.setHoldListener(new DoubleTapPlayerViewImpl.HoldListener() {
            @Override
            public boolean onHoldStart(float x, float y) {
                return beginHoldSpeed();
            }

            @Override
            public void onHoldEnd() {
                endHoldSpeed();
            }
        });
        mPlayerView.setDoubleTapBeganListener(posX -> {
            if (mPlayer != null && doubleTapSeekForward(mPlayer, posX) != null) {
                hideControls();
            } else {
                toggleControls();
            }
        });

        mControlsRoot.setOnClickListener(v -> hideControls());
        mControlsRoot.setOnTouchListener((v, event) -> mPlayerView.onTouchEvent(event));
        mVideoArea.setTapRouter(new PinchZoomLayout.TapRouter() {
            @Override
            public boolean claimDown(android.view.MotionEvent down) {
                return isSecondTapOfReveal(down);
            }

            @Override
            public void route(android.view.MotionEvent event) {
                mPlayerView.onTouchEvent(event);
            }
        });
        mTimeBar.setTouchRouted(true);
        if (mWatchRoot instanceof WatchRootLayout) {
            ((WatchRootLayout) mWatchRoot).setTouchRouter(new SeekBandRouter());
        }

        mBackButton.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        mPlayPauseButton.setOnClickListener(v -> togglePlayPause());
        mFullscreenButton.setOnClickListener(v -> toggleFullscreen());

        if (mPrevButton != null) {
            mPrevButton.setOnClickListener(v -> {
                if (mPresenter != null) {
                    mPresenter.onPreviousClicked();
                }
                armAutoHide();
            });
        }
        if (mNextButton != null) {
            mNextButton.setOnClickListener(v -> {
                if (mPresenter != null) {
                    mPresenter.onNextClicked();
                }
                armAutoHide();
            });
        }

        if (mMoreButton != null) {
            mMoreButton.setOnClickListener(v -> openPlayerMenu());
        }

        if (mCastButton != null) {
            mCastButton.setOnClickListener(v -> openCastPicker());
        }
        setupCastOverlay();
        mSubtitlesButton.setOnClickListener(v -> toggleCaptions());
        mSubtitlesButton.setOnLongClickListener(v -> {
            showCaptionsSheet();
            return true;
        });

        mTimeBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                mScrubbing = true;
                endUserSeekBurst();
                cancelAutoHide();
                updateScrubLabel(position);
                setScrubChrome(true);
                if (mStoryboardManager != null) {
                    mStoryboardManager.init(getVideo());
                }
                if (mSeekOverlayPreview != null) {
                    mSeekOverlayPreview.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
                updateScrubLabel(position);
                if (mStoryboardManager != null && mSeekOverlayPreview != null) {
                    mStoryboardManager.loadPreviewForPosition(position, bitmap -> {
                        if (mScrubbing && mSeekOverlayPreview != null && bitmap != null) {
                            mSeekOverlayPreview.setImageBitmap(bitmap);
                        }
                    });
                }
            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                mScrubbing = false;
                setScrubChrome(false);
                if (mSeekOverlayPreview != null) {
                    mSeekOverlayPreview.setVisibility(View.GONE);
                    mSeekOverlayPreview.setImageDrawable(null);
                }
                if (!canceled) {
                    setTextIfChanged(mPositionView, formatTime(position));
                    updateChapterButton(position);
                }
                if (!canceled && mExoPlayerController != null) {
                    mExoPlayerController.setPositionMs(position);
                }
                armAutoHide();
            }
        });
        mTimeBar.setCancelListener(armed ->
                showTopPill(armed ? getString(R.string.mobile_player_release_to_cancel) : null));

        mControlsVisible = true;
        mControlsRoot.setVisibility(View.VISIBLE);
        mControlsRoot.setAlpha(1f);
        armAutoHide();
    }

    private void setupWatchContent() {
        long touchPrefetchMs = com.newtube.mobile.player.SwitchExperiments.touchPrefetchStillMs();
        mRelatedAdapter = new RelatedVideoAdapter(this::onRelatedClicked,
                touchPrefetchMs > 0 ? this::onRelatedPressed : null, touchPrefetchMs);
        mWatchRelated.setLayoutManager(new LinearLayoutManager(this));
        mWatchRelated.setNestedScrollingEnabled(false);
        mWatchRelated.setHasFixedSize(false);
        mWatchRelated.setItemAnimator(null);
        mWatchRelated.setAdapter(mRelatedAdapter);

        mQueueAdapter = new RelatedVideoAdapter(this::onRelatedClicked);
        mQueueList.setLayoutManager(new LinearLayoutManager(this));
        mQueueList.setHasFixedSize(false);
        mQueueList.setAdapter(mQueueAdapter);
        mQueueList.setMaxHeight(Math.round(getResources().getDisplayMetrics().heightPixels * 0.5f));
        mQueueHeader.setOnClickListener(v -> toggleQueueExpanded());

        mWatchMetaRow.setOnClickListener(v -> toggleDescription());

        mWatchLike.setOnClickListener(v -> {
            if (!WatchActionFeedback.blockIfSignedOut(this, R.string.mobile_sign_in_to_rate)) {
                onRateTapped(R.id.action_thumbs_up);
            }
        });
        mWatchDislike.setOnClickListener(v -> {
            if (!WatchActionFeedback.blockIfSignedOut(this, R.string.mobile_sign_in_to_rate)) {
                onRateTapped(R.id.action_thumbs_down);
            }
        });
        mWatchSubscribe.setOnClickListener(v -> onSubscribeTapped());
        mWatchShare.setOnClickListener(v -> shareCurrentVideo());
        mWatchSave.setOnClickListener(v -> {
            if (!WatchActionFeedback.blockIfSignedOut(this, R.string.msg_sign_in_to_save)) {
                openPlayerOption(R.id.action_playlist_add, false);
            }
        });
        mWatchDownload.setOnClickListener(v -> onDownloadTapped());
        DownloadRegistry.instance(this).addListener(mDownloadsListener);

        View channelRow = findViewById(R.id.mobile_watch_channel_row);
        if (channelRow != null) {
            channelRow.setOnClickListener(v -> openCurrentChannel());
        }

        mCommentsPanel = new CommentsPanel(this, findViewById(R.id.mobile_comments_panel), mCommentsHost);
        if (mWatchCommentsEntry != null) {
            mWatchCommentsEntry.setOnClickListener(v -> onCommentsEntryClicked());
        }
        if (mWatchChatEntry != null) {
            mWatchChatEntry.setOnClickListener(v -> onChatEntryClicked());
        }

        mWatchScroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldX, oldY) -> {
                    View child = v.getChildAt(0);
                    if (child == null) {
                        return;
                    }
                    int distanceToBottom = child.getBottom() - (v.getHeight() + scrollY);
                    if (distanceToBottom <= SUGGESTIONS_PAGE_THRESHOLD_PX) {
                        maybePageSuggestions();
                    }
                });

        updateButtonVisual(R.id.action_thumbs_up, BUTTON_OFF);
        updateButtonVisual(R.id.action_thumbs_down, BUTTON_OFF);
        updateButtonVisual(R.id.action_subscribe, BUTTON_OFF);
    }

    private void applyWatchLayoutForOrientation(int orientation) {
        if (mVideoArea == null) {
            return;
        }

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mVideoArea.getLayoutParams();
        mVideoArea.setPinchEnabled(orientation == Configuration.ORIENTATION_LANDSCAPE);

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            lp.height = LinearLayout.LayoutParams.MATCH_PARENT;
            lp.weight = 0;
            mVideoArea.setLayoutParams(lp);
            if (mExoPlayerController != null) {
                mExoPlayerController.clearInlineViewport("fullscreen");
            }
            if (mWatchScroll != null) {
                mWatchScroll.setVisibility(View.GONE);
            }
            if (mCommentsPanel != null) {
                mCommentsPanel.setSuspended(true);
            }
            if (mTitleView != null) {
                mTitleView.setVisibility(View.VISIBLE);
            }
        } else {
            Configuration config = getResources().getConfiguration();
            int width = Math.round(config.screenWidthDp
                    * getResources().getDisplayMetrics().density);
            if (width > 0) {
                lp.height = Math.round(width * 9f / 16f);
                lp.weight = 0;
                mVideoArea.setLayoutParams(lp);
            }
            if (mWatchScroll != null) {
                mWatchScroll.setVisibility(View.VISIBLE);
            }
            if (mCommentsPanel != null) {
                mCommentsPanel.setSuspended(false);
            }
            if (mTitleView != null) {
                mTitleView.setVisibility(View.GONE);
            }
        }
    }

    private void onPinchZoom(boolean zoomIn) {
        int mode = zoomIn ? RESIZE_MODE_FIT_BOTH : RESIZE_MODE_DEFAULT;
        if (mode != getResizeMode()) {
            Haptics.tick(mVideoArea);
        }
        PlayerData playerData = PlayerData.instance(this);
        playerData.setResizeMode(mode);
        playerData.setZoomPercents(-1);
        animateResizeMode(mode);
        showZoomHint(zoomIn ? R.string.mobile_player_zoom_fill : R.string.mobile_player_zoom_original);
    }

    private void animateResizeMode(int mode) {
        ViewGroup contentFrame = mPlayerView != null ? mPlayerView.getContentFrame() : null;
        if (contentFrame == null || contentFrame.getWidth() == 0 || getResizeMode() == mode) {
            setResizeMode(mode);
            return;
        }
        final float visualWidth = contentFrame.getWidth() * contentFrame.getScaleX();
        contentFrame.animate().cancel();
        setResizeMode(mode);
        OneShotPreDrawListener.add(contentFrame, () -> {
            if (contentFrame.getWidth() == 0) {
                return;
            }
            float startScale = visualWidth / contentFrame.getWidth();
            contentFrame.setScaleX(startScale);
            contentFrame.setScaleY(startScale);
            contentFrame.animate().scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator()).start();
        });
    }

    private final Runnable mHideZoomHint = new Runnable() {
        @Override
        public void run() {
            mZoomHintView.animate().alpha(0f).setDuration(250)
                    .withEndAction(() -> mZoomHintView.setVisibility(View.GONE)).start();
        }
    };

    private void showZoomHint(int textRes) {
        if (mZoomHintView == null) {
            return;
        }
        mZoomHintView.removeCallbacks(mHideZoomHint);
        mZoomHintView.animate().cancel();
        mZoomHintView.setText(textRes);
        if (mZoomHintView.getVisibility() != View.VISIBLE) {
            mZoomHintView.setAlpha(0f);
            mZoomHintView.setVisibility(View.VISIBLE);
        }
        mZoomHintView.animate().alpha(1f).setDuration(120).start();
        mZoomHintView.postDelayed(mHideZoomHint, 900);
    }

    private void createPlayerObjects() {
        DefaultTrackSelector trackSelector = mPlayerInitializer.createTrackSelector();
        mExoPlayerController.setTrackSelector(trackSelector);

        if (mBackgroundAudioMode) {
            mExoPlayerController.setVideoTrackDisabled(true);
        }

        mPlayer = mPlayerInitializer.createPlayer(
                trackSelector, mExoPlayerController.getMediaSourceFactory().getBandwidthMeter());

        if (BuildConfig.DEBUG) {
            mPlayer.addAnalyticsListener(new androidx.media3.exoplayer.util.EventLogger());
            mPlayer.addAnalyticsListener(new NetPathLoadListener(this));
        } else if (BuildConfig.BENCHMARK) {
            mPlayer.addListener(new Player.Listener() {
                private boolean tracksLogged;

                @Override public void onMediaItemTransition(androidx.media3.common.MediaItem item, int reason) {
                    tracksLogged = false;
                }

                @Override public void onPlaybackStateChanged(int state) {
                    String name = state == Player.STATE_READY ? "READY"
                            : state == Player.STATE_BUFFERING ? "BUFFERING"
                            : state == Player.STATE_ENDED ? "ENDED" : "IDLE";
                    com.liskovsoft.smartyoutubetv2.common.misc.NetPath.log("benchmark-state state=" + name
                            + " position-ms=" + (mPlayer != null ? mPlayer.getCurrentPosition() : -1)
                            + " elapsed-realtime-ms=" + android.os.SystemClock.elapsedRealtime());
                }

                @Override public void onTracksChanged(androidx.media3.common.Tracks tracks) {
                    if (tracksLogged || tracks.getGroups().isEmpty()) return;
                    tracksLogged = true;
                    for (androidx.media3.common.Tracks.Group group : tracks.getGroups()) {
                        for (int index = 0; index < group.length; index++) {
                            if (!group.isTrackSelected(index)) continue;
                            androidx.media3.common.Format format = group.getTrackFormat(index);
                            com.liskovsoft.smartyoutubetv2.common.misc.NetPath.log("benchmark-track type="
                                    + group.getType() + " width=" + format.width + " height=" + format.height
                                    + " bitrate=" + format.bitrate + " mime=" + format.sampleMimeType);
                        }
                    }
                }
            });
        }
        mPlayer.setPlayWhenReady(true);
        mBenchTicker = com.newtube.mobile.player.BenchTicker.startIfEnabled(
                BuildConfig.DEBUG || BuildConfig.BENCHMARK, mPlayer, () -> {
                    Video video = getVideo();
                    return video != null ? video.videoId : null;
                });

        mPlayer.setSeekParameters(MOBILE_SEEK_PARAMETERS);

        mExoPlayerController.setPlayer(mPlayer);
        mExoPlayerController.attachPreloader(mPlayerInitializer.getPreloadManagerBuilder(),
                mPlayerInitializer.getPreloadTrackSelector());
        mPlayerView.setPlayer(mPlayer);

        if (mSessionSurface != null) {
            mPlayer.setVideoSurface(mSessionSurface);
        }
        mPlayerView.setShutterBackgroundColor(Color.TRANSPARENT);

        mYouTubeOverlay
                .performListener(new YouTubeOverlay.PerformListener() {
                    @Override
                    public void onAnimationStart() {
                        mYouTubeOverlay.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onAnimationEnd() {
                        endUserSeekBurst();
                        mYouTubeOverlay.setVisibility(View.GONE);
                    }

                    @Override
                    public Boolean shouldForward(Player player, DoubleTapPlayerView playerView, float posX) {
                        Boolean forward = doubleTapSeekForward(player, posX);
                        if (forward != null) {
                            setUserSeekDirection(forward);
                        }
                        return forward;
                    }
                })
                .player(mPlayer)
                .playerView(mPlayerView);
        mPlayerView.controller(mYouTubeOverlay);

        mPlayer.addListener(mUiPlayerListener);

        createSubtitleManager();

        mPresenter.onEngineInitialized();

        if (mServiceBound && mPlaybackService != null) {
            mPlaybackService.attachPlayer(mPlayer, mPresenter, buildContentIntent());
        } else {
            bindPlaybackService();
        }

        startProgressUpdates();
        updatePlayPauseIcon();
    }

    private void destroyPlayerObjects() {
        if (mPlayer == null) {
            return;
        }

        stopProgressUpdates();
        mPlayer.removeListener(mUiPlayerListener);

        if (mDebugInfoManager != null) {
            mDebugInfoManager.show(false);
            mDebugInfoManager = null;
        }
        mSubtitleManager = null;

        if (mPlaybackService != null) {
            mPlaybackService.detachPlayer();
        }

        if (mPresenter.getView() == null || mPresenter.getView() == this) {
            mPresenter.onEngineReleased();
        }

        if (mBenchTicker != null) {
            mBenchTicker.stop();
            mBenchTicker = null;
        }
        mPlayerView.setPlayer(null);
        mExoPlayerController.release();
        mPlayer = null;
    }
    @Override
    protected void onStart() {
        super.onStart();
        mIsStopped = false;
        startProgressUpdates();
        if (mControlsVisible) {
            armAutoHide();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        PlayerTransitionBridge.LaunchSnapshot launch = PlayerTransitionBridge.take();

        mIsResumed = true;
        mSuppressAutoPip = false;
        mDismissDragActive = false;
        if (mPipEnterPending) {
            logPip("enter-aborted-restored inPip=" + (isInPipModeNow() ? "y" : "n"));
            mPipStateStale = isInPipModeNow();
            int orientation = getResources().getConfiguration().orientation;
            applyWatchLayoutForOrientation(orientation);
            applySystemBarsForOrientation(orientation);
            showControlsInternal(false);
            SystemPipBridge.onPipEnded();
        }
        mPipEnterPending = false;
        updateSwipeBrightness();
        if (!mIsInPip && mTimeBar != null && mTimeBar.getVisibility() != View.VISIBLE) {
            mTimeBar.setVisibility(View.VISIBLE);
            updateSeekBarLine();
        }
        mPipDismissPending = false;
        mRoutedInWhileLeaving = false;
        setBackgroundAudioMode(false);
        if (!mIsInPip && mExoPlayerController != null) {
            mExoPlayerController.clearSmallWindowViewport("resume");
        }
        updatePipActions();

        boolean fromMini = MiniPlayerBridge.isActive();
        Rect miniBounds = fromMini ? MiniPlayerBridge.takeMiniBounds() : null;
        if (fromMini) {
            Bitmap handoff = MiniPlayerBridge.takeHandoffStill();
            if (handoff != null) {
                showHandoffStill(handoff);
            } else {
                mStillAwaitFrame = true;
            }
            reattachVideoTexture();
        }
        MiniPlayerBridge.deactivate();

        if (launch != null) {
            showHandoffStill(launch.frame);
            mStillAwaitFrame = false;
            if (fromMini) {
                armStillForReady();
            }
            startOpenMorph(launch.sourceBounds, 300);
            FrameGate.afterNextFrame(mContainer, HOST_CARD_FOLD_WAIT_MS,
                    () -> MiniPlayerBridge.foldHostCard(null));
        } else if (fromMini && mContainer != null) {
            overridePendingTransition(0, 0);
            mContainer.setVisibility(View.INVISIBLE);
            mMorphStartPending = true;
            mContainer.post(() -> {
                mMorphStartPending = false;
                if (miniBounds != null) {
                    computeMorphTarget(miniBounds);
                } else {
                    computeMorphTarget();
                }
                applyMorph(1f);
                mContainer.setVisibility(View.VISIBLE);
                FrameGate.afterNextFrame(mContainer, HOST_CARD_FOLD_WAIT_MS,
                        () -> MiniPlayerBridge.foldHostCard(() -> {
                            if (!isFinishing() && !isDestroyed() && mMorphAnimator == null) {
                                animateMorph(0f, 250, Motion.EMPHASIZED, this::resetMorph);
                            }
                        }));
            });
        } else {
            MiniPlayerBridge.foldHostCard(null);
        }

        if (mPresenter != null) {
            mPresenter.onViewResumed();
        }

        if (mPlaybackService != null) {
            mPlaybackService.ensureForeground();
        }

        applySystemBarsForOrientation(getResources().getConfiguration().orientation);
        if (mMorphStartPending) {
            clearBackdropForMorphStart();
        }
        updateOrientationHandBackListener();
    }

    private void clearBackdropForMorphStart() {
        if (mWatchScroll != null && mWatchScroll.getBackground() != null) {
            mWatchScroll.getBackground().mutate().setAlpha(0);
        }
        setWindowBackdropAlpha(0f);
    }

    @Override
    protected void onPause() {
        if (mPresenter != null) {
            mPresenter.onViewPaused();
        }

        mIsResumed = false;
        updateOrientationHandBackListener();

        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();

        mIsStopped = true;
        stopProgressUpdates();
        cancelAutoHide();
        if (mExoPlayerController != null && mExoPlayerController.isHoldSpeedOn()) {
            endHoldSpeed();
        }

        Utils.removeCallbacks(mReleaseImageRequests);
        mImageRequestsHeld = false;

        if (mPipDismissPending) {
            mPipDismissPending = false;
            finishFromPipDismiss();
            return;
        }

        if (mPlayer != null && !mIsInPip && !mSuppressAutoPip && !isFinishing()) {
            setBackgroundAudioMode(true);
        }

        if (mContainer != null && mMorphFraction != 0f) {
            resetMorph();
            if (mCommentsPanel != null) {
                mCommentsPanel.closeImmediately();
            }
        }
        Utils.removeCallbacks(mPrefetchComments);
    }

    private void setBackgroundAudioMode(boolean enabled) {
        mBackgroundAudioMode = enabled;
        if (mExoPlayerController != null) {
            mExoPlayerController.setVideoTrackDisabled(enabled);
            mExoPlayerController.onBackgroundAudio(enabled);
        }

        if (enabled) {
            if (mLiveChatAction != null) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.d(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG,
                            "live-chat poll stop (background audio)");
                }
                stopLiveChatStream();
            }
        } else if (mChatObserver != null && mChatReceiver == null
                && mLiveChatAction == null && mLiveChatKey != null) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG,
                        "live-chat poll resume (foreground)");
            }
            startLiveChatStream();
        }
    }

    @Override
    protected void onDestroy() {
        DownloadRegistry.instance(this).removeListener(mDownloadsListener);
        cancelAutoHide();
        mOrientationHandBack.disarm();
        updateOrientationHandBackListener();
        hideRelatedSkeleton();
        Utils.removeCallbacks(mReleaseImageRequests);
        Utils.removeCallbacks(mReleaseWatchMetadata);
        Utils.removeCallbacks(mPrefetchComments);
        if (mCommentsPanel != null) {
            mCommentsPanel.release();
        }
        mRelatedRenderGate.cancelPending();
        if (mVideoStill != null) {
            Glide.with(getApplicationContext()).clear(mVideoStill);
        }
        if (mSeekOverlayPreview != null) {
            Glide.with(getApplicationContext()).clear(mSeekOverlayPreview);
        }

        if (mCastSessionManager != null) {
            mCastSessionManager.removeListener(mCastListener);
        }
        Utils.removeCallbacks(mCastProgressRunnable);

        if (mWatchRoot != null) {
            mWatchRoot.removeOnLayoutChangeListener(mWatchRootLayoutListener);
        }
        SystemPipBridge.detach(this);

        RxHelper.disposeActions(mLiveChatAction);

        MiniPlayerBridge.deactivate();

        destroyPlayerObjects();
        releaseSessionTexture();

        unbindPlaybackService();
        unregisterPipReceiver();

        if (mPresenter != null && mPresenter.getView() == this) {
            mPresenter.onViewDestroyed();
        }

        super.onDestroy();
    }
    /** Set while minimizing into the in-app mini-player, so auto-PiP keeps its hands off. */
    private boolean mSuppressAutoPip;

    private boolean mDismissDragActive;

    private boolean mPipEnterPending;

    private boolean mPipStateStale;

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        if (mSuppressAutoPip) {
            logPip("leave-skip reason=mini-handoff");
            return;
        }
        if (mDismissDragActive) {
            logPip("leave-skip reason=minimize-drag");
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || mIsInPip || isFinishing()) {
            logPip("leave-skip reason=state");
            return;
        }
        if (!Helpers.isPictureInPictureSupported(this)) {
            logPip("leave-skip reason=unsupported");
            return;
        }
        if (mPlayer == null || !isPlaying()) {
            logPip("leave-skip reason=not-playing");
            return;
        }
        if (getViewManager() != null && getViewManager().isNewViewPending()) {
            logPip("leave-skip reason=internal-navigation");
            return;
        }
        if (BackgroundModePolicy.onLeave(getBackgroundMode()) != BackgroundModePolicy.Action.PIP) {
            logPip("leave-skip reason=audio-mode");
            return;
        }

        logPip("leave-enter");
        enterPipMode();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mIsInPip) {
            if (mExoPlayerController != null) {
                mExoPlayerController.setSmallWindowViewport("pip", newConfig);
            }
            return;
        }

        refreshContentInsets();
        applyWatchLayoutForOrientation(newConfig.orientation);
        applySystemBarsForOrientation(newConfig.orientation);
        updateFullscreenIcon(newConfig.orientation);
        onFullscreenSwipeConfigured();
        if (mVideoArea != null) {
            mVideoArea.post(this::updatePipActions);
        }
    }

    @Override
    protected boolean isStatusBarOverDarkContent() {
        return true;
    }

    @Override
    protected boolean onThemeChanged(int night) {
        for (java.lang.ref.WeakReference<BottomSheetDialog> ref : mShownSheets) {
            BottomSheetDialog sheet = ref.get();
            if (sheet != null && sheet.isShowing()) {
                sheet.dismiss();
            }
        }
        mShownSheets.clear();
        for (androidx.fragment.app.Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof androidx.fragment.app.DialogFragment) {
                ((androidx.fragment.app.DialogFragment) fragment).dismissAllowingStateLoss();
            }
        }
        ThemeMode.syncResources(this, night);
        View liveArea = findViewById(R.id.mobile_watch_area);
        if (liveArea == null) {
            return true;
        }
        int scrollY = mWatchScroll != null ? mWatchScroll.getScrollY() : 0;
        View fresh = getLayoutInflater().inflate(R.layout.activity_mobile_playback, new FrameLayout(this), false);
        View freshArea = fresh.findViewById(R.id.mobile_watch_area);
        List<RecyclerView> lists = new ArrayList<>();
        int skipped = freshArea != null ? ThemeRefresh.copyColors(liveArea, freshArea, lists) : 1;
        if (mCommentsPanel != null) {
            mCommentsPanel.onThemeChanged();
        }
        for (RecyclerView list : lists) {
            ThemeRefresh.rebuildRows(list);
        }
        for (int id : new int[] {R.id.action_thumbs_up, R.id.action_thumbs_down, R.id.action_playlist_add,
                R.id.action_subscribe}) {
            updateButtonVisual(id, getButtonState(id));
        }
        updateDownloadPill();
        if (!mIsInPip) {
            applySystemBarsForOrientation(getResources().getConfiguration().orientation);
        }
        if (mWatchScroll != null && scrollY > 0) {
            mWatchScroll.post(() -> mWatchScroll.scrollTo(0, scrollY));
        }
        NetPath.log("theme player in-place lists=" + lists.size() + " skipped=" + skipped);
        return true;
    }

    private void handleBack() {
        if (mClosing) {
            return;
        }
        if (!mIsInPip && !mPipEnterPending
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mBackPreview = false;
            toggleFullscreen();
            return;
        }
        if (mBackPreview || canMinimizeByBack()) {
            minimizeByBack();
            return;
        }
        if (canAnimateClose()) {
            animateCloseThenFinish();
            return;
        }
        if (mPresenter != null) {
            mPresenter.onFinish();
        }

        finish();
    }

    private static final float BACK_PREVIEW_FRACTION = 0.2f;
    private static final long BACK_MINIMIZE_MS = 300;
    private boolean mBackPreview;

    private boolean canMinimizeByBack() {
        return mPlayer != null && mContainer != null && mVideoArea != null && !mIsInPip && !mPipEnterPending
                && !mScrubbing && mMorphAnimator == null && mMorphFraction == 0f
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private void onBackGestureStarted() {
        mBackPreview = false;
        if (mClosing || !canMinimizeByBack()) {
            return;
        }
        beginMinimizeMorph();
        mBackPreview = true;
    }

    private void onBackGestureProgressed(float progress) {
        if (mBackPreview) {
            applyMorph(BACK_PREVIEW_FRACTION * Motion.STANDARD_DECELERATE.getInterpolation(progress));
        }
    }

    private void onBackGestureCancelled() {
        if (mBackPreview) {
            mBackPreview = false;
            animateMorph(0f, 180, Motion.STANDARD, this::resetMorph);
        }
    }

    private void minimizeByBack() {
        boolean moving = mBackPreview;
        mBackPreview = false;
        if (!moving) {
            beginMinimizeMorph();
        }
        float remaining = Math.max(0f, 1f - mMorphFraction);
        long durationMs = Math.max(120L, Math.round(BACK_MINIMIZE_MS * remaining));
        animateMorph(1f, durationMs, moving ? Motion.EMPHASIZED_DECELERATE : Motion.EMPHASIZED,
                this::minimizeByDrag);
    }

    private static final long CLOSE_MS = 200;
    private boolean mClosing;
    @Nullable
    private ValueAnimator mCloseAnimator;
    private float mVolumeBeforeClose = 1f;

    private boolean canAnimateClose() {
        return mContainer != null && mVideoArea != null && !mIsInPip && !mPipEnterPending
                && mMorphAnimator == null && mMorphFraction == 0f
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT
                && !isTaskRoot() && getViewManager().hasParentView(this)
                && !MiniPlayerBridge.isActive();
    }

    private void animateCloseThenFinish() {
        mClosing = true;
        if (mPlayer != null) {
            mVolumeBeforeClose = mExoPlayerController.getVolume();
            mExoPlayerController.setVolume(0f);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        mVideoArea.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        final float drop = 0.08f * mContainer.getHeight();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        mCloseAnimator = animator;
        animator.setDuration(CLOSE_MS);
        animator.setInterpolator(Motion.EMPHASIZED_ACCELERATE);
        animator.addUpdateListener(a -> applyCloseProgress((float) a.getAnimatedValue(), drop));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCloseAnimator == animator) {
                    mCloseAnimator = null;
                }
                if (!mCancelled && !isFinishing() && !isDestroyed()) {
                    if (mPresenter != null) {
                        mPresenter.onFinish();
                    }
                    finish();
                    overridePendingTransition(0, 0);
                }
            }
        });
        animator.start();
    }

    private void applyCloseProgress(float p, float drop) {
        float content = Math.max(0f, 1f - p * 3f);
        if (mWatchContent != null) {
            mWatchContent.setAlpha(content);
        }
        if (mControlsRoot != null && mControlsRoot.getVisibility() == View.VISIBLE) {
            mControlsRoot.setAlpha(content);
        }
        if (mTimeBar != null) {
            mTimeBar.setAlpha(content);
        }
        if (mCommentsPanel != null) {
            mCommentsPanel.setMorphAlpha(content);
        }
        float backdrop = 1f - p;
        if (mWatchScroll != null && mWatchScroll.getBackground() != null) {
            mWatchScroll.getBackground().mutate().setAlpha(Math.round(255f * backdrop));
        }
        setWindowBackdropAlpha(backdrop);
        mVideoArea.setTranslationY(drop * p);
        mVideoArea.setAlpha(1f - p);
    }

    private void cancelClose() {
        if (!mClosing) {
            return;
        }
        mClosing = false;
        if (mCloseAnimator != null) {
            mCloseAnimator.cancel();
            mCloseAnimator = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        if (mPlayer != null) {
            mExoPlayerController.setVolume(mVolumeBeforeClose);
        }
        mVideoArea.setLayerType(View.LAYER_TYPE_NONE, null);
        mVideoArea.setAlpha(1f);
        resetMorph();
    }

    private void finishFromPipDismiss() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (BuildConfig.DEBUG) {
            android.util.Log.d(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG,
                    "pip dismissed -> closing playback");
        }
        if (mPresenter != null) {
            mPresenter.onFinish();
        }
        getViewManager().removeTop(this);
        finishAndRemoveTask();
    }

    @Override
    protected void applyFullscreenModeIfNeeded() {
        applySystemBarsForOrientation(getResources().getConfiguration().orientation);
    }

    @Override
    protected boolean shouldInsetContentForSystemBars() {
        return getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
    }

    private void applyControlsInsets() {
        if (mControlsRoot == null) {
            return;
        }
        int left = 0, top = 0, right = 0, bottom = 0;
        if (isLandscape()) {
            WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(mControlsRoot);
            Insets bars = rootInsets != null
                    ? rootInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()) : Insets.NONE;
            View box = mVideoArea != null && mVideoArea.getWidth() > 0 ? mVideoArea : mControlsRoot;
            int width = box.getWidth() > 0
                    ? box.getWidth() : getResources().getDisplayMetrics().widthPixels;
            int height = box.getHeight() > 0
                    ? box.getHeight() : getResources().getDisplayMetrics().heightPixels;
            int strip = controlsStrip(width, height, mVideoAspect, getResizeMode());
            left = Math.max(bars.left, strip);
            right = Math.max(bars.right, strip);
            top = bars.top;
            bottom = bars.bottom;
        }
        if (mControlsRoot.getPaddingLeft() != left || mControlsRoot.getPaddingTop() != top
                || mControlsRoot.getPaddingRight() != right || mControlsRoot.getPaddingBottom() != bottom) {
            mControlsRoot.setPadding(left, top, right, bottom);
        }
        bleedScrim(mTopScrim, left, top, right, 0);
        bleedScrim(mBottomScrim, left, 0, right, bottom);
        applySeekBarLayout(left, top, right, bottom);
    }

    private void applySeekBarLayout(int left, int top, int right, int bottom) {
        if (mTimeBar == null) {
            return;
        }
        boolean inline = !isLandscape();
        int side = inline ? 0 : dp(12);
        int lift = inline ? 0 : dp(8);
        setMargins(mTimeBar, left + side, 0, right + side, bottom + lift);
        mTimeBar.setTrackAtBottom(inline);
        updateSeekBarLine();
        setMargins(mBottomRow, 0, 0, 0,
                getResources().getDimensionPixelSize(R.dimen.mobile_player_bottom_row_margin) + lift);
        setMargins(mScrubChapterView, 0, 0, 0,
                getResources().getDimensionPixelSize(R.dimen.mobile_player_scrub_pill_margin) + bottom + lift);
        setMargins(mTopPill, 0, top + dp(12), 0, 0);
        setMargins(mLevelPill, 0, top + dp(12), 0, 0);
        setMargins(mNoticeView, 0, 0, 0, dp(60) + bottom + lift);
    }

    private void updateSeekBarLine() {
        if (mTimeBar == null) {
            return;
        }
        Video video = getVideo();
        boolean casting = mCastOverlay != null && mCastOverlay.getVisibility() == View.VISIBLE;
        boolean line = !isLandscape() && !mIsInPip && !mPipEnterPending && !casting
                && (video == null || !video.isLive);
        if (line != mTimeBar.isLineWhenHidden()) {
            mTimeBar.setLineWhenHidden(line);
            if (!mControlsVisible) {
                startProgressUpdates();
            }
        }
    }

    private boolean isSecondTapOfReveal(android.view.MotionEvent down) {
        return mInstantRevealAt != 0L && mControlsVisible
                && down.getEventTime() - mInstantRevealAt
                        <= android.view.ViewConfiguration.getDoubleTapTimeout();
    }

    private final class SeekBandRouter implements WatchRootLayout.TouchRouter {
        private final int[] mRootAt = new int[2];
        private final int[] mBarAt = new int[2];
        private final int[] mViewAt = new int[2];
        private float mDx;
        private float mDy;

        @Override
        public boolean claimDown(android.view.MotionEvent down) {
            if (mTimeBar == null || mVideoArea == null || !mVideoArea.getMatrix().isIdentity()
                    || isSecondTapOfReveal(down)) {
                return false;
            }
            mWatchRoot.getLocationInWindow(mRootAt);
            mTimeBar.getLocationInWindow(mBarAt);
            mDx = mRootAt[0] - mBarAt[0];
            mDy = mRootAt[1] - mBarAt[1];
            if (!mTimeBar.isInTouchBand(down.getX() + mDx, down.getY() + mDy)) {
                return false;
            }
            float windowX = down.getX() + mRootAt[0];
            float windowY = down.getY() + mRootAt[1];
            return !isOn(mFullscreenButton, windowX, windowY) && !isOn(mChapterButton, windowX, windowY)
                    && !isOn(mLiveChip, windowX, windowY);
        }

        @Override
        public void route(android.view.MotionEvent event) {
            android.view.MotionEvent local = android.view.MotionEvent.obtain(event);
            local.offsetLocation(mDx, mDy);
            mTimeBar.onRoutedTouchEvent(local);
            local.recycle();
        }

        private boolean isOn(@Nullable View view, float windowX, float windowY) {
            if (view == null || !view.isShown() || view.getAlpha() <= 0f) {
                return false;
            }
            view.getLocationInWindow(mViewAt);
            return windowX >= mViewAt[0] && windowX < mViewAt[0] + view.getWidth()
                    && windowY >= mViewAt[1] && windowY < mViewAt[1] + view.getHeight();
        }
    }

    private static void setMargins(@Nullable View view, int left, int top, int right, int bottom) {
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (lp.leftMargin != left || lp.topMargin != top || lp.rightMargin != right || lp.bottomMargin != bottom) {
            lp.setMargins(left, top, right, bottom);
            view.setLayoutParams(lp);
        }
    }

    private static void bleedScrim(@Nullable View scrim, int left, int top, int right, int bottom) {
        if (scrim == null || !(scrim.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) scrim.getLayoutParams();
        if (lp.leftMargin != -left || lp.topMargin != -top
                || lp.rightMargin != -right || lp.bottomMargin != -bottom) {
            lp.setMargins(-left, -top, -right, -bottom);
            scrim.setLayoutParams(lp);
        }
    }

    static int controlsStrip(int width, int height, float videoAspect, int resizeMode) {
        int strip16x9 = Math.max(0, Math.round((width - height * 16f / 9f) / 2f));
        if (resizeMode != PlayerConstants.RESIZE_MODE_DEFAULT
                && resizeMode != PlayerConstants.RESIZE_MODE_FIT_HEIGHT) {
            return 0;
        }
        if (!(videoAspect > 0f)) {
            return strip16x9;
        }
        return Math.min(strip16x9, Math.max(0, Math.round((width - height * videoAspect) / 2f)));
    }

    private void applySystemBarsForOrientation(int orientation) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            applyDisplayCutoutMode(true);
            Helpers.makeActivityFullscreen2(this);
            if (ThemeMode.isLight(this)) {
                getWindow().getDecorView().setBackgroundColor(Color.BLACK);
            }
        } else {
            applyDisplayCutoutMode(false);
            applyMobileSystemBars();
            Window window = getWindow();
            if (ThemeMode.isLight(this)) {
                window.getDecorView().setBackground(new WatchBackdropDrawable(window.getDecorView(),
                        Color.BLACK, getColorInt(R.color.mobile_color_background)));
            } else {
                window.getDecorView().setBackgroundColor(Color.BLACK);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setStatusBarContrastEnforced(false);
            }
            window.setStatusBarColor(Color.BLACK);
        }

        if (mControlsRoot != null) {
            ViewCompat.requestApplyInsets(mControlsRoot);
        }
        if (mWatchRoot != null) {
            ViewCompat.requestApplyInsets(mWatchRoot);
        }
    }

    private void applyDisplayCutoutMode(boolean fullscreen) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }

        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        int desired;
        if (!fullscreen) {
            desired = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            desired = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else {
            desired = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        if (attributes.layoutInDisplayCutoutMode != desired) {
            attributes.layoutInDisplayCutoutMode = desired;
            getWindow().setAttributes(attributes);
        }
    }

    private void applyPortraitVideoHeight(int width) {
        if (mVideoArea == null || width <= 0) {
            return;
        }
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mVideoArea.getLayoutParams();
        int height = Math.round(width * 9f / 16f);
        if (lp.height != height || lp.weight != 0) {
            lp.height = height;
            lp.weight = 0;
            mVideoArea.setLayoutParams(lp);
        }
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void updateInlineViewport(int boxWidth) {
        if (mExoPlayerController == null || boxWidth <= 0 || mIsInPip || mPipEnterPending
                || isLandscape()) {
            return;
        }
        mExoPlayerController.setInlineViewport(boxWidth, Math.round(boxWidth * 9f / 16f),
                getResizeMode() != RESIZE_MODE_DEFAULT);
    }

    private void enterPipFromMenu() {
        boolean screenUnderPlayer = !isTaskRoot();
        enterPipMode();
        if (mPipEnterPending) {
            SystemPipBridge.onMenuPipEntered(screenUnderPlayer);
        }
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !Helpers.isPictureInPictureSupported(this)
                || mIsInPip) {
            logPip("enter-skip");
            return;
        }

        logPip("enter-request");
        applyPipVideoOnlyLayout();

        boolean entered = false;
        try {
            entered = enterPictureInPictureMode(buildPipParams());
        } catch (Exception e) {
            logPip("enter-exception error=" + e.getClass().getSimpleName()
                    + ':' + com.liskovsoft.smartyoutubetv2.common.misc.NetPath.trunc(
                            e.getMessage(), 100));
        }
        if (!entered) {
            int orientation = getResources().getConfiguration().orientation;
            applyWatchLayoutForOrientation(orientation);
            applySystemBarsForOrientation(orientation);
            if (mTimeBar != null) {
                mTimeBar.setVisibility(View.VISIBLE);
            }
            showControlsInternal(false);
            logPip("enter-refused-restored");
        } else {
            mPipEnterPending = true;
            logPip("enter-accepted");
        }
    }

    private void applyPipVideoOnlyLayout() {
        cancelAutoHide();
        hideControls();
        cancelHoldSpeed();
        for (View pill : new View[] {mTopPill, mScrubChapterView}) {
            if (pill != null) {
                pill.animate().cancel();
                pill.setVisibility(View.GONE);
            }
        }
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        if (!MiniPlayerBridge.isActive()) {
            reattachVideoTexture();
        }
        if (mVideoTexture != null) {
            mVideoTexture.setVisibility(View.VISIBLE);
        }
        if (mControlsRoot != null) {
            mControlsRoot.setVisibility(View.GONE);
        }
        if (mTimeBar != null) {
            mTimeBar.setVisibility(View.GONE);
        }
        if (mWatchScroll != null) {
            mWatchScroll.setVisibility(View.GONE);
        }
        if (mCommentsPanel != null) {
            mCommentsPanel.setSuspended(true);
        }
        if (mVideoArea != null) {
            mVideoArea.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mVideoArea.getLayoutParams();
            lp.height = LinearLayout.LayoutParams.MATCH_PARENT;
            lp.weight = 0;
            mVideoArea.setLayoutParams(lp);
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private PictureInPictureParams buildPipParams() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
        builder.setAspectRatio(getVideoAspectRatio());

        if (mVideoArea != null && !mIsInPip) {
            Rect sourceRect = new Rect();
            mVideoArea.getGlobalVisibleRect(sourceRect);
            if (!sourceRect.isEmpty()) {
                builder.setSourceRectHint(sourceRect);
            }
        }

        builder.setActions(java.util.Collections.singletonList(buildPlayPauseAction()));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(shouldAutoEnterPip());
        }

        return builder.build();
    }

    private boolean shouldAutoEnterPip() {
        return !mSuppressAutoPip
                && !mDismissDragActive
                && mIsResumed
                && !isFinishing()
                && !mIsEnded
                && mExoPlayerController != null
                && mExoPlayerController.getPlayWhenReady()
                && BackgroundModePolicy.autoEnterPip(getBackgroundMode());
    }

    private int getBackgroundMode() {
        return PlayerData.instance(this).getBackgroundMode();
    }

    private Rational getVideoAspectRatio() {
        int width = 16;
        int height = 9;

        if (mPlayer != null && mPlayer.getVideoFormat() != null && mPlayer.getVideoFormat().height > 0) {
            width = mPlayer.getVideoFormat().width;
            height = mPlayer.getVideoFormat().height;
        }

        float ratio = (float) width / height;
        if (ratio < 0.5f) {
            return new Rational(1, 2);
        }
        if (ratio > 2.3f) {
            return new Rational(23, 10);
        }
        return new Rational(width, height);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private RemoteAction buildPlayPauseAction() {
        boolean playing = mExoPlayerController != null && mExoPlayerController.getPlayWhenReady() && !mIsEnded;

        int iconRes = playing ? R.drawable.ic_player_pause : R.drawable.ic_player_play;
        int labelRes = playing ? R.string.mobile_player_pause : R.string.mobile_player_play;

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        PendingIntent intent = PendingIntent.getBroadcast(
                this,
                PIP_REQUEST_TOGGLE,
                new Intent(ACTION_PIP_TOGGLE).setPackage(getPackageName()),
                piFlags);

        Icon icon = Icon.createWithResource(this, iconRes);
        return new RemoteAction(icon, getString(labelRes), getString(labelRes), intent);
    }

    private void updatePipActions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Helpers.isPictureInPictureSupported(this)) {
            return;
        }
        if (isFinishing() || isDestroyed()) {
            return;
        }
        try {
            if (BuildConfig.DEBUG) {
                logPip("params autoEnter=" + (shouldAutoEnterPip() ? "y" : "n")
                        + " suppress=" + mSuppressAutoPip + " drag=" + mDismissDragActive
                        + " resumed=" + mIsResumed + " miniActive=" + MiniPlayerBridge.isActive());
            }
            setPictureInPictureParams(buildPipParams());
        } catch (Exception e) {
            logPip("params-error error=" + e.getClass().getSimpleName());
        }
    }

    private void registerPipReceiver() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || mPipReceiver != null) {
            return;
        }

        mPipReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && ACTION_PIP_TOGGLE.equals(intent.getAction())) {
                    togglePlayPause();
                    updatePipActions();
                }
            }
        };

        ContextCompat.registerReceiver(this, mPipReceiver,
                new IntentFilter(ACTION_PIP_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterPipReceiver() {
        if (mPipReceiver != null) {
            try {
                unregisterReceiver(mPipReceiver);
            } catch (Exception e) {
            }
            mPipReceiver = null;
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);

        mIsInPip = isInPictureInPictureMode;
        mPipStateStale = false;
        mPipEnterPending = false;
        logPip("mode-changed inPip=" + (isInPictureInPictureMode ? "y" : "n")
                + " stopped=" + (mIsStopped ? "y" : "n"));

        if (isInPictureInPictureMode && mRoutedInWhileLeaving) {
            mRoutedInWhileLeaving = false;
            mRoutedInRestoreAttempts = 0;
            restoreRoutedInPip();
        } else if (!isInPictureInPictureMode) {
            Utils.removeCallbacks(mRoutedInRestore);
        }

        if (mSwipeLevels != null) {
            if (isInPictureInPictureMode) {
                mSwipeLevels.cancel();
            }
            updateSwipeBrightness();
        }
        if (isInPictureInPictureMode) {
            mPipDismissPending = false;
            releaseOrientationLockForPip();
            applyPipVideoOnlyLayout();
            if (mExoPlayerController != null) {
                mExoPlayerController.setSmallWindowViewport("pip", newConfig);
            }
            if (mLiveChatAction != null) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.d(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG,
                            "live-chat poll stop (pip)");
                }
                stopLiveChatStream();
            }
            updatePipActions();
        } else {
            SystemPipBridge.onPipEnded();
            if (mExoPlayerController != null) {
                mExoPlayerController.clearSmallWindowViewport("pip-exit");
            }
            if (mIsStopped) {
                mPrePipOrientation = ORIENTATION_NONE;
                finishFromPipDismiss();
                return;
            }
            mPipDismissPending = true;
            restoreOrientationLockAfterPip();

            if (mChatObserver != null && mChatReceiver == null
                    && mLiveChatAction == null && mLiveChatKey != null) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.d(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG,
                            "live-chat poll resume (pip exit)");
                }
                startLiveChatStream();
            }
            int orientation = newConfig != null ? newConfig.orientation
                    : getResources().getConfiguration().orientation;
            logPip("exit-layout newConfig=" + (newConfig != null ? newConfig.orientation : -1)
                    + " resources=" + getResources().getConfiguration().orientation
                    + " chosen=" + orientation);
            applyWatchLayoutForOrientation(orientation);
            applySystemBarsForOrientation(orientation);
            updatePlayPauseIcon();
            if (mTimeBar != null) {
                mTimeBar.setVisibility(View.VISIBLE);
                updateSeekBarLine();
            }
            if (mControlsRoot != null) {
                showControlsInternal(false);
            }
        }
    }

    private void releaseOrientationLockForPip() {
        int requested = getRequestedOrientation();
        if (requested == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            mPrePipOrientation = ORIENTATION_NONE;
            return;
        }

        mPrePipOrientation = requested;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        logPip("orientation-released requested=" + requested);
    }

    private void restoreOrientationLockAfterPip() {
        if (mPrePipOrientation == ORIENTATION_NONE) {
            return;
        }

        final int restore = mPrePipOrientation;
        mPrePipOrientation = ORIENTATION_NONE;

        if (mVideoArea == null) {
            setRequestedOrientation(restore);
            return;
        }
        mVideoArea.post(() -> {
            if (mIsInPip || isFinishing()) {
                return;
            }
            setRequestedOrientation(restore);
            logPip("orientation-restored requested=" + restore);
        });
    }

    private void logPip(String event) {
        Video video = getVideo();
        boolean textureAttached = mVideoTexture != null && mVideoTexture.getParent() != null;
        boolean textureAvailable = mVideoTexture != null && mVideoTexture.isAvailable();
        boolean surfaceValid = mSessionSurface != null && mSessionSurface.isValid();
        int playbackState = mPlayer != null ? mPlayer.getPlaybackState() : -1;
        boolean playWhenReady = mExoPlayerController != null
                && mExoPlayerController.getPlayWhenReady();
        com.liskovsoft.smartyoutubetv2.common.misc.NetPath.log(
                com.liskovsoft.smartyoutubetv2.common.misc.NetPath.context()
                        + " pip " + event
                        + " video=" + (video != null ? video.videoId : "?")
                        + " state=" + playbackState
                        + " pwr=" + (playWhenReady ? "y" : "n")
                        + " ended=" + (mIsEnded ? "y" : "n")
                        + " texture=" + (textureAttached ? "attached" : "detached")
                        + '/' + (textureAvailable ? "available" : "unavailable")
                        + " surface=" + (surfaceValid ? "valid" : "invalid")
                        + " mini=" + (MiniPlayerBridge.isActive() ? "y" : "n"));
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mPlaybackService = ((MobilePlaybackService.LocalBinder) binder).getService();
            mServiceBound = true;
            if (mPlayer != null) {
                mPlaybackService.attachPlayer(mPlayer, mPresenter, buildContentIntent());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mPlaybackService = null;
            mServiceBound = false;
        }
    };

    private void bindPlaybackService() {
        Intent intent = new Intent(this, MobilePlaybackService.class);
        try {
            startService(intent);
        } catch (Exception e) {
        }
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindPlaybackService() {
        if (mServiceBound) {
            try {
                unbindService(mServiceConnection);
            } catch (Exception e) {
            }
            mServiceBound = false;
        }
        try {
            stopService(new Intent(this, MobilePlaybackService.class));
        } catch (Exception e) {
        }
        mPlaybackService = null;
    }

    private PendingIntent buildContentIntent() {
        Intent intent = new Intent(this, MobilePlaybackActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getActivity(this, 0, intent, piFlags);
    }

    private void openCastPicker() {
        cancelAutoHide();
        CastPickerLauncher.open(this, this::showPlayerSheet);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        CastPickerLauncher.handlePermissionResult(this, requestCode, this::showPlayerSheet);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (CastVolumeKeys.onDispatchKeyEvent(this, event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void setupCastOverlay() {
        if (mCastOverlay == null) {
            return;
        }

        if (mCastPlayPause != null) {
            mCastPlayPause.setOnClickListener(v -> {
                if (mCastSessionManager != null) {
                    if (mCastSessionManager.isPlayingOnTv()) {
                        mCastSessionManager.pause();
                    } else {
                        mCastSessionManager.play();
                    }
                    updateCastOverlay();
                }
            });
        }

        View disconnect = findViewById(R.id.mobile_cast_disconnect);
        if (disconnect != null) {
            disconnect.setOnClickListener(v -> {
                if (mCastSessionManager != null) {
                    mCastSessionManager.disconnect();
                }
            });
        }

        View options = findViewById(R.id.mobile_cast_options);
        if (options != null) {
            options.setOnClickListener(v -> showCastPlaybackOptions());
        }

        if (mCastSeekBar != null) {
            mCastSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mCastPosition != null) {
                        mCastPosition.setText(formatTime(progress * 1_000L));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    mCastScrubbing = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    mCastScrubbing = false;
                    if (mCastSessionManager != null && mCastSessionManager.isConnected()) {
                        mCastSessionManager.seekTo(seekBar.getProgress() * 1_000L);
                    }
                }
            });
        }
    }

    private final CastSessionManager.Listener mCastListener = new CastSessionManager.Listener() {
        @Override
        public void onCastSessionStarted(CastTarget target) {
            long resumeMs = Math.max(getPositionMs(), 0);
            if (mPlayer != null) {
                mPlayer.setPlayWhenReady(false);
            }
            Video video = getVideo();
            mCastSubtitleVssId = null;
            mCastSubtitleLabel = null;
            if (video != null && video.videoId != null) {
                mCastSessionManager.loadVideo(video.videoId, resumeMs);
            }
            showCastOverlay();
            updateCastIconTint();
        }

        @Override
        public void onCastSessionState(String videoId, long positionMs, long durationMs, boolean playing) {
            updateCastOverlay();
        }

        @Override
        public void onCastSessionEnded(String reason) {
            long castPositionMs = mCastSessionManager != null ? mCastSessionManager.getPositionMs() : -1;
            hideCastOverlay();
            mCastSubtitleVssId = null;
            mCastSubtitleLabel = null;
            updateCastIconTint();
            if (castPositionMs > 0) {
                setPositionMs(castPositionMs);
            }
            setPlayWhenReady(true);
        }
    };

    private void updateCastIconTint() {
        if (mCastButton == null) {
            return;
        }
        if (mCastSessionManager != null && mCastSessionManager.isConnected()) {
            mCastButton.setColorFilter(getColorInt(R.color.mobile_player_cast_active));
        } else {
            mCastButton.clearColorFilter();
        }
    }

    private void showCastOverlay() {
        if (mCastOverlay == null) {
            return;
        }
        CastTarget target = mCastSessionManager != null ? mCastSessionManager.getTarget() : null;
        if (mCastOverlayTitle != null) {
            mCastOverlayTitle.setText(getString(R.string.mobile_cast_playing_on,
                    target != null ? target.getName() : ""));
        }
        mCastOverlay.setVisibility(View.VISIBLE);
        cancelHoldSpeed();
        updateSeekBarLine();
        hideControls();
        updateCastOverlay();
        Utils.removeCallbacks(mCastProgressRunnable);
        Utils.postDelayed(mCastProgressRunnable, 1_000);
    }

    private void hideCastOverlay() {
        Utils.removeCallbacks(mCastProgressRunnable);
        if (mCastOverlay != null) {
            mCastOverlay.setVisibility(View.GONE);
        }
        updateSeekBarLine();
    }

    private void updateCastOverlay() {
        if (mCastOverlay == null || mCastOverlay.getVisibility() != View.VISIBLE
                || mCastSessionManager == null) {
            return;
        }

        long positionMs = Math.max(mCastSessionManager.getPositionMs(), 0);
        long durationMs = Math.max(mCastSessionManager.getDurationMs(), 0);
        Video currentVideo = getVideo();
        boolean isLive = currentVideo != null && currentVideo.isLive
                && Helpers.equals(currentVideo.videoId, mCastSessionManager.getVideoId());

        if (mCastPlayPause != null) {
            boolean playing = mCastSessionManager.isPlayingOnTv();
            mCastPlayPause.setImageResource(playing ? R.drawable.ic_player_pause : R.drawable.ic_player_play);
            mCastPlayPause.setContentDescription(
                    getString(playing ? R.string.mobile_player_pause : R.string.mobile_player_play));
        }
        if (mCastLiveChip != null) {
            mCastLiveChip.setVisibility(isLive ? View.VISIBLE : View.GONE);
        }
        if (mCastTimeline != null) {
            mCastTimeline.setVisibility(isLive ? View.GONE : View.VISIBLE);
        }
        if (isLive) {
            mCastScrubbing = false;
            return;
        }
        if (mCastDuration != null) {
            mCastDuration.setText(formatTime(durationMs));
        }
        if (mCastSeekBar != null) {
            mCastSeekBar.setMax((int) (durationMs / 1_000));
            if (!mCastScrubbing) {
                mCastSeekBar.setProgress((int) (positionMs / 1_000));
            }
        }
        if (mCastPosition != null && !mCastScrubbing) {
            mCastPosition.setText(formatTime(positionMs));
        }
    }

    private final Runnable mCastProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCastOverlay != null && mCastOverlay.getVisibility() == View.VISIBLE) {
                updateCastOverlay();
                Utils.postDelayed(this, 1_000);
            }
        }
    };

    private void showCastPlaybackOptions() {
        if (mCastSessionManager == null || !mCastSessionManager.isConnected()) {
            return;
        }

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout content = createSheetContent();
        addCastSheetHeader(content, R.string.mobile_cast_controls_title,
                mCastSessionManager.isDirectRoute()
                        ? R.string.mobile_cast_direct_summary : R.string.mobile_cast_app_summary);

        if (mCastSessionManager.isDirectRoute()) {
            addMenuRow(content, sheet, R.drawable.ic_player_quality,
                    R.string.mobile_player_quality, currentDirectCastQualityLabel(), true,
                    this::showDirectCastQualitySheet);
            addMenuRow(content, sheet, R.drawable.ic_player_cc,
                    R.string.mobile_player_subtitles,
                    getString(R.string.mobile_cast_subtitles_need_tv_app), true,
                    this::confirmSwitchDirectCastForSubtitles);
        } else {
            addMenuRow(content, sheet, R.drawable.ic_player_quality,
                    R.string.mobile_player_quality,
                    getString(R.string.mobile_cast_quality_tv_remote), false,
                    () -> showCastSnackbar(R.string.mobile_cast_quality_receiver_help));
            addMenuRow(content, sheet, R.drawable.ic_player_cc,
                    R.string.mobile_player_subtitles, currentReceiverCaptionsLabel(), true,
                    this::showReceiverCaptionsSheet);
        }

        sheet.setContentView(content);
        showPlayerSheet(sheet);
    }

    private void addCastSheetHeader(LinearLayout content, int titleRes, int summaryRes) {
        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextColor(getColorInt(R.color.mobile_color_on_surface));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(dp(20), dp(4), dp(20), dp(4));
        content.addView(title);

        TextView summary = new TextView(this);
        summary.setText(summaryRes);
        summary.setTextColor(getColorInt(R.color.mobile_color_on_surface_secondary));
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        summary.setPadding(dp(20), 0, dp(20), dp(10));
        content.addView(summary);
    }

    private String currentDirectCastQualityLabel() {
        int height = mCastSessionManager != null
                ? mCastSessionManager.getDirectQualityHeight() : 0;
        return height > 0
                ? getString(R.string.mobile_cast_quality_cap, height)
                : getString(R.string.mobile_cast_quality_auto_1080);
    }

    private void showDirectCastQualitySheet() {
        if (mCastSessionManager == null || !mCastSessionManager.isDirectRoute()) {
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_mobile_quality, null);
        dialog.setContentView(content);
        LinearLayout qualityList = content.findViewById(R.id.quality_sheet_quality_list);

        int selectedHeight = mCastSessionManager.getDirectQualityHeight();
        addQualityRow(qualityList, getString(R.string.mobile_cast_quality_auto_1080),
                selectedHeight == 0, () -> {
                    if (mCastSessionManager != null) {
                        mCastSessionManager.setDirectQualityHeight(0);
                    }
                    dialog.dismiss();
                });

        java.util.TreeSet<Integer> heights = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        List<FormatItem> formats = getVideoFormats();
        if (formats != null) {
            for (FormatItem item : formats) {
                if (item != null && item.getHeight() > 0
                        && item.getHeight() <= com.newtube.mobile.casting.proxy.MpdRewriter.MAX_VIDEO_HEIGHT) {
                    heights.add(item.getHeight());
                }
            }
        }
        for (int height : heights) {
            addQualityRow(qualityList,
                    getString(R.string.mobile_cast_quality_cap, height),
                    selectedHeight == height, () -> {
                        if (mCastSessionManager != null) {
                            mCastSessionManager.setDirectQualityHeight(height);
                        }
                        dialog.dismiss();
                    });
        }
        showPlayerSheet(dialog);
    }

    private String currentReceiverCaptionsLabel() {
        return mCastSubtitleLabel != null
                ? mCastSubtitleLabel : getString(R.string.mobile_menu_off);
    }

    private void showReceiverCaptionsSheet() {
        if (mCastSessionManager == null || mCastSessionManager.isDirectRoute()) {
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_mobile_captions, null);
        dialog.setContentView(content);
        content.findViewById(R.id.captions_sheet_style_divider).setVisibility(View.GONE);
        content.findViewById(R.id.captions_sheet_style).setVisibility(View.GONE);
        LinearLayout trackList = content.findViewById(R.id.captions_sheet_track_list);

        addQualityRow(trackList, getString(R.string.mobile_captions_off),
                mCastSubtitleVssId == null, () -> {
                    applyReceiverCaption(null);
                    dialog.dismiss();
                });

        List<FormatItem> tracks = new ArrayList<>();
        List<FormatItem> formats = getSubtitleFormats();
        if (formats != null) {
            for (FormatItem item : formats) {
                if (isCaptionTrack(item)) {
                    tracks.add(item);
                }
            }
        }
        moveLastUsedCaptionsFirst(tracks);
        for (FormatItem item : tracks) {
            String vssId = item.getFormatId();
            addQualityRow(trackList, captionLabel(item),
                    Helpers.equals(vssId, mCastSubtitleVssId), () -> {
                        applyReceiverCaption(item);
                        dialog.dismiss();
                    });
        }
        if (tracks.isEmpty()) {
            content.findViewById(R.id.captions_sheet_empty).setVisibility(View.VISIBLE);
        }
        showPlayerSheet(dialog);
    }

    private void applyReceiverCaption(@Nullable FormatItem item) {
        String vssId = item != null ? item.getFormatId() : null;
        String languageCode = item != null
                ? castCaptionLanguageCode(item.getFormatId(), item.getLanguage()) : null;
        if (mCastSessionManager == null
                || !mCastSessionManager.setReceiverSubtitle(vssId, languageCode)) {
            showCastSnackbar(R.string.mobile_cast_subtitles_failed);
            return;
        }
        mCastSubtitleVssId = vssId;
        mCastSubtitleLabel = item != null ? captionLabel(item) : null;
        showCastSnackbar(item != null
                ? R.string.mobile_cast_subtitles_sent : R.string.mobile_captions_off_toast);
    }

    @Nullable
    static String castCaptionLanguageCode(@Nullable String vssId, @Nullable String fallback) {
        if (vssId != null && !vssId.isEmpty()) {
            int dot = vssId.lastIndexOf('.');
            String candidate = dot >= 0 && dot + 1 < vssId.length()
                    ? vssId.substring(dot + 1) : vssId;
            if (candidate.matches("(?i)[a-z]{2,3}([_-][a-z0-9]{2,8})*")) {
                return candidate.replace('_', '-');
            }
        }
        if (fallback != null && !fallback.isEmpty()
                && fallback.matches("(?i)[a-z]{2,3}([_-][a-z0-9]{2,8})*")) {
            return fallback.replace('_', '-');
        }
        return null;
    }

    private void confirmSwitchDirectCastForSubtitles() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                this, R.style.MobileAlertDialog)
                .setTitle(R.string.mobile_cast_switch_subtitles_title)
                .setMessage(R.string.mobile_cast_switch_subtitles_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mobile_cast_switch_subtitles_positive,
                        (dialog, which) -> switchDirectCastToTvApp())
                .show();
    }

    private void switchDirectCastToTvApp() {
        if (mCastSessionManager == null || !mCastSessionManager.switchDirectSessionToTvApp()) {
            showCastSnackbar(R.string.mobile_cast_launch_failed);
        }
    }

    private void showCastSnackbar(int messageRes) {
        com.google.android.material.snackbar.Snackbar.make(
                findViewById(android.R.id.content), messageRes,
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
    }

    private void maybeRouteVideoToCast(String videoId) {
        if (mCastSessionManager == null || !mCastSessionManager.isConnected() || videoId == null) {
            return;
        }
        if (!Helpers.equals(videoId, mCastSessionManager.getVideoId())) {
            mCastSubtitleVssId = null;
            mCastSubtitleLabel = null;
            mCastSessionManager.loadVideo(videoId, 0);
        }
        if (mPlayer != null) {
            mPlayer.setPlayWhenReady(false);
        }
        showCastOverlay();
    }

    @Nullable
    private Boolean doubleTapSeekForward(Player player, float posX) {
        int state = player.getPlaybackState();
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
            return null;
        }
        int width = mPlayerView.getPlayerWidth();
        if (player.getCurrentPosition() > 500 && posX < width * 0.35f) {
            return false;
        }
        if (posX > width * 0.65f) {
            return true;
        }
        return null;
    }

    private void toggleControls() {
        if (mControlsVisible) {
            hideControls();
        } else {
            showControlsInternal(true);
        }
    }

    private void showControlsInternal(boolean animate) {
        mControlsVisible = true;
        mControlsRoot.setVisibility(View.VISIBLE);
        mControlsRoot.animate().cancel();
        if (animate) {
            mControlsRoot.setAlpha(0f);
            mControlsRoot.animate().alpha(1f).setDuration(150).start();
        } else {
            mControlsRoot.setAlpha(1f);
        }
        if (mScrubChromeHidden && !mScrubbing) {
            setScrubChrome(false);
        }
        mTimeBar.setShown(true, animate);
        updatePlayPauseIcon();
        startProgressUpdates();

        if (mPresenter != null) {
            mPresenter.onControlsShown(true);
        }

        armAutoHide();
    }

    private void hideControls() {
        if (!mControlsVisible) {
            return;
        }

        mControlsVisible = false;
        cancelAutoHide();
        startProgressUpdates();
        mTimeBar.setShown(false, true);
        mControlsRoot.animate().cancel();
        mControlsRoot.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> {
                    if (!mControlsVisible) {
                        mControlsRoot.setVisibility(View.GONE);
                    }
                }).start();

        if (mPresenter != null) {
            mPresenter.onControlsShown(false);
        }
    }

    private void armAutoHide() {
        cancelAutoHide();
        if (!mIsStopped) {
            Utils.postDelayed(mHideControlsRunnable, AUTO_HIDE_MS);
        }
    }

    private static final SeekParameters MOBILE_SEEK_PARAMETERS =
            new SeekParameters(5_000_000, 1_000_000);

    private static final long SEEK_BURST_WATCHDOG_MS = 1_500;

    private final Runnable mSeekBurstWatchdog = this::endUserSeekBurst;

    private void setUserSeekDirection(boolean forward) {
        if (mPlayer != null) {
            mPlayer.setSeekParameters(forward ? SeekParameters.NEXT_SYNC : SeekParameters.PREVIOUS_SYNC);
            Utils.removeCallbacks(mSeekBurstWatchdog);
            Utils.postDelayed(mSeekBurstWatchdog, SEEK_BURST_WATCHDOG_MS);
        }
    }

    private void endUserSeekBurst() {
        Utils.removeCallbacks(mSeekBurstWatchdog);
        if (mPlayer != null) {
            mPlayer.setSeekParameters(MOBILE_SEEK_PARAMETERS);
        }
    }

    private void cancelAutoHide() {
        Utils.removeCallbacks(mHideControlsRunnable);
    }

    private boolean isHoldingSeekBar() {
        return mScrubbing || (mTimeBar != null && mTimeBar.isHeld());
    }

    private void onAutoHideTick() {
        if (!mControlsVisible || mIsStopped) {
            return;
        }

        if (isHoldingSeekBar() || mIsEnded || mPlayer == null || !isPlaying()) {
            armAutoHide();
            return;
        }

        hideControls();
    }

    private void togglePlayPause() {
        if (mExoPlayerController == null) {
            return;
        }

        if (mIsEnded) {
            mIsEnded = false;
            mExoPlayerController.setPositionMs(0);
            mExoPlayerController.setPlayWhenReady(true);
            if (mPresenter != null) {
                mPresenter.onPlayClicked();
            }
        } else {
            boolean play = !mExoPlayerController.getPlayWhenReady();
            mExoPlayerController.setPlayWhenReady(play);
            if (mPresenter != null) {
                if (play) {
                    mPresenter.onPlayClicked();
                } else {
                    mPresenter.onPauseClicked();
                }
            }
        }

        updatePlayPauseIcon();
        armAutoHide();
    }

    private void toggleFullscreen() {
        boolean toLandscape =
                getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
        setRequestedOrientation(toLandscape
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        armOrientationHandBack(toLandscape
                ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT);
        armAutoHide();
    }

    private void armOrientationHandBack(int target) {
        if (isAutoRotateOn()) {
            mOrientationHandBack.arm(target);
        } else {
            mOrientationHandBack.disarm();
        }
        updateOrientationHandBackListener();
    }

    private void updateOrientationHandBackListener() {
        boolean listen = mOrientationHandBack.isArmed() && mIsResumed && !mIsInPip;
        if (listen) {
            if (mOrientationListener == null) {
                mOrientationListener = new android.view.OrientationEventListener(this) {
                    @Override
                    public void onOrientationChanged(int degrees) {
                        onPhoneOrientation(degrees);
                    }
                };
            }
            if (mOrientationListener.canDetectOrientation()) {
                mOrientationListener.enable();
            }
        } else {
            Utils.removeCallbacks(mOrientationSettleCheck);
            mOrientationHandBack.pause();
            if (mOrientationListener != null) {
                mOrientationListener.disable();
            }
        }
    }

    private void onPhoneOrientation(int degrees) {
        mLastPhoneDegrees = degrees;
        long now = android.os.SystemClock.uptimeMillis();
        if (!mOrientationHandBack.onOrientation(degrees, now)) {
            long remaining = mOrientationHandBack.remainingMs(now);
            if (remaining >= 0) {
                Utils.postDelayed(mOrientationSettleCheck, remaining + 1);
            } else {
                Utils.removeCallbacks(mOrientationSettleCheck);
            }
            return;
        }
        Utils.removeCallbacks(mOrientationSettleCheck);
        mOrientationHandBack.disarm();
        updateOrientationHandBackListener();

        int requested = getRequestedOrientation();
        boolean ours = requested == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                || requested == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (ours && !mIsInPip && isAutoRotateOn()) {
            setRequestedOrientation(FREE_ORIENTATION);
        }
        if (BuildConfig.DEBUG) {
            android.util.Log.d(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG,
                    "orientation hand-back degrees=" + degrees + " requested=" + requested
                            + " freed=" + (getRequestedOrientation() == FREE_ORIENTATION));
        }
    }

    private boolean isAutoRotateOn() {
        return android.provider.Settings.System.getInt(getContentResolver(),
                android.provider.Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    private void openPlayerOption(int actionId, boolean asLongClick) {
        if (mPresenter == null) {
            return;
        }

        cancelAutoHide();

        int state = getButtonState(actionId);
        if (state == BUTTON_DISABLED) {
            state = BUTTON_OFF;
        }

        if (asLongClick) {
            mPresenter.onButtonLongClicked(actionId, state);
        } else {
            mPresenter.onButtonClicked(actionId, state);
        }
    }

    private void openPlayerMenu() {
        if (mPresenter == null) {
            return;
        }

        cancelAutoHide();

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout content = createSheetContent();

        addMenuRow(content, sheet, R.drawable.ic_player_quality, R.string.mobile_player_quality,
                currentQualityLabel(), true, this::showQualitySheet);
        List<AudioTrackChoices.Choice> audioChoices = audioTrackChoices();
        if (audioChoices.size() > 1) {
            AudioTrackChoices.Choice playing = AudioTrackChoices.selected(audioChoices);
            addMenuRow(content, sheet, R.drawable.ic_player_audio_track,
                    R.string.mobile_player_audio_track, playing != null ? playing.label : null,
                    true, this::showAudioTrackSheet);
        }
        addMenuRow(content, sheet, R.drawable.ic_player_cc, R.string.mobile_player_subtitles,
                currentCaptionsLabel(), true, this::showCaptionsSheet);
        addMenuRow(content, sheet, R.drawable.ic_player_speed, R.string.mobile_player_speed,
                currentSpeedLabel(), true, this::showSpeedSheet);
        if (Helpers.isPictureInPictureSupported(this)) {
            addMenuRow(content, sheet, R.drawable.ic_player_pip, R.string.mobile_player_pip,
                    null, false, this::enterPipFromMenu);
        }
        addMenuRow(content, sheet, R.drawable.ic_mobile_settings, R.string.mobile_menu_more,
                null, true, this::openPlayerMoreMenu);

        sheet.setContentView(content);
        sheet.setOnDismissListener(d -> armAutoHide());
        showPlayerSheet(sheet);
    }

    private void openPlayerMoreMenu() {
        if (mPresenter == null) {
            return;
        }

        cancelAutoHide();

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout content = createSheetContent();

        boolean shuffleOn = isShuffling();
        boolean statsOn = getButtonState(R.id.action_video_stats) == BUTTON_ON;

        addMenuRow(content, sheet, R.drawable.ic_player_repeat, R.string.mobile_menu_repeat,
                null, true, () -> openPlayerOption(R.id.action_repeat, true));
        addMenuRow(content, sheet, R.drawable.ic_player_shuffle, R.string.mobile_menu_shuffle,
                stateLabel(shuffleOn), false, this::toggleShuffleMode);
        addMenuRow(content, sheet, R.drawable.ic_player_zoom, R.string.mobile_menu_zoom,
                null, true, () -> openPlayerOption(R.id.action_video_zoom, false));
        addMenuRow(content, sheet, R.drawable.ic_player_background, R.string.mobile_menu_background,
                null, true, this::openBackgroundModeDialog);
        addMenuRow(content, sheet, R.drawable.ic_player_playlist_add, R.string.mobile_menu_playlist_add,
                null, true, () -> openPlayerOption(R.id.action_playlist_add, false));
        if (VideoDownloads.canDownload(getVideo()) || currentDownload() != null) {
            addMenuRow(content, sheet, R.drawable.ic_watch_download, R.string.dialog_download,
                    downloadStateLabel(), true, this::onDownloadTapped);
        }
        addMenuRow(content, sheet, R.drawable.ic_player_queue, R.string.mobile_menu_queue,
                null, true, () -> openPlayerOption(R.id.action_playback_queue, false));
        addMenuRow(content, sheet, R.drawable.ic_player_stats, R.string.mobile_menu_stats,
                stateLabel(statsOn), false, () -> openPlayerOption(R.id.action_video_stats, false));

        sheet.setContentView(content);
        sheet.setOnDismissListener(d -> armAutoHide());
        showPlayerSheet(sheet);
    }

    private LinearLayout createSheetContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.bg_mobile_sheet);
        content.setPadding(0, dp(8), 0, dp(16));

        View handle = new View(this);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(8);
        handle.setLayoutParams(handleLp);
        handle.setBackgroundResource(R.drawable.bg_mobile_sheet_handle);
        content.addView(handle);

        return content;
    }

    private String currentQualityLabel() {
        PlayerData playerData = PlayerData.instance(this);
        FormatItem tempOverride = playerData.getTempVideoFormat();
        FormatItem chosen = tempOverride != null ? tempOverride : playerData.getFormat(FormatItem.TYPE_VIDEO);

        if (!isAutoFormat(chosen) && chosen.getHeight() > 0) {
            return qualityLabel(chosen);
        }

        FormatItem playing = mExoPlayerController != null ? mExoPlayerController.getVideoFormat() : null;
        return playing != null && playing.getHeight() > 0
                ? getString(R.string.mobile_quality_auto_current, qualityLabel(playing))
                : getString(R.string.mobile_quality_auto_short);
    }

    private String currentSpeedLabel() {
        float speed = mExoPlayerController != null ? mExoPlayerController.getSpeed() : -1;
        return speedLabel(speed <= 0 ? 1f : speed);
    }

    private final List<java.lang.ref.WeakReference<BottomSheetDialog>> mShownSheets = new ArrayList<>();

    private void showPlayerSheet(BottomSheetDialog dialog) {
        for (int i = mShownSheets.size() - 1; i >= 0; i--) {
            BottomSheetDialog shown = mShownSheets.get(i).get();
            if (shown == null || !shown.isShowing()) {
                mShownSheets.remove(i);
            }
        }
        mShownSheets.add(new java.lang.ref.WeakReference<>(dialog));
        Window window = dialog.getWindow();
        boolean immersive = isLandscape();
        if (immersive && window != null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility());
        }

        dialog.setOnShowListener(d -> {
            View sheetView = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheetView != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheetView);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
            if (immersive && window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            }
        });

        dialog.show();
    }

    private void addMenuRow(LinearLayout container, BottomSheetDialog sheet, int iconRes,
                            int labelRes, String trailing, boolean chevron, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackgroundResource(resolveSelectableItemBackground());
        row.setPadding(dp(20), dp(14), dp(16), dp(14));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(getColorInt(R.color.mobile_color_on_surface));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconLp.setMarginEnd(dp(20));
        icon.setLayoutParams(iconLp);
        row.addView(icon);

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextColor(getColorInt(R.color.mobile_color_on_surface));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);

        if (trailing != null) {
            TextView state = new TextView(this);
            state.setText(trailing);
            state.setTextColor(getColorInt(R.color.mobile_color_on_surface_secondary));
            state.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            row.addView(state);
        }

        if (chevron) {
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_chevron_right);
            arrow.setColorFilter(getColorInt(R.color.mobile_color_on_surface_secondary));
            LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(dp(20), dp(20));
            arrowLp.setMarginStart(dp(4));
            arrow.setLayoutParams(arrowLp);
            row.addView(arrow);
        }

        row.setOnClickListener(v -> {
            sheet.dismiss();
            action.run();
        });

        container.addView(row);
    }

    private String stateLabel(boolean on) {
        return getString(on ? R.string.mobile_menu_on : R.string.mobile_menu_off);
    }

    private int resolveSelectableItemBackground() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return tv.resourceId;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isShuffling() {
        return PlayerData.instance(this).getPlaybackMode() == PlayerConstants.PLAYBACK_MODE_SHUFFLE
                || QueuePlaybackMode.coversQueueOf(getVideo());
    }

    private void toggleShuffleMode() {
        int mode = isShuffling() ? PlayerConstants.PLAYBACK_MODE_ALL : PlayerConstants.PLAYBACK_MODE_SHUFFLE;
        PlayerData.instance(this).setPlaybackMode(mode);
        setButtonState(R.id.action_repeat, mode);
    }

    private void openBackgroundModeDialog() {
        cancelAutoHide();
        AppDialogPresenter dialog = AppDialogPresenter.instance(this);
        OptionCategory category = AppDialogUtil.createBackgroundPlaybackCategory(
                this, PlayerData.instance(this), GeneralData.instance(this));
        dialog.appendRadioCategory(category.title, category.options);
        dialog.showDialog(getString(R.string.mobile_menu_background));
    }

    private void updateFullscreenIcon(int orientation) {
        if (mFullscreenButton == null) {
            return;
        }

        mFullscreenButton.setImageResource(orientation == Configuration.ORIENTATION_LANDSCAPE
                ? R.drawable.ic_player_fullscreen_exit
                : R.drawable.ic_player_fullscreen);
    }

    private static final int GLYPH_NONE = 0;
    private static final int GLYPH_PLAY = 1;
    private static final int GLYPH_PAUSE = 2;
    private static final int GLYPH_REPLAY = 3;
    private int mPlayPauseGlyph = GLYPH_NONE;

    private void updatePlayPauseIcon() {
        if (mPlayPauseButton == null) {
            return;
        }

        int glyph;
        if (mIsEnded) {
            glyph = GLYPH_REPLAY;
        } else if (mExoPlayerController != null && mExoPlayerController.getPlayWhenReady()) {
            glyph = GLYPH_PAUSE;
        } else {
            glyph = GLYPH_PLAY;
        }
        if (glyph == mPlayPauseGlyph) {
            return;
        }

        boolean morph = mControlsVisible && mControlsRoot != null && mControlsRoot.getAlpha() > 0f
                && (mPlayPauseGlyph == GLYPH_PLAY && glyph == GLYPH_PAUSE
                        || mPlayPauseGlyph == GLYPH_PAUSE && glyph == GLYPH_PLAY);
        mPlayPauseGlyph = glyph;
        Drawable morphing = morph ? ContextCompat.getDrawable(this, glyph == GLYPH_PAUSE
                ? R.drawable.avd_player_play_to_pause : R.drawable.avd_player_pause_to_play) : null;
        if (morphing instanceof android.graphics.drawable.Animatable) {
            mPlayPauseButton.setImageDrawable(morphing);
            ((android.graphics.drawable.Animatable) morphing).start();
        } else {
            mPlayPauseButton.setImageResource(glyph == GLYPH_REPLAY ? R.drawable.ic_player_replay
                    : glyph == GLYPH_PAUSE ? R.drawable.ic_player_pause : R.drawable.ic_player_play);
        }
        mPlayPauseButton.setContentDescription(getString(glyph == GLYPH_REPLAY ? R.string.mobile_player_replay
                : glyph == GLYPH_PAUSE ? R.string.mobile_player_pause : R.string.mobile_player_play));
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        if (mIsStopped) {
            return;
        }
        if (mControlsVisible) {
            Utils.postDelayed(mProgressUpdateRunnable, 0);
        } else if (mTimeBar != null && mTimeBar.isLineShownWhenHidden()) {
            Utils.postDelayed(mLineUpdateRunnable, 0);
        }
    }

    private void stopProgressUpdates() {
        Utils.removeCallbacks(mProgressUpdateRunnable);
        Utils.removeCallbacks(mLineUpdateRunnable);
    }

    private long barUpdateDelayMs(long minMs, long maxMs) {
        long delay = mTimeBar != null ? mTimeBar.getPreferredUpdateDelay() : maxMs;
        float speed = mPlayer != null ? mPlayer.getPlaybackParameters().speed : 1f;
        if (speed > 0f && delay != Long.MAX_VALUE) {
            delay = (long) (delay / speed);
        }
        return Math.max(minMs, Math.min(maxMs, delay));
    }

    private void onLineTick() {
        if (mIsStopped || mControlsVisible || mPlayer == null || mExoPlayerController == null
                || !mTimeBar.isLineShownWhenHidden()) {
            return;
        }
        long duration = getDurationMs();
        mTimeBar.setDuration(Math.max(duration, 0));
        mTimeBar.setPosition(Math.max(mExoPlayerController.getPositionMs(), 0));
        Utils.postDelayed(mLineUpdateRunnable, isPlaying() ? barUpdateDelayMs(100, 1_000) : 1_000);
    }

    private static void setTextIfChanged(@Nullable TextView view, CharSequence text) {
        if (view != null && !TextUtils.equals(view.getText(), text)) {
            view.setText(text);
        }
    }

    private void onProgressTick() {
        if (mIsStopped || !mControlsVisible || mPlayer == null || mExoPlayerController == null) {
            return;
        }

        if (!mScrubbing) {
            long position = mExoPlayerController.getPositionMs();
            long duration = getDurationMs();
            long buffered = mPlayer.getBufferedPosition();

            if (duration < 0) {
                duration = 0;
            }
            if (position < 0) {
                position = 0;
            }

            mTimeBar.setDuration(duration);
            mTimeBar.setPosition(position);
            mTimeBar.setBufferedPosition(buffered);
            setTextIfChanged(mPositionView, formatTime(position));
            setTextIfChanged(mDurationView, getString(R.string.mobile_player_duration, formatTime(duration)));
            updateLiveChip(position, duration);
            updateChapterButton(position);
        }

        updatePlayPauseIcon();
        Utils.postDelayed(mProgressUpdateRunnable,
                isPlaying() ? barUpdateDelayMs(100, PROGRESS_UPDATE_MS) : PROGRESS_UPDATE_MS);
    }

    private void updateLiveChip(long positionMs, long durationMs) {
        if (mLiveChip == null) {
            return;
        }

        boolean isLive = getVideo() != null && getVideo().isLive;
        mLiveChip.setVisibility(isLive ? View.VISIBLE : View.GONE);
        updateSeekBarLine();

        if (isLive) {
            boolean atEdge = durationMs - positionMs <= LIVE_EDGE_THRESHOLD_MS;
            mLiveChip.setAlpha(atEdge ? 1f : 0.55f);
        }
    }

    private void jumpToLiveEdge() {
        long durationMs = getDurationMs();

        if (mExoPlayerController == null || durationMs <= 0) {
            return;
        }

        mExoPlayerController.setPositionMs(Math.max(0, durationMs - LIVE_EDGE_OFFSET_MS));

        if (mPlayer != null) {
            mPlayer.setPlayWhenReady(true);
        }
    }

    private String formatTime(long timeMs) {
        if (timeMs < 0) {
            timeMs = 0;
        }
        return PlayerTimeBar.formatTime(mFormatBuilder, mFormatter, timeMs);
    }
    private final Player.Listener mUiPlayerListener = new Player.Listener() {
        @Override
        public void onVideoSizeChanged(VideoSize videoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                mVideoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height;
                applyControlsInsets();
            }
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (mPlayer != null) {
                handleUiStateChange(playWhenReady, mPlayer.getPlaybackState());
            }
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            handleUiStateChange(mPlayer != null && mPlayer.getPlayWhenReady(), playbackState);
        }

        private void handleUiStateChange(boolean playWhenReady, int playbackState) {
            switch (playbackState) {
                case Player.STATE_BUFFERING:
                    showProgressBar(true);
                    break;
                case Player.STATE_READY:
                    showProgressBar(false);
                    mIsEnded = false;
                    if (mCastSessionManager != null && mCastSessionManager.isConnected()
                            && mPlayer != null && mPlayer.getPlayWhenReady()) {
                        mPlayer.setPlayWhenReady(false);
                    }
                    SessionWarmup.markWarm(MobilePlaybackActivity.this);
                    if (mStillAwaitReady) {
                        mStillAwaitReady = false;
                        if (canLiftStillAtReady()) {
                            mStillAwaitFrame = false;
                            liftLoadingStill("ready");
                        } else {
                            mStillAwaitFrame = true;
                        }
                    }
                    if (mBackgroundAudioMode || (mPlayer != null
                            && !mPlayer.getCurrentTracks().isTypeSelected(
                                    androidx.media3.common.C.TRACK_TYPE_VIDEO))) {
                        releaseWatchMetadata();
                    }
                    break;
                case Player.STATE_ENDED:
                    showProgressBar(false);
                    mIsEnded = true;
                    if (!mIsInPip) {
                        showControlsInternal(true);
                    }
                    break;
                default:
                    break;
            }
            updatePlayPauseIcon();
            updatePipActions();

            if (mPlayerView != null) {
                mPlayerView.setKeepScreenOn(playWhenReady
                        && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING));
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            releaseWatchMetadata();
            mStillAwaitReady = false;
            mStillAwaitFrame = false;
            hideVideoStill();
        }

        @Override
        public void onRenderedFirstFrame() {
            maybeLiftStillAtFrame();
        }
    };

    private TextureView mVideoTexture;
    private SurfaceTexture mSessionTexture;
    private Surface mSessionSurface;
    private ImageView mVideoStill;
    private boolean mStillAwaitReady;
    private boolean mStillAwaitFrame;
    private boolean mNewVideoStill;
    private String mStillVideoId;
    private long mLastTextureFrameRealtimeMs;
    private long mStillArmedRealtimeMs;

    private void armStillForReady() {
        mStillAwaitReady = true;
        mStillArmedRealtimeMs = android.os.SystemClock.elapsedRealtime();
    }

    private void setupVideoSurface() {
        ViewGroup contentFrame = mPlayerView.getContentFrame();

        mVideoTexture = new TextureView(this);
        mVideoTexture.setOpaque(false);
        mVideoTexture.setSurfaceTextureListener(mVideoTextureListener);
        contentFrame.addView(mVideoTexture, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mVideoStill = new ImageView(this);
        mVideoStill.setScaleType(ImageView.ScaleType.FIT_XY);
        mVideoStill.setBackgroundColor(Color.BLACK);
        mVideoStill.setVisibility(View.GONE);
        contentFrame.addView(mVideoStill, 1, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mStoryboardManager = new StoryboardManager(this);
        mSeekOverlayPreview = new ImageView(this);
        mSeekOverlayPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mSeekOverlayPreview.setBackgroundColor(Color.BLACK);
        mSeekOverlayPreview.setVisibility(View.GONE);
        contentFrame.addView(mSeekOverlayPreview, 2, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private final TextureView.SurfaceTextureListener mVideoTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            if (mSessionTexture == null) {
                mSessionTexture = texture;
                mSessionSurface = new Surface(texture);
                if (mPlayer != null) {
                    mPlayer.setVideoSurface(mSessionSurface);
                }
            } else if (texture != mSessionTexture) {
                mVideoTexture.setSurfaceTexture(mSessionTexture);
                texture.release();
            }
            logPip("surface-available size=" + width + 'x' + height
                    + " adopted=" + (texture == mSessionTexture ? "y" : "n"));
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            boolean release = texture != mSessionTexture;
            logPip("surface-destroyed release=" + (release ? "y" : "n"));
            return release;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            mLastTextureFrameRealtimeMs = android.os.SystemClock.elapsedRealtime();
            if (maybeLiftStillAtFrame()) {
                return;
            }
            if (mStillAwaitFrame && !mStillAwaitReady) {
                mStillAwaitFrame = false;
                liftLoadingStill("texture");
            }
        }
    };

    private void liftLoadingStill(String lift) {
        hideVideoStill(mNewVideoStill, lift);
        releaseWatchMetadata();
    }

    private boolean canLiftStillAtReady() {
        if (mExoPlayerController == null || mBackgroundAudioMode
                || !com.newtube.mobile.player.SwitchExperiments.stillLiftAtReady()) {
            return false;
        }
        return firstFrameOnTexture(mExoPlayerController.getOpenFirstFrameRealtimeMs(),
                mStillArmedRealtimeMs, mLastTextureFrameRealtimeMs);
    }

    private boolean maybeLiftStillAtFrame() {
        if (!mStillAwaitReady || !mNewVideoStill || mExoPlayerController == null
                || !com.newtube.mobile.player.SwitchExperiments.stillLiftAtFrame()) {
            return false;
        }
        if (!canLiftStillAtFrame(true, true, mBackgroundAudioMode,
                mExoPlayerController.getOpenFirstFrameRealtimeMs(), mStillArmedRealtimeMs,
                mLastTextureFrameRealtimeMs)) {
            return false;
        }
        mStillAwaitReady = false;
        mStillAwaitFrame = false;
        liftLoadingStill("frame");
        return true;
    }

    static boolean canLiftStillAtFrame(boolean awaitingReady, boolean newVideoStill,
            boolean backgroundAudio, long firstFrameAtMs, long stillArmedAtMs, long lastTextureFrameAtMs) {
        return awaitingReady && newVideoStill && !backgroundAudio
                && firstFrameOnTexture(firstFrameAtMs, stillArmedAtMs, lastTextureFrameAtMs);
    }

    static boolean firstFrameOnTexture(long firstFrameAtMs, long stillArmedAtMs,
            long lastTextureFrameAtMs) {
        return firstFrameAtMs > 0 && firstFrameAtMs >= stillArmedAtMs
                && lastTextureFrameAtMs >= firstFrameAtMs;
    }

    SurfaceTexture getSessionTexture() {
        return mSessionTexture;
    }

    private void detachVideoTexture() {
        if (mVideoTexture != null && mVideoTexture.getParent() instanceof ViewGroup) {
            ((ViewGroup) mVideoTexture.getParent()).removeView(mVideoTexture);
        }
    }

    private void reattachVideoTexture() {
        if (mVideoTexture != null && mVideoTexture.getParent() == null) {
            ViewGroup contentFrame = mPlayerView.getContentFrame();
            contentFrame.addView(mVideoTexture, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private int mStillW;
    private int mStillH;

    private void ensureStillSize() {
        if (mStillW == 0) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            mStillW = Math.min(dm.widthPixels, dm.heightPixels);
            mStillH = mStillW * 9 / 16;
        }
    }

    @Nullable
    private Bitmap mTappedStill;
    @Nullable
    private String mTappedStillVideoId;

    private void seedTappedStill(@Nullable Video video, @Nullable ImageView thumbnail) {
        mTappedStill = null;
        mTappedStillVideoId = null;
        if (video == null || video.videoId == null || thumbnail == null || thumbnail.getDrawable() == null
                || thumbnail.getWidth() <= 0 || thumbnail.getHeight() <= 0) {
            return;
        }
        try {
            Bitmap frame = Bitmap.createBitmap(thumbnail.getWidth(), thumbnail.getHeight(), Bitmap.Config.RGB_565);
            thumbnail.draw(new android.graphics.Canvas(frame));
            mTappedStill = frame;
            mTappedStillVideoId = video.videoId;
        } catch (RuntimeException | OutOfMemoryError ignored) {
        }
    }

    @Nullable
    private android.graphics.drawable.Drawable takeStillSeed(String videoId) {
        Bitmap tapped = mTappedStill;
        boolean match = tapped != null && videoId.equals(mTappedStillVideoId);
        mTappedStill = null;
        mTappedStillVideoId = null;
        if (match) {
            return new android.graphics.drawable.BitmapDrawable(getResources(), tapped);
        }
        if (mVideoStill.getVisibility() == View.VISIBLE && mVideoStill.getDrawable() != null
                && mVideoStill.getWidth() > 0 && mVideoStill.getHeight() > 0) {
            try {
                Bitmap copy = Bitmap.createBitmap(mVideoStill.getWidth(), mVideoStill.getHeight(),
                        Bitmap.Config.RGB_565);
                mVideoStill.draw(new android.graphics.Canvas(copy));
                return new android.graphics.drawable.BitmapDrawable(getResources(), copy);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                return null;
            }
        }
        return null;
    }

    private void maybeShowLoadingStill(Video item) {
        if (item == null || item.videoId == null || mVideoStill == null
                || Helpers.equals(item.videoId, mStillVideoId)) {
            return;
        }
        mStillVideoId = item.videoId;
        mNewVideoStill = true;
        armStillForReady();
        mStillAwaitFrame = false;
        android.graphics.drawable.Drawable seed = takeStillSeed(item.videoId);
        mVideoStill.animate().cancel();
        mVideoStill.setImageDrawable(seed);
        mVideoStill.setAlpha(1f);
        mVideoStill.setVisibility(View.VISIBLE);

        holdImageRequests();

        String thumb = ClickbaitRemover.updateThumbnail(item, MainUIData.instance(this).getThumbQuality());
        if (thumb != null && !isFinishing() && !isDestroyed()) {
            ensureStillSize();
            String narrow = ClickbaitRemover.fitThumbnail(thumb, getResources().getDimensionPixelSize(
                    R.dimen.mobile_watch_related_thumb_width));
            Glide.with(getApplicationContext())
                    .load(thumb)
                    .onlyRetrieveFromCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .override(mStillW, mStillH)
                    .centerCrop()
                    .placeholder(seed)
                    .error(Glide.with(getApplicationContext())
                            .load(narrow)
                            .onlyRetrieveFromCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .format(DecodeFormat.PREFER_RGB_565)
                            .override(mStillW, mStillH)
                            .centerCrop()
                            .placeholder(seed)
                            .error(seed))
                    .into(mVideoStill);
        }
    }

    private static final long IMAGE_HOLD_TIMEOUT_MS = 6_000;
    private boolean mImageRequestsHeld;
    private final Runnable mReleaseImageRequests = () -> releaseImageRequests("timeout");

    private void holdImageRequests() {
        Utils.removeCallbacks(mReleaseImageRequests);
        Utils.postDelayed(mReleaseImageRequests, IMAGE_HOLD_TIMEOUT_MS);
        if (mImageRequestsHeld || isFinishing() || isDestroyed()) {
            return;
        }
        mImageRequestsHeld = true;
        Glide.with(this).pauseRequests();
        NetPath.log(NetPath.context() + " image-hold on mgr="
                + Integer.toHexString(System.identityHashCode(Glide.with(this))));
    }

    private void releaseImageRequests(String why) {
        Utils.removeCallbacks(mReleaseImageRequests);
        if (!mImageRequestsHeld) {
            return;
        }
        mImageRequestsHeld = false;
        if (!isDestroyed()) {
            Glide.with(this).resumeRequests();
        }
        NetPath.log(NetPath.context() + " image-hold off why=" + why);
    }

    private void releaseImageRequests() {
        releaseImageRequests("unspecified");
    }

    private void showHandoffStill(Bitmap frame) {
        if (mVideoStill == null || frame == null) {
            return;
        }
        mStillAwaitReady = false;
        mStillAwaitFrame = true;
        mVideoStill.animate().cancel();
        mVideoStill.setImageBitmap(frame);
        mVideoStill.setAlpha(1f);
        mVideoStill.setVisibility(View.VISIBLE);
    }

    private void hideVideoStill() {
        hideVideoStill(false, "texture");
    }

    private void hideVideoStill(boolean revealNewVideo, String lift) {
        mNewVideoStill = false;
        if (revealNewVideo) {
            boolean hidden = fadeOutLoadingStill(mVideoStill);
            if (hidden && mVideoArea != null && mVideoArea.isShown()) {
                NetPath.log(NetPath.context() + " picture-visible +" + NetPath.elapsedMs()
                        + " state=ready-texture-overlay-gone lift=" + lift);
            }
            releaseImageRequests("picture-visible");
            return;
        }
        releaseImageRequests("still-lifted");

        if (mVideoStill == null || mVideoStill.getVisibility() != View.VISIBLE) {
            return;
        }
        mVideoStill.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            mVideoStill.setVisibility(View.GONE);
            mVideoStill.setAlpha(1f);
            mVideoStill.setImageDrawable(null);
        }).start();
    }

    private static final long STILL_REVEAL_MS = 100;

    static boolean fadeOutLoadingStill(@Nullable ImageView still) {
        if (still == null || still.getVisibility() != View.VISIBLE) {
            return false;
        }
        still.animate().cancel();
        still.animate().alpha(0f).setDuration(STILL_REVEAL_MS).setInterpolator(Motion.STANDARD)
                .withEndAction(() -> {
                    still.setVisibility(View.GONE);
                    still.setAlpha(1f);
                    still.setImageDrawable(null);
                    NetPath.log(NetPath.context() + " picture-revealed +" + NetPath.elapsedMs());
                }).start();
        return true;
    }

    static boolean hideLoadingStillImmediately(@Nullable ImageView still) {
        if (still == null || still.getVisibility() != View.VISIBLE) {
            return false;
        }
        still.animate().cancel();
        still.setVisibility(View.GONE);
        still.setAlpha(1f);
        still.setImageDrawable(null);
        return true;
    }

    private void releaseSessionTexture() {
        if (mSessionSurface != null) {
            mSessionSurface.release();
            mSessionSurface = null;
        }
        if (mSessionTexture != null) {
            try {
                mSessionTexture.release();
            } catch (RuntimeException ignored) {
            }
            mSessionTexture = null;
        }
    }

    private static final int SWIPE_MINIMIZE = 1;
    private static final int SWIPE_ENTER_FULLSCREEN = 2;
    private static final int SWIPE_EXIT_FULLSCREEN = 3;
    private static final int SWIPE_BRIGHTNESS = 4;
    private static final int SWIPE_VOLUME = 5;
    private static final int SWIPE_SEEK = 6;
    private static final float LEVEL_ZONE = 3f / 8f;
    private static final float FULLSCREEN_FLICK_DP = 800f;
    private static final long FULLSCREEN_ROTATION_WAIT_MS = 700;
    private static final float FULLSCREEN_PULL_SCALE = 0.95f;
    private static final float FULLSCREEN_PULL_SCALE_DP = 38f;
    private static final float FULLSCREEN_PULL_MAX = 0.3f;

    private SwipeLevels mSwipeLevels;
    private float mSwipeDownRawX;
    private float mSwipeStartDy;
    @Nullable
    private MagneticDrag mFullscreenMagnet;
    private int mFullscreenSwipe;
    @Nullable
    private ValueAnimator mFullscreenSettle;

    @Override
    public int onSwipeStart(int direction, float downRawX, float downRawY, float dx, float dy) {
        if (mClosing || mIsInPip || mPipEnterPending || mScrubbing || mBackPreview
                || mFullscreenSwipe != 0 || mVideoArea == null
                || (mExoPlayerController != null && mExoPlayerController.isHoldSpeedOn())) {
            return PlayerContainerLayout.SWIPE_NONE;
        }
        mSwipeDownRawX = downRawX;
        boolean vertical = direction == PlayerContainerLayout.UP || direction == PlayerContainerLayout.DOWN;
        if (!vertical) {
            return beginSeekSwipe(downRawX + dx);
        }
        if (!isLandscape()) {
            if (direction == PlayerContainerLayout.DOWN) {
                return canStartDismissDrag() ? SWIPE_MINIMIZE : PlayerContainerLayout.SWIPE_NONE;
            }
            return beginFullscreenSwipe(SWIPE_ENTER_FULLSCREEN);
        }
        int level = levelSwipeAt(downRawX);
        if (level != PlayerContainerLayout.SWIPE_NONE) {
            mSwipeStartDy = dy;
            hideControls();
            mSwipeLevels.begin(level == SWIPE_VOLUME ? SwipeLevels.VOLUME : SwipeLevels.BRIGHTNESS,
                    mVideoArea.getHeight());
            return level;
        }
        return direction == PlayerContainerLayout.DOWN
                ? beginFullscreenSwipe(SWIPE_EXIT_FULLSCREEN) : PlayerContainerLayout.SWIPE_NONE;
    }

    @Override
    public void onSwipeMove(int swipe, float dx, float dy) {
        switch (swipe) {
            case SWIPE_MINIMIZE:
                onDismissDrag(Math.max(0f, dy));
                break;
            case SWIPE_ENTER_FULLSCREEN:
            case SWIPE_EXIT_FULLSCREEN:
                if (mFullscreenSwipe == swipe) {
                    fullscreenMagnet().move(Math.max(0f, swipe == SWIPE_ENTER_FULLSCREEN ? -dy : dy));
                }
                break;
            case SWIPE_BRIGHTNESS:
            case SWIPE_VOLUME:
                mSwipeLevels.move(mSwipeStartDy - dy);
                break;
            case SWIPE_SEEK:
                mTimeBar.moveSwipeScrub(mSwipeDownRawX + dx);
                break;
            default:
                break;
        }
    }

    @Override
    public void onSwipeReleased(int swipe, float dx, float dy, float xVelocity, float yVelocity) {
        switch (swipe) {
            case SWIPE_MINIMIZE:
                onDismissDragReleased(Math.max(0f, dy), yVelocity);
                break;
            case SWIPE_ENTER_FULLSCREEN:
            case SWIPE_EXIT_FULLSCREEN:
                if (mFullscreenSwipe == swipe) {
                    boolean up = swipe == SWIPE_ENTER_FULLSCREEN;
                    fullscreenMagnet().move(Math.max(0f, up ? -dy : dy));
                    endFullscreenSwipe(up ? -yVelocity : yVelocity);
                }
                break;
            case SWIPE_BRIGHTNESS:
            case SWIPE_VOLUME:
                mSwipeLevels.move(mSwipeStartDy - dy);
                mSwipeLevels.end();
                break;
            case SWIPE_SEEK:
                mTimeBar.moveSwipeScrub(mSwipeDownRawX + dx);
                mTimeBar.stopSwipeScrub(false);
                break;
            default:
                break;
        }
    }

    @Override
    public void onSwipeCancelled(int swipe) {
        switch (swipe) {
            case SWIPE_MINIMIZE:
                onDismissDragCancelled();
                break;
            case SWIPE_ENTER_FULLSCREEN:
            case SWIPE_EXIT_FULLSCREEN:
                if (mFullscreenSwipe == swipe) {
                    fullscreenMagnet().finish();
                    settleFullscreenPull();
                }
                break;
            case SWIPE_BRIGHTNESS:
            case SWIPE_VOLUME:
                mSwipeLevels.end();
                break;
            case SWIPE_SEEK:
                mTimeBar.stopSwipeScrub(true);
                break;
            default:
                break;
        }
    }

    private int levelSwipeAt(float rawX) {
        if (!PlayerGesturePrefs.isLevelSwipesOn(this) || isCastOverlayShown()) {
            return PlayerContainerLayout.SWIPE_NONE;
        }
        int[] at = new int[2];
        mContainer.getLocationOnScreen(at);
        float width = Math.max(1, mContainer.getWidth());
        float x = (rawX - at[0]) / width;
        if (x < LEVEL_ZONE && mSwipeLevels.canSwipe(SwipeLevels.BRIGHTNESS)) {
            return SWIPE_BRIGHTNESS;
        }
        if (x > 1f - LEVEL_ZONE && mSwipeLevels.canSwipe(SwipeLevels.VOLUME)) {
            return SWIPE_VOLUME;
        }
        return PlayerContainerLayout.SWIPE_NONE;
    }

    private boolean isCastOverlayShown() {
        return mCastOverlay != null && mCastOverlay.getVisibility() == View.VISIBLE;
    }

    private int beginSeekSwipe(float rawX) {
        if (!PlayerGesturePrefs.isSeekSwipeOn(this) || mPlayer == null || mExoPlayerController == null
                || mTimeBar == null || isCastOverlayShown() || mMorphAnimator != null || mMorphFraction != 0f
                || mContainer.isInSideSystemGestureBand(mSwipeDownRawX)) {
            return PlayerContainerLayout.SWIPE_NONE;
        }
        long duration = getDurationMs();
        if (duration <= 0) {
            return PlayerContainerLayout.SWIPE_NONE;
        }
        if (!mControlsVisible) {
            showControlsInternal(true);
        }
        mTimeBar.setDuration(duration);
        mTimeBar.setPosition(Math.max(mExoPlayerController.getPositionMs(), 0));
        return mTimeBar.startSwipeScrub(rawX) ? SWIPE_SEEK : PlayerContainerLayout.SWIPE_NONE;
    }

    private int beginFullscreenSwipe(int swipe) {
        if (mPlayer == null || mMorphAnimator != null || mMorphFraction != 0f) {
            return PlayerContainerLayout.SWIPE_NONE;
        }
        if (mFullscreenSettle != null) {
            mFullscreenSettle.cancel();
            mFullscreenSettle = null;
        }
        mFullscreenSwipe = swipe;
        hideControls();
        fullscreenMagnet().start();
        return swipe;
    }

    private MagneticDrag fullscreenMagnet() {
        if (mFullscreenMagnet == null) {
            mFullscreenMagnet = new MagneticDrag(mContainer, MINIMIZE_PULL, this::applyFullscreenPull);
        }
        return mFullscreenMagnet;
    }

    private void applyFullscreenPull(float px) {
        if (mVideoArea == null || mFullscreenSwipe == 0) {
            return;
        }
        float pulled = Math.min(Math.max(0f, px), mVideoArea.getHeight() * FULLSCREEN_PULL_MAX);
        if (mFullscreenSwipe == SWIPE_ENTER_FULLSCREEN) {
            View page = findViewById(R.id.mobile_watch_area);
            if (page != null) {
                page.setTranslationY(-pulled);
            }
            return;
        }
        float shrink = Math.min(1f, pulled / (FULLSCREEN_PULL_SCALE_DP * getResources().getDisplayMetrics().density));
        float scale = 1f - (1f - FULLSCREEN_PULL_SCALE) * shrink;
        mVideoArea.setPivotX(mVideoArea.getWidth() / 2f);
        mVideoArea.setPivotY(0f);
        mVideoArea.setScaleX(scale);
        mVideoArea.setScaleY(scale);
        mVideoArea.setTranslationY(pulled);
    }

    private void endFullscreenSwipe(float velocity) {
        MagneticDrag magnet = fullscreenMagnet();
        boolean detached = magnet.isDetached();
        magnet.finish();
        boolean go;
        if (detached) {
            go = velocity >= -1200f;
        } else {
            go = velocity > FULLSCREEN_FLICK_DP * getResources().getDisplayMetrics().density;
            if (go) {
                Haptics.threshold(mContainer, true);
            }
        }
        if (!go) {
            settleFullscreenPull();
            return;
        }
        if (BuildConfig.DEBUG) {
            NetPath.log("gesture fullscreen " + (mFullscreenSwipe == SWIPE_ENTER_FULLSCREEN ? "enter" : "exit")
                    + " detached=" + detached);
        }
        boolean landscapeNow = isLandscape();
        toggleFullscreen();
        mContainer.postDelayed(() -> {
            if (mFullscreenSwipe != 0 && isLandscape() == landscapeNow) {
                settleFullscreenPull();
            }
        }, FULLSCREEN_ROTATION_WAIT_MS);
    }

    private void settleFullscreenPull() {
        if (mVideoArea == null) {
            mFullscreenSwipe = 0;
            return;
        }
        if (mFullscreenSettle != null) {
            mFullscreenSettle.cancel();
        }
        float from = fullscreenMagnet().position();
        ValueAnimator settle = ValueAnimator.ofFloat(from, 0f);
        settle.setDuration(180);
        settle.setInterpolator(new android.view.animation.DecelerateInterpolator());
        settle.addUpdateListener(a -> applyFullscreenPull((float) a.getAnimatedValue()));
        settle.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mFullscreenSettle == animation) {
                    mFullscreenSettle = null;
                }
                if (!mCancelled) {
                    resetFullscreenPull();
                }
            }
        });
        mFullscreenSettle = settle;
        settle.start();
    }

    private void resetFullscreenPull() {
        if (mFullscreenSettle != null) {
            mFullscreenSettle.cancel();
            mFullscreenSettle = null;
        }
        if (mFullscreenMagnet != null) {
            mFullscreenMagnet.finish();
        }
        if (mFullscreenSwipe != 0) {
            View page = findViewById(R.id.mobile_watch_area);
            if (page != null) {
                page.setTranslationY(0f);
            }
            if (mVideoArea != null) {
                mVideoArea.setScaleX(1f);
                mVideoArea.setScaleY(1f);
                mVideoArea.setTranslationY(0f);
            }
        }
        mFullscreenSwipe = 0;
    }

    private void onFullscreenSwipeConfigured() {
        resetFullscreenPull();
        updateSwipeBrightness();
    }

    private void updateSwipeBrightness() {
        if (mSwipeLevels != null) {
            mSwipeLevels.setBrightnessActive(isLandscape() && !mIsInPip && !mPipEnterPending);
        }
    }

    private float mMorphScaleX = 1f;
    private float mMorphScaleY = 1f;
    private float mMorphTx;
    private float mMorphTy;
    private float mMorphFraction;
    private ValueAnimator mMorphAnimator;
    private long mInstantRevealAt;
    private float mDragTravelPx = 1f;
    @Nullable
    private MagneticDrag mMinimizeMagnet;
    private boolean mMagnetDragging;
    private static final float MINIMIZE_FLICK_DP = 800f;
    private static final float MINIMIZE_PULL = 0.75f;
    private static final long SETTLE_MIN_MS = 90;
    private static final long SETTLE_MAX_MS = 280;
    private static final float SETTLE_LAND_STIFFNESS = 800f;
    private static final float SETTLE_LAND_DAMPING = 0.85f;
    private boolean mMorphOverOwnBackdrop;
    private boolean mMorphStartPending;
    private static final int MINI_CARD_WIDTH_DP = 180;
    private static final long HOST_CARD_FOLD_WAIT_MS = 50;
    private static final int MINI_CARD_HEIGHT_DP = 102;
    private static final float MORPH_CORNER_DP = 12f;
    private static final float MORPH_ELEVATION_DP = 8f;
    private float mMorphCornerLocalPx;
    @Nullable
    private android.view.ViewOutlineProvider mVideoAreaOutline;
    private final android.view.ViewOutlineProvider mMorphOutline = new android.view.ViewOutlineProvider() {
        @Override
        public void getOutline(View view, android.graphics.Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mMorphCornerLocalPx);
        }
    };

    private void computeMorphTarget() {
        float density = getResources().getDisplayMetrics().density;
        float cardW = MINI_CARD_WIDTH_DP * density;
        float margin = 12 * density;
        MiniPlayerBridge.MiniHost host = MiniPlayerBridge.getMiniHost();
        float bottomNav = host != null ? host.getMiniCardBottomOffsetPx() : 56 * density;

        Rect video = new Rect();
        video.set(0, 0, mVideoArea.getWidth(), mVideoArea.getHeight());
        mContainer.offsetDescendantRectToMyCoords(mVideoArea, video);

        float scale = video.width() > 0 ? cardW / video.width() : 0.44f;
        float cardH = video.height() * scale;
        float targetX = mContainer.getWidth() - margin - cardW;
        float targetY = mContainer.getHeight() - bottomNav - margin - cardH;

        mMorphScaleX = scale;
        mMorphScaleY = scale;
        mMorphTx = targetX - video.left;
        mMorphTy = targetY - video.top;
    }

    private void computeMorphTarget(Rect sourceBounds) {
        Rect video = new Rect(0, 0, mVideoArea.getWidth(), mVideoArea.getHeight());
        mContainer.offsetDescendantRectToMyCoords(mVideoArea, video);

        int[] containerLocation = new int[2];
        mContainer.getLocationOnScreen(containerLocation);
        float sourceLeft = sourceBounds.left - containerLocation[0];
        float sourceTop = sourceBounds.top - containerLocation[1];

        mMorphScaleX = video.width() > 0
                ? (float) sourceBounds.width() / video.width() : 1f;
        mMorphScaleY = video.height() > 0
                ? (float) sourceBounds.height() / video.height() : mMorphScaleX;
        mMorphTx = sourceLeft - video.left;
        mMorphTy = sourceTop - video.top;
    }

    private void startOpenMorph(Rect sourceBounds, long durationMs) {
        if (mContainer == null || mVideoArea == null) {
            return;
        }
        overridePendingTransition(0, 0);
        mContainer.setVisibility(View.INVISIBLE);
        mMorphStartPending = true;
        mContainer.post(() -> {
            mMorphStartPending = false;
            if (isFinishing() || isDestroyed()) {
                return;
            }
            computeMorphTarget(sourceBounds);
            applyMorph(1f);
            mContainer.setVisibility(View.VISIBLE);
            mContainer.postOnAnimation(() -> animateMorph(0f, durationMs, Motion.EMPHASIZED, () -> {
                resetMorph();
                if (mVideoStill != null && mVideoStill.getVisibility() == View.VISIBLE) {
                    mStillAwaitFrame = true;
                }
            }));
        });
    }

    private void applyMorph(float f) {
        mMorphFraction = f;
        mVideoArea.setPivotX(0f);
        mVideoArea.setPivotY(0f);
        float sx = 1f + (mMorphScaleX - 1f) * f;
        float sy = 1f + (mMorphScaleY - 1f) * f;
        mVideoArea.setScaleX(sx);
        mVideoArea.setScaleY(sy);
        mVideoArea.setTranslationX(mMorphTx * f);
        mVideoArea.setTranslationY(mMorphTy * f);
        float density = getResources().getDisplayMetrics().density;
        mVideoArea.setTranslationZ(f > 0f ? MORPH_ELEVATION_DP * density : 0f);
        float settled = Math.min(1f, f);
        applyMorphCorners(settled, Math.min(sx, sy), density);

        float contentAlpha = Math.max(0f, 1f - f * 5f);
        if (mWatchContent != null) {
            mWatchContent.setAlpha(contentAlpha);
        }
        float backdrop = morphBackdropAlpha(settled, mMorphOverOwnBackdrop);
        if (mWatchScroll != null && mWatchScroll.getBackground() != null) {
            int backdropAlpha = Math.round(255f * backdrop);
            mWatchScroll.getBackground().mutate().setAlpha(backdropAlpha);
        }
        setWindowBackdropAlpha(backdrop);
        if (mControlsRoot != null && mControlsRoot.getVisibility() == View.VISIBLE) {
            mControlsRoot.setAlpha(contentAlpha);
        }
        if (mTimeBar != null) {
            mTimeBar.setAlpha(contentAlpha);
        }
        if (mCommentsPanel != null) {
            mCommentsPanel.setMorphAlpha(contentAlpha);
        }
    }

    static float morphBackdropAlpha(float f, boolean overOwnBackdrop) {
        return overOwnBackdrop ? 1f : 1f - f;
    }

    private void setWindowBackdropAlpha(float alpha) {
        Drawable backdrop = getWindow().getDecorView().getBackground();
        if (backdrop != null) {
            int drawableAlpha = Math.round(255f * Math.max(0f, Math.min(1f, alpha)));
            backdrop.mutate().setAlpha(drawableAlpha);
        }
    }

    private void applyMorphCorners(float f, float scale, float density) {
        if (f <= 0f) {
            restoreVideoAreaOutline();
            return;
        }
        mMorphCornerLocalPx = scale > 0f ? MORPH_CORNER_DP * density * f / scale : 0f;
        if (mVideoArea.getOutlineProvider() != mMorphOutline) {
            mVideoAreaOutline = mVideoArea.getOutlineProvider();
            mVideoArea.setOutlineProvider(mMorphOutline);
            mVideoArea.setClipToOutline(true);
        }
        mVideoArea.invalidateOutline();
    }

    private void restoreVideoAreaOutline() {
        if (mVideoArea.getOutlineProvider() == mMorphOutline) {
            mVideoArea.setClipToOutline(false);
            mVideoArea.setOutlineProvider(mVideoAreaOutline != null
                    ? mVideoAreaOutline : android.view.ViewOutlineProvider.BACKGROUND);
            mVideoAreaOutline = null;
        }
        mMorphCornerLocalPx = 0f;
    }

    private void resetMorph() {
        if (mMorphAnimator != null) {
            mMorphAnimator.cancel();
            mMorphAnimator = null;
        }
        endMagnetDrag();
        if (mDismissDragActive) {
            mDismissDragActive = false;
            updatePipActions();
        }
        mMorphFraction = 0f;
        mMorphOverOwnBackdrop = false;
        mVideoArea.setScaleX(1f);
        mVideoArea.setScaleY(1f);
        mVideoArea.setTranslationX(0f);
        mVideoArea.setTranslationY(0f);
        mVideoArea.setTranslationZ(0f);
        restoreVideoAreaOutline();
        if (mWatchContent != null) {
            mWatchContent.setAlpha(1f);
        }
        if (mWatchScroll != null && mWatchScroll.getBackground() != null) {
            mWatchScroll.getBackground().mutate().setAlpha(255);
        }
        setWindowBackdropAlpha(1f);
        if (mControlsRoot != null) {
            mControlsRoot.setAlpha(mControlsVisible ? 1f : 0f);
        }
        if (mTimeBar != null) {
            mTimeBar.setAlpha(1f);
        }
        if (mCommentsPanel != null) {
            mCommentsPanel.setMorphAlpha(1f);
        }
    }

    private void animateMorph(float to, long durationMs, @Nullable Runnable endAction) {
        animateMorph(to, durationMs, new DecelerateInterpolator(), endAction);
    }

    private void animateMorph(float to, long durationMs, android.view.animation.Interpolator interpolator,
            @Nullable Runnable endAction) {
        if (mMorphAnimator != null) {
            mMorphAnimator.cancel();
        }
        ValueAnimator animator = ValueAnimator.ofFloat(mMorphFraction, to);
        mMorphAnimator = animator;
        animator.setDuration(durationMs);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(a -> applyMorph((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mMorphAnimator == animator) {
                    mMorphAnimator = null;
                }
                if (!mCancelled && endAction != null) {
                    endAction.run();
                }
            }
        });
        animator.start();
    }

    private boolean canStartDismissDrag() {
        return !mScrubbing && mPlayer != null && mMorphAnimator == null;
    }

    private void onDismissDrag(float dy) {
        if (!mMagnetDragging) {
            if (dy <= 0f) {
                return;
            }
            mMagnetDragging = true;
            beginMinimizeMorph();
            mDragTravelPx = dragTravelFor(mContainer.getDownRawY());
            minimizeMagnet().start();
        }
        minimizeMagnet().move(dy);
    }

    private MagneticDrag minimizeMagnet() {
        if (mMinimizeMagnet == null) {
            mMinimizeMagnet = new MagneticDrag(mContainer, MINIMIZE_PULL,
                    position -> applyMorph(Math.max(0f, Math.min(1f, position / mDragTravelPx))));
        }
        return mMinimizeMagnet;
    }

    private boolean endMagnetDrag() {
        if (!mMagnetDragging) {
            return false;
        }
        mMagnetDragging = false;
        MagneticDrag magnet = minimizeMagnet();
        magnet.finish();
        return magnet.isDetached();
    }

    private float dragTravelFor(float downRawY) {
        float fallback = Math.max(1, mContainer.getHeight()) * 0.6f;
        float height = mVideoArea.getHeight();
        if (height <= 0f) {
            return fallback;
        }
        int[] location = new int[2];
        mVideoArea.getLocationOnScreen(location);
        float grabbed = Math.max(0f, Math.min(1f, (downRawY - location[1]) / height));
        float travel = mMorphTy + grabbed * height * (mMorphScaleY - 1f);
        return Math.max(travel, Math.max(1, mContainer.getHeight()) * 0.33f);
    }

    private void onDismissDragCancelled() {
        endMagnetDrag();
        settleMorph(0f, 0f, this::resetMorph);
    }

    private void settleMorph(float to, float yVelocity, Runnable endAction) {
        float distance = Math.abs(to - mMorphFraction);
        float speedToward = to > mMorphFraction ? yVelocity : -yVelocity;
        long durationMs;
        if (speedToward > 300f) {
            durationMs = Math.round(2000f * distance * mDragTravelPx / speedToward);
        } else {
            durationMs = Math.round(60f + 150f * distance);
        }
        durationMs = Math.max(SETTLE_MIN_MS, Math.min(SETTLE_MAX_MS, durationMs));
        if (to <= mMorphFraction) {
            animateMorph(to, durationMs, new android.view.animation.DecelerateInterpolator(), endAction);
            return;
        }
        float travel = Math.max(1f, mDragTravelPx);
        float launch = Math.max(yVelocity / travel, 2f * distance * 1000f / durationMs);
        launch = Math.min(launch, 2f * distance * 1000f / SETTLE_MIN_MS);
        Motion.Spring spring = new Motion.Spring(mMorphFraction, to, launch,
                SETTLE_LAND_STIFFNESS, SETTLE_LAND_DAMPING, 1f / travel);
        animateMorph(to, spring.durationMs, spring, endAction);
    }

    private void beginMinimizeMorph() {
        computeMorphTarget();
        mMorphOverOwnBackdrop = MiniPlayerBridge.getMiniHost() == null;
        mDismissDragActive = true;
        updatePipActions();
    }

    private void onDismissDragReleased(float dy, float yVelocity) {
        if (mMagnetDragging) {
            minimizeMagnet().move(dy);
        }
        boolean dismiss;
        if (endMagnetDrag()) {
            dismiss = yVelocity >= -1200f;
        } else {
            dismiss = yVelocity > MINIMIZE_FLICK_DP * getResources().getDisplayMetrics().density;
            if (dismiss) {
                Haptics.threshold(mContainer, true);
            }
        }

        if (!dismiss) {
            settleMorph(0f, yVelocity, this::resetMorph);
            return;
        }

        if (mPlayer == null) {
            animateMorph(1f, 150, () -> {
                if (mPresenter != null) {
                    mPresenter.onFinish();
                }
                finish();
                overridePendingTransition(0, 0);
            });
            return;
        }

        float remaining = Math.max(0f, 1f - mMorphFraction);
        if (remaining < 0.001f) {
            applyMorph(1f);
            minimizeByDrag();
        } else {
            settleMorph(1f, yVelocity, this::minimizeByDrag);
        }
    }

    private void minimizeByDrag() {
        if (mIsInPip || mPipEnterPending) {
            logPip("minimize-blocked reason=in-pip");
            resetMorph();
            return;
        }

        if (!prepareMiniPlayerHandoff(false)) {
            return;
        }

        MiniPlayerBridge.MiniHost host = MiniPlayerBridge.getMiniHost();
        final Class<?> hostView = host != null ? host.getMiniHostViewClass() : BrowseView.class;

        Runnable showHost = () -> {
            if (isFinishing() || isDestroyed() || mIsInPip || mPipEnterPending) {
                return;
            }
            getViewManager().startView(hostView);
            if (host != null) {
                overridePendingTransition(0, 0);
            }
            getViewManager().removeTop(this);
        };

        boolean prepared = MiniPlayerBridge.prepareMiniHostForHandoff(showHost);
        if (BuildConfig.DEBUG) {
            android.util.Log.d(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG,
                    "mini minimize host=" + (host != null ? host.getClass().getSimpleName() : "none")
                            + " prepared=" + prepared);
        }
        if (!prepared) {
            showHost.run();
        }
    }

    boolean minimizeForNavigation() {
        if (mIsInPip || mPipEnterPending) {
            logPip("minimize-blocked reason=in-pip-navigation");
            return false;
        }

        if (!prepareMiniPlayerHandoff(true)) {
            return false;
        }
        getViewManager().removeTop(this);
        return true;
    }

    private boolean prepareMiniPlayerHandoff(boolean captureFullSizeStill) {
        if (mPlayer == null) {
            return false;
        }

        if (mVideoTexture != null && mVideoTexture.isAvailable()) {
            Bitmap frame;
            if (captureFullSizeStill) {
                frame = mVideoTexture.getBitmap();
            } else {
                float density = getResources().getDisplayMetrics().density;
                int width = Math.max(1, Math.round(MINI_CARD_WIDTH_DP * density));
                int height = Math.max(1, Math.round(MINI_CARD_HEIGHT_DP * density));
                frame = mVideoTexture.getBitmap(width, height);
            }
            if (frame != null) {
                showHandoffStill(frame);
                mStillAwaitFrame = false;
                MiniPlayerBridge.setMiniEntryStill(frame);
            }
        }
        detachVideoTexture();
        if (BuildConfig.DEBUG) {
            android.util.Log.d(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG,
                    "mini handoff detach t=" + android.os.SystemClock.uptimeMillis());
        }
        MiniPlayerBridge.activate(this);
        float cardDensity = getResources().getDisplayMetrics().density;
        mExoPlayerController.setSmallWindowViewport("mini", Math.round(MINI_CARD_WIDTH_DP * cardDensity),
                Math.round(MINI_CARD_HEIGHT_DP * cardDensity));
        getViewManager().enablePlayerOnlyMode(false);
        mSuppressAutoPip = true;
        updatePipActions();
        return true;
    }

    void closeFromMiniPlayer() {
        if (mPresenter != null) {
            mPresenter.onFinish();
        }
        getViewManager().removeTop(this);
        super.finish();
    }

    ExoPlayer getSharedPlayer() {
        return mPlayer;
    }
    @Override
    public void showOverlay(boolean show) {
        if (show) {
            showControlsInternal(true);
        } else if (!isHoldingSeekBar()) {
            hideControls();
        }
    }

    @Override
    public boolean isOverlayShown() {
        return mControlsVisible;
    }

    @Override
    public void showControls(boolean show) {
        showOverlay(show);
    }

    @Override
    public boolean isControlsShown() {
        return mControlsVisible;
    }

    @Override
    public void setTitle(String title) {
        if (mTitleView != null) {
            mTitleView.setText(title);
        }
    }

    private void syncPlayPauseWithSpinner() {
        if (mPlayPauseButton == null) {
            return;
        }
        boolean noticeUp = mNoticeView != null && mNoticeView.getVisibility() == View.VISIBLE;
        boolean hide = mSpinnerShown && !noticeUp;
        mPlayPauseButton.setClickable(!hide);
        float target = hide ? 0f : 1f;
        mPlayPauseButton.animate().cancel();
        if (mPlayPauseButton.getAlpha() != target) {
            mPlayPauseButton.animate().alpha(target)
                    .setDuration(hide ? Motion.FADE_OUT_MS : Motion.FADE_IN_MS)
                    .setInterpolator(Motion.STANDARD).start();
        }
    }

    @Override
    public void showPlaybackNotice(String message) {
        if (mNoticeView == null) {
            return;
        }

        boolean show = message != null && !message.isEmpty();
        mNoticeView.setText(show ? message : null);
        mNoticeView.setVisibility(show ? View.VISIBLE : View.GONE);
        syncPlayPauseWithSpinner();
        if (show) {
            showControls(true);
        }
    }

    private boolean mSpinnerShown;

    @Override
    public void showProgressBar(boolean show) {
        if (mProgressBar != null && show != mSpinnerShown) {
            mSpinnerShown = show;
            mProgressBar.animate().cancel();
            if (show) {
                mProgressBar.setAlpha(0f);
                mProgressBar.setVisibility(View.VISIBLE);
                mProgressBar.animate().alpha(1f).setDuration(Motion.FADE_IN_MS)
                        .setInterpolator(Motion.STANDARD).start();
            } else {
                mProgressBar.animate().alpha(0f).setDuration(Motion.FADE_OUT_MS)
                        .setInterpolator(Motion.STANDARD)
                        .withEndAction(() -> mProgressBar.setVisibility(View.GONE)).start();
            }
            syncPlayPauseWithSpinner();
        }
        if (mSetupHint != null) {
            mSetupHint.setVisibility(show && !SessionWarmup.isWarm() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void updateSuggestions(VideoGroup group) {
        if (group == null || group.isEmpty()) {
            return;
        }

        if (group.isChapters()) {
            runOnUiThread(() -> setChapters(group.getVideos()));
            return;
        }

        runOnUiThread(() -> {
            int id = group.getId();
            List<Video> incoming = group.getVideos();

            switch (group.getAction()) {
                case VideoGroup.ACTION_REPLACE:
                    mSuggestionVideos.put(id, new ArrayList<>(incoming));
                    mSuggestionGroups.put(id, group);
                    break;
                case VideoGroup.ACTION_REMOVE:
                case VideoGroup.ACTION_REMOVE_AUTHOR: {
                    List<Video> existing = mSuggestionVideos.get(id);
                    if (existing != null) {
                        existing.removeAll(incoming);
                    }
                    break;
                }
                case VideoGroup.ACTION_SYNC: {
                    List<Video> existing = mSuggestionVideos.get(id);
                    if (existing == null) {
                        mSuggestionVideos.put(id, new ArrayList<>(incoming));
                        mSuggestionGroups.put(id, group);
                    } else {
                        for (Video v : incoming) {
                            int idx = existing.indexOf(v);
                            if (idx >= 0) {
                                existing.set(idx, v);
                            }
                        }
                    }
                    break;
                }
                case VideoGroup.ACTION_APPEND:
                default: {
                    List<Video> existing = mSuggestionVideos.get(id);
                    if (existing == null) {
                        mSuggestionVideos.put(id, new ArrayList<>(incoming));
                    } else {
                        for (Video v : incoming) {
                            if (!existing.contains(v)) {
                                existing.add(v);
                            }
                        }
                    }
                    mSuggestionGroups.put(id, group);
                    break;
                }
            }

            rebuildRelatedList();
        });
    }

    @Override
    public void removeSuggestions(VideoGroup group) {
        if (group == null) {
            return;
        }

        runOnUiThread(() -> {
            mSuggestionVideos.remove(group.getId());
            mSuggestionGroups.remove(group.getId());
            rebuildRelatedList();
        });
    }

    @Override
    public int getSuggestionsIndex(VideoGroup group) {
        if (group == null) {
            return -1;
        }

        int id = group.getId();
        int i = 0;
        for (Integer key : mSuggestionVideos.keySet()) {
            if (key != null && key == id) {
                return i;
            }
            i++;
        }
        return -1;
    }

    @Override
    public VideoGroup getSuggestionsByIndex(int index) {
        if (mRelatedVideos.isEmpty() || index < 0) {
            return null;
        }

        int i = 0;
        for (Integer key : mSuggestionVideos.keySet()) {
            if (i == index) {
                List<Video> vids = mSuggestionVideos.get(key);
                return (vids == null || vids.isEmpty()) ? null : VideoGroup.from(vids);
            }
            i++;
        }
        return null;
    }

    @Override
    public void focusSuggestedItem(int index) {
    }

    @Override
    public void focusSuggestedItem(Video video) {
    }

    @Override
    public void resetSuggestedPosition() {
    }

    @Override
    public boolean isSuggestionsEmpty() {
        return mRelatedVideos.isEmpty() && mQueueVideos.isEmpty();
    }

    @Override
    public void clearSuggestions() {
        runOnUiThread(() -> {
            mRelatedRenderGate.cancelPending();
            mSuggestionVideos.clear();
            mSuggestionGroups.clear();
            mRelatedVideos.clear();
            mQueueVideos.clear();
            mLastPagedVideo = null;
            mRelatedWindow = RELATED_WINDOW_INITIAL;
            setChapters(null);
            if (mRelatedAdapter != null) {
                mRelatedAdapter.submitList(new ArrayList<>());
            }
            if (mQueueCard != null) {
                mQueueCard.setVisibility(View.GONE);
            }
            if (mQueueAdapter != null) {
                mQueueAdapter.submitList(new ArrayList<>());
            }
            if (mWatchRelatedLabel != null) {
                mWatchRelatedLabel.setText(R.string.mobile_watch_related);
                mWatchRelatedLabel.setVisibility(View.VISIBLE);
            }
            showRelatedSkeleton();
        });
    }

    private static final long SKELETON_TIMEOUT_MS = 10_000;
    private final Runnable mHideSkeletonTimeout = this::onRelatedSkeletonTimeout;

    private void onRelatedSkeletonTimeout() {
        hideRelatedSkeleton();
        if (!mRelatedVideos.isEmpty()) {
            return;
        }
        boolean offline = !com.liskovsoft.smartyoutubetv2.common.utils.LoadFailure.hasValidatedNetwork(this);
        if (mWatchRelatedLabel != null) {
            if (offline) {
                mWatchRelatedLabel.setText(R.string.mobile_watch_offline);
                mWatchRelatedLabel.setVisibility(View.VISIBLE);
            } else {
                mWatchRelatedLabel.setVisibility(View.GONE);
            }
        }
        if (mCommentsKey == null && mWatchCommentsEntry != null) {
            mWatchCommentsEntry.setVisibility(View.GONE);
        }
        if (TextUtils.isEmpty(mWatchSubs.getText())) {
            mWatchSubs.setVisibility(View.GONE);
        }
        if (offline) {
            if (isCountUnset(mWatchLikeCount)) {
                mWatchLikeCount.setVisibility(View.GONE);
            }
            if (isCountUnset(mWatchDislikeCount)) {
                mWatchDislikeCount.setVisibility(View.GONE);
            }
        }
    }

    private void showRelatedSkeleton() {
        if (mRelatedSkeleton == null) {
            return;
        }
        mRelatedSkeleton.setVisibility(View.VISIBLE);
        Utils.removeCallbacks(mHideSkeletonTimeout);
        Utils.postDelayed(mHideSkeletonTimeout, SKELETON_TIMEOUT_MS);
    }

    private void hideRelatedSkeleton() {
        Utils.removeCallbacks(mHideSkeletonTimeout);
        if (mRelatedSkeleton != null) {
            mRelatedSkeleton.setVisibility(View.GONE);
        }
    }

    @Override
    public void showSuggestions(boolean show) {
    }

    @Override
    public boolean isSuggestionsShown() {
        return false;
    }

    @Override
    public int getButtonState(int buttonId) {
        if (buttonId == R.id.action_thumbs_up
                || buttonId == R.id.action_thumbs_down
                || buttonId == R.id.action_subscribe
                || buttonId == R.id.action_chat
                || buttonId == R.id.action_repeat
                || buttonId == R.id.action_video_stats
                || buttonId == R.id.action_playlist_add
                || buttonId == R.id.action_rotate
                || buttonId == R.id.action_sound_off
                || buttonId == R.id.lb_control_closed_captioning) {
            return mButtonStates.get(buttonId, BUTTON_OFF);
        }
        return BUTTON_DISABLED;
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {
        mButtonStates.put(buttonId, buttonState);
        runOnUiThread(() -> updateButtonVisual(buttonId, buttonState));
    }

    @Override
    public void setChannelIcon(String iconUrl) {
        runOnUiThread(() -> {
            if (mWatchAvatar == null) {
                return;
            }
            if (TextUtils.isEmpty(iconUrl)) {
                mWatchAvatar.setImageResource(R.drawable.ic_watch_channel_placeholder);
            } else {
                Glide.with(this)
                        .load(iconUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_watch_channel_placeholder)
                        .error(R.drawable.ic_watch_channel_placeholder)
                        .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
                                .withCrossFade((int) Motion.FADE_IN_MS))
                        .into(mWatchAvatar);
            }
        });
    }

    @Override
    public void setSeekPreviewTitle(String title) {
    }

    @Override
    public void setNextTitle(Video nextVideo) {
    }

    @Override
    public void showDebugInfo(boolean show) {
        createDebugManager();
        if (mDebugInfoManager != null) {
            mDebugInfoManager.show(show);
        }
    }

    @Override
    public void showSubtitles(boolean show) {
        createSubtitleManager();
        if (mSubtitleManager != null) {
            mSubtitleManager.show(show);
        }
    }

    private void createSubtitleManager() {
        if (mSubtitleManager != null || mPlayer == null || mPlayerView == null) {
            return;
        }

        androidx.media3.ui.SubtitleView subtitleView = mPlayerView.getSubtitleView();
        if (subtitleView == null) {
            return;
        }

        mSubtitleManager = new Media3SubtitleManager(subtitleView);
        mPlayer.addListener(mSubtitleManager);
    }

    private void createDebugManager() {
        if (mDebugInfoManager != null || mDebugViewGroup == null || mPlayer == null) {
            return;
        }
        mDebugInfoManager = new Media3DebugInfoManager(mDebugViewGroup, mPlayer,
                mExoPlayerController.getMediaSourceFactory().getBandwidthMeter());
    }

    @Override
    public void loadStoryboard() {
    }

    @Override
    public void setSeekBarSegments(List<SeekBarSegment> segments) {
        if (mTimeBar == null) {
            return;
        }
        runOnUiThread(() -> mTimeBar.setSegments(segments));
    }

    @Override
    public void updateEndingTime() {
    }

    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
        runOnUiThread(() -> {
            mChatReceiver = chatReceiver;

            if (chatReceiver == null) {
                return;
            }

            chatReceiver.setCallback(this::onChatItemReceived);

            if (mWatchChatEntry != null) {
                mWatchChatEntry.setVisibility(View.VISIBLE);
            }
        });
    }

    private void onChatItemReceived(ChatItem item) {
        if (item == null) {
            return;
        }
        runOnUiThread(() -> {
            mChatItems.add(item);
            while (mChatItems.size() > MAX_CHAT_ITEMS) {
                mChatItems.remove(0);
            }
            if (mChatObserver != null) {
                mChatObserver.onChatItem(item);
            }
        });
    }
    private static final SeekParameters CHAPTER_SEEK_PARAMETERS =
            new SeekParameters(0, 2_000_000);

    private void setChapters(List<Video> chapters) {
        if (mChaptersSheet != null && !sameChapters(mChapterVideos, chapters)) {
            mChaptersSheet.dismiss();
            mChaptersSheet = null;
        }

        mChapterVideos.clear();

        if (chapters != null && !chapters.isEmpty()) {
            mChapterVideos.addAll(chapters);
        }

        updateChapterMarks();
        mChapterButtonIndex = -1;
        updateChapterButton(mExoPlayerController != null ? mExoPlayerController.getPositionMs() : 0);
    }

    private static boolean sameChapters(List<Video> current, @Nullable List<Video> incoming) {
        int size = incoming != null ? incoming.size() : 0;
        if (current.size() != size) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            Video a = current.get(i);
            Video b = incoming.get(i);
            if (a.startTimeMs != b.startTimeMs || !TextUtils.equals(a.title, b.title)) {
                return false;
            }
        }
        return true;
    }

    private void fitChapterButton() {
        if (mChapterButton == null || !(mChapterButton.getParent() instanceof View)) {
            return;
        }

        View line = (View) mChapterButton.getParent();
        int lineBottom = offsetInControls(line, false) + line.getHeight();
        int lineTop = lineBottom - Math.max(mChapterButton.getMinHeight(), mChapterButton.getHeight());
        int buttonLeft = offsetInControls(line, true)
                + ((ViewGroup.MarginLayoutParams) mChapterButton.getLayoutParams()).getMarginStart();

        int maxWidth = Integer.MAX_VALUE;
        for (View control : new View[] {mPrevButton, mPlayPauseButton, mNextButton}) {
            if (control == null || control.getVisibility() != View.VISIBLE) {
                continue;
            }
            int controlBottom = offsetInControls(control, false) + control.getHeight()
                    - (control == mPlayPauseButton ? 0 : control.getPaddingBottom());
            int controlLeft = offsetInControls(control, true)
                    + (control == mPlayPauseButton ? 0 : control.getPaddingLeft());
            if (controlBottom > lineTop && controlLeft > buttonLeft) {
                maxWidth = Math.min(maxWidth, Math.max(dp(64), controlLeft - buttonLeft - dp(8)));
            }
        }

        if (mChapterButton.getMaxWidth() != maxWidth) {
            mChapterButton.setMaxWidth(maxWidth);
        }
    }

    private int offsetInControls(View view, boolean horizontal) {
        int offset = 0;
        View current = view;
        while (current != null && current != mControlsRoot) {
            offset += horizontal ? current.getLeft() : current.getTop();
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return offset;
    }

    private void updateChapterMarks() {
        if (mTimeBar == null) {
            return;
        }

        long[] starts = new long[mChapterVideos.size()];
        for (int i = 0; i < starts.length; i++) {
            starts[i] = mChapterVideos.get(i).startTimeMs;
        }
        mTimeBar.setChapterStarts(starts);
    }

    private int chapterIndexAt(long positionMs) {
        int index = -1;
        for (int i = 0; i < mChapterVideos.size(); i++) {
            if (mChapterVideos.get(i).startTimeMs <= positionMs) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    private void updateChapterButton(long positionMs) {
        if (mChapterButton == null) {
            return;
        }

        int index = chapterIndexAt(positionMs);
        if (index >= 0 && TextUtils.isEmpty(mChapterVideos.get(index).title)) {
            index = -1;
        }

        if (index < 0) {
            mChapterButtonIndex = -1;
            mChapterButton.setVisibility(View.GONE);
            return;
        }

        if (index != mChapterButtonIndex) {
            boolean changed = mChapterButtonIndex >= 0 && mChapterButton.getVisibility() == View.VISIBLE;
            mChapterButtonIndex = index;
            String title = mChapterVideos.get(index).title;
            CharSequence text = getString(R.string.mobile_player_chapter_title, title);
            mChapterButton.setContentDescription(getString(R.string.mobile_player_chapter_button, title));
            if (changed && mControlsVisible && !mScrubbing) {
                crossfadeText(mChapterButton, text);
            } else {
                mChapterButton.animate().cancel();
                mChapterButton.setAlpha(1f);
                mChapterButton.setText(text);
            }
        }
        mChapterButton.setVisibility(View.VISIBLE);
    }

    private static void crossfadeText(TextView view, CharSequence text) {
        view.animate().cancel();
        view.animate().alpha(0f).setDuration(Motion.FADE_OUT_MS).setInterpolator(Motion.STANDARD_ACCELERATE)
                .withEndAction(() -> {
                    view.setText(text);
                    view.animate().alpha(1f).setDuration(Motion.FADE_IN_MS)
                            .setInterpolator(Motion.STANDARD_DECELERATE).start();
                }).start();
    }

    private void updateScrubLabel(long positionMs) {
        if (mScrubChapterView == null) {
            return;
        }

        int index = chapterIndexAt(positionMs);
        CharSequence title = index >= 0 ? mChapterVideos.get(index).title : null;
        String time = formatTime(positionMs);
        setTextIfChanged(mScrubChapterView, TextUtils.isEmpty(title) ? time : time + "   " + title);
    }

    private void setScrubChrome(boolean scrubbing) {
        mScrubChromeHidden = scrubbing;
        float alpha = scrubbing ? 0f : 1f;
        long duration = scrubbing ? Motion.FADE_OUT_MS : Motion.FADE_IN_MS;
        for (View view : new View[] {mBackButton, mTitleView, mOptionsRow, mTransport, mBottomRow, mTopScrim}) {
            if (view != null) {
                view.animate().cancel();
                view.animate().alpha(alpha).setDuration(duration).setInterpolator(Motion.STANDARD).start();
            }
        }
        fadePill(mScrubChapterView, scrubbing);
    }

    private void showTopPill(@Nullable CharSequence text) {
        showTopPill(text, 0);
    }

    private void showTopPill(@Nullable CharSequence text, int iconRes) {
        if (mTopPill == null) {
            return;
        }
        if (text != null) {
            setTextIfChanged(mTopPill, text);
            mTopPill.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, iconRes, 0);
            mTopPill.setCompoundDrawablePadding(iconRes != 0 ? dp(4) : 0);
        }
        fadePill(mTopPill, text != null);
    }

    private static final float HOLD_SPEED = 2f;

    private boolean beginHoldSpeed() {
        Video video = getVideo();
        if (mExoPlayerController == null || mPlayer == null || mIsEnded || mIsInPip || mScrubbing
                || (mCastSessionManager != null && mCastSessionManager.isConnected())
                || (video != null && video.isLive)
                || !mExoPlayerController.getPlayWhenReady()
                || mExoPlayerController.getSpeed() >= HOLD_SPEED) {
            return false;
        }
        mExoPlayerController.beginHoldSpeed(HOLD_SPEED);
        Haptics.longPress(mPlayerView);
        hideControls();
        showTopPill(getString(R.string.mobile_player_hold_speed), R.drawable.ic_player_hold_speed);
        return true;
    }

    private void endHoldSpeed() {
        if (mExoPlayerController != null) {
            mExoPlayerController.endHoldSpeed();
        }
        showTopPill(null);
    }

    private void cancelHoldSpeed() {
        if (mExoPlayerController != null && mExoPlayerController.isHoldSpeedOn()) {
            endHoldSpeed();
        }
    }

    private static void fadePill(@Nullable View pill, boolean show) {
        if (pill == null) {
            return;
        }
        pill.animate().cancel();
        if (show) {
            if (pill.getVisibility() != View.VISIBLE) {
                pill.setAlpha(0f);
                pill.setScaleX(0.9f);
                pill.setScaleY(0.9f);
                pill.setVisibility(View.VISIBLE);
            }
            pill.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.FADE_IN_MS)
                    .setInterpolator(Motion.STANDARD_DECELERATE).start();
        } else if (pill.getVisibility() == View.VISIBLE) {
            pill.animate().alpha(0f).setDuration(Motion.FADE_OUT_MS).setInterpolator(Motion.STANDARD_ACCELERATE)
                    .withEndAction(() -> pill.setVisibility(View.GONE)).start();
        }
    }

    private void showChaptersSheet() {
        if (mChapterVideos.isEmpty()) {
            return;
        }
        cancelAutoHide();

        int maxHeightPx = 0;
        if (!isLandscape() && mVideoArea != null) {
            int[] location = new int[2];
            mVideoArea.getLocationInWindow(location);
            int belowVideo = getWindow().getDecorView().getHeight() - (location[1] + mVideoArea.getHeight());
            if (belowVideo >= dp(240)) {
                maxHeightPx = belowVideo;
            }
        }

        releaseImageRequests("chapters-sheet");
        long positionMs = mExoPlayerController != null ? mExoPlayerController.getPositionMs() : 0;
        BottomSheetDialog dialog = ChaptersSheet.create(this, mChapterVideos, chapterIndexAt(positionMs),
                maxHeightPx, chapter -> seekFromList(chapter.startTimeMs, CHAPTER_SEEK_PARAMETERS));
        dialog.setOnDismissListener(d -> {
            if (mChaptersSheet == d) {
                mChaptersSheet = null;
            }
            armAutoHide();
        });
        mChaptersSheet = dialog;
        showPlayerSheet(dialog);
    }

    private void seekFromList(long positionMs, @Nullable SeekParameters parameters) {
        if (mCastSessionManager != null && mCastSessionManager.isConnected()) {
            mCastSessionManager.seekTo(positionMs);
        } else if (mExoPlayerController != null) {
            SeekParameters previous = parameters != null && mPlayer != null ? mPlayer.getSeekParameters() : null;
            if (previous != null) {
                mPlayer.setSeekParameters(parameters);
            }
            mExoPlayerController.setPositionMs(positionMs);
            if (previous != null) {
                mPlayer.setSeekParameters(previous);
            }
            updateChapterButton(positionMs);
        }
        if (mControlsRoot != null && !mIsInPip) {
            showControlsInternal(true);
            armAutoHide();
        }
    }

    private static String qualityLabel(FormatItem item) {
        int height = item.getHeight();
        boolean highFps = item.getFrameRate() > 40;
        return height + "p" + (highFps ? "60" : "");
    }

    private static boolean isAutoFormat(FormatItem item) {
        if (item == null || item.isPreset()) {
            return true;
        }

        com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack track = item.getTrack();
        return track == null || track.format == null || track.format.id == null;
    }

    private void showQualitySheet() {
        List<FormatItem> videoFormats = getVideoFormats();
        if (videoFormats == null) {
            videoFormats = new ArrayList<>();
        }

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_mobile_quality, null);
        dialog.setContentView(content);

        LinearLayout qualityList = content.findViewById(R.id.quality_sheet_quality_list);

        PlayerData playerData = PlayerData.instance(this);
        FormatItem tempOverride = playerData.getTempVideoFormat();
        FormatItem persisted = tempOverride != null ? tempOverride : playerData.getFormat(FormatItem.TYPE_VIDEO);
        boolean autoActive = isAutoFormat(persisted);

        java.util.LinkedHashMap<String, FormatItem> rungs = new java.util.LinkedHashMap<>();
        String selectedRung = null;
        for (FormatItem item : videoFormats) {
            if (item.getHeight() <= 0) {
                continue;
            }
            String label = qualityLabel(item);
            if (!rungs.containsKey(label)) {
                rungs.put(label, item);
            }
            if (!autoActive && item.isSelected()) {
                selectedRung = label;
            }
        }

        addQualityRow(qualityList, getString(R.string.mobile_quality_auto), autoActive, () -> {
            playerData.setTempVideoFormat(null);
            FormatItem auto = playerData.getDefaultVideoFormat();
            setFormat(auto);
            playerData.setFormat(auto);
            dialog.dismiss();
        });
        for (java.util.Map.Entry<String, FormatItem> rung : rungs.entrySet()) {
            FormatItem item = rung.getValue();
            addQualityRow(qualityList, rung.getKey(), rung.getKey().equals(selectedRung), () -> {
                setFormat(item);
                if (isAutoFormat(playerData.getFormat(FormatItem.TYPE_VIDEO))) {
                    playerData.setTempVideoFormat(item);
                } else {
                    playerData.setFormat(item);
                }
                dialog.dismiss();
            });
        }

        showPlayerSheet(dialog);
    }

    private List<AudioTrackChoices.Choice> audioTrackChoices() {
        List<FormatItem> audioFormats = mExoPlayerController != null ? getAudioFormats() : null;
        return AudioTrackChoices.from(audioFormats, this::audioTrackLabel,
                getResources().getConfiguration().getLocales().get(0));
    }

    private String audioTrackLabel(@Nullable String language) {
        return TextUtils.isEmpty(language)
                ? getString(R.string.mobile_audio_default) : AudioTrackLabel.format(this, language);
    }

    private void showAudioTrackSheet() {
        List<AudioTrackChoices.Choice> choices = audioTrackChoices();
        if (choices.size() < 2) {
            return;
        }

        cancelAutoHide();

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_mobile_quality, null);
        dialog.setContentView(content);
        ((TextView) content.findViewById(R.id.quality_sheet_quality_title))
                .setText(R.string.mobile_player_audio_track);
        LinearLayout list = content.findViewById(R.id.quality_sheet_quality_list);

        PlayerData playerData = PlayerData.instance(this);
        Video openedFor = getVideo();
        String openedForId = openedFor != null ? openedFor.videoId : null;
        for (AudioTrackChoices.Choice choice : choices) {
            addQualityRow(list, choice.label, choice.selected, () -> {
                dialog.dismiss();
                Video now = getVideo();
                if (!TextUtils.equals(openedForId, now != null ? now.videoId : null)) {
                    return;
                }
                setFormat(choice.item);
                playerData.setFormat(choice.item);
                playerData.setTempAudioFormat(null);
                com.google.android.material.snackbar.Snackbar.make(
                                findViewById(android.R.id.content),
                                getString(R.string.mobile_audio_track_toast, choice.label),
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                        .show();
            });
        }

        dialog.setOnDismissListener(d -> armAutoHide());
        showPlayerSheet(dialog);
    }

    private void addQualityRow(LinearLayout parent, CharSequence label, boolean selected, Runnable onClick) {
        View row = getLayoutInflater().inflate(R.layout.item_mobile_quality_row, parent, false);
        TextView labelView = row.findViewById(R.id.quality_row_label);
        labelView.setText(label);
        if (selected) {
            row.findViewById(R.id.quality_row_check).setVisibility(View.VISIBLE);
        }
        row.setOnClickListener(v -> onClick.run());
        parent.addView(row);
    }

    private static String capitalize(String text) {
        return TextUtils.isEmpty(text) ? text
                : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static boolean isCaptionTrack(FormatItem item) {
        return item != null && !item.isDefault() && item.getLanguage() != null;
    }

    private static String captionLabel(FormatItem item) {
        String label = item.getLanguage() != null ? item.getLanguage()
                : item.getTitle() != null ? item.getTitle().toString() : "";
        if (SubtitleTrack.isAuto(label)) {
            label = label.substring(0, label.length() - 1);
        }
        return capitalize(label);
    }

    private boolean areCaptionsOn() {
        List<FormatItem> formats = getSubtitleFormats();
        if (formats == null) {
            return false;
        }
        for (FormatItem item : formats) {
            if (item.isSelected() && isCaptionTrack(item)) {
                return true;
            }
        }
        return false;
    }

    private void toggleCaptions() {
        cancelAutoHide();

        if (areCaptionsOn()) {
            applyCaptionFormat(FormatItem.SUBTITLE_NONE);
            armAutoHide();
            return;
        }

        FormatItem match = null;
        List<FormatItem> formats = getSubtitleFormats();
        if (formats != null) {
            for (FormatItem last : PlayerData.instance(this).getLastSubtitleFormats()) {
                int index = formats.indexOf(last);
                if (index != -1) {
                    match = formats.get(index);
                    break;
                }
            }
        }

        if (match != null) {
            applyCaptionFormat(match);
            armAutoHide();
        } else {
            showCaptionsSheet();
        }
    }

    private void applyCaptionFormat(FormatItem format) {
        boolean on = isCaptionTrack(format);

        setFormat(format);
        PlayerData playerData = PlayerData.instance(this);
        playerData.setFormat(format);

        if (playerData.isSubtitlesPerChannelEnabled()) {
            Video video = getVideo();
            String channelId = video != null ? video.channelId : null;
            if (on) {
                playerData.enableSubtitlesPerChannel(channelId);
            } else {
                playerData.disableSubtitlesPerChannel(channelId);
            }
        }

        setButtonState(R.id.lb_control_closed_captioning, on ? BUTTON_ON : BUTTON_OFF);

        com.google.android.material.snackbar.Snackbar.make(
                        findViewById(android.R.id.content),
                        on ? getString(R.string.mobile_captions_on_toast, captionLabel(format))
                                : getString(R.string.mobile_captions_off_toast),
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                .show();
    }

    private void moveLastUsedCaptionsFirst(List<FormatItem> tracks) {
        List<FormatItem> top = new ArrayList<>();
        for (FormatItem last : PlayerData.instance(this).getLastSubtitleFormats()) {
            if (last == null || last.getLanguage() == null) {
                continue;
            }
            int index = tracks.indexOf(last);
            if (index != -1) {
                top.add(tracks.remove(index));
            }
        }
        tracks.addAll(0, top);
    }

    private void showCaptionsSheet() {
        cancelAutoHide();

        List<FormatItem> tracks = new ArrayList<>();
        List<FormatItem> autoTracks = new ArrayList<>();
        List<FormatItem> formats = getSubtitleFormats();
        if (formats != null) {
            for (FormatItem item : formats) {
                if (!isCaptionTrack(item)) {
                    continue;
                }
                if (SubtitleTrack.isAuto(item.getLanguage())) {
                    autoTracks.add(item);
                } else {
                    tracks.add(item);
                }
            }
        }
        moveLastUsedCaptionsFirst(tracks);
        moveLastUsedCaptionsFirst(autoTracks);

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_mobile_captions, null);
        dialog.setContentView(content);

        LinearLayout trackList = content.findViewById(R.id.captions_sheet_track_list);
        addQualityRow(trackList, getString(R.string.mobile_captions_off), !areCaptionsOn(), () -> {
            applyCaptionFormat(FormatItem.SUBTITLE_NONE);
            dialog.dismiss();
        });
        tracks.addAll(autoTracks);
        for (FormatItem item : tracks) {
            addQualityRow(trackList, captionLabel(item), item.isSelected(), () -> {
                applyCaptionFormat(item);
                dialog.dismiss();
            });
        }

        if (tracks.isEmpty()) {
            content.findViewById(R.id.captions_sheet_empty).setVisibility(View.VISIBLE);
        }

        content.findViewById(R.id.captions_sheet_style).setOnClickListener(v -> {
            dialog.dismiss();
            SubtitleSettingsPresenter.instance(this).show();
        });

        dialog.setOnDismissListener(d -> armAutoHide());
        showPlayerSheet(dialog);
    }

    private String currentCaptionsLabel() {
        List<FormatItem> formats = getSubtitleFormats();
        if (formats != null) {
            for (FormatItem item : formats) {
                if (item.isSelected() && isCaptionTrack(item)) {
                    return captionLabel(item);
                }
            }
        }
        return getString(R.string.mobile_menu_off);
    }

    private static final float[] SPEED_PRESETS = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};

    private String speedLabel(float speed) {
        if (Helpers.floatEquals(speed, 1.0f)) {
            return getString(R.string.mobile_speed_normal);
        }
        String number = speed == Math.floor(speed)
                ? String.valueOf((int) speed) : String.valueOf(speed);
        return number + "x";
    }

    private void showSpeedSheet() {
        cancelAutoHide();

        float current = mExoPlayerController != null ? mExoPlayerController.getSpeed() : -1;
        if (current <= 0) {
            current = 1f;
        }

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_mobile_speed, null);
        dialog.setContentView(content);

        LinearLayout list = content.findViewById(R.id.speed_sheet_list);
        for (float speed : SPEED_PRESETS) {
            addQualityRow(list, speedLabel(speed), Helpers.floatEquals(speed, current), () -> {
                applySpeed(speed);
                dialog.dismiss();
            });
        }

        content.findViewById(R.id.speed_sheet_more).setOnClickListener(v -> {
            dialog.dismiss();
            openPlayerOption(R.id.action_video_speed, true);
        });

        dialog.setOnDismissListener(d -> armAutoHide());
        showPlayerSheet(dialog);
    }

    private void applySpeed(float speed) {
        setSpeed(speed);

        Video video = getVideo();
        if (video != null && PlayerData.instance(this).isSpeedPerVideoEnabled()) {
            VideoStateService stateService = VideoStateService.instance(this);
            State state = stateService.getByVideoId(video.videoId);
            if (state != null) {
                stateService.save(new State(state.video, state.positionMs, state.durationMs, speed));
            }
        }

        com.google.android.material.snackbar.Snackbar.make(
                        findViewById(android.R.id.content),
                        getString(R.string.mobile_speed_toast, speedLabel(speed)),
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                .show();
    }

    private void onCommentsEntryClicked() {
        if (mCommentsKey == null || mCommentsPanel == null) {
            return;
        }
        Utils.removeCallbacks(mPrefetchComments);
        mCommentsPanel.open();
    }

    private void prefetchComments() {
        if (mCommentsPanel == null || mIsInPip || mIsStopped || isFinishing()) {
            return;
        }
        mCommentsPanel.prefetch();
    }

    private final CommentsPanel.Host mCommentsHost = new CommentsPanel.Host() {
        @Override
        public void onCommentTimestamp(long positionMs) {
            seekFromList(positionMs, null);
        }

        @Override
        public void onCommentVideoLink(String videoId) {
            onRelatedClicked(Video.from(videoId));
        }

        @Override
        public void onCommentsPanelShown(boolean shown) {
            if (mWatchScroll != null) {
                mWatchScroll.setImportantForAccessibility(shown
                        ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
        }
    };

    private void onChatEntryClicked() {
        if (mChatReceiver == null && mLiveChatAction == null) {
            startLiveChatStream();
        }
        releaseImageRequests("chat-sheet");
        LiveChatSheet.show(getSupportFragmentManager());
    }

    private void startLiveChatStream() {
        if (mLiveChatKey == null) {
            return;
        }
        RxHelper.disposeActions(mLiveChatAction);
        LiveChatService chatService = YouTubeServiceManager.instance().getLiveChatService();
        mLiveChatAction = chatService.openLiveChatObserve(mLiveChatKey)
                .subscribe(
                        this::onChatItemReceived,
                        error -> { },
                        () -> { });
    }

    private void stopLiveChatStream() {
        RxHelper.disposeActions(mLiveChatAction);
        mLiveChatAction = null;
    }

    @Override
    public List<ChatItem> getChatSnapshot() {
        return new ArrayList<>(mChatItems);
    }

    @Override
    public void registerChatObserver(LiveChatSheet.Observer observer) {
        mChatObserver = observer;
    }

    @Override
    public void unregisterChatObserver(LiveChatSheet.Observer observer) {
        if (mChatObserver == observer) {
            mChatObserver = null;
        }
    }

    @Override
    public void onChatSheetDismissed() {
        stopLiveChatStream();
    }

    private void bindWatchVideo(Video item) {
        if (item == null || mWatchTitle == null) {
            return;
        }

        mWatchVideo = item;
        boolean isNewVideo = !Helpers.equals(item.videoId, mWatchVideoId);

        if (isNewVideo) {
            mWatchVideoId = item.videoId;
            mWatchMetadataGate.open(item.videoId);
            mRelatedRenderGate.reset();
            Utils.removeCallbacks(mReleaseWatchMetadata);
            Utils.postDelayed(mReleaseWatchMetadata, WATCH_METADATA_TIMEOUT_MS);
            clearSuggestions();
            resetWatchHeader();
            Utils.removeCallbacks(mPrefetchComments);
            if (mCommentsPanel != null) {
                mCommentsPanel.onVideoChanged(item.videoId);
            }
            if (mWatchScroll != null) {
                mWatchScroll.scrollTo(0, 0);
            }
        }

        if (isNewVideo || !TextUtils.isEmpty(item.getTitleFull())) {
            mWatchTitle.setText(item.getTitleFull());
        }
        updateDownloadPill();
        if (isNewVideo || !TextUtils.isEmpty(item.getAuthor())) {
            mWatchChannelName.setText(item.getAuthor());
        }

        CharSequence second = item.getSecondTitleFull();
        if (mWatchMeta.length() == 0 && !TextUtils.isEmpty(second)) {
            String line = second.toString();
            String author = item.getAuthor();
            if (!TextUtils.isEmpty(author) && line.startsWith(author)) {
                String stripped = line.substring(author.length()).replaceFirst("^\\s*[•·]\\s*", "");
                if (!stripped.isEmpty()) {
                    line = stripped;
                }
            }
            mWatchMeta.setText(line.replaceFirst("(?i)(published|premiered|streamed live) on ", ""));
        }

        if (!TextUtils.isEmpty(item.likeCount)) {
            mWatchLikeCount.setText(item.likeCount);
        }
        if (!showsDislikeCount()) {
            mWatchDislikeCount.setVisibility(View.GONE);
        } else if (!TextUtils.isEmpty(item.dislikeCount)) {
            mWatchDislikeCount.setText(item.dislikeCount);
        }
        if (!TextUtils.isEmpty(item.subscriberCount)) {
            mWatchSubs.setText(item.subscriberCount);
            mWatchSubs.setVisibility(View.VISIBLE);
        }
        if (!TextUtils.isEmpty(item.description)) {
            mWatchDescription.setText(item.description);
        }
    }

    private void resetWatchHeader() {
        mDescriptionExpanded = false;
        mWatchDescription.setVisibility(View.GONE);
        mWatchDescription.setText(null);
        mWatchMeta.setText(null);
        mWatchMeta.setMaxLines(1);
        mWatchLikeCount.setText(R.string.mobile_watch_count_placeholder);
        mWatchDislikeCount.setText(R.string.mobile_watch_count_placeholder);
        mWatchSubs.setText(null);
        mWatchSubs.setVisibility(View.INVISIBLE);
        mWatchAvatar.setImageResource(R.drawable.ic_watch_channel_placeholder);
        mWatchLikeCount.setVisibility(View.VISIBLE);
        mWatchDislikeCount.setVisibility(showsDislikeCount() ? View.VISIBLE : View.GONE);

        mCommentsKey = null;
        mLiveChatKey = null;
        if (mWatchCommentsEntry != null) {
            mWatchCommentsEntry.setVisibility(View.VISIBLE);
        }
        if (mWatchCommentsCount != null) {
            mWatchCommentsCount.setText(null);
        }
        if (mWatchChatEntry != null) {
            mWatchChatEntry.setVisibility(View.GONE);
        }
        mChatItems.clear();
        RxHelper.disposeActions(mLiveChatAction);
        mLiveChatAction = null;
        mChatReceiver = null;
    }

    @Override
    public void onWatchMetadata(MediaItemMetadata metadata) {
        if (metadata == null) {
            return;
        }

        runOnUiThread(() -> {
            bindWatchMetadata(mWatchMetadataGate.offer(metadata));
        });
    }

    private void releaseWatchMetadata() {
        Utils.removeCallbacks(mReleaseWatchMetadata);
        bindWatchMetadata(mWatchMetadataGate.release());
        final String videoId = mWatchVideoId;
        Utils.removeCallbacks(mReleaseRelatedRender);
        mReleaseRelatedRender = () -> {
            if (Helpers.equals(videoId, mWatchVideoId)) {
                mRelatedRenderGate.release();
            }
        };
        Utils.postDelayed(mReleaseRelatedRender, STILL_REVEAL_MS + 20);
    }

    @Nullable
    private Runnable mReleaseRelatedRender;

    private void bindWatchMetadata(MediaItemMetadata metadata) {
        if (metadata == null) {
            return;
        }

        {
            if (!TextUtils.isEmpty(metadata.getTitle())) {
                setWatchTextFaded(mWatchTitle, metadata.getTitle());
                if (mTitleView != null && TextUtils.isEmpty(mTitleView.getText())) {
                    mTitleView.setText(metadata.getTitle());
                }
            }

            String views = metadata.getViewCount();
            String relativeDate = metadata.getRelativePublishedDate();
            String date = !TextUtils.isEmpty(relativeDate) ? relativeDate : metadata.getPublishedDate();
            if (date != null) {
                date = date.replaceFirst("(?i)^(published|premiered|streamed live) on ", "");
                String unlabeled = date.replaceFirst("^[^:]{1,40}:\\s*", "");
                if (!unlabeled.isEmpty()) {
                    date = unlabeled;
                }
            }
            String meta;
            if (!TextUtils.isEmpty(views) && !TextUtils.isEmpty(date)) {
                meta = views + com.newtube.mobile.ui.common.MetaSeparator.DOT + date;
            } else if (!TextUtils.isEmpty(views)) {
                meta = views;
            } else {
                meta = date;
            }
            if (!TextUtils.isEmpty(meta)
                    && (!TextUtils.isEmpty(relativeDate) || mWatchMeta.length() == 0)) {
                setWatchTextFaded(mWatchMeta, meta);
            }

            String description = metadata.getDescription();
            if (!TextUtils.isEmpty(description)) {
                mWatchDescription.setText(description);
            }

            if (!TextUtils.isEmpty(metadata.getAuthor())) {
                setWatchTextFaded(mWatchChannelName, metadata.getAuthor());
            }

            if (!TextUtils.isEmpty(metadata.getSubscriberCount())) {
                mWatchSubs.setVisibility(View.VISIBLE);
                setWatchTextFaded(mWatchSubs, metadata.getSubscriberCount());
            } else if (TextUtils.isEmpty(mWatchSubs.getText())) {
                mWatchSubs.setVisibility(View.GONE);
            }

            setChannelIcon(metadata.getAuthorImageUrl());

            if (isCountUnset(mWatchLikeCount) && !TextUtils.isEmpty(metadata.getLikeCount())) {
                setWatchTextFaded(mWatchLikeCount, metadata.getLikeCount());
            }
            if (showsDislikeCount() && isCountUnset(mWatchDislikeCount)
                    && !TextUtils.isEmpty(metadata.getDislikeCount())) {
                mWatchDislikeCount.setText(metadata.getDislikeCount());
            }

            setButtonState(R.id.action_thumbs_up,
                    metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_LIKE ? BUTTON_ON : BUTTON_OFF);
            setButtonState(R.id.action_thumbs_down,
                    metadata.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_DISLIKE ? BUTTON_ON : BUTTON_OFF);
            setButtonState(R.id.action_subscribe, metadata.isSubscribed() ? BUTTON_ON : BUTTON_OFF);

            mCommentsKey = metadata.getCommentsKey();
            mLiveChatKey = metadata.getLiveChatKey();
            if (mWatchCommentsEntry != null) {
                mWatchCommentsEntry.setVisibility(mCommentsKey != null ? View.VISIBLE : View.GONE);
            }
            String commentsCount = mCommentsKey != null ? metadata.getCommentsCount() : null;
            if (mWatchCommentsCount != null && !TextUtils.isEmpty(commentsCount)) {
                setWatchTextFaded(mWatchCommentsCount, commentsCount);
            }
            if (mCommentsPanel != null) {
                mCommentsPanel.setSource(mWatchVideoId, mCommentsKey, metadata.getNewestCommentsKey(), commentsCount);
                Utils.removeCallbacks(mPrefetchComments);
                if (mCommentsKey != null) {
                    Utils.postDelayed(mPrefetchComments, COMMENTS_PREFETCH_DELAY_MS);
                }
            }
            if (mWatchChatEntry != null && mLiveChatKey != null) {
                mWatchChatEntry.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setWatchTextFaded(@Nullable TextView view, CharSequence text) {
        if (view == null || TextUtils.equals(view.getText(), text)) {
            return;
        }
        view.setText(text);
        if (!view.isShown()) {
            return;
        }
        view.animate().cancel();
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(Motion.FADE_IN_MS).setInterpolator(Motion.STANDARD).start();
    }

    private boolean showsDislikeCount() {
        return PlayerTweaksData.instance(this).isReturnYouTubeDislikeEnabled();
    }

    private boolean isCountUnset(TextView view) {
        CharSequence text = view.getText();
        return TextUtils.isEmpty(text) || getString(R.string.mobile_watch_count_placeholder).contentEquals(text);
    }

    private void toggleDescription() {
        if (TextUtils.isEmpty(mWatchDescription.getText())) {
            return;
        }

        mDescriptionExpanded = !mDescriptionExpanded;
        ViewGroup content = (ViewGroup) mWatchDescription.getParent();
        if (content != null) {
            androidx.transition.TransitionManager.beginDelayedTransition(content,
                    new androidx.transition.AutoTransition().setDuration(180));
        }
        mWatchDescription.setVisibility(mDescriptionExpanded ? View.VISIBLE : View.GONE);
        mWatchMeta.setMaxLines(mDescriptionExpanded ? Integer.MAX_VALUE : 1);
        mWatchExpand.animate().rotation(mDescriptionExpanded ? 180f : 0f).setDuration(180).start();
    }

    private void onActionButtonClicked(int actionId) {
        if (mPresenter == null) {
            return;
        }

        int currentState = getButtonState(actionId);
        if (currentState == BUTTON_DISABLED) {
            currentState = BUTTON_OFF;
        }

        mPresenter.onButtonClicked(actionId, currentState);
    }

    private void onRateTapped(int actionId) {
        int stateBefore = getButtonState(actionId);
        int ratingBefore = currentRating();
        onActionButtonClicked(actionId);
        int stateAfter = getButtonState(actionId);
        if (stateAfter == stateBefore) {
            WatchActionFeedback.rateNotReady(this);
            return;
        }
        int ratingAfter = currentRating();
        boolean like = actionId == R.id.action_thumbs_up;
        Haptics.click(like ? mWatchLike : mWatchDislike);
        Motion.pop(like ? mWatchLikeIcon : mWatchDislikeIcon);
        WatchActionFeedback.confirmRating(this, like, stateAfter == BUTTON_ON,
                undoRating(ratingBefore, ratingAfter));
    }

    private int currentRating() {
        return RatingUndo.rating(getButtonState(R.id.action_thumbs_up) == BUTTON_ON,
                getButtonState(R.id.action_thumbs_down) == BUTTON_ON);
    }

    private Runnable undoRating(int ratingBefore, int ratingAfter) {
        String videoId = currentVideoId();
        return () -> {
            if (videoId == null || !videoId.equals(currentVideoId()) || currentRating() != ratingAfter) {
                return;
            }
            int tap = RatingUndo.tapFor(ratingAfter, ratingBefore, R.id.action_thumbs_up, R.id.action_thumbs_down);
            if (tap != 0) {
                onActionButtonClicked(tap);
            }
        };
    }

    private Runnable undoOnThisVideo(int actionId, int stateAfter) {
        String videoId = currentVideoId();
        return () -> {
            if (videoId != null && videoId.equals(currentVideoId()) && getButtonState(actionId) == stateAfter) {
                onActionButtonClicked(actionId);
            }
        };
    }

    @Nullable
    private String currentVideoId() {
        Video video = getVideo();
        return video != null ? video.videoId : null;
    }

    @Override
    public void onRatingNotSaved() {
        runOnUiThread(() -> WatchActionFeedback.ratingNotSaved(this));
    }

    private void onSubscribeTapped() {
        int before = getButtonState(R.id.action_subscribe);
        onActionButtonClicked(R.id.action_subscribe);
        int after = getButtonState(R.id.action_subscribe);
        if (after != before) {
            Haptics.click(mWatchSubscribe);
            Video video = getVideo();
            WatchActionFeedback.confirmSubscription(this, after == BUTTON_ON,
                    video != null ? video.getAuthor() : null,
                    undoOnThisVideo(R.id.action_subscribe, after));
        }
    }

    private void updateButtonVisual(int buttonId, int buttonState) {
        boolean on = buttonState == BUTTON_ON;

        if (buttonId == R.id.action_thumbs_up && mWatchLikeIcon != null) {
            mWatchLikeIcon.setImageResource(on ? R.drawable.ic_watch_thumb_up : R.drawable.ic_watch_thumb_up_outline);
            if (mWatchLike != null) {
                mWatchLike.setSelected(on);
            }
        } else if (buttonId == R.id.action_thumbs_down && mWatchDislikeIcon != null) {
            mWatchDislikeIcon.setImageResource(on ? R.drawable.ic_watch_thumb_down : R.drawable.ic_watch_thumb_down_outline);
            if (mWatchDislike != null) {
                mWatchDislike.setSelected(on);
            }
        } else if (buttonId == R.id.lb_control_closed_captioning && mSubtitlesButton != null) {
            mSubtitlesButton.setImageResource(on ? R.drawable.ic_player_cc : R.drawable.ic_player_cc_off);
        } else if (buttonId == R.id.action_playlist_add && mWatchSaveIcon != null) {
            mWatchSaveIcon.setImageResource(on ? R.drawable.ic_mobile_check : R.drawable.ic_player_playlist_add);
            if (mWatchSaveLabel != null) {
                mWatchSaveLabel.setText(on ? R.string.mobile_watch_saved : R.string.mobile_watch_save);
            }
        } else if (buttonId == R.id.action_subscribe && mWatchSubscribe != null) {
            mWatchSubscribe.setText(on ? R.string.mobile_watch_subscribed : R.string.mobile_watch_subscribe);
            mWatchSubscribe.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getColorInt(on ? R.color.mobile_color_subscribed_button : R.color.mobile_color_inverse_surface)));
            mWatchSubscribe.setTextColor(getColorInt(on
                    ? R.color.mobile_color_on_surface : R.color.mobile_color_on_inverse_surface));
        }
    }

    private int getColorInt(int colorRes) {
        return androidx.core.content.ContextCompat.getColor(this, colorRes);
    }

    private final DownloadRegistry.Listener mDownloadsListener = this::updateDownloadPill;

    @Nullable
    private DownloadItem currentDownload() {
        Video video = getVideo();
        if (video == null || video.videoId == null) {
            return null;
        }
        DownloadRegistry registry = DownloadRegistry.instance(this);
        DownloadItem active = registry.findActive(video.videoId);
        return active != null ? active : registry.findDone(video.videoId, DownloadOption.KIND_VIDEO);
    }

    @Nullable
    private String downloadStateLabel() {
        DownloadItem item = currentDownload();
        if (item == null) {
            return null;
        }
        if (item.isDone()) {
            return getString(R.string.mobile_download_pill_downloaded);
        }
        if (item.state == DownloadItem.STATE_DOWNLOADING && item.progressPercent() >= 0) {
            return item.progressPercent() + "%";
        }
        if (item.isFailed()) {
            return getString(R.string.mobile_download_retry);
        }
        return getString(R.string.mobile_download_badge_queued);
    }

    private void onDownloadTapped() {
        DownloadItem item = currentDownload();
        if (item != null) {
            DownloadMenu.show(this, item);
        } else {
            VideoDownloads.request(this, getVideo());
        }
    }

    private void updateDownloadPill() {
        if (mWatchDownload == null) {
            return;
        }
        runOnUiThread(() -> {
            Video video = getVideo();
            DownloadItem item = currentDownload();
            boolean offered = VideoDownloads.canDownload(video) || (video != null && video.isLocal()) || item != null;
            mWatchDownload.setVisibility(offered ? View.VISIBLE : View.GONE);
            if (!offered) {
                return;
            }
            if (item != null && item.isDone()) {
                mWatchDownloadIcon.setImageResource(R.drawable.ic_watch_downloaded);
                mWatchDownloadLabel.setText(R.string.mobile_download_pill_downloaded);
            } else if (item != null && item.state == DownloadItem.STATE_DOWNLOADING && item.progressPercent() >= 0) {
                mWatchDownloadIcon.setImageResource(R.drawable.ic_watch_download);
                mWatchDownloadLabel.setText(item.progressPercent() + "%");
            } else if (item != null && item.isActive()) {
                mWatchDownloadIcon.setImageResource(R.drawable.ic_watch_download);
                mWatchDownloadLabel.setText(R.string.mobile_download_badge_queued);
            } else {
                mWatchDownloadIcon.setImageResource(R.drawable.ic_watch_download);
                mWatchDownloadLabel.setText(R.string.dialog_download);
            }
        });
    }

    private void shareCurrentVideo() {
        Video video = getVideo();
        if (video == null || TextUtils.isEmpty(video.videoId)) {
            return;
        }

        String url = "https://youtu.be/" + video.videoId;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.mobile_watch_share_subject));
        intent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(intent, getString(R.string.mobile_watch_share)));
    }

    private String mTouchPrefetchVideoId;
    private long mTouchPrefetchAtMs;

    private void onRelatedPressed(Video video) {
        Video current = getVideo();
        if (video == null || video.videoId == null || video.isLocal() || isFinishing()
                || (current != null && video.videoId.equals(current.videoId))) {
            return;
        }
        if (MediaServiceManager.instance().speculativePrefetchFormatInfo(video)) {
            mTouchPrefetchVideoId = video.videoId;
            mTouchPrefetchAtMs = android.os.SystemClock.elapsedRealtime();
            NetPath.log(NetPath.context() + " touch-prefetch fire video=" + video.videoId);
        }
    }

    private void onRelatedClicked(Video video) {
        onRelatedClicked(video, null);
    }

    private void onRelatedClicked(Video video, @Nullable ImageView thumbnail) {
        seedTappedStill(video, thumbnail);
        if (video != null && video.videoId != null && video.hasVideo() && !video.isLocal()) {
            NetPath.logTap(video.videoId);
        }
        if (video != null && video.videoId != null && video.videoId.equals(mTouchPrefetchVideoId)) {
            NetPath.log("touch-prefetch used video=" + video.videoId + " leadMs="
                    + (android.os.SystemClock.elapsedRealtime() - mTouchPrefetchAtMs));
            mTouchPrefetchVideoId = null;
        }
        if (mPresenter != null && video != null) {
            mPresenter.onSuggestionItemClicked(video);
        }
    }

    private void openCurrentChannel() {
        Video video = getVideo();
        if (video == null || !ChannelPresenter.canOpenChannel(video)) {
            return;
        }
        MiniPlayerBridge.prepareNavigation(this);
        MediaServiceManager.chooseChannelPresenter(this, video);
    }

    private void maybePageSuggestions() {
        if (!mRelatedRenderGate.isReleased()) {
            return;
        }
        if (mPresenter == null || mRelatedVideos.isEmpty()) {
            return;
        }

        if (mRelatedWindow < mRelatedVideos.size()) {
            mRelatedWindow += RELATED_WINDOW_STEP;
            submitRelatedWindow();
            return;
        }

        Video last = mRelatedVideos.get(mRelatedVideos.size() - 1);
        if (last == mLastPagedVideo) {
            return;
        }

        mLastPagedVideo = last;
        mPresenter.onScrollEnd(last);
    }

    private void rebuildRelatedList() {
        mRelatedVideos.clear();
        mQueueVideos.clear();

        Video current = getVideo();
        String currentId = current != null ? current.videoId : null;
        Integer queueId = findQueueGroupId(current);

        for (Map.Entry<Integer, List<Video>> entry : mSuggestionVideos.entrySet()) {
            boolean isQueueRow = queueId != null && queueId.equals(entry.getKey());
            List<Video> vids = entry.getValue();
            if (vids == null) {
                continue;
            }
            for (Video v : vids) {
                if (v == null || com.newtube.mobile.ui.common.ShortsFilter.isShort(v)) {
                    continue;
                }
                if (isQueueRow) {
                    mQueueVideos.add(v);
                } else if (currentId == null || !currentId.equals(v.videoId)) {
                    mRelatedVideos.add(v);
                }
            }
        }

        NetPath.log("related-list rows=" + mSuggestionVideos.size() + " size=" + mRelatedVideos.size()
                + " queue=" + mQueueVideos.size());

        mRelatedRenderGate.renderWhenReady(this::renderRelatedList);
    }

    private void renderRelatedList() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Video current = getVideo();
        String currentId = current != null ? current.videoId : null;
        bindQueueCard(current, currentId, findQueueGroupId(current));

        submitRelatedWindow(() -> {
            if (!mRelatedVideos.isEmpty() || !mQueueVideos.isEmpty()) {
                hideRelatedSkeleton();
            }
        });
    }

    private static final int RELATED_WINDOW_INITIAL = 12;
    private static final int RELATED_WINDOW_STEP = 12;
    private int mRelatedWindow = RELATED_WINDOW_INITIAL;

    private void submitRelatedWindow() {
        submitRelatedWindow(null);
    }

    private void submitRelatedWindow(Runnable onCommitted) {
        if (mRelatedAdapter != null) {
            int end = Math.min(mRelatedWindow, mRelatedVideos.size());
            mRelatedAdapter.submitList(new ArrayList<>(mRelatedVideos.subList(0, end)), onCommitted);
        } else if (onCommitted != null) {
            onCommitted.run();
        }
        if (mWatchRelatedLabel != null && !mRelatedVideos.isEmpty()) {
            mWatchRelatedLabel.setText(R.string.mobile_watch_related);
            mWatchRelatedLabel.setVisibility(View.VISIBLE);
        }
    }

    private Integer findQueueGroupId(Video current) {
        String currentId = current != null ? current.videoId : null;

        if (currentId == null || !isChosenPlaylist(current)) {
            return null;
        }

        for (Map.Entry<Integer, List<Video>> entry : mSuggestionVideos.entrySet()) {
            List<Video> vids = entry.getValue();
            if (vids == null) {
                continue;
            }
            for (Video v : vids) {
                if (v != null && currentId.equals(v.videoId)) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }

    private boolean isChosenPlaylist(Video video) {
        String playlistId = video != null ? video.getPlaylistId() : null;
        return playlistId != null && !playlistId.startsWith("RD");
    }

    private void bindQueueCard(Video current, String currentId, Integer queueId) {
        if (mQueueCard == null) {
            return;
        }

        if (mQueueVideos.isEmpty()) {
            mQueueCard.setVisibility(View.GONE);
            if (mQueueAdapter != null) {
                mQueueAdapter.submitList(new ArrayList<>());
            }
            return;
        }

        VideoGroup sectionGroup = current != null ? current.getGroup() : null;
        boolean isSectionQueue = sectionGroup != null && queueId != null
                && queueId.equals(sectionGroup.getId());
        PlaylistInfo currentInfo = current != null ? current.playlistInfo : null;
        boolean namesSameList = currentInfo != null && current != null
                && Helpers.equals(currentInfo.getPlaylistId(), current.getPlaylistId());
        PlaylistInfo info = !isSectionQueue || namesSameList ? currentInfo : null;

        VideoGroup queueGroup = queueId != null ? mSuggestionGroups.get(queueId) : null;
        String name = queueGroup != null ? queueGroup.getTitle() : null;
        if (TextUtils.isEmpty(name) && info != null) {
            name = info.getTitle();
        }
        if (TextUtils.isEmpty(name)) {
            name = getString(R.string.mobile_watch_queue_fallback);
        }
        mQueueTitle.setText(getString(R.string.mobile_watch_queue_from, name));

        int size = info != null ? info.getSize() : 0;
        int index = info != null ? info.getCurrentIndex() + 1 : 0;
        if (size <= 0 || index <= 0 || index > size) {
            size = mQueueVideos.size();
            index = indexOfQueueVideo(currentId) + 1;
        }

        if (index > 0 && size > 0) {
            mQueueSubtitle.setText(getString(R.string.mobile_watch_queue_position, index, size));
            mQueueSubtitle.setVisibility(View.VISIBLE);
        } else {
            mQueueSubtitle.setVisibility(View.GONE);
        }

        mQueueAdapter.setCurrentVideoId(currentId);
        mQueueAdapter.submitList(new ArrayList<>(mQueueVideos));
        mQueueCard.setVisibility(View.VISIBLE);
        applyQueueExpanded();
    }

    private int indexOfQueueVideo(String videoId) {
        if (videoId == null) {
            return -1;
        }

        for (int i = 0; i < mQueueVideos.size(); i++) {
            Video v = mQueueVideos.get(i);
            if (v != null && videoId.equals(v.videoId)) {
                return i;
            }
        }

        return -1;
    }

    private void toggleQueueExpanded() {
        mQueueExpanded = !mQueueExpanded;
        float chevronFrom = mQueueChevron != null ? mQueueChevron.getRotation() : 0f;
        applyQueueExpanded();
        if (mQueueChevron != null) {
            mQueueChevron.setRotation(chevronFrom);
            mQueueChevron.animate().rotation(mQueueExpanded ? 180f : 0f).setDuration(180)
                    .setInterpolator(Motion.STANDARD).start();
        }

        if (mQueueExpanded) {
            int index = indexOfQueueVideo(getVideo() != null ? getVideo().videoId : null);
            if (index > 0) {
                mQueueList.scrollToPosition(index);
            }
        }
    }

    private void applyQueueExpanded() {
        if (mQueueList == null || mQueueChevron == null) {
            return;
        }

        mQueueList.setVisibility(mQueueExpanded ? View.VISIBLE : View.GONE);
        mQueueChevron.animate().cancel();
        mQueueChevron.setRotation(mQueueExpanded ? 180f : 0f);
    }

    @Override
    public void prebuildNextSource(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.prebuildNextSource(formatInfo);
    }

    @Override
    public void openSabr(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.openSabr(formatInfo);
    }

    @Override
    public boolean allowsAutomaticSourceRecovery() {
        return mExoPlayerController == null || mExoPlayerController.allowsAutomaticSourceRecovery();
    }

    @Override
    public long getMediaReadinessHoldMs() {
        return mExoPlayerController != null ? mExoPlayerController.getMediaReadinessHoldMs() : 0;
    }

    @Override
    public void openDash(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.openDash(formatInfo);
    }

    @Override
    public void openDash(InputStream dashManifest) {
        mExoPlayerController.openDash(dashManifest);
    }

    @Override
    public void openDashUrl(String dashManifestUrl) {
        mExoPlayerController.openDashUrl(dashManifestUrl);
    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {
        mExoPlayerController.openHlsUrl(hlsPlaylistUrl);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        mExoPlayerController.openUrlList(urlList);
    }

    @Override
    public void openHlsVod(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.openHlsVod(formatInfo);
    }

    @Override
    public void openProgressive(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.openProgressive(formatInfo);
    }

    @Override
    public void openMerged(MediaItemFormatInfo formatInfo, String hlsPlaylistUrl) {
        mExoPlayerController.openMerged(formatInfo, hlsPlaylistUrl);
    }

    @Override
    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {
        mExoPlayerController.openMerged(dashManifest, hlsPlaylistUrl);
    }

    @Override
    public long getPositionMs() {
        return mExoPlayerController.getPositionMs();
    }

    @Override
    public long getForbiddenMediaStartMs() {
        return mExoPlayerController != null ? mExoPlayerController.getMediaRequests().forbiddenStartMs() : -1;
    }

    @Override
    public long getLowestServedMediaStartMs() {
        return mExoPlayerController != null ? mExoPlayerController.getMediaRequests().lowestServedStartMs() : -1;
    }

    @Override
    public long getHighestServedMediaStartMs() {
        return mExoPlayerController != null ? mExoPlayerController.getMediaRequests().highestServedStartMs() : -1;
    }

    @Override
    public void setPositionMs(long positionMs) {
        mExoPlayerController.setPositionMs(positionMs);
    }

    @Override
    public void setResumePositionMs(long positionMs) {
        mExoPlayerController.seekToResumePosition(positionMs);
    }

    @Override
    public long getHistoryPositionMs() {
        return mExoPlayerController.getHistoryPositionMs();
    }

    @Override
    public long getDurationMs() {
        long durationMs = mExoPlayerController.getDurationMs();

        long liveDurationMs = getVideo() != null ? getVideo().getLiveDurationMs() : 0;

        if ((durationMs <= 0 || durationMs > Video.MAX_LIVE_DURATION_MS) && liveDurationMs != 0) {
            durationMs = liveDurationMs;
        }

        return durationMs;
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        mExoPlayerController.setPlayWhenReady(play);
    }

    @Override
    public boolean getPlayWhenReady() {
        return mExoPlayerController.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return mExoPlayerController.isPlaying();
    }

    @Override
    public boolean isLoading() {
        return mExoPlayerController.isLoading();
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return mExoPlayerController.getVideoFormats();
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return mExoPlayerController.getAudioFormats();
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return mExoPlayerController.getSubtitleFormats();
    }

    @Override
    public void setFormat(FormatItem option) {
        mExoPlayerController.selectFormat(option);
    }

    @Override
    public FormatItem getVideoFormat() {
        return mExoPlayerController.getVideoFormat();
    }

    @Override
    public FormatItem getAudioFormat() {
        return mExoPlayerController.getAudioFormat();
    }

    @Override
    public FormatItem getSubtitleFormat() {
        return mExoPlayerController.getSubtitleFormat();
    }

    @Override
    public boolean isEngineInitialized() {
        return mPlayer != null;
    }

    @Override
    public void restartEngine() {
        destroyPlayerObjects();
        createPlayerObjects();
    }

    @Override
    public void reloadPlayback() {
        if (mPlayer != null) {
            mPresenter.onEngineReleased();
            mPresenter.onEngineInitialized();
        }
    }

    @Override
    public void blockEngine(boolean block) {
        mIsEngineBlocked = block;
    }

    @Override
    public boolean isEngineBlocked() {
        return mIsEngineBlocked;
    }

    @Override
    public boolean isInPIPMode() {
        return mIsInPip;
    }

    @Override
    public boolean containsMedia() {
        return mExoPlayerController != null && mExoPlayerController.containsMedia();
    }

    @Override
    public void setSpeed(float speed) {
        mExoPlayerController.setSpeed(speed);
    }

    @Override
    public float getSpeed() {
        return mExoPlayerController.getSpeed();
    }

    @Override
    public float getEffectiveSpeed() {
        return mExoPlayerController != null ? mExoPlayerController.getEffectiveSpeed() : getSpeed();
    }

    @Override
    public void setPitch(float pitch) {
        mExoPlayerController.setPitch(pitch);
    }

    @Override
    public float getPitch() {
        return mExoPlayerController.getPitch();
    }

    @Override
    public void setVolume(float volume) {
        mExoPlayerController.setVolume(volume);
    }

    @Override
    public float getVolume() {
        return mExoPlayerController.getVolume();
    }

    @Override
    public void setResizeMode(int mode) {
        if (mPlayerView != null) {
            mPlayerView.setResizeMode(mode);
        }
        if (mWatchRoot != null) {
            updateInlineViewport(mWatchRoot.getWidth());
        }
        applyControlsInsets();
    }

    @Override
    public int getResizeMode() {
        return mPlayerView != null ? mPlayerView.getResizeMode() : RESIZE_MODE_DEFAULT;
    }

    @Override
    public void setZoomPercents(int percents) {
    }

    @Override
    public void setAspectRatio(float ratio) {
    }

    @Override
    public void setRotationAngle(int angle) {
    }

    @Override
    public void setVideoFlipEnabled(boolean enabled) {
    }

    @Override
    public void setVideoGravity(int gravity) {
    }

    @Override
    public void setVideo(Video item) {
        if (item != null && item.videoId != null) {
            SessionWarmup.onPlaybackRequested();
            if (!Helpers.equals(item.videoId, mWatchVideoId)) {
                cancelClose();
                MiniPlayerBridge.cancelClosing();
            }
        }
        if (mExoPlayerController != null) {
            mExoPlayerController.setVideo(item);
        }

        boolean sameVideo = item != null && Helpers.equals(item.videoId, mWatchVideoId);
        if (!sameVideo) {
            showPlaybackNotice(null);
            cancelHoldSpeed();
            if (mTimeBar != null) {
                mTimeBar.cancelScrub();
            }
        }
        if (!sameVideo || (item != null && !TextUtils.isEmpty(item.getTitleFull()))) {
            setTitle(item != null ? item.getTitleFull() : null);
        }
        bindWatchVideo(item);
        if (mStoryboardManager != null) {
            mStoryboardManager.init(item);
        }

        runOnUiThread(() -> maybeShowLoadingStill(item));

        if (item != null && mRelatedVideos.isEmpty()) {
            runOnUiThread(this::showRelatedSkeleton);
        }

        if (item != null && item.videoId != null) {
            final String videoId = item.videoId;
            runOnUiThread(() -> maybeRouteVideoToCast(videoId));
        }
    }

    @Override
    public Video getVideo() {
        return mExoPlayerController != null ? mExoPlayerController.getVideo() : null;
    }

    @Override
    public void showBackground(String url) {
    }

    @Override
    public void showBackgroundColor(int colorResId) {
    }

    @Override
    public void resetPlayerState() {
        if (mExoPlayerController != null) {
            mExoPlayerController.resetPlayerState();
        }
    }

    @Override
    public boolean isEmbed() {
        return false;
    }

    private static final class NetPathLoadListener implements androidx.media3.exoplayer.analytics.AnalyticsListener {
        private final Context mContext;

        NetPathLoadListener(Context context) {
            mContext = context.getApplicationContext();
        }

        @Override
        public void onLoadStarted(EventTime eventTime, androidx.media3.exoplayer.source.LoadEventInfo loadEventInfo,
                androidx.media3.exoplayer.source.MediaLoadData mediaLoadData, int retryCount) {
            log("load[S]", loadEventInfo, mediaLoadData, null);
        }

        @Override
        public void onLoadCompleted(EventTime eventTime, androidx.media3.exoplayer.source.LoadEventInfo loadEventInfo,
                androidx.media3.exoplayer.source.MediaLoadData mediaLoadData) {
            log("load[C]", loadEventInfo, mediaLoadData, null);
        }

        @Override
        public void onLoadCanceled(EventTime eventTime, androidx.media3.exoplayer.source.LoadEventInfo loadEventInfo,
                androidx.media3.exoplayer.source.MediaLoadData mediaLoadData) {
            log("load[X]", loadEventInfo, mediaLoadData, null);
        }

        @Override
        public void onLoadError(EventTime eventTime, androidx.media3.exoplayer.source.LoadEventInfo loadEventInfo,
                androidx.media3.exoplayer.source.MediaLoadData mediaLoadData, java.io.IOException error,
                boolean wasCanceled) {
            log("load[E]", loadEventInfo, mediaLoadData, error);
        }

        @Override
        public void onPositionDiscontinuity(EventTime eventTime,
                androidx.media3.common.Player.PositionInfo oldPosition,
                androidx.media3.common.Player.PositionInfo newPosition, int reason) {
            android.util.Log.d(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG,
                    com.liskovsoft.smartyoutubetv2.common.misc.NetPath.context()
                            + " position-discontinuity reason=" + discontinuityReason(reason)
                            + " from=" + oldPosition.positionMs + " to=" + newPosition.positionMs
                            + " delta=" + (newPosition.positionMs - oldPosition.positionMs));
        }

        @Override
        public void onDownstreamFormatChanged(EventTime eventTime,
                androidx.media3.exoplayer.source.MediaLoadData mediaLoadData) {
            androidx.media3.common.Format format = mediaLoadData.trackFormat;
            if (format == null) {
                return;
            }
            android.util.Log.d(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG,
                    com.liskovsoft.smartyoutubetv2.common.misc.NetPath.context()
                            + " track-selected type=" + mediaLoadData.trackType
                            + " id=" + com.liskovsoft.smartyoutubetv2.common.misc.NetPath.trunc(format.id, 32)
                            + " mime=" + format.sampleMimeType
                            + " bitrate=" + format.bitrate
                            + " size=" + format.width + 'x' + format.height
                            + " fps=" + format.frameRate);
        }

        private void log(String event,
                androidx.media3.exoplayer.source.LoadEventInfo info,
                androidx.media3.exoplayer.source.MediaLoadData data,
                @Nullable Exception error) {
            StringBuilder line = new StringBuilder(
                    com.liskovsoft.smartyoutubetv2.common.misc.NetPath.context())
                    .append(' ').append(event)
                    .append(" lid=").append(info.loadTaskId)
                    .append(' ').append(data.dataType).append('/').append(data.trackType)
                    .append(" host=").append(info.uri.getHost())
                    .append(' ').append(uriTail(info.uri))
                    .append(" req=").append(info.dataSpec.position).append('+').append(info.dataSpec.length)
                    .append(" bytes=").append(info.bytesLoaded)
                    .append(" ms=").append(info.loadDurationMs)
                    .append(" pos=").append(data.mediaStartTimeMs)
                    .append(" net=").append(activeNetwork());
            appendResponseSummary(line, info.responseHeaders);
            if (error != null) {
                if (info.uri.isHierarchical()) {
                    appendQueryParam(line, info.uri, "clen");
                    appendQueryParam(line, info.uri, "lmt");
                }
                line.append(' ').append(error.getClass().getSimpleName()).append(": ")
                        .append(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.trunc(error.getMessage(), 120));
                android.util.Log.w(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG, line.toString());
                androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException http =
                        findInvalidResponseCode(error);
                if (http != null) {
                    StringBuilder detail = new StringBuilder(
                            com.liskovsoft.smartyoutubetv2.common.misc.NetPath.context())
                            .append(" load[E-http] code=").append(http.responseCode);
                    byte[] body = http.responseBody;
                    if (body != null && body.length > 0) {
                        detail.append(" body[").append(body.length).append("] hash=")
                                .append(fingerprint(body)).append(" text=").append(printable(body, 240));
                    }
                    android.util.Log.w(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG, detail.toString());
                    android.util.Log.w(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG,
                            com.liskovsoft.smartyoutubetv2.common.misc.NetPath.context()
                                    + " load[E-request] " + safeRequestFingerprint(info));
                }
            } else {
                android.util.Log.d(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.TAG, line.toString());
            }
        }

        private static void appendResponseSummary(StringBuilder line,
                java.util.Map<String, java.util.List<String>> headers) {
            if (headers == null || headers.isEmpty()) {
                line.append(" responseHeaders=none");
                return;
            }
            line.append(" responseHeaders=y");
            appendHeader(line, headers, "content-length", "respLen");
            appendHeader(line, headers, "content-range", "contentRange");
            appendHeader(line, headers, "accept-ranges", "acceptRanges");
            appendHeader(line, headers, "content-encoding", "encoding");
            appendHeader(line, headers, "server", "server");
        }

        private static void appendHeader(StringBuilder line,
                java.util.Map<String, java.util.List<String>> headers,
                String wantedName, String logName) {
            for (java.util.Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && wantedName.equalsIgnoreCase(entry.getKey())
                        && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    line.append(' ').append(logName).append('=')
                            .append(com.liskovsoft.smartyoutubetv2.common.misc.NetPath.trunc(
                                    entry.getValue().get(0), 80));
                    return;
                }
            }
        }

        private static String discontinuityReason(int reason) {
            switch (reason) {
                case androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION:
                    return "auto";
                case androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK:
                    return "seek";
                case androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
                    return "seek-adjust";
                case androidx.media3.common.Player.DISCONTINUITY_REASON_SKIP:
                    return "skip";
                case androidx.media3.common.Player.DISCONTINUITY_REASON_REMOVE:
                    return "remove";
                case androidx.media3.common.Player.DISCONTINUITY_REASON_INTERNAL:
                    return "internal";
                default:
                    return Integer.toString(reason);
            }
        }

        private String activeNetwork() {
            return com.liskovsoft.smartyoutubetv2.common.misc.NetPath.networkId(mContext);
        }

        private static String uriTail(android.net.Uri uri) {
            StringBuilder tail = new StringBuilder();
            String segment = uri.getLastPathSegment();
            tail.append(segment != null ? segment : uri);
            if (uri.isHierarchical()) {
                appendQueryParam(tail, uri, "itag");
                appendQueryParam(tail, uri, "range");
                appendQueryParam(tail, uri, "sq");
                appendQueryParam(tail, uri, "rn");
            }
            return tail.length() <= 80 ? tail.toString() : tail.substring(0, 80);
        }

        private static void appendQueryParam(StringBuilder tail, android.net.Uri uri, String name) {
            String value = uri.getQueryParameter(name);
            if (value != null) {
                tail.append(' ').append(name).append('=').append(value);
            }
        }

        private static String safeRequestFingerprint(
                androidx.media3.exoplayer.source.LoadEventInfo info) {
            android.net.Uri uri = info.uri;
            StringBuilder result = new StringBuilder("host=").append(uri.getHost())
                    .append(" req=").append(info.dataSpec.position).append('+').append(info.dataSpec.length);
            appendQueryParam(result, uri, "itag");
            appendQueryParam(result, uri, "c");
            appendQueryParam(result, uri, "cver");
            appendQueryParam(result, uri, "range");
            String expire = uri.getQueryParameter("expire");
            if (expire != null) {
                try {
                    long remaining = Long.parseLong(expire) - System.currentTimeMillis() / 1_000L;
                    result.append(" expireInSec=").append(remaining);
                } catch (NumberFormatException ignored) {
                    result.append(" expire=invalid");
                }
            }
            result.append(" ipBound=").append(uri.getQueryParameter("ip") != null ? 'y' : 'n')
                    .append(" pot=").append(uri.getQueryParameter("pot") != null ? 'y' : 'n');
            return result.toString();
        }

        @Nullable
        private static androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                findInvalidResponseCode(Throwable error) {
            for (Throwable e = error; e != null; e = e.getCause()) {
                if (e instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                    return (androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) e;
                }
            }
            return null;
        }

        private static String printable(byte[] body, int max) {
            int n = Math.min(body.length, max);
            StringBuilder sb = new StringBuilder(n);
            for (int i = 0; i < n; i++) {
                char c = (char) (body[i] & 0xFF);
                sb.append(c >= 0x20 && c < 0x7F ? c : '.');
            }
            if (body.length > max) {
                sb.append("...");
            }
            return sb.toString();
        }

        private static String fingerprint(byte[] value) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(value);
                StringBuilder result = new StringBuilder(10);
                for (int i = 0; i < 5; i++) {
                    result.append(String.format(Locale.US, "%02x", hash[i] & 0xff));
                }
                return result.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                return Integer.toHexString(java.util.Arrays.hashCode(value));
            }
        }
    }
}
