package com.newtube.mobile.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** NEWTUBE(shorts): a list thinned out by the Shorts filter fetches more on its own, within a cap. */
public class FilteredPageTopUpTest {
    @Test
    public void aThinnedOutListFetchesUntilItScrolls() {
        FilteredPageTopUp topUp = new FilteredPageTopUp();
        topUp.onDropped(134); // "shorts funny": 1 video kept of 135

        assertTrue(topUp.take(1, true));
        assertTrue(topUp.take(4, true));
        assertFalse("enough to scroll: the scroll listener takes over",
                topUp.take(FilteredPageTopUp.ENOUGH, true));
    }

    @Test
    public void aListOfNothingButShortsStopsAtTheCap() {
        FilteredPageTopUp topUp = new FilteredPageTopUp();
        topUp.onDropped(20);

        int pages = 0;
        while (topUp.take(0, true)) {
            pages++;
            if (pages > 100) {
                break;
            }
        }
        assertEquals(FilteredPageTopUp.MAX_PAGES, pages);
    }

    @Test
    public void theEndOfTheListIsTheEnd() {
        FilteredPageTopUp topUp = new FilteredPageTopUp();
        topUp.onDropped(3);

        assertFalse(topUp.take(2, false));
        assertEquals(0, topUp.pages());
    }

    @Test
    public void aListThatIsShortOnItsOwnIsLeftAlone() {
        FilteredPageTopUp topUp = new FilteredPageTopUp();
        topUp.onDropped(0);

        assertFalse(topUp.take(3, true));
    }

    @Test
    public void theUserAskingForMoreRefillsTheBudget() {
        FilteredPageTopUp topUp = new FilteredPageTopUp();
        topUp.onDropped(1);
        while (topUp.take(0, true)) {
            // spend it
        }
        assertFalse(topUp.take(0, true));

        topUp.onUserAction();
        assertTrue(topUp.take(0, true));
    }

    @Test
    public void aNewListForgetsTheOldOne() {
        FilteredPageTopUp topUp = new FilteredPageTopUp();
        topUp.onDropped(5);
        topUp.take(0, true);

        topUp.clear();
        assertFalse("nothing dropped from the new list yet", topUp.take(0, true));
        assertEquals(0, topUp.pages());
    }
}
