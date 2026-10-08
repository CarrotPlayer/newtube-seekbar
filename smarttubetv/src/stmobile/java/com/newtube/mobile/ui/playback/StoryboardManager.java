package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemStoryboard;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemStoryboard.Size;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import java.util.Set;

public class StoryboardManager {
    private static final String TAG = StoryboardManager.class.getSimpleName();
    private static final int MAX_PRELOADED_IMAGES = 3;
    private static final int DIRECTION_RIGHT = 0;
    private static final int DIRECTION_LEFT = 1;
    private final MediaItemService mMediaItemService;
    private final Context mContext;
    private MediaItemStoryboard mStoryboard;
    private Disposable mFormatAction;
    private int mCurrentImgNum = -1;
    private final Set<Integer> mCachedImageNums = new ArraySet<>();
    private int mSeekDirection = DIRECTION_RIGHT;

    public interface Callback {
        void onBitmapLoaded(Bitmap bitmap);
    }

    public StoryboardManager(Context context) {
        mContext = context.getApplicationContext();
        ServiceManager service = YouTubeServiceManager.instance();
        mMediaItemService = service.getMediaItemService();
    }

    public void init(Video video) {
        mStoryboard = null;
        mCachedImageNums.clear();
        mCurrentImgNum = -1;
        RxHelper.disposeActions(mFormatAction);

        if (video == null || video.isUpcoming) {
            return;
        }

        Observable<MediaItemStoryboard> storyboardObserve;
        if (video.mediaItem != null) {
            storyboardObserve = mMediaItemService.getStoryboardObserve(video.mediaItem);
        } else {
            storyboardObserve = mMediaItemService.getStoryboardObserve(video.videoId);
        }

        mFormatAction = storyboardObserve.subscribe(
                storyboard -> mStoryboard = storyboard,
                error -> Log.e(TAG, "Error obtaining storyboard: %s", error.getMessage())
        );
    }

    public void loadPreviewForPosition(long currentPositionMs, Callback callback) {
        if (mStoryboard == null || mStoryboard.getGroupDurationMS() == 0) {
            return;
        }

        int groupNum = (int) (currentPositionMs / mStoryboard.getGroupDurationMS());
        long realPosMS = currentPositionMs % mStoryboard.getGroupDurationMS();
        Size size = mStoryboard.getGroupSize();

        GlideThumbnailTransformation transformation =
                new GlideThumbnailTransformation(realPosMS, size.getWidth(), size.getHeight(),
                        size.getRowCount(), size.getColCount(), size.getDurationEachMS());

        Glide.with(mContext)
                .asBitmap()
                .load(mStoryboard.getGroupUrl(groupNum))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                .transform(transformation)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        callback.onBitmapLoaded(resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });

        if (mCurrentImgNum != groupNum) {
            mSeekDirection = mCurrentImgNum < groupNum ? DIRECTION_RIGHT : DIRECTION_LEFT;
            mCachedImageNums.add(groupNum);
            mCurrentImgNum = groupNum;
            preloadNextImage();
        }
    }

    private void preloadNextImage() {
        if (mStoryboard == null) {
            return;
        }
        for (int i = 1; i <= MAX_PRELOADED_IMAGES; i++) {
            int imgNum = mSeekDirection == DIRECTION_RIGHT ? mCurrentImgNum + i : mCurrentImgNum - i;
            preloadImage(imgNum);
        }
    }

    private void preloadImage(int imgNum) {
        if (mCachedImageNums.contains(imgNum) || imgNum < 0) {
            return;
        }
        mCachedImageNums.add(imgNum);
        String link = mStoryboard.getGroupUrl(imgNum);
        if (link != null) {
            Glide.with(mContext)
                    .load(link)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .preload();
        }
    }
}
