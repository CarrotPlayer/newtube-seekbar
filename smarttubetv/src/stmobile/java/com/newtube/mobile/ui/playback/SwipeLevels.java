package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.liskovsoft.smartyoutubetv2.common.misc.NetPath;
import com.liskovsoft.smartyoutubetv2.tv.BuildConfig;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.Haptics;

/**
 * NEWTUBE(gestures): what an up/down swipe on the side of the fullscreen video sets - the
 * brightness on the left, the volume on the right (issue #12) - and the pill that shows it.
 */
final class SwipeLevels {
    static final int BRIGHTNESS = 1;
    static final int VOLUME = 2;

    private static final float RANGE_OF_HEIGHT = 0.75f;
    static final long PILL_LINGER_MS = 700;
    private static final long BRIGHTNESS_MEMORY_MS = 4 * 60 * 60 * 1000L;

    interface Pill {
        void showLevel(@DrawableRes int iconRes, float level);

        void hideLevel();
    }

    private static float sBrightness = -1f;
    private static long sBrightnessAt;

    private final Window mWindow;
    private final View mHapticView;
    private final Pill mPill;
    @Nullable
    private final AudioManager mAudio;
    private final Runnable mHidePill;

    private int mKind;
    private float mStartLevel;
    private float mRangePx = 1f;
    private float mLevel;
    private int mVolumeStep;
    private boolean mSafeVolumeAsked;
    private boolean mBrightnessActive;

    SwipeLevels(Context context, Window window, View hapticView, Pill pill) {
        mWindow = window;
        mHapticView = hapticView;
        mPill = pill;
        mAudio = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        mHidePill = mPill::hideLevel;
    }

    static float rangePxFor(float videoHeightPx) {
        return Math.max(1f, videoHeightPx * RANGE_OF_HEIGHT);
    }

    boolean canSwipe(int kind) {
        Context context = mWindow.getContext();
        if (kind == VOLUME) {
            return PlayerGesturePrefs.isVolumeSwipeOn(context)
                    && mAudio != null && !mAudio.isVolumeFixed() && maxVolume() > minVolume();
        }
        if (kind == BRIGHTNESS) {
            return PlayerGesturePrefs.isBrightnessSwipeOn(context);
        }
        return false;
    }

    void begin(int kind, float videoHeightPx) {
        mKind = kind;
        mRangePx = rangePxFor(videoHeightPx);
        mHapticView.removeCallbacks(mHidePill);
        if (kind == VOLUME) {
            int min = minVolume();
            mVolumeStep = currentVolume();
            mSafeVolumeAsked = false;
            mStartLevel = (mVolumeStep - min) / (float) Math.max(1, maxVolume() - min);
        } else {
            mStartLevel = currentBrightness();
        }
        mLevel = mStartLevel;
        showPill();
    }

    void move(float upPx) {
        if (mKind == 0) {
            return;
        }
        float level = clamp(mStartLevel + upPx / mRangePx);
        if (mKind == VOLUME) {
            setVolumeLevel(level);
        } else {
            setBrightnessLevel(level);
        }
        showPill();
    }

    void end() {
        if (mKind == 0) {
            return;
        }
        if (BuildConfig.DEBUG) {
            NetPath.log("gesture level " + (mKind == VOLUME ? "volume step=" + mVolumeStep
                    : "brightness=" + (mLevel <= 0f ? "auto" : Math.round(mLevel * 100) + "%")));
        }
        mKind = 0;
        mHapticView.removeCallbacks(mHidePill);
        mHapticView.postDelayed(mHidePill, PILL_LINGER_MS);
    }

    void cancel() {
        mKind = 0;
        mHapticView.removeCallbacks(mHidePill);
        mPill.hideLevel();
    }

    void setBrightnessActive(boolean active) {
        mBrightnessActive = active;
        float level = rememberedBrightness();
        if (active && level > 0f) {
            applyWindowBrightness(Math.max(0.01f, gammaToLinear(level)));
        } else {
            applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
        }
    }

    // ---------------------------------------------------------------------------------
    // Volume
    // ---------------------------------------------------------------------------------

    private void setVolumeLevel(float level) {
        int min = minVolume();
        int max = maxVolume();
        int step = min + Math.round(level * (max - min));
        if (step != mVolumeStep) {
            try {
                mAudio.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0);
                int now = currentVolume();
                if (now < step && step > mVolumeStep && !mSafeVolumeAsked) {
                    mSafeVolumeAsked = true;
                    mAudio.setStreamVolume(AudioManager.STREAM_MUSIC, step, AudioManager.FLAG_SHOW_UI);
                    now = currentVolume();
                }
                if (now == mVolumeStep) {
                    return;
                }
                mVolumeStep = now;
            } catch (SecurityException e) {
                return;
            }
            Haptics.tick(mHapticView);
        }
        mLevel = (mVolumeStep - min) / (float) Math.max(1, max - min);
    }

    private int currentVolume() {
        return mAudio != null ? mAudio.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
    }

    private int maxVolume() {
        return mAudio != null ? mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 0;
    }

    private int minVolume() {
        return mAudio != null && Build.VERSION.SDK_INT >= 28
                ? mAudio.getStreamMinVolume(AudioManager.STREAM_MUSIC) : 0;
    }

    // ---------------------------------------------------------------------------------
    // Brightness
    // ---------------------------------------------------------------------------------

    private void setBrightnessLevel(float level) {
        boolean bookend = (level <= 0f || level >= 1f) && level != mLevel;
        mLevel = level;
        sBrightness = level;
        sBrightnessAt = SystemClock.elapsedRealtime();
        if (mBrightnessActive) {
            if (level <= 0f) {
                applyWindowBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
            } else {
                applyWindowBrightness(Math.max(0.01f, gammaToLinear(level)));
            }
        }
        if (bookend) {
            Haptics.tick(mHapticView);
        }
    }

    private float currentBrightness() {
        float remembered = rememberedBrightness();
        if (remembered >= 0f) {
            return remembered;
        }
        float window = mWindow.getAttributes().screenBrightness;
        if (window >= 0f) {
            return linearToGamma(window);
        }
        int setting = Settings.System.getInt(mWindow.getContext().getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 128);
        return linearToGamma(setting / 255f);
    }

    private static float rememberedBrightness() {
        if (sBrightness < 0f || SystemClock.elapsedRealtime() - sBrightnessAt > BRIGHTNESS_MEMORY_MS) {
            return -1f;
        }
        return sBrightness;
    }

    private void applyWindowBrightness(float value) {
        WindowManager.LayoutParams params = mWindow.getAttributes();
        if (params.screenBrightness != value) {
            params.screenBrightness = value;
            mWindow.setAttributes(params);
        }
    }

    private static final float HLG_R = 0.5f;
    private static final float HLG_A = 0.17883277f;
    private static final float HLG_B = 0.28466892f;
    private static final float HLG_C = 0.55991073f;

    @VisibleForTesting
    static float gammaToLinear(float position) {
        float p = clamp(position);
        float ret = p <= HLG_R ? (p / HLG_R) * (p / HLG_R) : (float) Math.exp((p - HLG_C) / HLG_A) + HLG_B;
        return clamp(ret / 12f);
    }

    @VisibleForTesting
    static float linearToGamma(float linear) {
        float v = clamp(linear) * 12f;
        float ret = v <= 1f ? (float) Math.sqrt(v) * HLG_R : HLG_A * (float) Math.log(v - HLG_B) + HLG_C;
        return clamp(ret);
    }

    // ---------------------------------------------------------------------------------
    // Pill
    // ---------------------------------------------------------------------------------

    private void showPill() {
        mPill.showLevel(iconFor(mKind, mLevel), mLevel);
    }

    @VisibleForTesting
    @DrawableRes
    static int iconFor(int kind, float level) {
        if (kind == VOLUME) {
            if (level <= 0f) {
                return R.drawable.ic_player_volume_off;
            }
            return level < 0.5f ? R.drawable.ic_player_volume_down
                    : R.drawable.ic_player_volume_up;
        }
        if (level <= 0f) {
            return R.drawable.ic_player_brightness_auto;
        }
        if (level < 1f / 3f) {
            return R.drawable.ic_player_brightness_low;
        }
        return level < 2f / 3f ? R.drawable.ic_player_brightness_medium
                : R.drawable.ic_player_brightness_high;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
