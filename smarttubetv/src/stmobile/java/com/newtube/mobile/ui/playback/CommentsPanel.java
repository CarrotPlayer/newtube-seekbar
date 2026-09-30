package com.newtube.mobile.ui.playback;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.liskovsoft.mediaserviceinterfaces.CommentsService;
import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadFailure;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.newtube.mobile.ui.common.MobileSnackbar;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.disposables.Disposable;

/**
 * NEWTUBE(comments-panel): the watch page's comments, in a panel that slides up over the page
 * under the video (the video keeps playing and stays usable: a timestamp in a comment seeks it).
 * Replaced the comments bottom sheet (CommentsSheet).
 *
 * <p>Everything here belongs to one video. A new video closes the panel, drops its comments and
 * ignores late answers for the old one (a generation number on every request). Within a video the
 * two sort orders and the open thread are kept, so closing and reopening, switching Top / Newest,
 * or coming back from a replies page never reloads what is already here, and each keeps its place.</p>
 *
 * <p>Loading: the first page when the panel opens (or earlier, best effort, once the video plays -
 * {@link #prefetch()}), then page by page as the list nears its end. A failed first page offers
 * Try again; a failed next page keeps the rows and ends the list with a retry row. Back closes the
 * sort menu, then a replies page, then the panel.</p>
 */
final class CommentsPanel implements CommentsAdapter.Listener, CommentsPanelLayout.Callback {

    interface Host {
        /** A timestamp in a comment on the video that is playing. */
        void onCommentTimestamp(long positionMs);

        /** A link in a comment to another video. */
        void onCommentVideoLink(String videoId);

        /** The panel now covers (true) or uncovers the watch page. */
        void onCommentsPanelShown(boolean shown);
    }

    private static final int NO_FAILURE = -1;
    private static final int FIRST_PAGE_SKELETONS = 6;
    private static final int REPLIES_SKELETONS = 3;
    /** Rows left below the last visible one when the next page is asked for. */
    private static final int PAGE_AHEAD = 5;
    private static final long SHARED_AXIS_MS = 300;
    private static final long FADE_OUT_MS = 90;
    private static final long FADE_IN_MS = 210;
    private static final long QUICK_FADE_MS = 150;
    private static final float SORT_DIM_ALPHA = 0.4f;
    private static final long MENU_GROW_MS = 200;
    private static final long MENU_FADE_MS = 120;

    /** One page sequence: the comments in one sort order, or the replies of one comment. */
    private static final class Feed {
        @Nullable
        final String firstKey;
        final boolean replies;
        final List<CommentsAdapter.Entry> entries = new ArrayList<>();
        @Nullable
        String nextKey;
        boolean loading;
        boolean loaded;
        int failure = NO_FAILURE;
        boolean moreFailed;
        @Nullable
        Parcelable scroll;
        @Nullable
        Disposable request;
        /** Continuation pages in a row that brought no comments (a runaway guard). */
        int emptyPages;

        Feed(@Nullable String firstKey, boolean replies) {
            this.firstKey = firstKey;
            this.replies = replies;
        }

        void cancel() {
            RxHelper.disposeActions(request);
            request = null;
            loading = false;
        }

        int footer() {
            if (moreFailed) {
                return CommentsAdapter.FOOTER_RETRY;
            }
            return loading && loaded ? CommentsAdapter.FOOTER_LOADING : CommentsAdapter.FOOTER_NONE;
        }
    }

    private final FragmentActivity mActivity;
    private final Host mHost;
    @Nullable
    private final CommentsService mService;
    private final CommentsPanelLayout mLayout;
    private final View mTitles;
    private final View mTitleList;
    private final View mTitleReplies;
    private final TextView mCountView;
    private final View mSort;
    private final TextView mSortLabel;
    private final ImageView mSortChevron;
    private final View mBack;
    private final View mHairline;
    private final LinearProgressIndicator mProgress;
    private final RecyclerView mList;
    private final RecyclerView mReplies;
    private final CommentsAdapter mListAdapter;
    private final CommentsAdapter mRepliesAdapter;
    private final View mLoadState;
    private final TextView mLoadStateMessage;
    private final float mDensity;
    private final OnBackPressedCallback mBackCallback;
    @Nullable
    private BackEventCompat mLastBackEvent;
    @Nullable
    private PopupWindow mSortMenu;

    // --- The video the panel is about; everything below belongs to it.
    @Nullable
    private String mVideoId;
    @Nullable
    private String mCount;
    private int mGeneration;
    @Nullable
    private Feed mTop;
    @Nullable
    private Feed mNewest;
    private boolean mNewestSelected;
    /** A switch to a sort order whose first page is still loading (the list is dimmed meanwhile). */
    private boolean mSortPending;
    /** The replies page's comment and its replies; null on the list. */
    @Nullable
    private CommentsAdapter.Entry mThreadParent;
    @Nullable
    private Feed mThread;
    @Nullable
    private String mCreatorHandle;
    private boolean mSuspended;
    @Nullable
    private Parcelable mSuspendedList;
    @Nullable
    private Parcelable mSuspendedReplies;
    private boolean mPageTransition;
    /** A new video arrived while the sheet slid away: empty it once it is gone (see onVideoChanged). */
    private boolean mViewResetPending;
    /** The list page is fading from one sort order's rows to the other's (see swapList). */
    private boolean mListSwapping;
    /** The sort menu while it fades out (kept so teardown can still remove it). */
    @Nullable
    private PopupWindow mClosingMenu;
    private boolean mReleased;
    @Nullable
    private android.animation.ValueAnimator mPageAnimator;
    private boolean mHairlineShown;

    CommentsPanel(FragmentActivity activity, CommentsPanelLayout layout, Host host) {
        mActivity = activity;
        mHost = host;
        mLayout = layout;
        mDensity = activity.getResources().getDisplayMetrics().density;
        CommentsService service = null;
        try {
            service = YouTubeServiceManager.instance().getCommentsService();
        } catch (RuntimeException e) {
            // No service, no comments: the entry stays hidden without a key anyway.
        }
        mService = service;

        mTitles = layout.findViewById(R.id.comments_titles);
        mTitleList = layout.findViewById(R.id.comments_title_list);
        mTitleReplies = layout.findViewById(R.id.comments_title_replies);
        mCountView = layout.findViewById(R.id.comments_count);
        mSort = layout.findViewById(R.id.comments_sort);
        mSortLabel = layout.findViewById(R.id.comments_sort_label);
        mSortChevron = layout.findViewById(R.id.comments_sort_chevron);
        mBack = layout.findViewById(R.id.comments_back);
        mHairline = layout.findViewById(R.id.comments_hairline);
        mProgress = layout.findViewById(R.id.comments_progress);
        mList = layout.findViewById(R.id.comments_list);
        mReplies = layout.findViewById(R.id.comments_replies);
        mLoadState = layout.findViewById(R.id.mobile_page_load_state);
        mLoadStateMessage = mLoadState.findViewById(R.id.mobile_page_load_state_message);
        mLoadState.findViewById(R.id.mobile_page_load_state_action).setOnClickListener(v -> onRetryFirstPage());
        // A comments panel is a quieter place than a whole page: a smaller picture over the message.
        View stateIcon = mLoadState.findViewById(R.id.mobile_page_load_state_icon);
        ViewGroup.LayoutParams iconParams = stateIcon.getLayoutParams();
        iconParams.width = iconParams.height = Math.round(64 * mDensity);
        stateIcon.setLayoutParams(iconParams);

        mListAdapter = new CommentsAdapter(this);
        mRepliesAdapter = new CommentsAdapter(this);
        setUpList(mList, mListAdapter);
        setUpList(mReplies, mRepliesAdapter);

        layout.setCallback(this);
        layout.findViewById(R.id.comments_close).setOnClickListener(v -> close());
        mBack.setOnClickListener(v -> leaveReplies());
        mSort.setOnClickListener(v -> showSortMenu());
        ViewCompat.setAccessibilityPaneTitle(layout.getSheet(), activity.getString(R.string.mobile_comments_title));
        updateSortLabel();

        mBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
                mLastBackEvent = null;
                if (mThreadParent == null && !mPageTransition) {
                    mLayout.startBackProgress(backEvent);
                }
            }

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                mLastBackEvent = backEvent;
                if (mThreadParent == null) {
                    mLayout.updateBackProgress(backEvent);
                }
            }

            @Override
            public void handleOnBackCancelled() {
                mLastBackEvent = null;
                mLayout.cancelBackProgress();
            }

            @Override
            public void handleOnBackPressed() {
                BackEventCompat last = mLastBackEvent;
                mLastBackEvent = null;
                onBack(last);
            }
        };
        // Added after the player's own handler, so it is asked first while enabled.
        activity.getOnBackPressedDispatcher().addCallback(activity, mBackCallback);
    }

    private void setUpList(RecyclerView list, CommentsAdapter adapter) {
        RowAnimator animator = new RowAnimator();
        list.setLayoutManager(new PageLayoutManager(list.getContext(), animator));
        list.setAdapter(adapter);
        list.setHasFixedSize(true);
        list.setItemAnimator(animator);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (isActivePage(recyclerView)) {
                    updateHairline(true);
                }
                if (dy > 0) {
                    maybeLoadMore(recyclerView);
                }
            }
        });
    }

    /**
     * Rows fade in where skeletons were (150ms) and move when a footer changes; a whole page being
     * replaced (a sort switch, a replies page opening) is not animated row by row - the page's
     * own fade or slide carries it, and a second, row-level fade on top read as a flicker.
     */
    private static final class RowAnimator extends DefaultItemAnimator {
        private boolean mSkipping;

        RowAnimator() {
            setSupportsChangeAnimations(false);
            setAddDuration(QUICK_FADE_MS);
            setRemoveDuration(0);
            setMoveDuration(200);
        }

        /** The next layout's row animations are skipped (cleared when that layout completes). */
        void skipNextLayout() {
            mSkipping = true;
        }

        void onLayoutCompleted() {
            mSkipping = false;
        }

        @Override
        public boolean animateAdd(RecyclerView.ViewHolder holder) {
            if (mSkipping) {
                holder.itemView.setAlpha(1f);
                dispatchAddFinished(holder);
                return false;
            }
            return super.animateAdd(holder);
        }

        @Override
        public boolean animateRemove(RecyclerView.ViewHolder holder) {
            if (mSkipping) {
                dispatchRemoveFinished(holder);
                return false;
            }
            return super.animateRemove(holder);
        }

        @Override
        public boolean animateMove(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
            if (mSkipping) {
                dispatchMoveFinished(holder);
                return false;
            }
            return super.animateMove(holder, fromX, fromY, toX, toY);
        }

        @Override
        public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder,
                                     int fromX, int fromY, int toX, int toY) {
            if (mSkipping) {
                dispatchChangeFinished(oldHolder, true);
                if (newHolder != null && newHolder != oldHolder) {
                    dispatchChangeFinished(newHolder, false);
                }
                return false;
            }
            return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY);
        }
    }

    /** Tells the {@link RowAnimator} when a layout (and so its animations) is over. */
    private static final class PageLayoutManager extends LinearLayoutManager {
        private final RowAnimator mAnimator;

        PageLayoutManager(Context context, RowAnimator animator) {
            super(context);
            mAnimator = animator;
        }

        @Override
        public void onLayoutCompleted(RecyclerView.State state) {
            super.onLayoutCompleted(state);
            mAnimator.onLayoutCompleted();
        }
    }

    /** Replace a page's rows without row-level animation (see {@link RowAnimator}). */
    private void replaceContent(CommentsAdapter adapter, @Nullable CommentsAdapter.Entry parent,
                                @Nullable CharSequence label, List<CommentsAdapter.Entry> items,
                                int skeletons, int footer) {
        RecyclerView list = adapter == mRepliesAdapter ? mReplies : mList;
        if (list.getItemAnimator() instanceof RowAnimator) {
            ((RowAnimator) list.getItemAnimator()).skipNextLayout();
        }
        adapter.setContent(parent, label, items, skeletons, footer);
    }

    // ---------------------------------------------------------------------------------
    // Video lifecycle (driven by MobilePlaybackActivity)
    // ---------------------------------------------------------------------------------

    /** A different video is now on the watch page: close, and forget the old one's comments. */
    void onVideoChanged(@Nullable String videoId) {
        if (Helpers.equals(videoId, mVideoId)) {
            return;
        }
        dismissSortMenu(false);
        cancelRequests();
        mGeneration++;
        mVideoId = videoId;
        mCount = null;
        mTop = null;
        mNewest = null;
        mThread = null;
        mThreadParent = null;
        mNewestSelected = false;
        mSortPending = false;
        mCreatorHandle = null;
        if (mLayout.isOpen() && !mSuspended && mLayout.getVisibility() == View.VISIBLE) {
            // The old video's comments stay in the sheet while it slides away; emptied once gone.
            mViewResetPending = true;
            mLayout.close();
        } else {
            if (mLayout.isOpen()) {
                mLayout.closeImmediately();
            }
            resetViews();
        }
    }

    /** Back to an empty list page, for the next video. */
    private void resetViews() {
        mViewResetPending = false;
        mProgress.hide();
        showListPage();
        hideState();
        replaceContent(mListAdapter, null, null, new ArrayList<>(), 0, CommentsAdapter.FOOTER_NONE);
        replaceContent(mRepliesAdapter, null, null, new ArrayList<>(), 0, CommentsAdapter.FOOTER_NONE);
        mListAdapter.setCreatorHandle(null);
        mRepliesAdapter.setCreatorHandle(null);
        updateSortLabel();
        updateCount();
        mSort.setVisibility(View.VISIBLE);
    }

    /** The video's metadata named where its comments are (null keys = comments are off). */
    void setSource(@Nullable String videoId, @Nullable String topKey, @Nullable String newestKey,
                   @Nullable String count) {
        if (!Helpers.equals(videoId, mVideoId)) {
            onVideoChanged(videoId);
        }
        if (topKey == null) {
            return;
        }
        // A later /next for the same video (a refresh) carries new tokens for the same pages:
        // what is already loaded stays.
        if (mTop == null) {
            mTop = new Feed(topKey, false);
        }
        if (mNewest == null && newestKey != null) {
            mNewest = new Feed(newestKey, false);
        }
        if (!TextUtils.isEmpty(count)) {
            mCount = count;
        }
        updateCount();
        mSort.setVisibility(mNewest != null ? View.VISIBLE : View.GONE);
    }

    boolean hasComments() {
        return mTop != null;
    }

    boolean isOpen() {
        return mLayout.isOpen();
    }

    /**
     * Best effort: fetch the first page now so the panel opens full. Only the default order's first
     * page, only when nothing is loading or loaded; the avatars wait until the rows are shown.
     */
    void prefetch() {
        if (mReleased) {
            return;
        }
        Feed feed = mTop;
        if (feed != null && !feed.loaded && !feed.loading && feed.failure == NO_FAILURE) {
            loadFirstPage(feed);
        }
    }

    // ---------------------------------------------------------------------------------
    // Open / close
    // ---------------------------------------------------------------------------------

    void open() {
        Feed feed = currentFeed();
        if (feed == null || mLayout.isOpen() || mReleased) {
            return;
        }
        if (mViewResetPending) {
            resetViews();
        }
        if (!feed.loaded && !feed.loading) {
            // Opening is a fresh ask: a first page that failed before (the background fetch, or
            // an earlier open) is tried again rather than greeting the person with an error.
            feed.failure = NO_FAILURE;
        }
        // A sort switch still in flight owns the list page (its old rows stay, dimmed, until the
        // new order lands); otherwise show the current order as it stands.
        if (mThreadParent == null && !mSortPending && !mListSwapping) {
            showListPage();
            bindFeedToList(feed, false);
        }
        mLayout.open();
        mLayout.getSheet().post(() -> maybeLoadMore(activePage()));
    }

    @Override
    public void onPanelOpened() {
        mBackCallback.setEnabled(true);
        mHost.onCommentsPanelShown(true);
    }

    void close() {
        dismissSortMenu(false);
        mLayout.close();
    }

    /** Minimize: the panel went with the watch page; it is closed when the player comes back. */
    void closeImmediately() {
        dismissSortMenu(false);
        mLayout.closeImmediately();
    }

    /** Fullscreen or picture-in-picture: out of the way, and back where it was on return. */
    void setSuspended(boolean suspended) {
        if (mSuspended == suspended) {
            return;
        }
        mSuspended = suspended;
        if (!mLayout.isOpen()) {
            return;
        }
        if (suspended) {
            dismissSortMenu(false);
            // In fullscreen the pages get no height, and a list laid out in no height forgets
            // where it was: keep both places for the way back.
            mSuspendedList = saveState(mList);
            mSuspendedReplies = saveState(mReplies);
            mLayout.suspend();
            mBackCallback.setEnabled(false);
            mHost.onCommentsPanelShown(false);
        } else {
            restoreState(mList, mSuspendedList);
            restoreState(mReplies, mSuspendedReplies);
            mSuspendedList = null;
            mSuspendedReplies = null;
            mLayout.resume();
            mBackCallback.setEnabled(true);
            mHost.onCommentsPanelShown(true);
        }
    }

    @Nullable
    private static Parcelable saveState(RecyclerView list) {
        RecyclerView.LayoutManager manager = list.getLayoutManager();
        return manager != null ? manager.onSaveInstanceState() : null;
    }

    private static void restoreState(RecyclerView list, @Nullable Parcelable state) {
        RecyclerView.LayoutManager manager = list.getLayoutManager();
        if (manager != null && state != null) {
            manager.onRestoreInstanceState(state);
            list.requestLayout();
        }
    }

    /** Minimize drag: the panel fades out with the watch page (and back in if the drag is let go). */
    void setMorphAlpha(float alpha) {
        mLayout.setAlpha(alpha);
    }

    /** The activity is going: nothing here may start again (requests, fades, posted work). */
    void release() {
        mReleased = true;
        dismissSortMenu(false);
        cancelRequests();
        mList.animate().cancel();
        showListPage();
        mLayout.setCallback(null);
        mLayout.closeImmediately();
        mBackCallback.remove();
    }

    @Override
    public void onPanelClosed() {
        if (mReleased) {
            return;
        }
        mBackCallback.setEnabled(false);
        mHost.onCommentsPanelShown(false);
        dismissSortMenu(false);
        if (mViewResetPending) {
            resetViews();
        } else {
            saveScroll();
        }
    }

    private void onBack(@Nullable BackEventCompat lastEvent) {
        if (mSortMenu != null) {
            dismissSortMenu(true);
        } else if (mThreadParent != null) {
            leaveReplies();
        } else {
            mLayout.handleBack(lastEvent);
        }
    }

    // ---------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------

    @Nullable
    private Feed currentFeed() {
        return mNewestSelected && mNewest != null ? mNewest : mTop;
    }

    private boolean isShown(Feed feed) {
        return feed == mThread ? mThreadParent != null : feed == currentFeed() && !mSortPending;
    }

    private CommentsAdapter adapterFor(Feed feed) {
        return feed.replies ? mRepliesAdapter : mListAdapter;
    }

    private void loadFirstPage(Feed feed) {
        if (mReleased || mService == null || feed.firstKey == null || feed.loading) {
            return;
        }
        final int generation = mGeneration;
        feed.loading = true;
        feed.failure = NO_FAILURE;
        feed.request = mService.getCommentsObserve(feed.firstKey).subscribe(
                group -> onFirstPage(feed, generation, group),
                error -> onFirstPageFailed(feed, generation, error));
    }

    private void onFirstPage(Feed feed, int generation, @Nullable CommentGroup group) {
        if (generation != mGeneration) {
            return;
        }
        feed.loading = false;
        feed.request = null;
        feed.loaded = true;
        feed.entries.clear();
        feed.entries.addAll(toEntries(group, feed.replies));
        feed.nextKey = nextKey(group, feed.firstKey);
        if (!feed.replies) {
            learnCreator(feed.entries);
        }
        if (feed == pendingSortFeed()) {
            finishSortSwitch(feed);
            return;
        }
        if (!isShown(feed)) {
            return;
        }
        if (feed.entries.isEmpty() && !feed.replies) {
            showState(R.string.mobile_comments_none, false);
            replaceContent(mListAdapter, null, null, feed.entries, 0, CommentsAdapter.FOOTER_NONE);
            return;
        }
        hideState();
        adapterFor(feed).showFirstPage(feed.entries, feed.footer());
        RecyclerView list = feed.replies ? mReplies : mList;
        // A short first page (under a screenful) would never scroll to ask for more.
        list.post(() -> maybeLoadMore(list));
    }

    private void onFirstPageFailed(Feed feed, int generation, Throwable error) {
        if (generation != mGeneration) {
            return;
        }
        feed.loading = false;
        feed.request = null;
        feed.failure = LoadFailure.classify(mActivity, error);
        if (feed == pendingSortFeed()) {
            abandonSortSwitch();
            return;
        }
        if (!isShown(feed)) {
            return;
        }
        if (feed.replies) {
            mRepliesAdapter.showFirstPage(feed.entries, CommentsAdapter.FOOTER_RETRY);
        } else {
            replaceContent(mListAdapter, null, null, feed.entries, 0, CommentsAdapter.FOOTER_NONE);
            showState(feed.failure == LoadFailure.NO_CONNECTION
                    ? R.string.mobile_empty_no_connection : R.string.mobile_comments_load_error, true);
        }
    }

    private void onRetryFirstPage() {
        Feed feed = currentFeed();
        if (feed == null || feed.loading) {
            return;
        }
        feed.failure = NO_FAILURE;
        hideState();
        replaceContent(mListAdapter, null, null, feed.entries, FIRST_PAGE_SKELETONS, CommentsAdapter.FOOTER_NONE);
        loadFirstPage(feed);
    }

    private void maybeLoadMore(@Nullable RecyclerView list) {
        if (list == null || mReleased || !isActivePage(list) || !mLayout.isOpen()) {
            return;
        }
        Feed feed = list == mReplies ? mThread : currentFeed();
        if (feed == null || !feed.loaded || feed.loading || feed.moreFailed || feed.nextKey == null
                || (list == mList && (mSortPending || mListSwapping))) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) list.getLayoutManager();
        CommentsAdapter adapter = adapterFor(feed);
        int last = adapter.lastCommentPosition();
        if (manager != null && last >= 0 && manager.findLastVisibleItemPosition() >= last - PAGE_AHEAD) {
            loadNextPage(feed);
        }
    }

    private void loadNextPage(Feed feed) {
        if (mReleased || mService == null || feed.nextKey == null || feed.loading) {
            return;
        }
        // The token stays on the feed until its page arrives, so a failure can ask for it again.
        final String key = feed.nextKey;
        final int generation = mGeneration;
        feed.loading = true;
        feed.moreFailed = false;
        if (isShown(feed)) {
            adapterFor(feed).setFooter(CommentsAdapter.FOOTER_LOADING);
        }
        feed.request = mService.getCommentsObserve(key).subscribe(
                group -> {
                    if (generation != mGeneration) {
                        return;
                    }
                    feed.loading = false;
                    feed.request = null;
                    List<CommentsAdapter.Entry> more = toEntries(group, feed.replies);
                    feed.emptyPages = more.isEmpty() ? feed.emptyPages + 1 : 0;
                    // A page of nothing may still carry a token; a few in a row means the end.
                    feed.nextKey = feed.emptyPages >= 3 ? null : nextKey(group, key);
                    feed.entries.addAll(more);
                    if (isShown(feed)) {
                        adapterFor(feed).append(more, feed.footer());
                        // Still short of the end of the screen: no scroll will come to ask for more.
                        RecyclerView list = feed.replies ? mReplies : mList;
                        list.post(() -> maybeLoadMore(list));
                    }
                },
                error -> {
                    if (generation != mGeneration) {
                        return;
                    }
                    feed.loading = false;
                    feed.request = null;
                    feed.moreFailed = true;
                    if (isShown(feed)) {
                        adapterFor(feed).setFooter(CommentsAdapter.FOOTER_RETRY);
                    }
                });
    }

    @Override
    public void onRetry() {
        Feed feed = mThreadParent != null ? mThread : currentFeed();
        if (feed == null || feed.loading) {
            return;
        }
        if (!feed.loaded) {
            // The replies page's first page failed: its retry row asks for it again.
            adapterFor(feed).setFooter(CommentsAdapter.FOOTER_NONE);
            replaceContent(adapterFor(feed), mThreadParent, labelFor(mThreadParent), feed.entries,
                    REPLIES_SKELETONS, CommentsAdapter.FOOTER_NONE);
            loadFirstPage(feed);
            return;
        }
        feed.moreFailed = false;
        loadNextPage(feed);
    }

    private static List<CommentsAdapter.Entry> toEntries(@Nullable CommentGroup group, boolean replies) {
        List<CommentsAdapter.Entry> entries = new ArrayList<>();
        if (group == null || group.getComments() == null) {
            return entries;
        }
        for (CommentItem item : group.getComments()) {
            // A replies page starts with the comment itself, as a bare renderer the parser leaves
            // blank: skip that and any other empty placeholder. (Not CommentItem.isEmpty(), which
            // YouTube's implementation uses to mean "has no replies".)
            if (item == null || TextUtils.isEmpty(item.getMessage()) && TextUtils.isEmpty(item.getAuthorName())) {
                continue;
            }
            entries.add(new CommentsAdapter.Entry(item, replies));
        }
        return entries;
    }

    @Nullable
    private static String nextKey(@Nullable CommentGroup group, @Nullable String requested) {
        String next = group != null ? group.getNextCommentsKey() : null;
        return Helpers.equals(next, requested) ? null : next;
    }

    /** "Pinned by @handle" names the creator: their comments get the creator pill from here on. */
    private void learnCreator(List<CommentsAdapter.Entry> entries) {
        if (mCreatorHandle != null) {
            return;
        }
        for (CommentsAdapter.Entry entry : entries) {
            String pinned = entry.item.getPinnedLabel();
            if (pinned == null) {
                continue;
            }
            int at = pinned.lastIndexOf('@');
            if (at >= 0) {
                mCreatorHandle = pinned.substring(at).trim();
                mListAdapter.setCreatorHandle(mCreatorHandle);
                mRepliesAdapter.setCreatorHandle(mCreatorHandle);
            }
            return;
        }
    }

    private void cancelRequests() {
        if (mTop != null) {
            mTop.cancel();
        }
        if (mNewest != null) {
            mNewest.cancel();
        }
        if (mThread != null) {
            mThread.cancel();
        }
    }

    // ---------------------------------------------------------------------------------
    // The list page
    // ---------------------------------------------------------------------------------

    /** Show {@code feed} on the list page as it stands: rows, its failure, or skeletons + a load. */
    private void bindFeedToList(Feed feed, boolean restoreScroll) {
        if (feed.loaded) {
            if (feed.entries.isEmpty()) {
                replaceContent(mListAdapter, null, null, feed.entries, 0, CommentsAdapter.FOOTER_NONE);
                showState(R.string.mobile_comments_none, false);
            } else {
                hideState();
                replaceContent(mListAdapter, null, null, feed.entries, 0, feed.footer());
            }
        } else if (feed.failure != NO_FAILURE && !feed.loading) {
            replaceContent(mListAdapter, null, null, feed.entries, 0, CommentsAdapter.FOOTER_NONE);
            showState(feed.failure == LoadFailure.NO_CONNECTION
                    ? R.string.mobile_empty_no_connection : R.string.mobile_comments_load_error, true);
        } else {
            hideState();
            replaceContent(mListAdapter, null, null, feed.entries, FIRST_PAGE_SKELETONS, CommentsAdapter.FOOTER_NONE);
            loadFirstPage(feed);
        }
        LinearLayoutManager manager = (LinearLayoutManager) mList.getLayoutManager();
        if (manager != null) {
            if (restoreScroll && feed.scroll != null) {
                manager.onRestoreInstanceState(feed.scroll);
            } else if (!restoreScroll && feed.scroll != null && feed.loaded) {
                manager.onRestoreInstanceState(feed.scroll);
            } else {
                manager.scrollToPositionWithOffset(0, 0);
            }
        }
        updateHairline(false);
    }

    private void saveScroll() {
        Feed feed = currentFeed();
        LinearLayoutManager manager = (LinearLayoutManager) mList.getLayoutManager();
        if (feed != null && manager != null && feed.loaded && !mSortPending) {
            feed.scroll = manager.onSaveInstanceState();
        }
    }

    private void showState(int message, boolean retry) {
        mLoadStateMessage.setText(message);
        mLoadState.findViewById(R.id.mobile_page_load_state_action).setVisibility(retry ? View.VISIBLE : View.GONE);
        mLoadState.findViewById(R.id.mobile_page_load_state_icon).setVisibility(retry ? View.VISIBLE : View.GONE);
        mLoadState.setVisibility(View.VISIBLE);
    }

    private void hideState() {
        mLoadState.setVisibility(View.GONE);
    }

    // ---------------------------------------------------------------------------------
    // Sort
    // ---------------------------------------------------------------------------------

    private void updateSortLabel() {
        int label = mNewestSelected ? R.string.mobile_comments_sort_newest : R.string.mobile_comments_sort_top;
        mSortLabel.setText(label);
        mSort.setContentDescription(mActivity.getString(R.string.mobile_comments_sort_description,
                mActivity.getString(label)));
    }

    private void updateCount() {
        mCountView.setText(mCount);
        mCountView.setVisibility(TextUtils.isEmpty(mCount) ? View.GONE : View.VISIBLE);
    }

    private void showSortMenu() {
        if (mSortMenu != null || mNewest == null || mThreadParent != null || mPageTransition
                || mListSwapping || mReleased) {
            return;
        }
        View content = LayoutInflater.from(mActivity).inflate(R.layout.mobile_comments_sort_menu,
                (ViewGroup) mLayout, false);
        content.findViewById(R.id.comments_sort_top_check).setVisibility(mNewestSelected ? View.INVISIBLE : View.VISIBLE);
        content.findViewById(R.id.comments_sort_newest_check).setVisibility(mNewestSelected ? View.VISIBLE : View.INVISIBLE);
        content.findViewById(R.id.comments_sort_top).setOnClickListener(v -> {
            dismissSortMenu(true);
            setSort(false);
        });
        content.findViewById(R.id.comments_sort_newest).setOnClickListener(v -> {
            dismissSortMenu(true);
            setSort(true);
        });
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

        PopupWindow menu = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        menu.setAnimationStyle(0);
        menu.setOutsideTouchable(true);
        menu.setElevation(8 * mDensity);
        menu.setOnDismissListener(() -> {
            if (mSortMenu == menu) {
                mSortMenu = null;
            }
            mSortChevron.animate().rotation(0f).setStartDelay(0).setDuration(MENU_GROW_MS)
                    .setInterpolator(CommentsPanelLayout.STANDARD).start();
        });
        mSortMenu = menu;

        // Grows from under the sort button, its end edge on the button's.
        int[] at = new int[2];
        mSort.getLocationInWindow(at);
        boolean rtl = ViewCompat.getLayoutDirection(mLayout) == ViewCompat.LAYOUT_DIRECTION_RTL;
        int width = content.getMeasuredWidth();
        int x = rtl ? at[0] : at[0] + mSort.getWidth() - width;
        int y = at[1] + mSort.getHeight() - Math.round(4 * mDensity);
        // LEFT, not START: x is measured from the window's left edge in both directions.
        menu.showAtLocation(mLayout, Gravity.TOP | Gravity.LEFT, Math.max(0, x), y);

        content.setPivotX(rtl ? width * 0.15f : width * 0.85f);
        content.setPivotY(0f);
        content.setScaleX(0.85f);
        content.setScaleY(0.85f);
        content.setAlpha(0f);
        content.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(MENU_GROW_MS)
                .setInterpolator(CommentsPanelLayout.EMPHASIZED_DECELERATE).start();
        content.animate().alpha(1f).setStartDelay(0).setDuration(MENU_FADE_MS).setInterpolator(null).start();
        mSortChevron.animate().rotation(180f).setStartDelay(0).setDuration(MENU_GROW_MS)
                .setInterpolator(CommentsPanelLayout.STANDARD).start();
    }

    private void dismissSortMenu(boolean animate) {
        if (!animate && mClosingMenu != null) {
            // A fade already under way: teardown can't wait for it.
            PopupWindow closing = mClosingMenu;
            mClosingMenu = null;
            if (closing.getContentView() != null) {
                closing.getContentView().animate().cancel();
            }
            closing.dismiss();
        }
        PopupWindow menu = mSortMenu;
        if (menu == null) {
            return;
        }
        mSortMenu = null;
        View content = menu.getContentView();
        if (!animate || content == null || !menu.isShowing()) {
            menu.dismiss();
            return;
        }
        mClosingMenu = menu;
        content.animate().cancel();
        content.animate().alpha(0f).setStartDelay(0).setDuration(100).setInterpolator(null)
                .withEndAction(() -> {
                    if (mClosingMenu == menu) {
                        mClosingMenu = null;
                    }
                    menu.dismiss();
                }).start();
    }

    private void setSort(boolean newest) {
        if (newest == mNewestSelected || mSortPending || mListSwapping || (newest && mNewest == null)) {
            return;
        }
        Feed from = currentFeed();
        saveScroll();
        mNewestSelected = newest;
        updateSortLabel();
        Feed to = currentFeed();
        if (to == null) {
            return;
        }
        if (from == null || !from.loaded || from.entries.isEmpty()) {
            // Nothing worth keeping on screen: show the other order at once (skeletons or rows).
            bindFeedToList(to, true);
            return;
        }
        if (to.loaded || to.failure != NO_FAILURE && !to.loading) {
            // Kept from before: out in 90ms, back in 150ms, where it was left.
            swapList(to, true);
            return;
        }
        // First time for this order: the current list dims under the progress line until it lands.
        mSortPending = true;
        fadeList(SORT_DIM_ALPHA, QUICK_FADE_MS, null);
        mProgress.show();
        loadFirstPage(to);
    }

    @Nullable
    private Feed pendingSortFeed() {
        return mSortPending ? currentFeed() : null;
    }

    private void finishSortSwitch(Feed feed) {
        mProgress.hide();
        swapList(feed, false); // mSortPending holds until the new rows are in
    }

    /**
     * The list page fades out (90ms), takes {@code feed}'s rows, and fades back in (150ms). Until
     * the rows are swapped nothing may act on the list: no replies page, pagination or sort.
     */
    private void swapList(Feed feed, boolean restoreScroll) {
        mListSwapping = true;
        fadeList(0f, FADE_OUT_MS, () -> {
            mListSwapping = false;
            mSortPending = false;
            bindFeedToList(feed, restoreScroll);
            fadeList(1f, QUICK_FADE_MS, null);
            mList.post(() -> maybeLoadMore(mList));
        });
    }

    /** The other order didn't load: back on the one that was showing, and say so. */
    private void abandonSortSwitch() {
        mSortPending = false;
        mProgress.hide();
        mNewestSelected = !mNewestSelected;
        updateSortLabel();
        Feed restored = currentFeed();
        if (restored != null && mThreadParent == null) {
            // Rebound, not assumed: the panel may have been closed and reopened meanwhile.
            bindFeedToList(restored, true);
        }
        fadeList(1f, QUICK_FADE_MS, null);
        MobileSnackbar.show(mActivity, R.string.mobile_comments_load_error);
    }

    // ---------------------------------------------------------------------------------
    // The replies page (Material shared axis X)
    // ---------------------------------------------------------------------------------

    @Override
    public void onRepliesClicked(CommentsAdapter.Entry entry) {
        if (mThreadParent != null || mPageTransition || !entry.hasReplies() || mSortPending
                || mListSwapping || !mLayout.isOpen()) {
            return;
        }
        dismissSortMenu(false);
        saveScroll();
        mThreadParent = entry;
        mThread = new Feed(entry.item.getNestedCommentsKey(), true);
        replaceContent(mRepliesAdapter, entry, labelFor(entry), mThread.entries, REPLIES_SKELETONS,
                CommentsAdapter.FOOTER_NONE);
        LinearLayoutManager manager = (LinearLayoutManager) mReplies.getLayoutManager();
        if (manager != null) {
            manager.scrollToPositionWithOffset(0, 0);
        }
        loadFirstPage(mThread);
        sharedAxis(true);
    }

    private void leaveReplies() {
        if (mThreadParent == null || mPageTransition) {
            return;
        }
        if (mThread != null) {
            mThread.cancel();
        }
        mThreadParent = null;
        sharedAxis(false);
    }

    @Nullable
    private static CharSequence labelFor(@Nullable CommentsAdapter.Entry entry) {
        return entry != null ? entry.item.getReplyCount() : null;
    }

    /**
     * Forward: the list slides 30dp toward the start and fades out in 90ms while the replies page
     * slides in from 30dp and fades in over the next 210ms (both moves 300ms, emphasized); the
     * header's title crossfades and shifts to make room for the back arrow. Backward is the mirror.
     * One animator drives it all, so it can't come apart, and with animations off it just ends.
     */
    private void sharedAxis(boolean forward) {
        if (mPageAnimator != null) {
            mPageAnimator.cancel();
        }
        mPageTransition = true;
        float dir = isRtl() ? -1f : 1f;
        float shift = 30 * mDensity * dir;
        float titleShift = 40 * mDensity * dir;
        float backShift = -10 * mDensity * dir;
        View out = forward ? mList : mReplies;
        View in = forward ? mReplies : mList;
        View titleOut = forward ? mTitleList : mTitleReplies;
        View titleIn = forward ? mTitleReplies : mTitleList;
        float outTo = forward ? -shift : shift;
        float inFrom = forward ? shift : -shift;
        float titlesFrom = mTitles.getTranslationX();
        float titlesTo = forward ? titleShift : 0f;
        float backFrom = mBack.getAlpha();
        float sortFrom = mSort.getAlpha();
        boolean sortShown = mNewest != null;

        for (View view : new View[]{out, in, titleOut, titleIn, mBack, mSort, mTitles}) {
            view.animate().cancel();
        }
        in.setVisibility(View.VISIBLE);
        titleIn.setVisibility(View.VISIBLE);
        mBack.setVisibility(View.VISIBLE);
        mBack.setEnabled(forward);
        mSort.setEnabled(!forward);
        out.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        in.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        mHairline.animate().cancel();
        mHairline.animate().alpha(0f).setStartDelay(0).setDuration(QUICK_FADE_MS).start();
        mHairlineShown = false;

        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SHARED_AXIS_MS);
        animator.setInterpolator(null); // linear time: each property applies its own curve below
        animator.addUpdateListener(a -> {
            float t = a.getAnimatedFraction();
            float ms = t * SHARED_AXIS_MS;
            float move = CommentsPanelLayout.EMPHASIZED.getInterpolation(t);
            out.setTranslationX(outTo * move);
            out.setAlpha(1f - clamp(ms / FADE_OUT_MS));
            in.setTranslationX(inFrom * (1f - move));
            in.setAlpha(clamp((ms - FADE_OUT_MS) / FADE_IN_MS));
            mTitles.setTranslationX(titlesFrom + (titlesTo - titlesFrom) * move);
            titleOut.setAlpha(1f - clamp(ms / QUICK_FADE_MS));
            titleIn.setAlpha(clamp((ms - FADE_OUT_MS) / QUICK_FADE_MS));
            float back = clamp(ms / 200f);
            mBack.setAlpha(forward ? backFrom + (1f - backFrom) * back : backFrom * (1f - back));
            mBack.setTranslationX(forward ? backShift * (1f - move) : backShift * move);
            if (sortShown) {
                float sort = forward ? sortFrom * (1f - clamp(ms / QUICK_FADE_MS))
                        : sortFrom + (1f - sortFrom) * clamp((ms - FADE_OUT_MS) / QUICK_FADE_MS);
                mSort.setAlpha(sort);
            }
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (mPageAnimator == animation) {
                    mPageAnimator = null;
                }
                if (mCancelled) {
                    return;
                }
                mPageTransition = false;
                out.setVisibility(View.INVISIBLE);
                out.setTranslationX(0f);
                out.setAlpha(1f);
                titleOut.setVisibility(View.INVISIBLE);
                if (!forward) {
                    mBack.setVisibility(View.INVISIBLE);
                    replaceContent(mRepliesAdapter, null, null, new ArrayList<>(), 0, CommentsAdapter.FOOTER_NONE);
                    mThread = null;
                }
                updateHairline(true);
                // TalkBack lands on the new page's way back (or on the list's sort control).
                View focus = forward ? mBack : mSort.getVisibility() == View.VISIBLE ? mSort : mList;
                focus.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
            }
        });
        mPageAnimator = animator;
        animator.start();
    }

    /** The list page as the resting state (no transition, no replies). */
    private void showListPage() {
        if (mPageAnimator != null) {
            mPageAnimator.cancel();
            mPageAnimator = null;
        }
        mPageTransition = false;
        if (mListSwapping) {
            // The swap's fade is cancelled below with the rest: whoever reset the page rebinds it.
            mListSwapping = false;
            mSortPending = false;
        }
        for (View view : new View[]{mList, mReplies, mTitles, mTitleList, mTitleReplies, mBack, mSort}) {
            view.animate().cancel();
            view.setTranslationX(0f);
        }
        mList.setVisibility(View.VISIBLE);
        mList.setAlpha(1f);
        mList.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        mReplies.setVisibility(View.INVISIBLE);
        mReplies.setAlpha(1f);
        mReplies.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        mTitleList.setVisibility(View.VISIBLE);
        mTitleList.setAlpha(1f);
        mTitleReplies.setVisibility(View.INVISIBLE);
        mTitleReplies.setAlpha(0f);
        mBack.setVisibility(View.INVISIBLE);
        mBack.setAlpha(0f);
        mSort.setAlpha(1f);
        mSort.setEnabled(true);
    }

    /** The list page's fades (sort switches). Linear, no delay - set every time, since a view's
     *  ViewPropertyAnimator keeps the last call's settings. */
    private void fadeList(float alpha, long durationMs, @Nullable Runnable end) {
        mList.animate().cancel();
        ViewPropertyAnimator animator = mList.animate().alpha(alpha).setDuration(durationMs).setStartDelay(0)
                .setInterpolator(null);
        if (end != null) {
            animator.withEndAction(end);
        }
        animator.start();
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private boolean isRtl() {
        return ViewCompat.getLayoutDirection(mLayout) == ViewCompat.LAYOUT_DIRECTION_RTL;
    }

    @Nullable
    private RecyclerView activePage() {
        return mThreadParent != null ? mReplies : mList;
    }

    private boolean isActivePage(RecyclerView list) {
        return list == activePage();
    }

    /** The hairline under the header shows once the page is scrolled away from its top. */
    private void updateHairline(boolean animate) {
        RecyclerView page = activePage();
        boolean show = page != null && page.canScrollVertically(-1) && !mPageTransition;
        if (show == mHairlineShown && animate) {
            return;
        }
        mHairlineShown = show;
        mHairline.animate().cancel();
        if (animate) {
            mHairline.animate().alpha(show ? 1f : 0f).setStartDelay(0).setDuration(QUICK_FADE_MS).start();
        } else {
            mHairline.setAlpha(show ? 1f : 0f);
        }
    }

    // ---------------------------------------------------------------------------------
    // Row actions
    // ---------------------------------------------------------------------------------

    @Override
    public void onLikeClicked(CommentsAdapter.Entry entry) {
        if (WatchActionFeedback.blockIfSignedOut(mActivity, R.string.mobile_comments_sign_in_to_like)) {
            return;
        }
        boolean liked = !entry.liked;
        entry.liked = liked;
        String count = entry.likeCount;
        if (TextUtils.isEmpty(count)) {
            count = "0";
        }
        if (Helpers.isInteger(count)) {
            int value = Helpers.parseInt(count) + (liked ? 1 : -1);
            entry.likeCount = value > 0 ? String.valueOf(value) : null;
        }
        notifyEntry(entry, CommentsAdapter.PAYLOAD_LIKE);
        String key = entry.item.getNestedCommentsKey();
        if (key != null && mService != null) {
            RxHelper.execute(mService.toggleLikeObserve(key));
        }
    }

    private void notifyEntry(CommentsAdapter.Entry entry, Object payload) {
        mListAdapter.notifyEntry(entry, payload);
        mRepliesAdapter.notifyEntry(entry, payload);
    }

    @Override
    public void onLinkClicked(CommentItem.Span span) {
        if (span.videoId != null) {
            if (span.videoId.equals(mVideoId)) {
                if (span.startTimeSeconds >= 0) {
                    mHost.onCommentTimestamp(span.startTimeSeconds * 1000L);
                }
            } else {
                mHost.onCommentVideoLink(span.videoId);
            }
            return;
        }
        if (span.url != null) {
            openUrl(span.url);
        }
    }

    private void openUrl(String url) {
        Uri uri = Uri.parse(url);
        // YouTube wraps outside links in its redirect page: go straight to the address itself.
        String host = uri.getHost();
        if (host != null && host.endsWith("youtube.com") && "/redirect".equals(uri.getPath())) {
            String target = uri.getQueryParameter("q");
            if (!TextUtils.isEmpty(target)) {
                uri = Uri.parse(target);
                host = uri.getHost();
            }
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        if (host != null && (host.endsWith("youtube.com") || host.equals("youtu.be"))) {
            // A YouTube address is ours to open (the manifest takes these links).
            intent.setPackage(mActivity.getPackageName());
        }
        try {
            mActivity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            intent.setPackage(null);
            try {
                mActivity.startActivity(intent);
            } catch (ActivityNotFoundException ignored) {
                // Nothing on the device opens it.
            }
        }
    }

    @Override
    public void onCopy(CommentsAdapter.Entry entry) {
        String text = entry.item.getMessage();
        if (TextUtils.isEmpty(text)) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(mActivity.getString(R.string.mobile_comments_title), text));
        // Android 13+ confirms a copy itself; earlier versions get ours.
        if (Build.VERSION.SDK_INT < 33) {
            MobileSnackbar.show(mActivity, R.string.mobile_comments_copied);
        }
    }
}
