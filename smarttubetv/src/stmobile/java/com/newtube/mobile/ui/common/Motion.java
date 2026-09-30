package com.newtube.mobile.ui.common;

import android.graphics.Path;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * NEWTUBE(motion): the app's motion tokens - Material 3 easing curves and the few durations the
 * phone UI shares, so every screen moves the same way. Things that arrive decelerate (EMPHASIZED_
 * DECELERATE), things that leave accelerate (EMPHASIZED_ACCELERATE), things that move from one
 * resting place to another use EMPHASIZED, and small fades use STANDARD. Nothing here is slower than
 * what it replaced: the point of the curves is that the first frames move a lot, so a transition
 * reads as fast even at 250-300 ms.
 */
public final class Motion {
    /** Material 3 emphasized: the two-segment path, not the (0.2, 0, 0, 1) approximation. */
    public static final Interpolator EMPHASIZED = new PathInterpolator(emphasizedPath());
    public static final Interpolator EMPHASIZED_DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    public static final Interpolator EMPHASIZED_ACCELERATE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    public static final Interpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);
    public static final Interpolator STANDARD_DECELERATE = new PathInterpolator(0f, 0f, 0f, 1f);
    public static final Interpolator STANDARD_ACCELERATE = new PathInterpolator(0.3f, 0f, 1f, 1f);

    /** Content that swaps in place (text, counts, a list replacing its skeleton). */
    public static final long FADE_IN_MS = 150;
    /** Content leaving before other content takes its place (the first half of a fade-through). */
    public static final long FADE_OUT_MS = 90;
    /** A surface arriving (a sheet sliding up). */
    public static final long ENTER_MS = 250;
    /** A surface leaving (a sheet sliding down). */
    public static final long EXIT_MS = 200;

    private Motion() {
    }

    private static Path emphasizedPath() {
        Path path = new Path();
        path.moveTo(0f, 0f);
        path.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f);
        return path;
    }
}
