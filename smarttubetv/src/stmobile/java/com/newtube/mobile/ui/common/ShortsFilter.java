package com.newtube.mobile.ui.common;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.ArrayList;
import java.util.List;

/**
 * NEWTUBE(shorts): NewTube has no Shorts. Every list the phone shows - the feeds, search results,
 * channel pages and their tabs, playlists, Up next - drops them, always; there is no setting (the
 * shared "Hide content" Shorts rows and the Shorts section are not shown on the phone, and nothing
 * here reads their prefs). A Short opened from a shared youtube.com/shorts/ link still plays in the
 * normal player: links are not lists.
 *
 * <p>A Short is what the item itself says it is ({@link Video#isShorts}: a reel renderer, the Shorts
 * badge or tile style, or a lockup whose tap opens the reel player - see MediaServiceCore's
 * ItemWrapper.isShorts), never a guess from its duration - a guess would also hide ordinary videos.
 */
public final class ShortsFilter {
    private ShortsFilter() {
    }

    public static boolean isShort(Video video) {
        return video != null && video.isShorts;
    }

    /** {@code videos} without its Shorts, in order (the same list when it has none). */
    public static List<Video> withoutShorts(List<Video> videos) {
        if (videos == null || !containsShort(videos)) {
            return videos;
        }
        List<Video> result = new ArrayList<>(videos.size());
        for (Video video : videos) {
            if (!isShort(video)) {
                result.add(video);
            }
        }
        return result;
    }

    private static boolean containsShort(List<Video> videos) {
        for (Video video : videos) {
            if (isShort(video)) {
                return true;
            }
        }
        return false;
    }
}
