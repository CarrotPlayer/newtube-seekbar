package com.newtube.mobile.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileSnackbar;

import java.util.List;

/**
 * One page of the phone Settings tree: a top bar with the page's title and a list of
 * {@link SettingsRow}s from {@link SettingsPages}. The rows are built again whenever the page comes
 * back to the front (a sheet or another page may have changed a value), and their values are read
 * again after every change on the page.
 */
public final class SettingsPageFragment extends Fragment implements SettingsAdapter.Listener {
    private static final String ARG_PAGE = "page";

    private String mPageId;
    private SettingsAdapter mAdapter;
    private RecyclerView mList;
    private TextView mTitle;
    @Nullable private AlertDialog mDialog;

    public static SettingsPageFragment newInstance(@NonNull String pageId) {
        SettingsPageFragment fragment = new SettingsPageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PAGE, pageId);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    public String getPageId() {
        return mPageId;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPageId = requireArguments().getString(ARG_PAGE, SettingsPages.ROOT);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mobile_settings_page, container, false);
        mTitle = view.findViewById(R.id.settings_title);
        mList = view.findViewById(R.id.settings_list);
        view.findViewById(R.id.settings_back).setOnClickListener(
                v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        mAdapter = new SettingsAdapter(this);
        mList.setLayoutManager(new LinearLayoutManager(requireContext()));
        mList.setAdapter(mAdapter);
        // Rows are rebound in place when a value changes: no cross-fade on every tap.
        mList.setItemAnimator(null);
        // The rows go in before the first layout: the list's saved position (coming back from a
        // page opened over this one, or after a recreation) is dropped by a layout with no items.
        rebuild();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // A sheet or another page may have changed a value; same rows, so they rebind in place.
        rebuild();
    }

    @Override
    public void onDestroyView() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        super.onDestroyView();
    }

    /** Builds the page again: its rows, and which of them exist, may depend on other values. */
    public void rebuild() {
        if (getContext() == null || mAdapter == null) {
            return;
        }
        SettingsPages.Page page = SettingsPages.build(requireContext(), mPageId);
        mTitle.setText(page.title);
        mAdapter.submit(page.rows);
    }

    /** Opens another page of the tree on top of this one. */
    public void openPage(@NonNull String pageId) {
        if (getActivity() instanceof MobileSettingsActivity) {
            ((MobileSettingsActivity) getActivity()).openPage(pageId);
        }
    }

    @Override
    public void onRowClicked(@NonNull SettingsRow row) {
        switch (row.kind) {
            case SettingsRow.KIND_LINK:
                if (row.page != null) {
                    openPage(row.page);
                } else if (row.action != null) {
                    row.action.run(this);
                }
                break;
            case SettingsRow.KIND_SWITCH:
                if (row.checked != null && row.toggle != null) {
                    row.toggle.set(!row.checked.getAsBoolean());
                    applied(row);
                }
                break;
            case SettingsRow.KIND_CHOICE:
                showChoiceDialog(row);
                break;
            default:
                break;
        }
    }

    private void applied(SettingsRow row) {
        rebuild();
        if (row.restart) {
            offerRestart();
        }
    }

    /** A change that lands after a restart says so, and offers to do it now. */
    public void offerRestart() {
        // The snackbar outlives this page (it sits on the activity): its action can't ask the
        // fragment for a context after Back took the page away.
        Context app = requireContext().getApplicationContext();
        MobileSnackbar.show(requireContext(), getString(R.string.mobile_settings_restart_needed),
                getString(R.string.mobile_settings_restart), () -> Utils.restartTheApp(app));
    }

    /**
     * YouTube's choice dialog: the options as a radio list, the current one marked; a tap applies
     * that option and closes the dialog (Cancel leaves everything as it was).
     */
    private void showChoiceDialog(SettingsRow row) {
        if (row.options == null || row.pick == null) {
            return;
        }
        List<SettingsRow.Option> options = row.options;
        int current = row.currentIndex();

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return options.size();
            }

            @Override
            public Object getItem(int position) {
                return options.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView != null ? convertView
                        : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mobile_settings_choice, parent, false);
                SettingsRow.Option option = options.get(position);
                ((RadioButton) view.findViewById(R.id.settings_choice_radio)).setChecked(position == current);
                ((TextView) view.findViewById(R.id.settings_choice_label)).setText(option.label);
                TextView description = view.findViewById(R.id.settings_choice_description);
                description.setText(option.description);
                description.setVisibility(option.description != null ? View.VISIBLE : View.GONE);
                return view;
            }
        };

        mDialog = new MaterialAlertDialogBuilder(requireContext(), R.style.MobileAlertDialog)
                .setTitle(row.title)
                .setAdapter(adapter, (dialog, which) -> {
                    if (which != current) {
                        row.pick.pick(options.get(which).value);
                        applied(row);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        if (current > 0 && mDialog.getListView() != null) {
            // A long list (UI scale, quality) opens on the current option, not its first row.
            mDialog.getListView().setSelection(Math.max(0, current - 2));
        }
    }

    /** For rows whose action opens their own dialog. */
    public void showDialog(@NonNull AlertDialog dialog) {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        mDialog = dialog;
    }
}
