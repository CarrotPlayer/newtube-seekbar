package com.newtube.mobile.ui.search;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * NEWTUBE(search-filters): YouTube's search filter sheet, opened from the search bar's filter
 * button. It drives the same {@link SearchOptions} bits as the TV search dialog (SearchPresenter),
 * and every change reruns the current query immediately, exactly like YouTube: the sheet stays up
 * while the results behind it update.
 *
 * <p>Sort by, Upload date, Duration and Type are single-choice. Tapping the selected chip again
 * clears the group (0 = any), except Sort by where Relevance is the always-present default.
 * Features is multi-choice and ORs Live / 4K / HDR. Reset clears everything and reruns once.</p>
 */
final class SearchFilterSheet {

    private final SearchPresenter mPresenter;
    private final BottomSheetDialog mDialog;

    private final ChipGroup mSortGroup;
    private final ChipGroup mDateGroup;
    private final ChipGroup mDurationGroup;
    private final ChipGroup mTypeGroup;
    private final ChipGroup mFeatureGroup;
    private final Chip mFeatureLive;
    private final Chip mFeature4k;
    private final Chip mFeatureHdr;
    private final TextView mReset;

    /** Set while the initial values are checked, so those checks do not start a search. */
    private boolean mBinding;

    private SearchFilterSheet(Activity activity, SearchPresenter presenter) {
        mPresenter = presenter;

        mDialog = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity)
                .inflate(R.layout.sheet_mobile_search_filters, null);
        mDialog.setContentView(content);

        mSortGroup = content.findViewById(R.id.search_filter_sort_group);
        mDateGroup = content.findViewById(R.id.search_filter_date_group);
        mDurationGroup = content.findViewById(R.id.search_filter_duration_group);
        mTypeGroup = content.findViewById(R.id.search_filter_type_group);
        mFeatureGroup = content.findViewById(R.id.search_filter_feature_group);
        mFeatureLive = content.findViewById(R.id.search_filter_feature_live);
        mFeature4k = content.findViewById(R.id.search_filter_feature_4k);
        mFeatureHdr = content.findViewById(R.id.search_filter_feature_hdr);
        mReset = content.findViewById(R.id.search_filter_reset);
    }

    static void show(Activity activity, SearchPresenter presenter) {
        if (activity == null || presenter == null || activity.isFinishing()) {
            return;
        }
        new SearchFilterSheet(activity, presenter).show(activity);
    }

    private void show(Activity activity) {
        bindInitialState();

        mSortGroup.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
        mDateGroup.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
        mDurationGroup.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
        mTypeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
        mFeatureGroup.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
        mReset.setOnClickListener(v -> reset());

        // The layout wraps its content, like the Download picker's sheet, so the sheet is exactly
        // as tall as the five groups. Expanded still matters: Material's default peek would open a
        // taller-than-peek sheet half-collapsed, and skipCollapsed keeps a stray drag from parking
        // it there. When the groups outgrow the screen, the weighted NestedScrollView scrolls.
        BottomSheetBehavior<?> behavior = mDialog.getBehavior();
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        // Held sideways the groups are taller than the phone: stop short of the top edge, as the
        // player's sheets do, so the search bar (and a scrim to tap) stays in sight.
        int windowHeight = activity.getWindow().getDecorView().getHeight();
        int topGap = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        if (windowHeight > topGap) {
            behavior.setMaxHeight(windowHeight - topGap);
        }
        mDialog.setOnShowListener(dialog -> {
            BottomSheetBehavior<?> shown = mDialog.getBehavior();
            shown.setSkipCollapsed(true);
            shown.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        mDialog.show();
    }

    /** Checks the chips that match the presenter's current options (no listener is attached yet). */
    private void bindInitialState() {
        mBinding = true;

        mSortGroup.check(mPresenter.getSortingOptions() == SearchOptions.SORT_BY_VIEW_COUNT
                ? R.id.search_filter_sort_popularity : R.id.search_filter_sort_relevance);

        checkSingle(mDateGroup, mPresenter.getUploadDateOptions(),
                new int[] {R.id.search_filter_date_today, R.id.search_filter_date_week,
                        R.id.search_filter_date_month, R.id.search_filter_date_year},
                new int[] {SearchOptions.UPLOAD_DATE_TODAY, SearchOptions.UPLOAD_DATE_THIS_WEEK,
                        SearchOptions.UPLOAD_DATE_THIS_MONTH, SearchOptions.UPLOAD_DATE_THIS_YEAR});

        checkSingle(mDurationGroup, mPresenter.getDurationOptions(),
                new int[] {R.id.search_filter_duration_under4, R.id.search_filter_duration_4to20,
                        R.id.search_filter_duration_over20},
                new int[] {SearchOptions.DURATION_UNDER_4, SearchOptions.DURATION_BETWEEN_4_20,
                        SearchOptions.DURATION_OVER_20});

        checkSingle(mTypeGroup, mPresenter.getTypeOptions(),
                new int[] {R.id.search_filter_type_videos, R.id.search_filter_type_channels,
                        R.id.search_filter_type_playlists, R.id.search_filter_type_movies},
                new int[] {SearchOptions.TYPE_VIDEO, SearchOptions.TYPE_CHANNEL,
                        SearchOptions.TYPE_PLAYLIST, SearchOptions.TYPE_MOVIE});

        int features = mPresenter.getFeatureOptions();
        mFeatureLive.setChecked((features & SearchOptions.FEATURE_LIVE) != 0);
        mFeature4k.setChecked((features & SearchOptions.FEATURE_4K) != 0);
        mFeatureHdr.setChecked((features & SearchOptions.FEATURE_HDR) != 0);

        mBinding = false;
    }

    /** A single-choice group whose stored value is 0 when nothing is selected. */
    private static void checkSingle(ChipGroup group, int value, int[] chipIds, int[] values) {
        group.clearCheck();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                group.check(chipIds[i]);
                return;
            }
        }
    }

    private void applyFilters() {
        if (mBinding) {
            return;
        }
        mPresenter.applySearchFilters(
                valueOf(mDateGroup,
                        new int[] {R.id.search_filter_date_today, R.id.search_filter_date_week,
                                R.id.search_filter_date_month, R.id.search_filter_date_year},
                        new int[] {SearchOptions.UPLOAD_DATE_TODAY, SearchOptions.UPLOAD_DATE_THIS_WEEK,
                                SearchOptions.UPLOAD_DATE_THIS_MONTH, SearchOptions.UPLOAD_DATE_THIS_YEAR}),
                valueOf(mDurationGroup,
                        new int[] {R.id.search_filter_duration_under4, R.id.search_filter_duration_4to20,
                                R.id.search_filter_duration_over20},
                        new int[] {SearchOptions.DURATION_UNDER_4, SearchOptions.DURATION_BETWEEN_4_20,
                                SearchOptions.DURATION_OVER_20}),
                valueOf(mTypeGroup,
                        new int[] {R.id.search_filter_type_videos, R.id.search_filter_type_channels,
                                R.id.search_filter_type_playlists, R.id.search_filter_type_movies},
                        new int[] {SearchOptions.TYPE_VIDEO, SearchOptions.TYPE_CHANNEL,
                                SearchOptions.TYPE_PLAYLIST, SearchOptions.TYPE_MOVIE}),
                features(),
                mSortGroup.getCheckedChipId() == R.id.search_filter_sort_popularity
                        ? SearchOptions.SORT_BY_VIEW_COUNT : 0);
    }

    private int features() {
        int features = 0;
        if (mFeatureLive.isChecked()) {
            features |= SearchOptions.FEATURE_LIVE;
        }
        if (mFeature4k.isChecked()) {
            features |= SearchOptions.FEATURE_4K;
        }
        if (mFeatureHdr.isChecked()) {
            features |= SearchOptions.FEATURE_HDR;
        }
        return features;
    }

    /** The stored value for the checked chip of {@code group}, or 0 (any) when none is checked. */
    private static int valueOf(ChipGroup group, int[] chipIds, int[] values) {
        int checked = group.getCheckedChipId();
        for (int i = 0; i < chipIds.length; i++) {
            if (chipIds[i] == checked) {
                return values[i];
            }
        }
        return 0;
    }

    private void reset() {
        mBinding = true;
        mSortGroup.check(R.id.search_filter_sort_relevance);
        mDateGroup.clearCheck();
        mDurationGroup.clearCheck();
        mTypeGroup.clearCheck();
        mFeatureLive.setChecked(false);
        mFeature4k.setChecked(false);
        mFeatureHdr.setChecked(false);
        mBinding = false;
        mPresenter.applySearchFilters(0, 0, 0, 0, 0);
    }
}
