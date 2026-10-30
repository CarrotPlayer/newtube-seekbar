package com.newtube.mobile.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * NEWTUBE(card-meta): a card shows Title / channel / views+date, never a bare {@code @handle}
 * twice, and the French prefixes are shortened so the last line fits.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class CardMetaTest {
    @Test
    public void channelIsThePlainAuthorName() {
        assertEquals("Chaîne", CardMeta.channel(video("Chaîne", "Chaîne • 1,2 M de vues")));
    }

    @Test
    public void statsDropTheChannelAndShortenTheFrenchPrefixes() {
        Video video = video("Chaîne", "Chaîne • 1,2 M de vues • il y a 3 semaines");

        assertEquals("1,2 M vues • 3 semaines", CardMeta.stats(video).toString());
    }

    @Test
    public void handleAliasIsDroppedAndNeverShownTwice() {
        Video video = video("Chaîne", "Chaîne • @chaîne • 1,2 M de vues • il y a 3 semaines");

        assertEquals("Chaîne", CardMeta.channel(video));
        assertEquals("1,2 M vues • 3 semaines", CardMeta.stats(video).toString());
    }

    @Test
    public void handleOnlyCardKeepsTheViewsAndDateLine() {
        Video video = video("@chaîne", "@chaîne • 1,2 M de vues • il y a 3 semaines");

        assertNull(CardMeta.channel(video));
        assertEquals("1,2 M vues • 3 semaines", CardMeta.stats(video).toString());
    }

    @Test
    public void anInfoLineOfOnlyTheChannelHasNoSecondLine() {
        assertNull(CardMeta.stats(video("Chaîne", "Chaîne")));
    }

    private static Video video(String author, String secondTitle) {
        Video video = new Video();
        video.videoId = "abc";
        video.title = "Title";
        video.author = author;
        video.secondTitle = secondTitle;
        return video;
    }
}
