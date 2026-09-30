package com.newtube.mobile.ui.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.lang.reflect.Field;

/**
 * NEWTUBE(haptics): pull-to-refresh that you can feel - the faint grain while the spinner is pulled
 * ({@link Haptics#tension}), and one click the moment the pull passes the point where letting go
 * refreshes, the same click if it comes back under ({@link Haptics#threshold}); the platform names
 * pull-to-refresh as the example of exactly this (HapticFeedbackConstants.GESTURE_THRESHOLD_*).
 *
 * <p>SwipeRefreshLayout keeps how far it is pulled to itself, so this reads its two private fields
 * after each step of the pull (swiperefreshlayout 1.2.0: {@code mTotalUnconsumed} for a pull that
 * comes through the list's nested scrolling, {@code mInitialMotionY} for one it catches itself).
 * If a later version renames them, the pull just goes back to being silent.</p>
 */
public class HapticSwipeRefreshLayout extends SwipeRefreshLayout {
    /** SwipeRefreshLayout.DRAG_RATE: its own touch pull moves the spinner at half the finger. */
    private static final float DRAG_RATE = 0.5f;

    private static boolean sLookedUp;
    @Nullable
    private static Field sTotalUnconsumed;
    @Nullable
    private static Field sTotalDragDistance;
    @Nullable
    private static Field sIsBeingDragged;
    @Nullable
    private static Field sInitialMotionY;

    /** The pull is past the refresh point: letting go now refreshes. */
    private boolean mEngaged;

    public HapticSwipeRefreshLayout(@NonNull Context context) {
        super(context);
    }

    public HapticSwipeRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        super.onNestedPreScroll(target, dx, dy, consumed);
        follow(nestedPull());
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed,
            int dyUnconsumed, int type, @NonNull int[] consumed) {
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed);
        follow(nestedPull());
    }

    @Override
    public void onStopNestedScroll(View target) {
        super.onStopNestedScroll(target);
        mEngaged = false; // released: it refreshes or springs back on its own, silently
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = super.onTouchEvent(ev);
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mEngaged = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            follow(touchPull(ev.getY()));
        }
        return handled;
    }

    private void follow(float pull) {
        float distance = readFloat(sTotalDragDistance);
        if (pull < 0f || distance <= 0f || isRefreshing()) {
            return;
        }
        boolean engaged = pull > distance;
        if (engaged != mEngaged) {
            mEngaged = engaged;
            Haptics.threshold(this, engaged);
        } else if (!engaged) {
            Haptics.tension(this, pull / distance);
        }
    }

    /** How far a nested-scroll pull has gone (px), or -1 when unknown. */
    private float nestedPull() {
        lookUp();
        return readFloat(sTotalUnconsumed);
    }

    /** How far a pull caught by this layout itself has gone (px), or -1 when it is not dragging. */
    private float touchPull(float y) {
        lookUp();
        try {
            if (sIsBeingDragged == null || sInitialMotionY == null || !sIsBeingDragged.getBoolean(this)) {
                return -1f;
            }
            return (y - sInitialMotionY.getFloat(this)) * DRAG_RATE;
        } catch (IllegalAccessException e) {
            return -1f;
        }
    }

    private float readFloat(@Nullable Field field) {
        lookUp();
        if (field == null) {
            return -1f;
        }
        try {
            return field.getFloat(this);
        } catch (IllegalAccessException e) {
            return -1f;
        }
    }

    private static void lookUp() {
        if (sLookedUp) {
            return;
        }
        sLookedUp = true;
        sTotalUnconsumed = field("mTotalUnconsumed");
        sTotalDragDistance = field("mTotalDragDistance");
        sIsBeingDragged = field("mIsBeingDragged");
        sInitialMotionY = field("mInitialMotionY");
    }

    @Nullable
    private static Field field(String name) {
        try {
            Field field = SwipeRefreshLayout.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException e) {
            return null;
        }
    }
}
