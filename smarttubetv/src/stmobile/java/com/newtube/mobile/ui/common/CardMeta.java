package com.newtube.mobile.ui.common;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.regex.Pattern;

/**
 * NEWTUBE(card-meta): splits the service's single "Channel • 1.2M views • 3 weeks ago" line into
 * the two lines the phone shows under a card's title: the channel name first, then views + date.
 * Doing that in one place keeps Home, Search, channel pages and Up next identical.
 *
 * <p>An element that is only a channel handle ({@code @name}) is never shown: it duplicates the
 * plain channel name YouTube puts in the same line, and on some Home rows both used to appear.
 * The parts it leaves behind ("1.2M views", "3 weeks ago") take its place, so no line is left
 * empty.</p>
 *
 * <p>The two longest localized prefixes are shortened for a phone-sized line: "il y a 3 semaines"
 * becomes "3 semaines" and "1,2 M de vues" becomes "1,2 M vues".</p>
 */
public final class CardMeta {
    /** Bidi isolates/wrappers the service adds around each part (BidiFormatter.unicodeWrap). */
    private static final Pattern BIDI =
            Pattern.compile("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]");
    /** ServiceHelper.ITEMS_DIVIDER (" • ") and the older " · ". */
    private static final Pattern DIVIDER = Pattern.compile("\\s*[•·]\\s*");
    /** "Published on Jan 14, 2024" / "Premiered Jul 2, 2023" -> "Jan 14, 2024". */
    private static final Pattern PUBLISHED_ON =
            Pattern.compile("(?iu)^(published|premiered|streamed live)\\s+on\\s+");
    /** A relative date's leading words ("il y a 3 semaines" -> "3 semaines"). */
    private static final Pattern IL_Y_A =
            Pattern.compile("(?iu)^il[\\s\\u00A0]+y[\\s\\u00A0]+a[\\s\\u00A0]+");
    /** French view counts: "1,2 M de vues" -> "1,2 M vues". */
    private static final Pattern DE_VIEWS =
            Pattern.compile("(?iu)[\\s\\u00A0]+de[\\s\\u00A0]+(?=(?:vues?|views?)\\b)");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\u202F]+");
    /**
     * A fallback "author" actually read off the info line can be the views/date text (a card
     * without a real author field). Such a string must not become the channel line.
     */
    private static final Pattern INFO_LIKE = Pattern.compile(
            "(?iu)\\b(?:views?|vues?|visualizaciones|visualiza\u00e7\u00f5es|watching|ago|"
                    + "il y a|hace|semaines?|semana|semanas|mois|mes|meses|jours?|d[ií]as?|"
                    + "a\u00f1os?|ans?)\\b");
    /** A bare count such as "1,4 M" (a view count with the noun left out). */
    private static final Pattern BARE_COUNT = Pattern.compile("^[\\d.,\\s]+(?:[kKmMbB]|md|mille)?$");

    private CardMeta() {
    }

    /**
     * The channel line under a card's title, or null when there is nothing but a handle to show.
     */
    @Nullable
    public static String channel(@Nullable Video video) {
        if (video == null) {
            return null;
        }
        String author = video.getAuthor();
        if (TextUtils.isEmpty(author)) {
            return null;
        }
        author = clean(author);
        if (author.isEmpty() || author.startsWith("@") || author.equals(video.channelId)) {
            return null;
        }

        // Video.getAuthor() falls back to parsing the info line when the card has no author field
        // (or only a handle). That fallback can return the views/date text itself; a channel line
        // must never be "1.4M views".
        boolean derived = TextUtils.isEmpty(video.author) || clean(video.author).startsWith("@");
        if (derived && (INFO_LIKE.matcher(author).find() || BARE_COUNT.matcher(author).matches())) {
            return null;
        }
        return author;
    }

    /**
     * The views + date line under a card's title: the channel name and any {@code @handle} are
     * removed, the remaining parts are shortened. Null when nothing is left to show.
     */
    @Nullable
    public static CharSequence stats(@Nullable Video video) {
        return video == null ? null : statsLine(video, video.getSecondTitle());
    }

    /**
     * Same as {@link #stats(Video)}, for a caller that already has the service's info line (the
     * watch page prefers {@code Video.getSecondTitleFull()}).
     */
    @Nullable
    public static CharSequence statsLine(@Nullable Video video, @Nullable CharSequence infoLine) {
        if (video == null || TextUtils.isEmpty(infoLine)) {
            return null;
        }

        String channel = channel(video);
        String rawAuthor = clean(video.author != null ? video.author : null);
        String[] parts = DIVIDER.split(clean(infoLine.toString()));

        StringBuilder result = new StringBuilder();
        for (String rawPart : parts) {
            String part = rawPart.trim();
            if (part.isEmpty() || part.startsWith("@")) {
                continue;
            }
            if (channel != null && part.equalsIgnoreCase(channel)) {
                continue; // already the line above
            }
            if (!rawAuthor.isEmpty() && part.equalsIgnoreCase(rawAuthor)) {
                continue;
            }
            if (video.channelId != null && part.equals(video.channelId)) {
                continue;
            }
            part = shorten(part);
            if (part.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(MetaSeparator.DOT);
            }
            result.append(part);
        }

        return result.length() == 0 ? null : result.toString();
    }

    /** One metadata part or name: no bidi marks, no no-break spaces, single spaces. */
    private static String clean(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String result = BIDI.matcher(value).replaceAll("");
        result = result.replace('\u00A0', ' ').replace('\u202F', ' ');
        return SPACES.matcher(result).replaceAll(" ").trim();
    }

    private static String shorten(String value) {
        String result = PUBLISHED_ON.matcher(value).replaceFirst("");
        result = IL_Y_A.matcher(result).replaceFirst("");
        result = DE_VIEWS.matcher(result).replaceAll(" ");
        return SPACES.matcher(result).replaceAll(" ").trim();
    }
}
