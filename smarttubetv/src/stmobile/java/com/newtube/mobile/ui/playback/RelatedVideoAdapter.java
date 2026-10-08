package com.newtube.mobile.ui.playback;

import android.annotation.SuppressLint;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact single-column "up next / related" list adapter for the watch page
 * ({@link MobilePlaybackActivity}). Mirrors {@code VideoCardAdapter}'s thumbnail/badge/progress
 * binding (Glide + {@code ClickbaitRemover} + {@code MainUIData} thumb quality) but with a
 * small horizontal row layout ({@code item_mobile_related_video}) instead of a full-width card.
 *
 * <p>Tapping a row routes the tapped {@link Video} to
 * {@code PlaybackPresenter.onSuggestionItemClicked(Video)} (via the click listener) which both
 * marks queue state and loads/plays the video in the same player.</p>
 */
public class RelatedVideoAdapter extends ListAdapter<Video, RelatedVideoAdapter.RelatedViewHolder> {

    public interface OnRelatedClickListener {
        void onRelatedClick(Video video, @androidx.annotation.Nullable ImageView thumbnail);
    }

    public interface OnRelatedPressListener {
        void onRelatedPress(Video video);
    }

    private final OnRelatedClickListener mClickListener;
    @androidx.annotation.Nullable
    private final OnRelatedPressListener mPressListener;
    private final long mPressIntentMs;
    private String mCurrentVideoId;

    public RelatedVideoAdapter(OnRelatedClickListener clickListener) {
        this(clickListener, null, 0);
    }

    public RelatedVideoAdapter(OnRelatedClickListener clickListener,
            @androidx.annotation.Nullable OnRelatedPressListener pressListener, long pressIntentMs) {
        super(DIFF_CALLBACK);
        mClickListener = clickListener;
        mPressListener = pressIntentMs > 0 ? pressListener : null;
        mPressIntentMs = pressIntentMs;
    }

    public void setCurrentVideoId(String videoId) {
        if (android.text.TextUtils.equals(mCurrentVideoId, videoId)) {
            return;
        }

        mCurrentVideoId = videoId;
        notifyDataSetChanged();
    }

    private static final DiffUtil.ItemCallback<Video> DIFF_CALLBACK = new DiffUtil.ItemCallback<Video>() {
        @Override
        public boolean areItemsTheSame(@NonNull Video oldItem, @NonNull Video newItem) {
            return oldItem.equals(newItem);
        }

        @Override
        @SuppressLint("DiffUtilEquals")
        public boolean areContentsTheSame(@NonNull Video oldItem, @NonNull Video newItem) {
            return oldItem == newItem;
        }
    };

    @NonNull
    @Override
    public RelatedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mobile_related_video, parent, false);
        return new RelatedViewHolder(view, mClickListener, mPressListener, mPressIntentMs);
    }

    @Override
    public void onBindViewHolder(@NonNull RelatedViewHolder holder, int position) {
        Video video = getItem(position);
        boolean isCurrent = mCurrentVideoId != null && video != null
                && mCurrentVideoId.equals(video.videoId);
        holder.bind(video, isCurrent);
    }

    @Override
    public void onViewRecycled(@NonNull RelatedViewHolder holder) {
        super.onViewRecycled(holder);
        holder.unbind();
    }

    static class RelatedViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mThumbnail;
        private final TextView mBadge;
        private final ProgressBar mWatchProgress;
        private final TextView mTitle;
        private final TextView mSubtitle;
        private Video mVideo;

        @SuppressLint("ClickableViewAccessibility")
        RelatedViewHolder(@NonNull View itemView, OnRelatedClickListener clickListener,
                @androidx.annotation.Nullable OnRelatedPressListener pressListener, long pressIntentMs) {
            super(itemView);

            mThumbnail = itemView.findViewById(R.id.related_thumbnail);
            mBadge = itemView.findViewById(R.id.related_badge);
            mWatchProgress = itemView.findViewById(R.id.related_watch_progress);
            mTitle = itemView.findViewById(R.id.related_title);
            mSubtitle = itemView.findViewById(R.id.related_subtitle);

            itemView.setOnClickListener(v -> {
                if (mVideo != null && clickListener != null) {
                    clickListener.onRelatedClick(mVideo, mThumbnail);
                }
            });

            if (pressListener != null) {
                PressIntentDetector detector = new PressIntentDetector(
                        android.view.ViewConfiguration.get(itemView.getContext()).getScaledTouchSlop(),
                        pressIntentMs,
                        new PressIntentDetector.Scheduler() {
                            @Override
                            public void postDelayed(Runnable task, long delayMs) {
                                itemView.postDelayed(task, delayMs);
                            }

                            @Override
                            public void remove(Runnable task) {
                                itemView.removeCallbacks(task);
                            }
                        },
                        () -> {
                            if (mVideo != null) {
                                pressListener.onRelatedPress(mVideo);
                            }
                        });
                itemView.setOnTouchListener((v, event) -> detector.onTouch(event));
            }
        }

        void bind(Video video, boolean isCurrent) {
            mVideo = video;
            Context context = itemView.getContext();

            mTitle.setText(video.getTitle());

            CharSequence subtitle = video.getSecondTitle();
            if (subtitle == null || subtitle.length() == 0) {
                subtitle = video.getAuthor();
            }
            if (subtitle != null && subtitle.length() > 0) {
                String formatted = formatTwoLineSubtitle(subtitle.toString(), video.getAuthor(), 26);
                mSubtitle.setMaxLines(2);
                mSubtitle.setSingleLine(false);
                mSubtitle.setText(formatted);
                mSubtitle.setVisibility(View.VISIBLE);
            } else {
                mSubtitle.setVisibility(View.GONE);
            }

            bindBadge(video, isCurrent);
            bindProgress(video);
            bindThumbnail(context, video);
        }

        private static String formatTwoLineSubtitle(String fullSubtitle, String author, int maxAuthorLength) {
            if (fullSubtitle == null || fullSubtitle.trim().isEmpty()) {
                return author != null ? author : "";
            }

            String[] rawSegments = fullSubtitle.split("\\s*[•·]\\s*");
            List<String> segments = new ArrayList<>();
            for (String seg : rawSegments) {
                String s = seg.trim();
                if (!s.isEmpty() && !s.startsWith("@")) {
                    segments.add(s);
                }
            }

            if (segments.isEmpty()) {
                return author != null ? author : "";
            }

            String channelName;
            int startIndex = 1;

            if (author != null && !author.trim().isEmpty()) {
                channelName = author.trim();
            } else {
                channelName = segments.get(0);
            }

            if (channelName.length() > maxAuthorLength) {
                channelName = channelName.substring(0, maxAuthorLength - 1) + "…";
            }

            StringBuilder metaBuilder = new StringBuilder();
            for (int i = startIndex; i < segments.size(); i++) {
                String clean = cleanMeta(segments.get(i));
                if (!clean.isEmpty()) {
                    if (metaBuilder.length() > 0) {
                        metaBuilder.append(" • ");
                    }
                    metaBuilder.append(clean);
                }
            }

            String metaLine = metaBuilder.toString().trim();
            if (metaLine.isEmpty()) {
                return channelName;
            }

            return channelName + "\n" + metaLine;
        }

        private static String cleanMeta(String text) {
            if (text == null) return "";
            String s = text.trim();
            s = s.replaceAll("(?i)\\bil y a\\s*", "");
            s = s.replaceAll("(?i)\\bde\\s+vues?\\b", "vues");
            return s.trim();
        }

        private void bindBadge(Video video, boolean isCurrent) {
            if (isCurrent) {
                mBadge.setText(R.string.mobile_watch_queue_now_playing);
                mBadge.setVisibility(View.VISIBLE);
                return;
            }

            String badgeText;
            if (video.isLive) {
                badgeText = itemView.getContext().getString(R.string.badge_live);
            } else {
                badgeText = video.badge;
            }

            if (badgeText == null || badgeText.isEmpty()) {
                mBadge.setVisibility(View.GONE);
            } else {
                mBadge.setText(badgeText);
                mBadge.setVisibility(View.VISIBLE);
            }
        }

        private void bindProgress(Video video) {
            int progress = video.percentWatched > 0 && video.percentWatched < 1 ? 1 : Math.round(video.percentWatched);

            if (progress > 0 && progress <= 100) {
                mWatchProgress.setProgress(progress);
                mWatchProgress.setVisibility(View.VISIBLE);
            } else {
                mWatchProgress.setVisibility(View.GONE);
            }
        }

        private void bindThumbnail(Context context, Video video) {
            int thumbQuality = MainUIData.instance(context).getThumbQuality();
            String thumbnailUrl = ClickbaitRemover.updateThumbnail(video, thumbQuality);

            thumbnailUrl = ClickbaitRemover.fitThumbnail(thumbnailUrl, relatedThumbWidthPx(context));

            com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request = Glide.with(context)
                    .load(thumbnailUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .centerCrop()
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(150));

            String fallbackUrl = video.getCardImageUrl();
            if (fallbackUrl != null && !fallbackUrl.equals(thumbnailUrl)) {
                request = request.error(Glide.with(context)
                        .load(fallbackUrl)
                        .format(DecodeFormat.PREFER_RGB_565)
                        .centerCrop());
            }

            request.into(mThumbnail);
        }

        private int relatedThumbWidthPx(Context context) {
            return context.getResources().getDimensionPixelSize(
                    R.dimen.mobile_watch_related_thumb_width);
        }

        void unbind() {
            mVideo = null;
            Glide.with(itemView.getContext().getApplicationContext()).clear(mThumbnail);
        }
    }
}
