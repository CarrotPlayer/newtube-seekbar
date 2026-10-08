package com.newtube.mobile.ui.search;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

public final class SearchFilterBottomSheet {

    private SearchFilterBottomSheet() {}

    public static void show(Context context, SearchPresenter presenter) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_mobile_search_filter, null);
        dialog.setContentView(view);

        // Rendre le conteneur Material transparent pour conserver nos coins arrondis
        View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetInternal != null) {
            bottomSheetInternal.setBackgroundColor(Color.TRANSPARENT);
        }

        // Copies locales des états actuels
        final int[] uploadDate = {presenter.getUploadDateOptions()};
        final int[] duration = {presenter.getDurationOptions()};
        final int[] type = {presenter.getTypeOptions()};
        final int[] sorting = {presenter.getSortingOptions()};
        final int[] feature = {presenter.getFeatureOptions()};

        TextView valType = view.findViewById(R.id.filter_val_type);
        TextView valDuration = view.findViewById(R.id.filter_val_duration);
        TextView valDate = view.findViewById(R.id.filter_val_date);
        TextView valSort = view.findViewById(R.id.filter_val_sort);

        TextView chipLive = view.findViewById(R.id.chip_feature_live);
        TextView chip4k = view.findViewById(R.id.chip_feature_4k);
        TextView chipHdr = view.findViewById(R.id.chip_feature_hdr);

        // --- Données des menus déroulants ---

        // Type
        String[] typeLabels = {"Toutes les catégories", "Vidéos", "Chaînes", "Playlists", "Films"};
        int[] typeValues = {0, SearchOptions.TYPE_VIDEO, SearchOptions.TYPE_CHANNEL, SearchOptions.TYPE_PLAYLIST, SearchOptions.TYPE_MOVIE};
        updateLabel(valType, typeLabels, typeValues, type[0]);
        view.findViewById(R.id.filter_row_type).setOnClickListener(v ->
                showSingleChoiceDialog(context, "Type", typeLabels, typeValues, type[0], chosen -> {
                    type[0] = chosen;
                    updateLabel(valType, typeLabels, typeValues, chosen);
                }));

        // Durée
        String[] durationLabels = {"Toutes", "Moins de 4 minutes", "De 4 à 20 minutes", "Plus de 20 minutes"};
        int[] durationValues = {0, SearchOptions.DURATION_UNDER_4, SearchOptions.DURATION_BETWEEN_4_20, SearchOptions.DURATION_OVER_20};
        updateLabel(valDuration, durationLabels, durationValues, duration[0]);
        view.findViewById(R.id.filter_row_duration).setOnClickListener(v ->
                showSingleChoiceDialog(context, "Durée", durationLabels, durationValues, duration[0], chosen -> {
                    duration[0] = chosen;
                    updateLabel(valDuration, durationLabels, durationValues, chosen);
                }));

        // Date d'ajout
        String[] dateLabels = {"Date indifférente", "Aujourd'hui", "Cette semaine", "Ce mois-ci", "Cette année"};
        int[] dateValues = {0, SearchOptions.UPLOAD_DATE_TODAY, SearchOptions.UPLOAD_DATE_THIS_WEEK, SearchOptions.UPLOAD_DATE_THIS_MONTH, SearchOptions.UPLOAD_DATE_THIS_YEAR};
        updateLabel(valDate, dateLabels, dateValues, uploadDate[0]);
        view.findViewById(R.id.filter_row_date).setOnClickListener(v ->
                showSingleChoiceDialog(context, "Date d'ajout", dateLabels, dateValues, uploadDate[0], chosen -> {
                    uploadDate[0] = chosen;
                    updateLabel(valDate, dateLabels, dateValues, chosen);
                }));

        // Priorité / Tri
        String[] sortLabels = {"Pertinence", "Popularité", "Date d'ajout", "Note"};
        int[] sortValues = {0, SearchOptions.SORT_BY_VIEW_COUNT, SearchOptions.SORT_BY_UPLOAD_DATE, SearchOptions.SORT_BY_RATING};
        updateLabel(valSort, sortLabels, sortValues, sorting[0]);
        view.findViewById(R.id.filter_row_sort).setOnClickListener(v ->
                showSingleChoiceDialog(context, "Priorité", sortLabels, sortValues, sorting[0], chosen -> {
                    sorting[0] = chosen;
                    updateLabel(valSort, sortLabels, sortValues, chosen);
                }));

        // --- Puces Caractéristiques ---
        setupChip(chipLive, (feature[0] & SearchOptions.FEATURE_LIVE) != 0, selected -> {
            feature[0] = selected ? (feature[0] | SearchOptions.FEATURE_LIVE) : (feature[0] & ~SearchOptions.FEATURE_LIVE);
        });
        setupChip(chip4k, (feature[0] & SearchOptions.FEATURE_4K) != 0, selected -> {
            feature[0] = selected ? (feature[0] | SearchOptions.FEATURE_4K) : (feature[0] & ~SearchOptions.FEATURE_4K);
        });
        setupChip(chipHdr, (feature[0] & SearchOptions.FEATURE_HDR) != 0, selected -> {
            feature[0] = selected ? (feature[0] | SearchOptions.FEATURE_HDR) : (feature[0] & ~SearchOptions.FEATURE_HDR);
        });

        // --- Validation ---
        view.findViewById(R.id.filter_btn_apply).setOnClickListener(v -> {
            presenter.setTypeOptions(type[0]);
            presenter.setDurationOptions(duration[0]);
            presenter.setUploadDateOptions(uploadDate[0]);
            presenter.setSortingOptions(sorting[0]);
            presenter.setFeatureOptions(feature[0]);

            dialog.dismiss();
            presenter.reloadSearch();
        });

        dialog.show();
    }

    private static void setupChip(TextView chip, boolean active, ChipToggleListener listener) {
        chip.setSelected(active);
        chip.setTextColor(active ? Color.WHITE : Color.parseColor("#3EA6FF"));
        chip.setOnClickListener(v -> {
            boolean newState = !chip.isSelected();
            chip.setSelected(newState);
            chip.setTextColor(newState ? Color.WHITE : Color.parseColor("#3EA6FF"));
            listener.onToggle(newState);
        });
    }

    private static void updateLabel(TextView target, String[] labels, int[] values, int currentValue) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentValue) {
                target.setText(labels[i]);
                return;
            }
        }
        target.setText(labels[0]);
    }

    private static void showSingleChoiceDialog(Context context, String title, String[] labels, int[] values, int currentValue, OnSelectedListener listener) {
        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentValue) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    listener.onSelected(values[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private interface OnSelectedListener {
        void onSelected(int value);
    }

    private interface ChipToggleListener {
        void onToggle(boolean selected);
    }
}
