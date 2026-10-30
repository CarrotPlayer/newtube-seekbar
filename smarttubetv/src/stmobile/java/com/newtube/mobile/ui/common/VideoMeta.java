package com.newtube.mobile.ui.common;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * NEWTUBE(cards): the phone's two info lines under a video title, on Home, in search results and
 * in "Next up".
 *
 * <p>The service hands the whole info line over as one string, "channel • views • date" (plus an
 * optional leading badge and, on search results, the channel's {@code @handle}); drawn on one line
 * it ran off the card and cut the date. So the pieces are split here once, into:</p>
 *
 * <ul>
 *   <li><b>the channel</b> - the author token, never a {@code @handle}: a handle duplicates the
 *       name above it, so any token starting with {@code @} is dropped (and the line hides when
 *       nothing is left, which lets the stats line move up instead of leaving a hole);</li>
 *   <li><b>the stats</b> - the remaining views/date tokens, shortened: the "de"/"of" of
 *       "1,2 M de vues" and the "il y a"/"ago" of a relative date add nothing a two-line card
 *       cannot say without them.</li>
 * </ul>
 */
public final class VideoMeta {

    /** The service's divider between the items of the info line (ServiceHelper.ITEMS_DIVIDER). */
    private static final String DELIM = Video.TERTIARY_TEXT_DELIM;

    /** "1,2 M de vues" / "1.2M of views": the preposition belongs to no one. */
    private static final Pattern VIEWS_PREPOSITION = Pattern.compile(
            "(?iu)[\\s\\u00a0\\u202f]+(?:de|di|of|d'|da)[\\s\\u00a0\\u202f]+"
                    + "(?=(?:vues?|views?|vista[s]?|visualizzazioni|visualiza\\u00e7\\u00f5es"
                    + "|visualizaciones|reproducciones|reprod\\.?|aufrufe|weergaven|\\u043f\\u0440\\u043e\\u0441\\u043c\\u043e\\u0442\\p{L}*)\\b)");

    /** "il y a 3 semaines" / "hace 3 semanas": the relative-date lead-in. */
    private static final Pattern RELATIVE_PREFIX = Pattern.compile(
            "(?iu)^(?:il y a|hace|vor|h\\u00e1|faz|przed|\\u043d\\u0430\\u0437\\u0430\\u0434)\\s+");

    /** "3 weeks ago" / "3 settimane fa": the same idea, trailing. */
    private static final Pattern RELATIVE_SUFFIX = Pattern.compile(
            "(?iu)[\\s\\u00a0\\u202f]+(?:ago|fa|geleden|sedan|temu|\\u00f6nce"
                    + "|\\u043d\\u0430\\u0437\\u0430\\u0434)$");

    /** Directional marks ServiceHelper's bidi wrap leaves around every item. */
    private static final Pattern BIDI = Pattern.compile("[\\u061c\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]");

    private static final Pattern DIGIT = Pattern.compile("\\d");

    private VideoMeta() {
    }

    /** The two lines: {@link #channel} may be null (hide the line), {@link #stats} may be empty. */
    public static final class Info {
        @Nullable public final String channel;
        public final CharSequence stats;

        private Info(@Nullable String channel, CharSequence stats) {
            this.channel = channel;
            this.stats = stats;
        }
    }

    private static final Info EMPTY = new Info(null, "");

    /** NEWTUBE(cards): channel line + "views • date" line for {@code video}. Never null. */
    public static Info from(@Nullable Video video) {
        if (video == null) {
            return EMPTY;
        }

        List<String> parts = split(video.getSecondTitle());
        String channel = clean(video.getAuthor());
        if (channel.isEmpty() || isHandle(channel)) {
            channel = null;
        }

        // Everything up to and including the channel token (a leading badge, the author, a
        // @handle) belongs to the line above: the stats start after it.
        int channelIndex = channel != null ? indexOf(parts, channel) : -1;
        if (channelIndex < 0 && channel == null) {
            channelIndex = firstChannelIndex(parts);
            channel = channelIndex >= 0 ? parts.get(channelIndex) : null;
        }
        List<String> stats = channelIndex >= 0 ? parts.subList(channelIndex + 1, parts.size()) : parts;

        StringBuilder line = new StringBuilder();
        for (String part : stats) {
            if (isHandle(part)) {
                continue; // never show a @handle
            }
            CharSequence text = shorten(part);
            if (text.length() == 0) {
                continue;
            }
            if (line.length() > 0) {
                line.append(MetaSeparator.DOT);
            }
            line.append(text);
        }

        return channel == null && line.length() == 0 ? EMPTY : new Info(channel, line);
    }

    /** The service's info string as clean tokens (bidi marks and empty items dropped). */
    private static List<String> split(@Nullable CharSequence info) {
        List<String> parts = new ArrayList<>();

        if (info == null) {
            return parts;
        }

        for (String part : BIDI.matcher(info).replaceAll("").split(DELIM)) {
            String clean = clean(part);
            if (!clean.isEmpty()) {
                parts.add(clean);
            }
        }

        return parts;
    }

    /** The first token before the last one that is not a stat: the channel's display name. */
    private static int firstChannelIndex(List<String> parts) {
        for (int i = 0; i < parts.size() - 1; i++) {
            if (!looksLikeStat(parts.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** A views count or a date carries a number; a channel name usually does not. */
    private static boolean looksLikeStat(String part) {
        return DIGIT.matcher(part).find();
    }

    private static int indexOf(List<String> parts, String value) {
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).equals(value)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isHandle(String text) {
        return text != null && text.startsWith("@");
    }

    private static String clean(@Nullable CharSequence text) {
        return text == null ? "" : BIDI.matcher(text).replaceAll("").trim();
    }

    /** "1,2 M de vues" → "1,2 M vues", "il y a 3 semaines" → "3 semaines". */
    private static CharSequence shorten(String text) {
        String result = VIEWS_PREPOSITION.matcher(text).replaceAll(" ");
        result = RELATIVE_PREFIX.matcher(result).replaceAll("");
        result = RELATIVE_SUFFIX.matcher(result).replaceAll("");
        return result.trim();
    }
}
