package com.newtube.mobile.ui.common;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * NEWTUBE(haptics): the app's haptic vocabulary, taken from what the YouTube app plays (read from
 * the vibrator service's history while using YouTube 21.18): a light tick when the seek bar crosses
 * a chapter boundary, a click when it snaps back to where the drag started ("Release to cancel")
 * and on a like, and a firm buzz when press-and-hold turns on 2x speed. Taps, double-tap seeks,
 * swipes and play/pause stay silent there, and here too: haptics mark a boundary, a snap or a
 * confirmed action, never an ordinary touch.
 *
 * <p>Everything goes through {@link View#performHapticFeedback}, which needs no permission and
 * follows the system's touch-feedback setting, so a phone with vibration off stays silent.</p>
 */
public final class Haptics {

    private Haptics() {
    }

    /** A boundary crossed while dragging (a chapter on the seek bar, a zoom snap). */
    public static void tick(@Nullable View view) {
        if (view != null) {
            // SEGMENT_TICK is the platform's name for exactly this (API 34); before it,
            // CONTEXT_CLICK is the constant that plays the same light TICK effect.
            view.performHapticFeedback(Build.VERSION.SDK_INT >= 34
                    ? HapticFeedbackConstants.SEGMENT_TICK : HapticFeedbackConstants.CONTEXT_CLICK);
        }
    }

    /** A snap into a resting place, or an action taking effect (like, dislike). */
    public static void click(@Nullable View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    /** Press-and-hold switching a mode on. */
    public static void longPress(@Nullable View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }
}
