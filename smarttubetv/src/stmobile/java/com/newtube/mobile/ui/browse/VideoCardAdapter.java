package com.newtube.mobile.ui.browse;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.newtube.mobile.ui.playback.PlayerTransitionBridge;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Touch video card adapter (port of the gist of
 * {@code com.liskovsoft.smartyoutubetv2.tv.presenter.VideoCardPresenter}, minus
 * focus-scale/d-pad concerns; ripple/elevation instead via the card's own foreground
 * and {@code MaterialCardView} elevation).
 */
public class VideoCardAdapter extends ListAdapter<Video, RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_VIDEO = 0;
    private static final int VIEW_TYPE_CHANNEL = 1;

    public interface OnVideoClickListener {
        void onVideoClick(Video video);
    }

    public interface OnVideoLongClickListener {
        boolean onVideoLongClick(Video video);
    }

    private final OnVideoClickListener mClickListener;
    private final OnVideoLongClickListener mLongClickListener;

    public VideoCardAdapter(OnVideoClickListener clickListener) {
        this(clickListener, null);
    }

    public VideoCardAdapter(OnVideoClickListener clickListener, OnVideoLongClickListener longClickListener) {
        super(DIFF_CALLBACK);
        mClickListener = clickListener;
        mLongClickListener = longClickListener;
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

    @Override
    public int getItemViewType(int position) {
        Video item = getItem(position);
        return item.isChannel() && !item.isPlaylistAsChannel() ? VIEW_TYPE_CHANNEL : VIEW_TYPE_VIDEO;
    }

    public boolean isFullSpan(int position) {
        return position >= 0 && position < getItemCount() && getItemViewType(position) == VIEW_TYPE_CHANNEL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_CHANNEL) {
            return new ChannelViewHolder(inflater.inflate(R.layout.item_mobile_channel_card, parent, false), mClickListener);
        }
        return new VideoViewHolder(inflater.inflate(R.layout.item_video_card, parent, false), mClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ChannelViewHolder) {
            ((ChannelViewHolder) holder).bind(getItem(position), mLongClickListener);
        } else {
            ((VideoViewHolder) holder).bind(getItem(position), mLongClickListener);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof VideoViewHolder) {
            ((VideoViewHolder) holder).unbind();
        } else if (holder instanceof ChannelViewHolder) {
            ((ChannelViewHolder) holder).unbind();
        }
    }

    public void refreshItems(java.util.List<Video> items) {
        java.util.List<Video> current = getCurrentList();
        for (Video item : items) {
            for (int i = 0; i < current.size(); i++) {
                if (current.get(i).equals(item)) {
                    notifyItemChanged(i, PAYLOAD_SYNC);
                }
            }
        }
    }

    private static final Object PAYLOAD_SYNC = new Object();

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
            @NonNull java.util.List<Object> payloads) {
        if (!payloads.isEmpty() && holder instanceof VideoViewHolder) {
            ((VideoViewHolder) holder).bindPartial(getItem(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mThumbnail;
        private final View mThumbnailFrame;
        private final TextView mBadge;
        private final ProgressBar mWatchProgress;
        private final TextView mTitle;
        private final TextView mMeta;
        private final View mOverflow;
        private Video mVideo;
        private String mBoundThumbUrl;

        public VideoViewHolder(@NonNull View itemView, OnVideoClickListener clickListener) {
            super(itemView);

            mThumbnail = itemView.findViewById(R.id.video_thumbnail);
            mThumbnailFrame = itemView.findViewById(R.id.video_thumbnail_frame);
            mBadge = itemView.findViewById(R.id.video_badge);
            mWatchProgress = itemView.findViewById(R.id.video_watch_progress);
            mTitle = itemView.findViewById(R.id.video_title);
            mMeta = itemView.findViewById(R.id.video_meta);
            mOverflow = itemView.findViewById(R.id.video_overflow);

            itemView.setOnClickListener(v -> {
                if (mVideo != null && clickListener != null) {
                    if (mVideo.videoId != null) {
                        PlayerTransitionBridge.prepare(mThumbnailFrame, mBadge, mWatchProgress);
                    } else {
                        PlayerTransitionBridge.clear();
                    }
                    clickListener.onVideoClick(mVideo);
                }
            });
        }

        public void bind(Video video, OnVideoLongClickListener longClickListener) {
            mVideo = video;
            Context context = itemView.getContext();

            itemView.setOnLongClickListener(v ->
                    mVideo != null && longClickListener != null && longClickListener.onVideoLongClick(mVideo));

            if (mOverflow != null) {
                mOverflow.setVisibility(longClickListener == null ? View.GONE : View.VISIBLE);
                mOverflow.setOnClickListener(v -> {
                    if (mVideo != null && longClickListener != null) {
                        longClickListener.onVideoLongClick(mVideo);
                    }
                });
                mOverflow.setContentDescription(video.getTitle() != null
                        ? context.getString(R.string.mobile_more_options_for, video.getTitle())
                        : context.getString(R.string.mobile_player_more));
            }

            mTitle.setText(video.getTitle());

            CharSequence meta = video.getSecondTitle();
            CharSequence rawMeta = (meta == null || meta.length() == 0) ? video.getAuthor() : meta;
            if (rawMeta != null && rawMeta.length() > 0) {
                String formatted = formatTwoLineSubtitle(rawMeta.toString(), video.getAuthor(), 32);
                mMeta.setMaxLines(2);
                mMeta.setSingleLine(false);
                mMeta.setText(formatted);
                mMeta.setVisibility(View.VISIBLE);
            } else {
                mMeta.setVisibility(View.GONE);
            }

            bindBadge(context, video);
            bindProgress(video);
            bindReadiness(video);
            bindThumbnail(context, video);
        }

        void bindPartial(Video video) {
            mVideo = video;
            Context context = itemView.getContext();

            mTitle.setText(video.getTitle());
            CharSequence meta = video.getSecondTitle();
            CharSequence rawMeta = (meta == null || meta.length() == 0) ? video.getAuthor() : meta;
            if (rawMeta != null && rawMeta.length() > 0) {
                String formatted = formatTwoLineSubtitle(rawMeta.toString(), video.getAuthor(), 32);
                mMeta.setMaxLines(2);
                mMeta.setSingleLine(false);
                mMeta.setText(formatted);
                mMeta.setVisibility(View.VISIBLE);
            } else {
                mMeta.setVisibility(View.GONE);
            }

            bindBadge(context, video);
            bindProgress(video);
            bindReadiness(video);

            String thumbnailUrl = thumbnailUrl(context, video);
            if (thumbnailUrl == null ? mBoundThumbUrl != null : !thumbnailUrl.equals(mBoundThumbUrl)) {
                bindThumbnail(context, video);
            }
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
            if (author != null && !author.trim().isEmpty()) {
                channelName = author.trim();
            } else {
                channelName = segments.get(0);
            }

            if (channelName.length() > maxAuthorLength) {
                channelName = channelName.substring(0, maxAuthorLength - 1) + "…";
            }

            StringBuilder metaBuilder = new StringBuilder();
            for (int i = 0; i < segments.size(); i++) {
                String seg = segments.get(i);
                if (isSameAuthor(seg, channelName)) {
                    continue;
                }
                String clean = cleanMeta(seg);
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

        private static boolean isSameAuthor(String segment, String author) {
            if (segment == null || author == null) return false;
            String s = segment.trim();
            String a = author.trim();
            if (s.equalsIgnoreCase(a)) return true;
            return a.length() >= 3 && s.toLowerCase().contains(a.toLowerCase());
        }

        private static String cleanMeta(String text) {
            if (text == null) return "";
            String s = text.trim();
            s = s.replaceAll("(?i)\\bil y a\\s*", "");
            s = s.replaceAll("(?i)\\bde\\s+vues?\\b", "vues");
            return s.trim();
        }

        private void bindBadge(Context context, Video video) {
            String badgeText;
            if (video.hasNewContent) {
                badgeText = context.getString(R.string.badge_new_content);
            } else if (video.isLive) {
                badgeText = context.getString(R.string.badge_live);
            } else if (video.isShorts) {
                badgeText = context.getString(R.string.header_shorts).toUpperCase();
            } else {
                badgeText = video.badge;
            }

            if (badgeText == null || badgeText.isEmpty()) {
                mBadge.setVisibility(View.GONE);
            } else {
                mBadge.setText(badgeText);
                mBadge.setBackgroundColor(ContextCompat.getColor(context,
                        video.isLive || video.isUpcoming ? R.color.mobile_color_badge_live_bg : R.color.mobile_color_badge_bg));
                mBadge.setVisibility(View.VISIBLE);
            }
        }

        private void bindReadiness(Video video) {
            mThumbnail.setAlpha(video.isPendingDownload() ? 0.4f : 1f);
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

        private static int sThumbW;
        private static int sThumbH;

        private static int thumbWidth(Context context) {
            if (sThumbW == 0) {
                android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
                sThumbW = Math.min(dm.widthPixels, dm.heightPixels);
                sThumbH = sThumbW * 9 / 16;
            }
            return sThumbW;
        }

        public static com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> thumbnailRequest(
                com.bumptech.glide.RequestManager glide, Context context, Video video) {
            int w = thumbWidth(context);
            return glide
                    .load(thumbnailUrl(context, video))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .format(DecodeFormat.PREFER_RGB_565)
                    .override(w, sThumbH)
                    .centerCrop();
        }

        public static String thumbnailUrl(Context context, Video video) {
            com.newtube.mobile.ui.common.LocalThumbnails.fill(video);
            return ClickbaitRemover.updateThumbnail(video, MainUIData.instance(context).getThumbQuality());
        }

        private void bindThumbnail(Context context, Video video) {
            String thumbnailUrl = thumbnailUrl(context, video);
            mBoundThumbUrl = thumbnailUrl;

            int w = thumbWidth(context);
            com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request =
                    thumbnailRequest(Glide.with(context), context, video)
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(150));

            if (com.newtube.mobile.LaunchMilestones.wantsFirstThumb()) {
                request = request.addListener(FIRST_THUMB_LISTENER);
            }

            String fallbackUrl = video.getCardImageUrl();
            if (fallbackUrl != null && !fallbackUrl.equals(thumbnailUrl)) {
                request = request.error(Glide.with(context)
                        .load(fallbackUrl)
                        .format(DecodeFormat.PREFER_RGB_565)
                        .override(w, sThumbH)
                        .centerCrop());
            }

            request.into(mThumbnail);
        }

        private static final com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> FIRST_THUMB_LISTENER =
                new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                            com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        com.newtube.mobile.LaunchMilestones.onFirstThumb(dataSource);
                        return false;
                    }
                };

        public void unbind() {
            mVideo = null;
            mBoundThumbUrl = null;
            Glide.with(itemView.getContext().getApplicationContext()).clear(mThumbnail);
        }
    }

    static class ChannelViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mAvatar;
        private final TextView mName;
        private final TextView mMeta;
        private Video mVideo;

        ChannelViewHolder(@NonNull View itemView, OnVideoClickListener clickListener) {
            super(itemView);

            mAvatar = itemView.findViewById(R.id.channel_avatar);
            mName = itemView.findViewById(R.id.channel_name);
            mMeta = itemView.findViewById(R.id.channel_meta);

            itemView.setOnClickListener(v -> {
                if (mVideo != null && clickListener != null) {
                    PlayerTransitionBridge.clear();
                    clickListener.onVideoClick(mVideo);
                }
            });
        }

        void bind(Video video, OnVideoLongClickListener longClickListener) {
            mVideo = video;

            itemView.setOnLongClickListener(v ->
                    mVideo != null && longClickListener != null && longClickListener.onVideoLongClick(mVideo));

            mName.setText(video.getTitle());

            CharSequence subs = video.getSecondTitle();
            String videosCount = video.badge;
            StringBuilder meta = new StringBuilder();
            if (subs != null && subs.length() > 0) {
                meta.append(subs);
            }
            if (videosCount != null && !videosCount.isEmpty()) {
                if (meta.length() > 0) {
                    meta.append(com.newtube.mobile.ui.common.MetaSeparator.DOT);
                }
                meta.append(videosCount);
            }
            mMeta.setText(meta);
            mMeta.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);

            Glide.with(itemView.getContext())
                    .load(video.getCardImageUrl())
                    .circleCrop()
                    .placeholder(R.drawable.ic_watch_channel_placeholder)
                    .error(R.drawable.ic_watch_channel_placeholder)
                    .into(mAvatar);
        }

        void unbind() {
            mVideo = null;
            Glide.with(itemView.getContext().getApplicationContext()).clear(mAvatar);
        }
    }
}
