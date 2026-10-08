package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * NEWTUBE(gestures): the switches for the player's optional swipes (issue #12), both on by default:
 * up and down on the sides of the fullscreen video set the brightness (left) and the volume
 * (right), and sideways anywhere on the video seeks.
 */
public final class PlayerGesturePrefs {

    private static final String PREFS_NAME = "newtube_gestures";
    private static final String KEY_LEVEL_SWIPES = "level_swipes";
    private static final String KEY_BRIGHTNESS_SWIPE = "brightness_swipe";
    private static final String KEY_VOLUME_SWIPE = "volume_swipe";
    private static final String KEY_SEEK_SWIPE = "seek_swipe";

    private PlayerGesturePrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Compatibility helper: true if either level gesture is enabled. */
    public static boolean isLevelSwipesOn(Context context) {
        return isBrightnessSwipeOn(context) || isVolumeSwipeOn(context);
    }

    public static void setLevelSwipesOn(Context context, boolean on) {
        setBrightnessSwipeOn(context, on);
        setVolumeSwipeOn(context, on);
    }

    /** Swipe up and down on the left (brightness) of the fullscreen video. */
    public static boolean isBrightnessSwipeOn(Context context) {
        boolean defaultVal = prefs(context).getBoolean(KEY_LEVEL_SWIPES, true);
        return prefs(context).getBoolean(KEY_BRIGHTNESS_SWIPE, defaultVal);
    }

    public static void setBrightnessSwipeOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_BRIGHTNESS_SWIPE, on).apply();
    }

    /** Swipe up and down on the right (volume) of the fullscreen video. */
    public static boolean isVolumeSwipeOn(Context context) {
        boolean defaultVal = prefs(context).getBoolean(KEY_LEVEL_SWIPES, true);
        return prefs(context).getBoolean(KEY_VOLUME_SWIPE, defaultVal);
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
}
