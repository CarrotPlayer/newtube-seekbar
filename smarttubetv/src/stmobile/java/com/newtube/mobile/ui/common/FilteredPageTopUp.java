package com.newtube.mobile.ui.common;

/**
 * NEWTUBE(shorts): a paged list that {@link ShortsFilter} thinned out can end up too short to
 * scroll - "shorts funny" kept 1 video of 135 - and a list that cannot scroll never asks for its
 * next page. So after each page lands, the list asks for the next one itself while it keeps fewer
 * than {@link #ENOUGH} items, until the list ends or {@link #MAX_PAGES} such pages have been
 * fetched since the user last asked for more (a new list, a scroll to the end, a tab switch): a
 * query that returns nothing but Shorts ends honestly instead of paging forever.
 *
 * <p>Only a list that actually dropped Shorts does this: a list that is short on its own is left as
 * it always was.
 */
public final class FilteredPageTopUp {
    /** Enough kept items to scroll a phone-width grid. */
    public static final int ENOUGH = 10;
    /** Pages fetched on the list's own initiative per user action. */
    public static final int MAX_PAGES = 5;

    private int mPages;
    private boolean mDropped;

    /** A new list: nothing dropped yet, a fresh budget. */
    public void clear() {
        mPages = 0;
        mDropped = false;
    }

    /** The user asked for more (scrolled to the end, picked a tab): a fresh budget. */
    public void onUserAction() {
        mPages = 0;
    }

    /** {@code count} Shorts were just dropped from this list. */
    public void onDropped(int count) {
        if (count > 0) {
            mDropped = true;
        }
    }

    /**
     * Whether to fetch one more page now, for a list that keeps {@code kept} items and has a next
     * page when {@code hasMore}. A yes is counted against the budget.
     */
    public boolean take(int kept, boolean hasMore) {
        if (!mDropped || kept >= ENOUGH || !hasMore || mPages >= MAX_PAGES) {
            return false;
        }
        mPages++;
        return true;
    }

    /** Pages taken since the last user action. */
    public int pages() {
        return mPages;
    }
}
