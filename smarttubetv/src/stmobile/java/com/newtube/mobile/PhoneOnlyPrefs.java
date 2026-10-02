package com.newtube.mobile;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * NEWTUBE(settings): values the phone pins, because the phone Settings (issue #2) have no row for
 * them and the phone reads them only to do harm or to do something other than what the TV row
 * said: the Oculus fix (landscape-locks every screen), "Ambilight"/TextureView (stops SponsorBlock
 * skipping near a segment's end), the auto-hide timeout (could only hide the controls sooner), the
 * likes counter (it only gated the dislike fetch), Channels' old look and its auto-load (they change
 * what a tap on a channel does), "Fullscreen mode" (unticked it adds a TV inset theme), and two card
 * menu items that are broken on the phone (Open comments shows a stub; Pause history never pauses).
 *
 * <p>Applied at every start and whenever the settings profile changes (with "separate settings
 * per account" each account has its own copy), writing only what differs. A one-shot migration
 * didn't hold: the prefs classes save 10 s after a change, so its marker could outlive values that
 * were never saved, and it covered only the profile that was active.</p>
 */
final class PhoneOnlyPrefs implements AppPrefs.ProfileChangeListener {
    private static final int UI_HIDE_TIMEOUT_SEC = 3;
    private static final long[] BROKEN_MENU_ITEMS = {MainUIData.MENU_ITEM_OPEN_COMMENTS, MainUIData.MENU_ITEM_TOGGLE_HISTORY};

    private final Context mContext;

    PhoneOnlyPrefs(Context context) {
        mContext = context.getApplicationContext();
    }

    /** Applies the values now and again on every profile change (keep a reference: listeners are weak). */
    void install() {
        apply();
        AppPrefs.instance(mContext).addListener(this);
    }

    @Override
    public void onProfileChanged() {
        apply();
    }

    private void apply() {
        PlayerTweaksData tweaks = PlayerTweaksData.instance(mContext);
        if (tweaks.isOculusQuestFixEnabled() && !Utils.isOculusQuest()) {
            tweaks.setOculusQuestFixEnabled(false);
        }
        if (tweaks.isTextureViewEnabled()) {
            tweaks.setTextureViewEnabled(false);
        }
        if (!tweaks.isLikesCounterEnabled()) {
            tweaks.setLikesCounterEnabled(true);
        }

        PlayerData player = PlayerData.instance(mContext);
        if (player.getUiHideTimeoutSec() != UI_HIDE_TIMEOUT_SEC) {
            player.setUiHideTimeoutSec(UI_HIDE_TIMEOUT_SEC);
        }

        MainUIData ui = MainUIData.instance(mContext);
        if (ui.isUploadsOldLookEnabled()) {
            ui.setUploadsOldLookEnabled(false);
        }
        if (!ui.isUploadsAutoLoadEnabled()) {
            ui.setUploadsAutoLoadEnabled(true);
        }
        for (long item : BROKEN_MENU_ITEMS) {
            if (ui.isMenuItemEnabled(item)) {
                ui.setMenuItemDisabled(item);
            }
        }

        GeneralData general = GeneralData.instance(mContext);
        if (!general.isFullscreenModeEnabled()) {
            general.setFullscreenModeEnabled(true);
        }
    }
}
