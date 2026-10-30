package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * NEWTUBE(gestures): the switches for the player's optional swipes (issue #12), all on by default:
 * up and down on the left of the fullscreen video sets the brightness, up and down on the right
 * sets the volume, and sideways anywhere on the video seeks. Brightness and volume are separate
 * switches (they used to share one). The fullscreen swipes (up into it, down out of it) and swipe
 * down to minimize are YouTube's own and always on.
 *
 * <p>A private preferences file, like the mobile flavor's other one-off stores (CastPrefs,
 * ThemeMode). The player reads the switches when a swipe starts, so a change applies to the next
 * swipe without a restart.</p>
 */
public final class PlayerGesturePrefs {

    private static final String PREFS_NAME = "newtube_gestures";
    private static final String KEY_BRIGHTNESS_SWIPE = "brightness_swipe";
    private static final String KEY_VOLUME_SWIPE = "volume_swipe";
    private static final String KEY_SEEK_SWIPE = "seek_swipe";
    /** The pre-1.18 switch that turned brightness and volume together on or off. */
    private static final String KEY_LEVEL_SWIPES_LEGACY = "level_swipes";

    private PlayerGesturePrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Swipe up and down on the left of the fullscreen video to set the brightness. */
    public static boolean isBrightnessSwipeOn(Context context) {
        return readLevelSwitch(context, KEY_BRIGHTNESS_SWIPE);
    }

    public static void setBrightnessSwipeOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_BRIGHTNESS_SWIPE, on).apply();
    }

    /** Swipe up and down on the right of the fullscreen video to set the volume. */
    public static boolean isVolumeSwipeOn(Context context) {
        return readLevelSwitch(context, KEY_VOLUME_SWIPE);
    }

    public static void setVolumeSwipeOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_VOLUME_SWIPE, on).apply();
    }

    /** Swipe sideways on the video to seek. */
    public static boolean isSeekSwipeOn(Context context) {
        return prefs(context).getBoolean(KEY_SEEK_SWIPE, true);
    }

    public static void setSeekSwipeOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_SEEK_SWIPE, on).apply();
    }

    /**
     * Before the two switches existed, one switch turned both side swipes off. A profile that had
     * it off keeps both new switches off until one of them is touched; after that the old value is
     * ignored for good.
     */
    private static boolean readLevelSwitch(Context context, String key) {
        SharedPreferences prefs = prefs(context);
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, true);
        }
        return prefs.getBoolean(KEY_LEVEL_SWIPES_LEGACY, true);
    }
}
