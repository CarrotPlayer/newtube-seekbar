package com.newtube.mobile.ui.search;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * NEWTUBE(search-filters): YouTube's "Search filter" sheet for the phone search screen, in the
 * same window as the Download drawer (see sheet_mobile_search_filters).
 *
 * <p>The categories, and nothing else, are {@code SearchPresenter}'s own search options, which its
 * search call already knows how to turn into InnerTube params - so a pill here really filters the
 * results, it is not a cosmetic chip:</p>
 *
 * <ul>
 *   <li>Sort by: Relevance / Popularity (view count)</li>
 *   <li>Upload date: Today / This week / This month / This year</li>
 *   <li>Duration: Under 4 mins / 4-20 mins / Over 20 mins</li>
 *   <li>Type: Videos / Channels / Playlists / Movies</li>
 *   <li>Features: Live / 4K / HDR (the only multi-select row)</li>
 * </ul>
 *
 * <p>Each category owns one horizontal line: the pills NEVER wrap onto a second line, and the
 * scroller's padding is inside its drawing area (clipToPadding=false), so a scrolled pill runs to
 * the real screen edge instead of being clipped at the sheet's margin.</p>
 *
 * <p>Tapping a pill applies it at once and closes the sheet, like YouTube's and like the Download
 * drawer; Reset clears every filter and searches again.</p>
 */
final class SearchFilterSheet {

    interface Listener {
        /** The user's new {@link SearchOptions} bit set. */
        void onSearchOptions(int options);
    }

    private static final int MARGIN_DP = 20;
    private static final int GAP_DP = 8;
    private static final int PILL_HEIGHT_DP = 40;

    private static final int[] SORT_MASK = {
            SearchOptions.SORT_BY_UPLOAD_DATE, SearchOptions.SORT_BY_VIEW_COUNT,
            SearchOptions.SORT_BY_RATING};
    private static final int[] DATE_MASK = {
            SearchOptions.UPLOAD_DATE_LAST_HOUR, SearchOptions.UPLOAD_DATE_TODAY,
            SearchOptions.UPLOAD_DATE_THIS_WEEK, SearchOptions.UPLOAD_DATE_THIS_MONTH,
            SearchOptions.UPLOAD_DATE_THIS_YEAR};
    private static final int[] DURATION_MASK = {
            SearchOptions.DURATION_UNDER_4, SearchOptions.DURATION_BETWEEN_4_20,
            SearchOptions.DURATION_OVER_20};
    private static final int[] TYPE_MASK = {
            SearchOptions.TYPE_VIDEO, SearchOptions.TYPE_CHANNEL, SearchOptions.TYPE_PLAYLIST,
            SearchOptions.TYPE_MOVIE};
    private static final int[] FEATURE_MASK = {
            SearchOptions.FEATURE_LIVE, SearchOptions.FEATURE_4K, SearchOptions.FEATURE_HDR};

    /** One filter category: its title, its pills and how their selection combines. */
    private static final class Section {
        final int titleRes;
        final boolean multiSelect;
        final int mask;
        final int[][] choices; // {labelRes, value}

        Section(int titleRes, boolean multiSelect, int[] bits, int[][] choices) {
            this.titleRes = titleRes;
            this.multiSelect = multiSelect;
            this.mask = or(bits);
            this.choices = choices;
        }

        boolean isSelected(int options, int value) {
            return multiSelect ? (options & value) == value : (options & mask) == value;
        }

        int select(int options, int value) {
            if (multiSelect) {
                // Features: tapping a selected pill turns it off, the three combine freely.
                return (options & value) == value ? options & ~value : options | value;
            }
            // One value per category: clear the group, then set the picked bit (0 = the default
            // "Relevance"/"Any", which owns no bit).
            return (options & ~mask) | value;
        }
    }

    /** The snapshot of the screenshot: same categories, same pills, same order. */
    private static final Section[] SECTIONS = {
            new Section(R.string.mobile_search_filter_sort_by, false, SORT_MASK, new int[][] {
                    {R.string.mobile_search_filter_relevance, 0},
                    {R.string.mobile_search_filter_popularity, SearchOptions.SORT_BY_VIEW_COUNT}}),
            new Section(R.string.mobile_search_filter_upload_date, false, DATE_MASK, new int[][] {
                    {R.string.mobile_search_filter_today, SearchOptions.UPLOAD_DATE_TODAY},
                    {R.string.mobile_search_filter_this_week, SearchOptions.UPLOAD_DATE_THIS_WEEK},
                    {R.string.mobile_search_filter_this_month, SearchOptions.UPLOAD_DATE_THIS_MONTH},
                    {R.string.mobile_search_filter_this_year, SearchOptions.UPLOAD_DATE_THIS_YEAR}}),
            new Section(R.string.mobile_search_filter_duration, false, DURATION_MASK, new int[][] {
                    {R.string.mobile_search_filter_under_4, SearchOptions.DURATION_UNDER_4},
                    {R.string.mobile_search_filter_between_4_20, SearchOptions.DURATION_BETWEEN_4_20},
                    {R.string.mobile_search_filter_over_20, SearchOptions.DURATION_OVER_20}}),
            new Section(R.string.mobile_search_filter_type, false, TYPE_MASK, new int[][] {
                    {R.string.mobile_search_filter_videos, SearchOptions.TYPE_VIDEO},
                    {R.string.mobile_search_filter_channels, SearchOptions.TYPE_CHANNEL},
                    {R.string.mobile_search_filter_playlists, SearchOptions.TYPE_PLAYLIST},
                    {R.string.mobile_search_filter_movies, SearchOptions.TYPE_MOVIE}}),
            new Section(R.string.mobile_search_filter_features, true, FEATURE_MASK, new int[][] {
                    {R.string.mobile_search_filter_live, SearchOptions.FEATURE_LIVE},
                    {R.string.mobile_search_filter_4k, SearchOptions.FEATURE_4K},
                    {R.string.mobile_search_filter_hdr, SearchOptions.FEATURE_HDR}}),
    };

    private final Context mContext;
    private final BottomSheetDialog mDialog;
    private final Listener mListener;
    private int mOptions;

    private SearchFilterSheet(Context context, int options, Listener listener) {
        mContext = context;
        mOptions = options;
        mListener = listener;
        mDialog = new BottomSheetDialog(context);
    }

    /** Opens the sheet with the current selection; {@code listener} gets every change. */
    static void show(Context context, int options, Listener listener) {
        new SearchFilterSheet(context, options, listener).show();
    }

    private void show() {
        View content = LayoutInflater.from(mContext)
                .inflate(R.layout.sheet_mobile_search_filters, null);
        mDialog.setContentView(content);

        LinearLayout list = content.findViewById(R.id.mobile_search_filter_content);
        for (Section section : SECTIONS) {
            addSection(list, section);
        }

        content.findViewById(R.id.mobile_search_filter_reset).setOnClickListener(v -> apply(0));

        mDialog.show();
    }

    private void addSection(LinearLayout list, Section section) {
        LinearLayout.LayoutParams headerParams =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = dp(18);
        headerParams.setMarginStart(dp(MARGIN_DP));
        headerParams.setMarginEnd(dp(MARGIN_DP));

        TextView header = new TextView(mContext);
        header.setLayoutParams(headerParams);
        header.setText(section.titleRes);
        header.setTextColor(ContextCompat.getColor(mContext, R.color.mobile_color_on_surface));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        list.addView(header);

        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);

        for (int[] choice : section.choices) {
            row.addView(createPill(section, choice[0], choice[1]));
        }

        HorizontalScrollView scroller = new HorizontalScrollView(mContext);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // The side margin belongs to the scroller, not to a wrapper: with clipToPadding=false the
        // row may draw INTO it while scrolled, so only the display's own edge cuts a pill.
        scroller.setClipToPadding(false);
        scroller.setPadding(dp(MARGIN_DP), 0, dp(MARGIN_DP), 0);
        scroller.addView(row, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollerParams.topMargin = dp(10);
        scroller.setLayoutParams(scrollerParams);

        list.addView(scroller);
    }

    private TextView createPill(Section section, int labelRes, int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(PILL_HEIGHT_DP));
        params.setMarginEnd(dp(GAP_DP));

        TextView pill = new TextView(mContext);
        pill.setLayoutParams(params);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(16), 0, dp(16), 0);
        pill.setSingleLine(true); // a pill is one line, always - the ROW is what scrolls
        pill.setText(labelRes);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        pill.setTextColor(ContextCompat.getColorStateList(mContext,
                R.color.mobile_filter_pill_text));
        pill.setBackgroundResource(R.drawable.bg_mobile_filter_pill);
        pill.setSelected(section.isSelected(mOptions, value));
        pill.setClickable(true);
        pill.setFocusable(true);
        pill.setForeground(selectableItemBackground());
        pill.setOnClickListener(v -> apply(section.select(mOptions, value)));
        return pill;
    }

    private void apply(int options) {
        mDialog.dismiss();
        if (options == mOptions) {
            return; // nothing picked (or Reset on an already clean search): no reload
        }
        mOptions = options;
        mListener.onSearchOptions(options);
    }

    private android.graphics.drawable.Drawable selectableItemBackground() {
        TypedValue value = new TypedValue();
        if (mContext.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                && value.resourceId != 0) {
            return ContextCompat.getDrawable(mContext, value.resourceId);
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(value * mContext.getResources().getDisplayMetrics().density);
    }

    private static int or(int[] bits) {
        int mask = 0;
        for (int bit : bits) {
            mask |= bit;
        }
        return mask;
    }
}
