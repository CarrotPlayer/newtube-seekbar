package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * NEWTUBE(storyboard): the seek-preview frame, painted straight into the video box - the phone
 * deliberately has no popup above the scrubber, so the frame under the finger simply replaces the
 * video for as long as the bar is dragged.
 *
 * <p>The view keeps the last frame it was given: while the next sheet of YouTube's storyboard is
 * still on the wire, the previous frame stays on screen instead of the box going black. Only
 * {@link #clear()} (a new video) drops it.</p>
 */
public class StoryboardPreviewView extends View {
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Rect mSrc = new Rect();
    private final RectF mDst = new RectF();

    @Nullable
    private Bitmap mSheet;
    private boolean mHasFrame;

    public StoryboardPreviewView(Context context) {
        this(context, null);
    }

    public StoryboardPreviewView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StoryboardPreviewView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // A purely visual overlay while a finger is on the seek bar: TalkBack has the bar itself.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Whether there is a frame to show (the last one received, even from a previous position). */
    public boolean hasFrame() {
        return mHasFrame && mSheet != null && !mSheet.isRecycled();
    }

    /** Show {@code tile} of {@code sheet}, fitted (whole frame, letterboxed if needed) here. */
    public void showTile(@NonNull Bitmap sheet, @NonNull Rect tile) {
        mSheet = sheet;
        mSrc.set(tile);
        mHasFrame = true;
        invalidate();
    }

    /** Forget the frame (a different video's storyboard must never be shown). */
    public void clear() {
        mSheet = null;
        mSrc.setEmpty();
        mHasFrame = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (!hasFrame() || width <= 0 || height <= 0 || mSrc.isEmpty()) {
            return;
        }

        // Fit the whole frame in the box (the video itself may be pillarboxed/letterboxed too);
        // a vertical video's storyboard tile must not be cropped to a horizontal strip.
        float scale = Math.min(width / (float) mSrc.width(), height / (float) mSrc.height());
        float drawnWidth = mSrc.width() * scale;
        float drawnHeight = mSrc.height() * scale;
        float left = (width - drawnWidth) / 2f;
        float top = (height - drawnHeight) / 2f;
        mDst.set(left, top, left + drawnWidth, top + drawnHeight);
        canvas.drawBitmap(mSheet, mSrc, mDst, mPaint);
    }
}
