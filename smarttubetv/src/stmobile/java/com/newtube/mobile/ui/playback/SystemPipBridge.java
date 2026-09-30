package com.newtube.mobile.ui.playback;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

/**
 * Process-local route back to the one live player while Android has re-parented it into a pinned
 * PiP task. The launcher normally resumes the separate Browse task; routing from the player
 * instance itself makes Android expand that exact task and cannot create a duplicate player.
 */
public final class SystemPipBridge {
    private static final String ACTION_RESTORE_FROM_PIP =
            "com.newtube.mobile.action.RESTORE_FROM_PIP";
    private static WeakReference<MobilePlaybackActivity> sActivity = new WeakReference<>(null);

    /**
     * NEWTUBE(menu-pip): started activities of ours other than the player. Zero means none of our
     * screens is on screen (Home, another app, recents) - the player may still float in PiP.
     */
    private static int sStartedScreens;

    /**
     * NEWTUBE(menu-pip): the user opened PiP from the player menu and our screen under the player
     * is still the one on screen. Browse regaining focus then is the hand-off itself (or the user
     * closing a sheet or the notification shade), not a return from the launcher, so the restore
     * must not expand the player again. Until 1.11.0 it did, about 100 ms after the entry: the
     * menu's Picture-in-picture row popped straight back to full screen. Cleared when none of our
     * screens is on screen any more (a later launcher tap restores, as always) or the PiP ends.
     */
    private static boolean sInAppPip;

    private static WeakReference<Application> sInstalledOn = new WeakReference<>(null);

    private SystemPipBridge() {
    }

    /** Called once from the Application, before any screen starts, so the count is exact. */
    public static void install(Application app) {
        if (sInstalledOn.get() == app) {
            return;
        }
        sInstalledOn = new WeakReference<>(app);
        sStartedScreens = 0;
        sInAppPip = false;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (!(activity instanceof MobilePlaybackActivity)) {
                    sStartedScreens++;
                }
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                if (!(activity instanceof MobilePlaybackActivity)) {
                    sStartedScreens = Math.max(0, sStartedScreens - 1);
                    if (sStartedScreens == 0 && sInAppPip) {
                        sInAppPip = false;
                        com.liskovsoft.smartyoutubetv2.common.misc.NetPath.log(
                                "pip in-app end reason=app-left");
                    }
                }
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }

    static void attach(MobilePlaybackActivity activity) {
        sActivity = new WeakReference<>(activity);
    }

    static void detach(MobilePlaybackActivity activity) {
        if (sActivity.get() == activity) {
            sActivity = new WeakReference<>(null);
            sInAppPip = false;
        }
    }

    /**
     * The player menu's PiP request was accepted. {@code screenUnderPlayer}: the player shares its
     * task with a screen of ours (it is not the task root), which stays on screen under the PiP.
     */
    static void onMenuPipEntered(boolean screenUnderPlayer) {
        sInAppPip = screenUnderPlayer;
        com.liskovsoft.smartyoutubetv2.common.misc.NetPath.log(
                "pip in-app start underPlayer=" + (screenUnderPlayer ? "y" : "n"));
    }

    /** The PiP stint ended (expanded, dismissed or refused). */
    static void onPipEnded() {
        sInAppPip = false;
    }

    static boolean isInAppPip() {
        return sInAppPip;
    }

    /** Decision half of {@link #restoreFromLauncher}, split out for tests. */
    static boolean shouldRestore(boolean playerPinned, boolean inAppPip) {
        return playerPinned && !inAppPip;
    }

    /** Returns true only when a live pinned player accepted the launcher restore. */
    public static boolean restoreFromLauncher(Activity launcher) {
        MobilePlaybackActivity player = sActivity.get();
        // NEWTUBE(menu-pip): both the player's own mode callbacks and the platform must say pinned.
        // A PiP entry the system undoes at once can skip both callbacks and leave
        // isInPictureInPictureMode() true for the fullscreen player (API 35); on its own it then
        // "restored" a player that was not in PiP on every later Home focus, which threw the
        // player back to full screen right after a minimize.
        boolean pinned = player != null && !player.isFinishing() && !player.isDestroyed()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && player.isInPIPMode() && player.isInPictureInPictureMode();
        if (!shouldRestore(pinned, sInAppPip)) {
            if (pinned) {
                com.liskovsoft.smartyoutubetv2.common.misc.NetPath.log(
                        "pip-restore skipped reason=in-app-pip foregroundTask=" + launcher.getTaskId());
            }
            return false;
        }

        com.liskovsoft.smartyoutubetv2.common.misc.NetPath.log(
                "pip-restore pinnedTask=" + player.getTaskId() + " foregroundTask=" + launcher.getTaskId());
        restore(player);
        return true;
    }

    /**
     * Expand the pinned player back to full screen: a launch routed from the player instance itself
     * makes Android expand exactly its task (see the class doc).
     */
    static void restore(MobilePlaybackActivity player) {
        Intent restore = new Intent(player, MobilePlaybackActivity.class)
                .setAction(ACTION_RESTORE_FROM_PIP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        player.startActivity(restore);
    }

    /** True for our own expand request (so it is never mistaken for a new video being routed in). */
    static boolean isRestoreIntent(Intent intent) {
        return intent != null && ACTION_RESTORE_FROM_PIP.equals(intent.getAction());
    }
}
