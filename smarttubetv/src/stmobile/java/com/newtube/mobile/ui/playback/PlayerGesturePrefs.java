package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * NEWTUBE(gestures): the switches for the player's optional swipes (issues #12, #40), each on by
 * default: up and down on the LEFT of the fullscreen video sets the brightness, on the RIGHT the
 * volume, and sideways anywhere on the video seeks. Brightness and volume are separate switches
 * since 1.18 - a phone whose automatic brightness does the job wants the volume swipe without the
 * brightness one, and the other way round. The fullscreen swipes (up into it, down out of it) and
 * swipe down to minimize are YouTube's own and always on.
 *
 * <p>A private preferences file, like the mobile flavor's other one-off stores (CastPrefs,
 * ThemeMode). The player reads the switches when a swipe starts, so a change applies to the next
 * swipe without a restart.</p>
 */
public final class PlayerGesturePrefs {

    private static final String PREFS_NAME = "newtube_gestures";
    /** 1.17's single brightness+volume switch, kept only to seed the two new ones. */
    private static final String KEY_LEVEL_SWIPES = "level_swipes";
    private static final String KEY_BRIGHTNESS_SWIPE = "brightness_swipe";
    private static final String KEY_VOLUME_SWIPE = "volume_swipe";
    private static final String KEY_SEEK_SWIPE = "seek_swipe";

    private PlayerGesturePrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Swipe up and down on the left of the fullscreen video to set the brightness. */
    public static boolean isBrightnessSwipeOn(Context context) {
        // A device that had turned the old combined switch OFF must not get it back for one of the
        // two halves: until a new switch is written, both follow the old value.
        SharedPreferences prefs = prefs(context);
        return prefs.getBoolean(KEY_BRIGHTNESS_SWIPE, prefs.getBoolean(KEY_LEVEL_SWIPES, true));
    }

    public static void setBrightnessSwipeOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_BRIGHTNESS_SWIPE, on).remove(KEY_LEVEL_SWIPES).apply();
    }

    /** Swipe up and down on the right of the fullscreen video to set the volume. */
    public static boolean isVolumeSwipeOn(Context context) {
        SharedPreferences prefs = prefs(context);
        return prefs.getBoolean(KEY_VOLUME_SWIPE, prefs.getBoolean(KEY_LEVEL_SWIPES, true));
    }

    public static void setVolumeSwipeOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_VOLUME_SWIPE, on).remove(KEY_LEVEL_SWIPES).apply();
    }

    /** Swipe sideways on the video to seek. */
    public static boolean isSeekSwipeOn(Context context) {
        return prefs(context).getBoolean(KEY_SEEK_SWIPE, true);
    }

    public static void setSeekSwipeOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_SEEK_SWIPE, on).apply();
    }
}
