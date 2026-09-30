package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

/**
 * NEWTUBE(seek bar): the watch page's column - the video box, then the page under it - drawing the
 * video box LAST. The seek bar's dot sits on the video's bottom edge like YouTube's, so half of it
 * hangs over the top of the page; drawn first, the box had that half painted over by the page's
 * opaque background. This and the video box leave their children unclipped (layout XML). Touches
 * are unaffected: the two children never overlap.
 */
public class WatchRootLayout extends LinearLayout {

    public WatchRootLayout(Context context) {
        this(context, null);
    }

    public WatchRootLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setChildrenDrawingOrderEnabled(true);
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int drawingPosition) {
        // 1, 2, ..., n-1, then 0: the first child (the video box) on top of the rest.
        return childCount < 2 ? drawingPosition : (drawingPosition + 1) % childCount;
    }
}
