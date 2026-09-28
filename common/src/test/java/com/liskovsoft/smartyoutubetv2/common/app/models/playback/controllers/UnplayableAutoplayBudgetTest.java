package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UnplayableAutoplayBudgetTest {
    @Test
    public void aLoneUnplayableVideoStillSkipsOnce() {
        UnplayableAutoplayBudget budget = new UnplayableAutoplayBudget();
        assertTrue(budget.onUnplayable());
        budget.onPlayable();
        assertTrue("a video played in between: a new streak", budget.onUnplayable());
    }

    @Test
    public void aSecondUnplayableVideoInARowEndsTheChain() {
        UnplayableAutoplayBudget budget = new UnplayableAutoplayBudget();
        assertTrue(budget.onUnplayable());   // the video the user opened
        assertFalse(budget.onUnplayable());  // the one autoplay moved to: stop here
        assertFalse(budget.onUnplayable());  // and it stays stopped until something plays
        assertEquals(UnplayableAutoplayBudget.MAX_SKIPS, budget.skips());
    }

    @Test
    public void aVideoTheUserOpensStartsOver() {
        UnplayableAutoplayBudget budget = new UnplayableAutoplayBudget();
        assertTrue(budget.onUnplayable());
        assertFalse(budget.onUnplayable());
        budget.onUserOpen();
        assertTrue(budget.onUnplayable());
    }
}
