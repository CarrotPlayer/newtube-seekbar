package com.newtube.mobile.ui.settings;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Search on the top level of the phone Settings: the field stays where it is, under the title,
 * and typing lays the results over the list (which keeps its place underneath); clearing the
 * field brings the list back. A result opens its page with the row lit up, on top of this one, so
 * Back comes back to the same results. While there is a query, Back clears it first.
 *
 * <p>One per view of the top-level page; the query outlives the view in the page fragment.</p>
 */
final class SettingsSearchController {
    private static final long FADE_MS = 150;

    interface QueryHolder {
        @NonNull String getQuery();

        void setQuery(@NonNull String query);

        /** The index from the last time, shown at once when the page comes back. */
        @Nullable SettingsSearch getLastIndex();

        void setLastIndex(@NonNull SettingsSearch index);
    }

    private final SettingsPageFragment mPage;
    private final QueryHolder mHolder;
    private final EditText mInput;
    private final View mClear;
    private final RecyclerView mList;
    private final RecyclerView mResults;
    private final TextView mEmpty;
    private final ResultsAdapter mAdapter;
    private final OnBackPressedCallback mBackClears;
    @Nullable private SettingsSearch mSearch;

    SettingsSearchController(@NonNull SettingsPageFragment page, @NonNull View root, @NonNull QueryHolder holder) {
        mPage = page;
        mHolder = holder;
        root.findViewById(R.id.settings_search_field).setVisibility(View.VISIBLE);
        mInput = root.findViewById(R.id.settings_search_input);
        mClear = root.findViewById(R.id.settings_search_clear);
        mList = root.findViewById(R.id.settings_list);
        mResults = root.findViewById(R.id.settings_search_results);
        mEmpty = root.findViewById(R.id.settings_search_empty);

        mAdapter = new ResultsAdapter(this::open);
        // The results arrive with the index, a moment after a recreation: a layout with no rows
        // would throw the saved scroll position away.
        mAdapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        mResults.setLayoutManager(new LinearLayoutManager(root.getContext()));
        mResults.setAdapter(mAdapter);
        mResults.setItemAnimator(null);
        mResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideKeyboard(); // reading the results, not typing
                }
            }
        });
        // Edge to edge, the window doesn't shrink for the keyboard: keep the last results above it.
        int basePadding = mResults.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(mResults, (list, insets) -> {
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(),
                    basePadding + Math.max(0, ime.bottom - bars.bottom));
            return insets;
        });

        mClear.setOnClickListener(v -> {
            mInput.setText("");
            mInput.requestFocus();
            showKeyboard();
        });
        mInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(); // the results are already there
                return true;
            }
            return false;
        });

        mBackClears = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                mInput.setText("");
                mInput.clearFocus();
                hideKeyboard();
            }
        };
        page.requireActivity().getOnBackPressedDispatcher().addCallback(page.getViewLifecycleOwner(), mBackClears);

        // What was found last time shows at once (Back from a result); the index is built again
        // in the background, as the rows and which exist follow the current settings.
        mSearch = holder.getLastIndex();
        mInput.setText(holder.getQuery());
        mInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().equals(mHolder.getQuery())) {
                    return; // the field restoring its text (Back from a result), not typing
                }
                mHolder.setQuery(s.toString());
                update(true);
            }
        });
        update(false);
        refreshIndex();
    }

    void hideKeyboard() {
        if (mPage.getActivity() == null) {
            return;
        }
        WindowCompat.getInsetsController(mPage.getActivity().getWindow(), mInput).hide(WindowInsetsCompat.Type.ime());
    }

    private void refreshIndex() {
        SettingsSearch.buildAsync(mPage.requireContext(), search -> {
            View view = mPage.getView();
            if (view == null || view.findViewById(R.id.settings_list) != mList) {
                return; // this controller's view is gone (the page was left, or rebuilt)
            }
            mSearch = search;
            mHolder.setLastIndex(search);
            update(false);
        });
    }

    private void update(boolean typed) {
        String query = mHolder.getQuery();
        String trimmed = query.trim();
        boolean searching = !trimmed.isEmpty();
        mClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        mBackClears.setEnabled(!query.isEmpty());

        List<SettingsSearch.Entry> hits = mSearch != null ? mSearch.find(query) : new ArrayList<>();
        mAdapter.submit(hits, query);
        if (typed) {
            mResults.scrollToPosition(0);
        }
        show(mResults, searching, typed);
        boolean nothing = searching && mSearch != null && hits.isEmpty();
        mEmpty.setVisibility(nothing ? View.VISIBLE : View.GONE);
        if (nothing) {
            mEmpty.setText(mPage.getString(R.string.mobile_settings_search_none, trimmed));
        }
        // TalkBack and a mistaken tap shouldn't reach the list under the results.
        mList.setImportantForAccessibility(searching ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    /** A quick cross-fade when the results come and go (none when the page is just built). */
    private static void show(View view, boolean visible, boolean animate) {
        boolean shown = view.getVisibility() == View.VISIBLE;
        if (visible == shown) {
            return;
        }
        view.animate().cancel();
        if (!animate) {
            view.setAlpha(1f);
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            return;
        }
        if (visible) {
            view.setAlpha(0f);
            view.setVisibility(View.VISIBLE);
            view.animate().alpha(1f).setDuration(FADE_MS).start();
        } else {
            view.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
                view.setVisibility(View.GONE);
                view.setAlpha(1f);
            }).start();
        }
    }

    private void open(@NonNull SettingsSearch.Entry entry) {
        hideKeyboard();
        if (mPage.getActivity() instanceof MobileSettingsActivity) {
            ((MobileSettingsActivity) mPage.getActivity()).openResult(mPage, entry);
        }
    }

    private void showKeyboard() {
        if (mPage.getActivity() == null) {
            return;
        }
        mInput.post(() -> WindowCompat.getInsetsController(mPage.requireActivity().getWindow(), mInput)
                .show(WindowInsetsCompat.Type.ime()));
    }

    /** The results: section icon, title with the matched words in bold, where the row is. */
    private static final class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.Holder> {
        interface OnOpen {
            void open(@NonNull SettingsSearch.Entry entry);
        }

        private final OnOpen mOnOpen;
        private List<SettingsSearch.Entry> mEntries = new ArrayList<>();
        private String mQuery = "";

        ResultsAdapter(OnOpen onOpen) {
            mOnOpen = onOpen;
        }

        void submit(List<SettingsSearch.Entry> entries, String query) {
            mEntries = entries;
            mQuery = query;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_mobile_settings_search_result, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            SettingsSearch.Entry entry = mEntries.get(position);
            if (entry.icon != 0) {
                holder.icon.setImageResource(entry.icon);
                holder.icon.setVisibility(View.VISIBLE);
            } else {
                holder.icon.setImageDrawable(null);
                holder.icon.setVisibility(View.INVISIBLE); // keep the titles in one column
            }
            holder.title.setText(SettingsSearch.highlight(entry.title, mQuery));
            holder.path.setText(entry.path);
            holder.itemView.setOnClickListener(v -> mOnOpen.open(entry));
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView path;

            Holder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.settings_result_icon);
                title = itemView.findViewById(R.id.settings_result_title);
                path = itemView.findViewById(R.id.settings_result_path);
            }
        }
    }
}
