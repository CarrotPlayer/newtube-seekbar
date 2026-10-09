package com.newtube.mobile.ui.search;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.HashMap;
import java.util.Map;

public final class SearchFilterBottomSheet {

    private SearchFilterBottomSheet() {}

    public static void show(Context context, SearchPresenter presenter) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_mobile_search_filter, null);
        dialog.setContentView(view);

        // Supprime le fond natif Material pour éviter tout conflit de superposition d'arrondis
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) {
            bottomSheetInternal.setBackground(null);
        }

        // --- SORT BY ---
        TextView pillSortRel = view.findViewById(R.id.pill_sort_relevance);
        TextView pillSortPop = view.findViewById(R.id.pill_sort_popularity);
        Map<Integer, TextView> sortGroup = new HashMap<>();
        sortGroup.put(0, pillSortRel);
        sortGroup.put(SearchOptions.SORT_BY_VIEW_COUNT, pillSortPop);

        int currentSort = presenter.getSortingOptions();
        updateExclusiveGroup(sortGroup, currentSort == SearchOptions.SORT_BY_VIEW_COUNT ? SearchOptions.SORT_BY_VIEW_COUNT : 0);

        pillSortRel.setOnClickListener(v -> {
            presenter.setSortingOptions(0);
            updateExclusiveGroup(sortGroup, 0);
            presenter.reloadSearch();
        });
        pillSortPop.setOnClickListener(v -> {
            presenter.setSortingOptions(SearchOptions.SORT_BY_VIEW_COUNT);
            updateExclusiveGroup(sortGroup, SearchOptions.SORT_BY_VIEW_COUNT);
            presenter.reloadSearch();
        });

        // --- UPLOAD DATE ---
        TextView pillDateToday = view.findViewById(R.id.pill_date_today);
        TextView pillDateWeek = view.findViewById(R.id.pill_date_week);
        TextView pillDateMonth = view.findViewById(R.id.pill_date_month);
        TextView pillDateYear = view.findViewById(R.id.pill_date_year);
        Map<Integer, TextView> dateGroup = new HashMap<>();
        dateGroup.put(SearchOptions.UPLOAD_DATE_TODAY, pillDateToday);
        dateGroup.put(SearchOptions.UPLOAD_DATE_THIS_WEEK, pillDateWeek);
        dateGroup.put(SearchOptions.UPLOAD_DATE_THIS_MONTH, pillDateMonth);
        dateGroup.put(SearchOptions.UPLOAD_DATE_THIS_YEAR, pillDateYear);

        updateExclusiveGroup(dateGroup, presenter.getUploadDateOptions());

        setupUncheckablePill(pillDateToday, SearchOptions.UPLOAD_DATE_TODAY, dateGroup, presenter, 1);
        setupUncheckablePill(pillDateWeek, SearchOptions.UPLOAD_DATE_THIS_WEEK, dateGroup, presenter, 1);
        setupUncheckablePill(pillDateMonth, SearchOptions.UPLOAD_DATE_THIS_MONTH, dateGroup, presenter, 1);
        setupUncheckablePill(pillDateYear, SearchOptions.UPLOAD_DATE_THIS_YEAR, dateGroup, presenter, 1);

        // --- DURATION ---
        TextView pillDurUnder4 = view.findViewById(R.id.pill_duration_under4);
        TextView pillDur4to20 = view.findViewById(R.id.pill_duration_4to20);
        TextView pillDurOver20 = view.findViewById(R.id.pill_duration_over20);
        Map<Integer, TextView> durGroup = new HashMap<>();
        durGroup.put(SearchOptions.DURATION_UNDER_4, pillDurUnder4);
        durGroup.put(SearchOptions.DURATION_BETWEEN_4_20, pillDur4to20);
        durGroup.put(SearchOptions.DURATION_OVER_20, pillDurOver20);

        updateExclusiveGroup(durGroup, presenter.getDurationOptions());

        setupUncheckablePill(pillDurUnder4, SearchOptions.DURATION_UNDER_4, durGroup, presenter, 2);
        setupUncheckablePill(pillDur4to20, SearchOptions.DURATION_BETWEEN_4_20, durGroup, presenter, 2);
        setupUncheckablePill(pillDurOver20, SearchOptions.DURATION_OVER_20, durGroup, presenter, 2);

        // --- TYPE ---
        TextView pillTypeVid = view.findViewById(R.id.pill_type_videos);
        TextView pillTypeChan = view.findViewById(R.id.pill_type_channels);
        TextView pillTypePlay = view.findViewById(R.id.pill_type_playlists);
        TextView pillTypeMov = view.findViewById(R.id.pill_type_movies);
        Map<Integer, TextView> typeGroup = new HashMap<>();
        typeGroup.put(SearchOptions.TYPE_VIDEO, pillTypeVid);
        typeGroup.put(SearchOptions.TYPE_CHANNEL, pillTypeChan);
        typeGroup.put(SearchOptions.TYPE_PLAYLIST, pillTypePlay);
        typeGroup.put(SearchOptions.TYPE_MOVIE, pillTypeMov);

        updateExclusiveGroup(typeGroup, presenter.getTypeOptions());

        setupUncheckablePill(pillTypeVid, SearchOptions.TYPE_VIDEO, typeGroup, presenter, 3);
        setupUncheckablePill(pillTypeChan, SearchOptions.TYPE_CHANNEL, typeGroup, presenter, 3);
        setupUncheckablePill(pillTypePlay, SearchOptions.TYPE_PLAYLIST, typeGroup, presenter, 3);
        setupUncheckablePill(pillTypeMov, SearchOptions.TYPE_MOVIE, typeGroup, presenter, 3);

        // --- FEATURES ---
        TextView pillFeatLive = view.findViewById(R.id.pill_feat_live);
        TextView pillFeat4k = view.findViewById(R.id.pill_feat_4k);
        TextView pillFeatHdr = view.findViewById(R.id.pill_feat_hdr);

        setupToggleFeature(pillFeatLive, SearchOptions.FEATURE_LIVE, presenter);
        setupToggleFeature(pillFeat4k, SearchOptions.FEATURE_4K, presenter);
        setupToggleFeature(pillFeatHdr, SearchOptions.FEATURE_HDR, presenter);

        // --- RESET BUTTON ---
        view.findViewById(R.id.filter_btn_reset).setOnClickListener(v -> {
            presenter.setSortingOptions(0);
            presenter.setUploadDateOptions(0);
            presenter.setDurationOptions(0);
            presenter.setTypeOptions(0);
            presenter.setFeatureOptions(0);

            updateExclusiveGroup(sortGroup, 0);
            updateExclusiveGroup(dateGroup, 0);
            updateExclusiveGroup(durGroup, 0);
            updateExclusiveGroup(typeGroup, 0);

            setPillState(pillFeatLive, false);
            setPillState(pillFeat4k, false);
            setPillState(pillFeatHdr, false);

            presenter.reloadSearch();
        });

        dialog.show();
    }

    private static void setupUncheckablePill(TextView pill, int value, Map<Integer, TextView> group, SearchPresenter presenter, int category) {
        pill.setOnClickListener(v -> {
            int current = getCurrentVal(presenter, category);
            int nextVal = (current == value) ? 0 : value;

            setCurrentVal(presenter, category, nextVal);
            updateExclusiveGroup(group, nextVal);
            presenter.reloadSearch();
        });
    }

    private static int getCurrentVal(SearchPresenter presenter, int category) {
        switch (category) {
            case 1: return presenter.getUploadDateOptions();
            case 2: return presenter.getDurationOptions();
            case 3: return presenter.getTypeOptions();
            default: return 0;
        }
    }

    private static void setCurrentVal(SearchPresenter presenter, int category, int val) {
        switch (category) {
            case 1: presenter.setUploadDateOptions(val); break;
            case 2: presenter.setDurationOptions(val); break;
            case 3: presenter.setTypeOptions(val); break;
        }
    }

    private static void setupToggleFeature(TextView pill, int mask, SearchPresenter presenter) {
        boolean active = (presenter.getFeatureOptions() & mask) != 0;
        setPillState(pill, active);

        pill.setOnClickListener(v -> {
            int current = presenter.getFeatureOptions();
            int next = (current & mask) != 0 ? (current & ~mask) : (current | mask);
            presenter.setFeatureOptions(next);
            setPillState(pill, (next & mask) != 0);
            presenter.reloadSearch();
        });
    }

    private static void updateExclusiveGroup(Map<Integer, TextView> group, int activeVal) {
        for (Map.Entry<Integer, TextView> entry : group.entrySet()) {
            setPillState(entry.getValue(), entry.getKey() == activeVal);
        }
    }

    private static void setPillState(TextView pill, boolean active) {
        pill.setSelected(active);
        pill.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
    }
}
