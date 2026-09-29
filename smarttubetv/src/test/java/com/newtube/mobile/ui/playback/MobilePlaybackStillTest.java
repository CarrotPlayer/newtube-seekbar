package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class MobilePlaybackStillTest {
    @Test
    public void readyStreamRemovesItsStillWithoutAdvancingTimeOrRunningAnAnimation() {
        ImageView still = new ImageView(RuntimeEnvironment.getApplication());
        still.setImageDrawable(new ColorDrawable(Color.BLACK));
        still.setVisibility(View.VISIBLE);
        still.setAlpha(0.8f);

        assertTrue(MobilePlaybackActivity.hideLoadingStillImmediately(still));

        // No looper/clock advance: a scheduled fade would leave this VISIBLE until its end action.
        assertEquals(View.GONE, still.getVisibility());
        assertEquals(1f, still.getAlpha(), 0f);
        assertNull(still.getDrawable());
    }

    @Test
    public void repeatedTextureFramesDoNotReportAnotherReveal() {
        ImageView still = new ImageView(RuntimeEnvironment.getApplication());
        assertTrue(MobilePlaybackActivity.hideLoadingStillImmediately(still));
        assertFalse(MobilePlaybackActivity.hideLoadingStillImmediately(still));
    }

    /** NEWTUBE(still-lift): READY may lift the still only once this open's frame reached the texture. */
    @Test
    public void readyLiftNeedsThisOpensFirstFrameOnTheTexture() {
        // The still started waiting at 3 000.
        assertFalse(MobilePlaybackActivity.firstFrameOnTexture(0, 3_000, 5_000)); // no frame of this open
        assertFalse(MobilePlaybackActivity.firstFrameOnTexture(4_000, 3_000, 3_990)); // texture stale
        // The old stream's open (its first frame long before the new selection) re-reached READY.
        assertFalse(MobilePlaybackActivity.firstFrameOnTexture(1_000, 3_000, 4_070));
        assertTrue(MobilePlaybackActivity.firstFrameOnTexture(4_000, 3_000, 4_000));
        assertTrue(MobilePlaybackActivity.firstFrameOnTexture(4_000, 3_000, 4_070));
    }

    /**
     * NEWTUBE(still-lift): the FRAME lift (v21 default) - before READY, at this open's first frame
     * on the texture - only for a new selection's still that waits for READY, never in background
     * audio, and with the same stale-frame marker as the READY lift.
     */
    @Test
    public void frameLiftNeedsAWaitingNewSelectionAndThisOpensFrame() {
        assertTrue(frame(true, true, false, 4_000, 4_010, NEW_BUFFER));
        assertFalse("not waiting for READY (already lifted, or a hand-off still)",
                frame(false, true, false, 4_000, 4_010, NEW_BUFFER));
        assertFalse("a mini-player hand-off, not a new selection",
                frame(true, false, false, 4_000, 4_010, NEW_BUFFER));
        assertFalse("background audio", frame(true, true, true, 4_000, 4_010, NEW_BUFFER));
        assertFalse("the previous video's frame", frame(true, true, false, 1_000, 4_010, NEW_BUFFER));
        assertFalse("not on the texture yet", frame(true, true, false, 4_000, 3_990, NEW_BUFFER));
        assertFalse("no first frame of this open", frame(true, true, false, 0, 4_010, NEW_BUFFER));
    }

    /**
     * The Codex review's case: the texture latched a buffer the previous stream queued, after this
     * open's first frame was released. The callback time passes; the buffer's own timestamp (from
     * before this open's fence) does not. A missing timestamp or fence never lifts at the frame.
     */
    @Test
    public void frameLiftNeedsTheLatchedBufferToBeThisOpens() {
        assertFalse("an old buffer", frame(true, true, false, 4_000, 4_010, FENCE - 5_000_000L));
        assertFalse("an old frame released early, for just after the fence",
                frame(true, true, false, 4_000, 4_010, FENCE + 40_000_000L));
        assertTrue(frame(true, true, false, 4_000, 4_010,
                FENCE + MobilePlaybackActivity.FRAME_LIFT_MARGIN_NANOS));
        assertFalse("no timestamp", frame(true, true, false, 4_000, 4_010, 0));
        assertFalse("no fence yet", MobilePlaybackActivity.canLiftStillAtFrame(true, true, false,
                4_000, 3_000, 4_010, NEW_BUFFER, 0));
    }

    private static final long FENCE = 900_000_000_000L;
    private static final long NEW_BUFFER = FENCE + 300_000_000L;

    private static boolean frame(boolean awaitingReady, boolean newVideoStill, boolean backgroundAudio,
            long firstFrameAtMs, long lastTextureFrameAtMs, long latchedBufferNanos) {
        return MobilePlaybackActivity.canLiftStillAtFrame(awaitingReady, newVideoStill, backgroundAudio,
                firstFrameAtMs, 3_000, lastTextureFrameAtMs, latchedBufferNanos, FENCE);
    }

    @Test
    public void absentOrAlreadyHiddenStillHasNoVisibilityMilestone() {
        assertFalse(MobilePlaybackActivity.hideLoadingStillImmediately(null));
        ImageView still = new ImageView(RuntimeEnvironment.getApplication());
        still.setVisibility(View.INVISIBLE);
        assertFalse(MobilePlaybackActivity.hideLoadingStillImmediately(still));
        assertEquals(View.INVISIBLE, still.getVisibility());
    }
}
