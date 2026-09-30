package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.media3.ui.TimeBar;
import org.robolectric.RuntimeEnvironment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

/**
 * NEWTUBE(seek bar): the seek bar's touch rules - a drag seeks where it lets go; back on where
 * playback was, it snaps there and letting go cancels ("Release to cancel"); a touch that never
 * leaves the dot cancels too; the hidden bar leaves touches to the video. Density 1 here, so dp = px:
 * a 1000 px track over a 100 s video is 100 ms per px.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlayerTimeBarTest {
    private PlayerTimeBar mBar;
    private final List<String> mEvents = new ArrayList<>();
    private final List<Boolean> mCancelArmed = new ArrayList<>();
    private long mDownTime;

    @Before
    public void setUp() {
        mBar = new PlayerTimeBar(RuntimeEnvironment.getApplication());
        mBar.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(18, View.MeasureSpec.EXACTLY));
        mBar.layout(0, 0, 1000, 18);
        mBar.setDuration(100_000);
        mBar.setPosition(50_000); // the dot at x = 500
        mBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                mEvents.add("start " + position);
            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
                mEvents.add("move " + position);
            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                mEvents.add((canceled ? "cancel " : "stop ") + position);
            }
        });
        mBar.setCancelListener(mCancelArmed::add);
    }

    @Test
    public void aDragSeeksWhereItLetsGo() {
        assertTrue(touch(MotionEvent.ACTION_DOWN, 800));
        touch(MotionEvent.ACTION_MOVE, 900);
        touch(MotionEvent.ACTION_UP, 900);

        assertEquals("start 80000", mEvents.get(0));
        assertEquals("move 90000", mEvents.get(1));
        assertEquals("stop 90000", last());
        assertTrue(mCancelArmed.isEmpty());
    }

    @Test
    public void draggingBackOntoPlaybackSnapsAndLettingGoCancels() {
        touch(MotionEvent.ACTION_DOWN, 800);
        touch(MotionEvent.ACTION_MOVE, 600);
        touch(MotionEvent.ACTION_MOVE, 506); // within 10 dp of the dot: snaps onto 50 s

        assertEquals("move 50000", last());
        assertEquals(java.util.Arrays.asList(true), mCancelArmed);

        touch(MotionEvent.ACTION_UP, 506);
        assertEquals("cancel 50000", last());
        assertEquals(java.util.Arrays.asList(true, false), mCancelArmed);
    }

    @Test
    public void leavingTheSnapAgainSeeksNormally() {
        touch(MotionEvent.ACTION_DOWN, 800);
        touch(MotionEvent.ACTION_MOVE, 505);
        touch(MotionEvent.ACTION_MOVE, 512); // inside the 14 dp release margin: still snapped
        assertEquals(java.util.Arrays.asList(true), mCancelArmed);

        touch(MotionEvent.ACTION_MOVE, 300);
        touch(MotionEvent.ACTION_UP, 300);
        assertEquals(java.util.Arrays.asList(true, false), mCancelArmed);
        assertEquals("stop 30000", last());
    }

    @Test
    public void aTouchThatNeverLeavesTheDotSeeksNothing() {
        touch(MotionEvent.ACTION_DOWN, 503);
        touch(MotionEvent.ACTION_MOVE, 507);
        touch(MotionEvent.ACTION_UP, 507);

        assertEquals("start 50000", mEvents.get(0));
        assertEquals("cancel 50000", last());
        assertTrue(mCancelArmed.isEmpty());
    }

    @Test
    public void aTakenTouchCancels() {
        touch(MotionEvent.ACTION_DOWN, 800);
        touch(MotionEvent.ACTION_CANCEL, 800);
        assertEquals("cancel 50000", last());
    }

    @Test
    public void theHiddenBarLeavesTouchesToTheVideo() {
        mBar.setShown(false, false);
        assertFalse(touch(MotionEvent.ACTION_DOWN, 800));
        assertTrue(mEvents.isEmpty());
    }

    @Test
    public void hidingTheControlsMidDragCancelsIt() {
        touch(MotionEvent.ACTION_DOWN, 800);
        mBar.setShown(false, false);
        assertEquals("cancel 50000", last());
    }

    @Test
    public void letGoIsWhereTheFingerLifts() {
        touch(MotionEvent.ACTION_DOWN, 800);
        touch(MotionEvent.ACTION_MOVE, 850);
        touch(MotionEvent.ACTION_UP, 900);
        assertEquals("stop 90000", last());
    }

    @Test
    public void theTouchBandReachesPastTheThinView() {
        // The 2 px track lies on the 18 px view's bottom edge, its middle at y = 17: the band runs
        // 20 above it, 16 below it (over the page, in portrait) and 12 past its ends.
        assertTrue(mBar.isInTouchBand(500, 17 - 20));
        assertFalse(mBar.isInTouchBand(500, 17 - 21));
        assertTrue(mBar.isInTouchBand(500, 17 + 16));
        assertFalse(mBar.isInTouchBand(500, 17 + 17));
        assertTrue(mBar.isInTouchBand(-12, 17));
        assertFalse(mBar.isInTouchBand(-13, 17));
        assertTrue(mBar.isInTouchBand(1012, 17));
        assertFalse(mBar.isInTouchBand(1013, 17));
    }

    @Test
    public void theBandFollowsACenteredTrack() {
        mBar.setTrackAtBottom(false); // fullscreen: the track in the middle, y = 9
        assertTrue(mBar.isInTouchBand(500, 9 - 20));
        assertFalse(mBar.isInTouchBand(500, 9 - 21));
        assertTrue(mBar.isInTouchBand(500, 9 + 16));
        assertFalse(mBar.isInTouchBand(500, 9 + 17));
    }

    @Test
    public void theHiddenBarHasNoBand() {
        mBar.setShown(false, false);
        assertFalse(mBar.isInTouchBand(500, 17));
    }

    @Test
    public void aRoutedBarTakesTouchesOnlyFromItsRouter() {
        mBar.setTouchRouted(true);
        assertFalse(touch(MotionEvent.ACTION_DOWN, 800)); // on the view itself: left to the views under it
        assertTrue(mEvents.isEmpty());

        assertTrue(routed(MotionEvent.ACTION_DOWN, 800, 30)); // under the view, in the band
        routed(MotionEvent.ACTION_MOVE, 900, 30);
        routed(MotionEvent.ACTION_UP, 900, 30);
        assertEquals("start 80000", mEvents.get(0));
        assertEquals("stop 90000", last());
    }

    @Test
    public void chapterIndexCountsTheStartsPassed() {
        long[] starts = {10_000, 20_000, 35_000};
        assertEquals(0, PlayerTimeBar.chapterAt(starts, 0));
        assertEquals(0, PlayerTimeBar.chapterAt(starts, 9_999));
        assertEquals(1, PlayerTimeBar.chapterAt(starts, 10_000));
        assertEquals(2, PlayerTimeBar.chapterAt(starts, 34_999));
        assertEquals(3, PlayerTimeBar.chapterAt(starts, 90_000));
        assertEquals(0, PlayerTimeBar.chapterAt(new long[0], 5_000));
    }

    @Test
    public void chapterStartsAreSortedAndTheImplicitZeroDropped() {
        mBar.setChapterStarts(new long[] {35_000, 0, 10_000, 10_000});
        assertEquals(0, mBar.chapterAt(5_000));
        assertEquals(1, mBar.chapterAt(20_000));
        assertEquals(2, mBar.chapterAt(40_000));
    }

    @Test
    public void theClockReadsLikeYouTubes() {
        StringBuilder builder = new StringBuilder();
        Formatter formatter = new Formatter(builder, Locale.US);
        assertEquals("0:00", PlayerTimeBar.formatTime(builder, formatter, 0));
        assertEquals("0:05", PlayerTimeBar.formatTime(builder, formatter, 5_000));
        assertEquals("2:18", PlayerTimeBar.formatTime(builder, formatter, 138_000));
        assertEquals("59:59", PlayerTimeBar.formatTime(builder, formatter, 3_599_000));
        assertEquals("4:26:52", PlayerTimeBar.formatTime(builder, formatter, 16_012_000));
        assertEquals("0:00", PlayerTimeBar.formatTime(builder, formatter, -1_000));
    }

    private boolean touch(int action, float x) {
        MotionEvent event = event(action, x, 9);
        try {
            return mBar.onTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private boolean routed(int action, float x, float y) {
        MotionEvent event = event(action, x, y);
        try {
            return mBar.onRoutedTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private MotionEvent event(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            mDownTime = now;
        }
        return MotionEvent.obtain(mDownTime, now, action, x, y, 0);
    }

    private String last() {
        return mEvents.get(mEvents.size() - 1);
    }
}
