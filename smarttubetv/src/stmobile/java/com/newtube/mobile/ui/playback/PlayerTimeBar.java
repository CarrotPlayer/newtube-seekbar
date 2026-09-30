package com.newtube.mobile.ui.playback;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.ui.TimeBar;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.newtube.mobile.ui.common.Haptics;
import com.newtube.mobile.ui.common.Motion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * NEWTUBE(seek bar): the player's seek bar, drawn the way YouTube's is. The owner's verdict on
 * media3's bar with chapters: "so many steps and in big red" - a 3 dp track squeezed between the
 * times, cut by a black notch per chapter, under a 14 dp red dot. Measured on YouTube 21.18: a 2 dp
 * track that becomes 3 dp while dragging; chapters as 2 dp transparent gaps (3 dp while dragging); a
 * 12 dp dot, 20 dp while dragging; played #FF0033 turning pink (#FF2791) over the last 40 dp before
 * the dot; white 70% buffered, white 20% unplayed. This bar keeps those proportions (the dragged dot
 * a little smaller) with a calmer buffered tone.
 *
 * <p>Two resting states, animated between: shown (the controls are up: the whole track and the
 * dot) and hidden (portrait keeps only the played part as a line on the video's bottom edge, like
 * YouTube's inline player; fullscreen hides the bar). Only the shown bar takes touches, in a band
 * as tall as a fingertip around the track ({@link #isInTouchBand}; WatchRootLayout routes it).</p>
 *
 * <p>Dragging ticks a haptic at every chapter boundary, and dragging back onto where playback was
 * snaps there with a click: letting go then cancels the seek ({@link CancelListener} shows
 * "Release to cancel"). A touch that never leaves that spot cancels too, so resting a finger on the
 * dot never nudges playback.</p>
 */
public class PlayerTimeBar extends View implements TimeBar {

    /** "Release to cancel": the drag is back on where playback was. */
    public interface CancelListener {
        void onReleaseToCancel(boolean armed);
    }

    static final int PLAYED_COLOR = 0xFFFF0033;
    static final int PLAYED_TAIL_COLOR = 0xFFFF2791;
    static final int BUFFERED_COLOR = 0x80FFFFFF;
    static final int UNPLAYED_COLOR = 0x33FFFFFF;

    private static final float TRACK_DP = 2f;
    private static final float TRACK_DRAGGING_DP = 3f;
    private static final float GAP_DP = 2f;
    private static final float GAP_DRAGGING_DP = 3f;
    private static final float SCRUBBER_DP = 12f;
    private static final float SCRUBBER_DRAGGING_DP = 18f;
    /** The pink tail of the played track, ending at the dot. */
    private static final float TAIL_DP = 40f;
    /** Within this of where playback was, a drag snaps back onto it. */
    private static final float SNAP_DP = 10f;
    /** ...and has to move this far to come off it again (no flicker at the edge). */
    private static final float UNSNAP_DP = 14f;
    /** A SponsorBlock range narrower than this still shows. */
    private static final float MIN_SEGMENT_DP = 2f;
    /**
     * The touch band around the track, most of it outside this thin view: YouTube 21.18 takes a
     * drag that starts 18 dp above its bar (not 22 dp) and 16 dp below it, over the page (measured
     * on the reference emulator). The first cut took only this view's 18 dp above the video's edge,
     * and a finger a little off the bar landed on the video, the page or the system's Back swipe.
     */
    private static final float TOUCH_ABOVE_DP = 20f;
    private static final float TOUCH_BELOW_DP = 16f;
    /** ...and past the track's ends (fullscreen insets the bar from the screen's edges). */
    private static final float TOUCH_SIDE_DP = 12f;
    /** A fast drag across many short chapters: no more than one tick per this. */
    private static final long TICK_MIN_INTERVAL_MS = 30;
    private static final long SHOW_MS = Motion.FADE_IN_MS;
    private static final long DRAG_MS = 120;
    /** Default accessibility / key step when none is set: a twentieth of the video. */
    private static final int DEFAULT_KEY_COUNT = 20;
    /** A keyboard scrub seeks this long after its last arrow key (media3's DefaultTimeBar's rule). */
    private static final long KEY_SCRUB_STOP_MS = 1000;

    private final Paint mPlayedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mTailMatrix = new Matrix();
    private final CopyOnWriteArraySet<OnScrubListener> mListeners = new CopyOnWriteArraySet<>();
    private final List<SeekBarSegment> mSegments = new ArrayList<>();
    private final StringBuilder mFormatBuilder = new StringBuilder();
    private final Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
    private final float mDensity;
    private final float mTailPx;

    @Nullable
    private CancelListener mCancelListener;

    private long mDuration = C.TIME_UNSET;
    private long mPosition;
    private long mBufferedPosition;
    /** Chapter starts after 0, ascending. */
    private long[] mChapterStarts = new long[0];

    private long mKeyTimeIncrement = C.TIME_UNSET;
    private int mKeyCountIncrement = DEFAULT_KEY_COUNT;
    /** Lands a keyboard scrub; a plain main-thread handler, so it runs whether or not the bar is attached. */
    private final Handler mKeyHandler = new Handler(Looper.getMainLooper());
    private final Runnable mStopKeyScrub = () -> stopScrubbing(false);

    /** 0 = hidden (a line or nothing), 1 = shown; animated. */
    private float mShown = 1f;
    private boolean mShownTarget = true;
    /** 0 = resting, 1 = dragging; animated. */
    private float mDrag;
    @Nullable
    private ValueAnimator mShowAnimator;
    @Nullable
    private ValueAnimator mDragAnimator;

    /** Portrait: hidden, the played part stays as a line on the bottom edge. */
    private boolean mLineWhenHidden = true;
    /** Portrait: the track lies on the view's bottom edge (the video's); else it is centered. */
    private boolean mTrackAtBottom = true;
    /** Touches come through {@link #onRoutedTouchEvent} (WatchRootLayout's band), not this view's bounds. */
    private boolean mTouchRouted;

    private boolean mScrubbing;
    private long mScrubPosition;
    /** Where playback was when the drag began: the snap-back / cancel spot. */
    private long mScrubOrigin;
    private boolean mLeftOrigin;
    private boolean mSnapped;
    private int mScrubChapter;
    private long mLastTickAt;

    public PlayerTimeBar(Context context) {
        this(context, null);
    }

    public PlayerTimeBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerTimeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDensity = getResources().getDisplayMetrics().density;
        mTailPx = dp(TAIL_DP);
        mPlayedPaint.setColor(PLAYED_COLOR);
        mPlayedPaint.setShader(new LinearGradient(0f, 0f, mTailPx, 0f,
                PLAYED_COLOR, PLAYED_TAIL_COLOR, Shader.TileMode.CLAMP));
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    // ---------------------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------------------

    public void setCancelListener(@Nullable CancelListener listener) {
        mCancelListener = listener;
    }

    /** Portrait: the track lies on the view's bottom edge (the video's). Fullscreen: centered. */
    public void setTrackAtBottom(boolean atBottom) {
        if (mTrackAtBottom != atBottom) {
            mTrackAtBottom = atBottom;
            updateGestureExclusion(); // the band moves with the track
            invalidate();
        }
    }

    /**
     * The bar's touches are hit-tested by a parent over its whole band ({@link #isInTouchBand}) and
     * arrive through {@link #onRoutedTouchEvent}; touches that reach this view directly are left to
     * the views under it - where the band's router gave a touch to the row's buttons, say.
     */
    public void setTouchRouted(boolean routed) {
        mTouchRouted = routed;
    }

    /** Whether the played part stays on screen as a line while the controls are hidden. */
    public void setLineWhenHidden(boolean line) {
        if (mLineWhenHidden != line) {
            mLineWhenHidden = line;
            invalidate();
        }
    }

    /** Follows the player controls: shown with them, a line (or nothing) without them. */
    public void setShown(boolean shown, boolean animate) {
        if (mShownTarget == shown && (mShowAnimator != null || mShown == (shown ? 1f : 0f))) {
            return;
        }
        mShownTarget = shown;
        if (!shown && mScrubbing) {
            stopScrubbing(true);
        }
        // Hidden, the bar is a line at most: not a control TalkBack or a keyboard can land on (its
        // old home, the controls overlay, went GONE with the controls and took it along).
        setFocusable(shown);
        setImportantForAccessibility(shown ? IMPORTANT_FOR_ACCESSIBILITY_YES : IMPORTANT_FOR_ACCESSIBILITY_NO);
        updateGestureExclusion();
        if (mShowAnimator != null) {
            mShowAnimator.cancel();
            mShowAnimator = null;
        }
        float target = shown ? 1f : 0f;
        if (!animate || !isLaidOut()) {
            mShown = target;
            invalidate();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(mShown, target);
        animator.setDuration(SHOW_MS);
        animator.setInterpolator(Motion.STANDARD);
        animator.addUpdateListener(a -> {
            mShown = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (mShowAnimator == animation) {
                    mShowAnimator = null;
                }
            }
        });
        mShowAnimator = animator;
        animator.start();
    }

    /** Whether the played part stays as a line while the controls are hidden (portrait). */
    public boolean isLineWhenHidden() {
        return mLineWhenHidden;
    }

    /** ...and the bar is on screen to show it (not in PiP): worth a progress loop. */
    public boolean isLineShownWhenHidden() {
        return mLineWhenHidden && getVisibility() == VISIBLE;
    }

    public boolean isScrubbing() {
        return mScrubbing;
    }

    /** Chapter starts in ms (the first chapter's 0 is implied); null/empty = no chapters. */
    public void setChapterStarts(@Nullable long[] startsMs) {
        long[] starts = startsMs != null ? startsMs.clone() : new long[0];
        Arrays.sort(starts);
        int count = 0;
        for (long start : starts) {
            if (start > 0 && (count == 0 || starts[count - 1] != start)) {
                starts[count++] = start;
            }
        }
        mChapterStarts = Arrays.copyOf(starts, count);
        invalidate();
    }

    /** SponsorBlock ranges (progress fractions + a resolved color); null clears them. */
    public void setSegments(@Nullable List<SeekBarSegment> segments) {
        mSegments.clear();
        if (segments != null) {
            for (SeekBarSegment segment : segments) {
                if (segment != null) {
                    mSegments.add(segment);
                }
            }
        }
        invalidate();
    }

    // ---------------------------------------------------------------------------------
    // TimeBar
    // ---------------------------------------------------------------------------------

    @Override
    public void addListener(OnScrubListener listener) {
        mListeners.add(listener);
    }

    @Override
    public void removeListener(OnScrubListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void setKeyTimeIncrement(long time) {
        mKeyTimeIncrement = time;
        mKeyCountIncrement = C.INDEX_UNSET;
    }

    @Override
    public void setKeyCountIncrement(int count) {
        mKeyCountIncrement = count;
        mKeyTimeIncrement = C.TIME_UNSET;
    }

    @Override
    public void setPosition(long position) {
        if (mPosition != position) {
            mPosition = position;
            if (!mScrubbing && isVisibleEnough()) {
                invalidate();
            }
        }
    }

    @Override
    public void setBufferedPosition(long bufferedPosition) {
        if (mBufferedPosition != bufferedPosition) {
            mBufferedPosition = bufferedPosition;
            if (mShown > 0f) {
                invalidate();
            }
        }
    }

    @Override
    public void setDuration(long duration) {
        if (mDuration != duration) {
            mDuration = duration;
            if (mScrubbing && (duration == C.TIME_UNSET || duration <= 0)) {
                stopScrubbing(true);
            }
            invalidate();
        }
    }

    /** One pixel's worth of playback: how often the position is worth redrawing. */
    @Override
    public long getPreferredUpdateDelay() {
        float width = trackRight() - trackLeft();
        return width <= 0f || mDuration <= 0 || mDuration == C.TIME_UNSET
                ? Long.MAX_VALUE : (long) (mDuration / width);
    }

    /** Media3's marker API, for callers of the {@link TimeBar} interface: the times are chapter starts. */
    @Override
    public void setAdGroupTimesMs(@Nullable long[] adGroupTimesMs, @Nullable boolean[] playedAdGroups,
                                  int adGroupCount) {
        setChapterStarts(adGroupTimesMs != null ? Arrays.copyOf(adGroupTimesMs, adGroupCount) : null);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled && mScrubbing) {
            stopScrubbing(true);
        }
    }

    // ---------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        float left = trackLeft();
        float right = trackRight();
        if (right <= left || (mShown <= 0f && !mLineWhenHidden)) {
            return;
        }

        float track = lerp(dp(TRACK_DP), dp(TRACK_DRAGGING_DP), mDrag);
        float gap = lerp(dp(GAP_DP), dp(GAP_DRAGGING_DP), mDrag);
        float bottom = mTrackAtBottom
                ? getHeight() - getPaddingBottom()
                : getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() + track) / 2f;
        float top = bottom - track;
        boolean hasDuration = mDuration > 0 && mDuration != C.TIME_UNSET;

        float playedX = hasDuration ? xFor(mScrubbing ? mScrubPosition : mPosition, left, right) : left;
        float bufferedX = hasDuration ? Math.max(playedX, xFor(mBufferedPosition, left, right)) : left;
        int restAlpha = Math.round(255 * mShown);

        mTailMatrix.setTranslate(playedX - mTailPx, 0f);
        mPlayedPaint.getShader().setLocalMatrix(mTailMatrix);

        // The track, one piece per chapter: played, buffered, then the rest.
        int chapters = hasDuration ? mChapterStarts.length : 0;
        for (int i = 0; i <= chapters; i++) {
            float segmentLeft = i == 0 ? left : xFor(mChapterStarts[i - 1], left, right) + gap / 2f;
            float segmentRight = i == chapters ? right : xFor(mChapterStarts[i], left, right) - gap / 2f;
            if (segmentRight <= segmentLeft) {
                continue;
            }
            if (restAlpha > 0) {
                float unplayedLeft = Math.max(segmentLeft, bufferedX);
                if (unplayedLeft < segmentRight) {
                    drawRect(canvas, unplayedLeft, top, segmentRight, bottom, UNPLAYED_COLOR, restAlpha);
                }
                float bufferedLeft = Math.max(segmentLeft, playedX);
                float bufferedRight = Math.min(segmentRight, bufferedX);
                if (bufferedLeft < bufferedRight) {
                    drawRect(canvas, bufferedLeft, top, bufferedRight, bottom, BUFFERED_COLOR, restAlpha);
                }
            }
            float playedRight = Math.min(segmentRight, playedX);
            if (segmentLeft < playedRight) {
                canvas.drawRect(segmentLeft, top, playedRight, bottom, mPlayedPaint);
            }
        }

        // SponsorBlock ranges, over the track and cut by the same chapter gaps.
        if (restAlpha > 0 && hasDuration && !mSegments.isEmpty()) {
            for (SeekBarSegment segment : mSegments) {
                float start = left + (right - left) * clamp01(segment.startProgress);
                float end = left + (right - left) * clamp01(segment.endProgress);
                if (end <= start) {
                    continue;
                }
                end = Math.max(end, start + dp(MIN_SEGMENT_DP));
                for (int i = 0; i <= chapters; i++) {
                    float segmentLeft = i == 0 ? left : xFor(mChapterStarts[i - 1], left, right) + gap / 2f;
                    float segmentRight = i == chapters ? right : xFor(mChapterStarts[i], left, right) - gap / 2f;
                    float from = Math.max(start, segmentLeft);
                    float to = Math.min(end, segmentRight);
                    if (from < to) {
                        drawRect(canvas, from, top, to, bottom, segment.color, restAlpha);
                    }
                }
            }
        }

        // The dot grows in with the controls and a little more under the finger.
        float radius = lerp(dp(SCRUBBER_DP), dp(SCRUBBER_DRAGGING_DP), mDrag) / 2f * mShown;
        if (radius > 0.5f && hasDuration) {
            float restRadius = dp(SCRUBBER_DP) / 2f;
            float cx = Math.max(left + restRadius, Math.min(right - restRadius, playedX));
            mPaint.setColor(PLAYED_COLOR);
            canvas.drawCircle(cx, (top + bottom) / 2f, radius, mPaint);
        }
    }

    private void drawRect(Canvas canvas, float left, float top, float right, float bottom, int color, int alpha) {
        mPaint.setColor(color);
        if (alpha < 255) {
            mPaint.setAlpha(Color.alpha(color) * alpha / 255);
        }
        canvas.drawRect(left, top, right, bottom, mPaint);
    }

    private boolean isVisibleEnough() {
        return mShown > 0f || mLineWhenHidden;
    }

    private float trackLeft() {
        return getPaddingLeft();
    }

    private float trackRight() {
        return getWidth() - getPaddingRight();
    }

    private float xFor(long timeMs, float left, float right) {
        if (mDuration <= 0) {
            return left;
        }
        float fraction = Math.max(0f, Math.min(1f, timeMs / (float) mDuration));
        return left + (right - left) * fraction;
    }

    private long positionAt(float x) {
        float left = trackLeft();
        float right = trackRight();
        if (right <= left || mDuration <= 0) {
            return 0;
        }
        float fraction = Math.max(0f, Math.min(1f, (x - left) / (right - left)));
        return (long) (fraction * mDuration);
    }

    // ---------------------------------------------------------------------------------
    // Touch
    // ---------------------------------------------------------------------------------

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            updateGestureExclusion();
        }
    }

    /**
     * The shown bar keeps the system's edge swipes off its touch band (API 29+): with gesture
     * navigation, a drag from the dot at the start of a video - or anywhere near the screen edges -
     * opened Back instead of seeking. Android's own SeekBar excludes its thumb; this bar takes a
     * touch anywhere in the band, so the whole band is excluded - only while it is shown. The rect
     * reaches past this view (below the video's edge in portrait): the video box and the watch
     * column leave their children unclipped, and that is what lets the system keep all of it.
     */
    private void updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < 29) {
            return;
        }
        if (mShownTarget && getWidth() > 0) {
            float center = trackCenterY();
            int side = Math.round(dp(TOUCH_SIDE_DP));
            setSystemGestureExclusionRects(Collections.singletonList(new Rect(-side,
                    Math.round(center - dp(TOUCH_ABOVE_DP)), getWidth() + side,
                    Math.round(center + dp(TOUCH_BELOW_DP)))));
        } else {
            setSystemGestureExclusionRects(Collections.emptyList());
        }
    }

    /**
     * Whether a touch at ({@code x}, {@code y}), in this view's coordinates, is the bar's: the bar is
     * shown and the point is in the band around the track - up to {@link #TOUCH_ABOVE_DP} above it,
     * {@link #TOUCH_BELOW_DP} below it and {@link #TOUCH_SIDE_DP} past its ends, mostly outside
     * this view. Hidden, the bar takes nothing: a touch on the line is the video's.
     */
    public boolean isInTouchBand(float x, float y) {
        if (!isEnabled() || getVisibility() != VISIBLE || !mShownTarget || mShown < 0.5f
                || mDuration <= 0 || mDuration == C.TIME_UNSET || getWidth() <= 0) {
            return false;
        }
        float center = trackCenterY();
        float side = dp(TOUCH_SIDE_DP);
        return x >= -side && x <= getWidth() + side
                && y >= center - dp(TOUCH_ABOVE_DP) && y <= center + dp(TOUCH_BELOW_DP);
    }

    /** The resting track's middle: the band is centered on it and does not move while dragging. */
    private float trackCenterY() {
        return mTrackAtBottom
                ? getHeight() - getPaddingBottom() - dp(TRACK_DP) / 2f
                : getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return !mTouchRouted && handleTouch(event);
    }

    /** A touch the band's router claimed ({@link #setTouchRouted}), in this view's coordinates. */
    public boolean onRoutedTouchEvent(MotionEvent event) {
        return handleTouch(event);
    }

    private boolean handleTouch(MotionEvent event) {
        if (!isEnabled() || mDuration <= 0 || mDuration == C.TIME_UNSET) {
            return false;
        }
        float x = event.getX();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!mShownTarget || mShown < 0.5f) {
                    return false; // hidden: the tap belongs to the video under it
                }
                if (mScrubbing) {
                    stopScrubbing(false); // a keyboard scrub still waiting to land
                }
                startScrubbing(x);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mScrubbing) {
                    moveScrubbing(x);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mScrubbing) {
                    moveScrubbing(x); // the finger's last spot rides on the UP itself
                    // Back on the start (or never off it): nothing to seek to.
                    stopScrubbing(mSnapped || !mLeftOrigin);
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mScrubbing) {
                    stopScrubbing(true);
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    private void startScrubbing(float x) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true); // no minimize drag, no pinch
        }
        boolean leftOrigin = Math.abs(x - xFor(mPosition, trackLeft(), trackRight())) > dp(SNAP_DP);
        beginScrub(leftOrigin, leftOrigin ? positionAt(x) : mPosition);
    }

    /** A drag or an arrow key starts a scrub from where playback is; {@code leftOrigin}: already off it. */
    private void beginScrub(boolean leftOrigin, long position) {
        mScrubbing = true;
        mScrubOrigin = mPosition;
        mSnapped = false;
        mLeftOrigin = leftOrigin;
        mScrubPosition = position;
        mScrubChapter = chapterAt(position);
        mLastTickAt = 0;
        setPressed(true);
        animateDrag(1f);
        for (OnScrubListener listener : mListeners) {
            listener.onScrubStart(this, position);
        }
    }

    private void moveScrubbing(float x) {
        float originX = xFor(mScrubOrigin, trackLeft(), trackRight());
        float fromOrigin = Math.abs(x - originX);
        long position = positionAt(x);
        if (!mLeftOrigin) {
            if (fromOrigin <= dp(SNAP_DP)) {
                return; // still resting where it started
            }
            mLeftOrigin = true;
        } else if (!mSnapped && fromOrigin <= dp(SNAP_DP)) {
            setSnapped(true);
        } else if (mSnapped && fromOrigin > dp(UNSNAP_DP)) {
            setSnapped(false);
        }
        if (mSnapped) {
            position = mScrubOrigin;
        }

        int chapter = chapterAt(position);
        if (chapter != mScrubChapter) {
            mScrubChapter = chapter;
            long now = SystemClock.uptimeMillis();
            if (!mSnapped && now - mLastTickAt >= TICK_MIN_INTERVAL_MS) {
                mLastTickAt = now;
                Haptics.tick(this);
            }
        }

        if (position != mScrubPosition) {
            mScrubPosition = position;
            invalidate();
            for (OnScrubListener listener : mListeners) {
                listener.onScrubMove(this, position);
            }
        }
    }

    private void setSnapped(boolean snapped) {
        mSnapped = snapped;
        if (snapped) {
            Haptics.click(this);
        }
        if (mCancelListener != null) {
            mCancelListener.onReleaseToCancel(snapped);
        }
    }

    private void stopScrubbing(boolean canceled) {
        mKeyHandler.removeCallbacks(mStopKeyScrub);
        if (!mScrubbing) {
            return;
        }
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(false);
        }
        mScrubbing = false;
        if (mSnapped && mCancelListener != null) {
            mCancelListener.onReleaseToCancel(false);
        }
        mSnapped = false;
        setPressed(false);
        animateDrag(0f);
        long position = canceled ? mScrubOrigin : mScrubPosition;
        if (!canceled) {
            mPosition = position; // no jump back to the old spot while the seek lands
        }
        invalidate();
        for (OnScrubListener listener : mListeners) {
            listener.onScrubStop(this, position, canceled);
        }
    }

    // ---------------------------------------------------------------------------------
    // Keys (a hardware keyboard or D-pad on the focused bar, like media3's DefaultTimeBar)
    // ---------------------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isEnabled() && mShownTarget && mDuration > 0 && mDuration != C.TIME_UNSET) {
            long increment = positionIncrement();
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    increment = -increment;
                    // fall through
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (scrubByKey(increment)) {
                        mKeyHandler.removeCallbacks(mStopKeyScrub);
                        mKeyHandler.postDelayed(mStopKeyScrub, KEY_SCRUB_STOP_MS);
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (mScrubbing) {
                        stopScrubbing(false);
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /** One arrow key: starts a scrub off where playback is, or moves it on a step. */
    private boolean scrubByKey(long increment) {
        long from = mScrubbing ? mScrubPosition : mPosition;
        long target = Math.max(0, Math.min(mDuration, from + increment));
        if (target == from) {
            return false;
        }
        if (!mScrubbing) {
            beginScrub(true, target);
        }
        mScrubPosition = target;
        mScrubChapter = chapterAt(target);
        invalidate();
        for (OnScrubListener listener : mListeners) {
            listener.onScrubMove(this, target);
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mScrubbing) {
            stopScrubbing(true);
        }
        mKeyHandler.removeCallbacks(mStopKeyScrub);
    }

    private void animateDrag(float target) {
        if (mDragAnimator != null) {
            mDragAnimator.cancel();
        }
        ValueAnimator animator = ValueAnimator.ofFloat(mDrag, target);
        animator.setDuration(DRAG_MS);
        animator.setInterpolator(Motion.STANDARD);
        animator.addUpdateListener(a -> {
            mDrag = (float) a.getAnimatedValue();
            invalidate();
        });
        mDragAnimator = animator;
        animator.start();
    }

    /** Index of the chapter at {@code positionMs} among the gaps: 0 before the first start. */
    @VisibleForTesting
    int chapterAt(long positionMs) {
        return chapterAt(mChapterStarts, positionMs);
    }

    @VisibleForTesting
    static int chapterAt(long[] starts, long positionMs) {
        int index = Arrays.binarySearch(starts, positionMs);
        return index >= 0 ? index + 1 : -index - 1;
    }

    // ---------------------------------------------------------------------------------
    // Accessibility (TalkBack reads it as a seek bar and can step it)
    // ---------------------------------------------------------------------------------

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            event.getText().add(progressText());
        }
        event.setClassName(ACCESSIBILITY_CLASS_NAME);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(ACCESSIBILITY_CLASS_NAME);
        info.setContentDescription(progressText());
        if (mDuration <= 0 || !mShownTarget) {
            return;
        }
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, @Nullable Bundle args) {
        if (super.performAccessibilityAction(action, args)) {
            return true;
        }
        if (mDuration <= 0 || !mShownTarget) {
            return false; // hidden: nothing on screen to seek with
        }
        long step = positionIncrement();
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            step = -step;
        } else if (action != AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            return false;
        }
        long target = Math.max(0, Math.min(mDuration, mPosition + step));
        for (OnScrubListener listener : mListeners) {
            listener.onScrubStart(this, target);
        }
        mPosition = target;
        for (OnScrubListener listener : mListeners) {
            listener.onScrubStop(this, target, false);
        }
        invalidate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        return true;
    }

    private static final String ACCESSIBILITY_CLASS_NAME = "android.widget.SeekBar";

    private long positionIncrement() {
        if (mKeyTimeIncrement != C.TIME_UNSET && mKeyTimeIncrement > 0) {
            return mKeyTimeIncrement;
        }
        int count = mKeyCountIncrement > 0 ? mKeyCountIncrement : DEFAULT_KEY_COUNT;
        return mDuration / count;
    }

    private String progressText() {
        return formatTime(mFormatBuilder, mFormatter, mPosition);
    }

    /**
     * YouTube's clock: "4:26:52", "2:18", "0:05" (media3's Util.getStringForTime pads the minutes,
     * "02:18"). Rounded to the nearest second like media3's.
     */
    static String formatTime(StringBuilder builder, Formatter formatter, long timeMs) {
        long totalSeconds = Math.max(0, (timeMs + 500) / 1000);
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        builder.setLength(0);
        return hours > 0
                ? formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
                : formatter.format("%d:%02d", minutes, seconds).toString();
    }

    // ---------------------------------------------------------------------------------

    private float dp(float dp) {
        return dp * mDensity;
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }
}
