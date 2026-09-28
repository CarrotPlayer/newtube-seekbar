package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

/**
 * NEWTUBE(autoplay-budget): how many unplayable videos in a row autoplay skips past.
 *
 * <p>Upstream moves to the next video 5 s after any unplayable answer - right for a lone 18+ or
 * removed video. When the related videos are unplayable too (made-for-kids videos signed out,
 * issue #5) it became a chain: a new video every ~7.5 s, each costing a full /player walk, about
 * 90 /player calls a minute until the app was closed (Pixel 9 over LTE, 2026-09-28, twice). Now
 * one unplayable video still skips once; the next unplayable one that autoplay itself reached
 * stops there, with its reason on screen. A video that plays, or any video the user opens,
 * starts over.
 */
final class UnplayableAutoplayBudget {
    /** Unplayable videos in a row that autoplay skips past; the next one ends the chain. */
    static final int MAX_SKIPS = 1;

    private int mSkips;

    /** An unplayable answer for the current video: true = advance to the next one. */
    boolean onUnplayable() {
        if (mSkips >= MAX_SKIPS) {
            return false;
        }
        mSkips++;
        return true;
    }

    /** A playable answer: the chain is broken. */
    void onPlayable() {
        mSkips = 0;
    }

    /** A video the user opened (or normal end-of-video autoplay), not a skip past an unplayable one. */
    void onUserOpen() {
        mSkips = 0;
    }

    int skips() {
        return mSkips;
    }
}
