package com.stone.transition;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Created by xmuSistone on 2016/9/18.
 */
public class DragLayout extends FrameLayout {

    private int bottomDragVisibleHeight;
    private int dragTopDest = 0;
    private static final int DECELERATE_THRESHOLD = 120;
    private static final int DRAG_SWITCH_DISTANCE_THRESHOLD = 100;
    private static final int DRAG_SWITCH_VEL_THRESHOLD = 800;

    private static final float MIN_SCALE_RATIO = 0.5f;
    private static final float MAX_SCALE_RATIO = 1.0f;

    private static final int STATE_CLOSE = 1;
    private static final int STATE_EXPANDED = 2;
    private int downState; // 按下时的状态

    private final ViewDragHelper mDragHelper;
    private final GestureDetectorCompat moveDetector;
    private int mTouchSlop = 5; // 判定为滑动的阈值，单位是像素
    private int originX, originY; // 初始状态下，topView的坐标
    private View bottomView, topView; // FrameLayout的两个子View

    private GotoDetailListener gotoDetailListener;

    public DragLayout(Context context) {
        this(context, null);
    }

    public DragLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DragLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.app, 0, 0);
        bottomDragVisibleHeight = (int) a.getDimension(R.styleable.app_bottomDragVisibleHeight, 0);
        a.recycle();

        mDragHelper = ViewDragHelper
                .create(this, 10f, new DragHelperCallback());
        moveDetector = new GestureDetectorCompat(context, new MoveDetector());
        moveDetector.setIsLongpressEnabled(false); // 不处理长按事件

        // 滑动的距离阈值由系统提供
        ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        bottomView = getChildAt(0);
        topView = getChildAt(1);

        topView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 点击回调
                int state = getCurrentState();
                if (state == STATE_CLOSE) {
                    // 点击时为初始状态，需要展开
                    if (mDragHelper.smoothSlideViewTo(topView, originX, dragTopDest)) {
                        ViewCompat.postInvalidateOnAnimation(DragLayout.this);
                    }
                } else {
                    gotoDetailActivity();
                }
            }
        });
    }

    // 跳转到下一页
    private void gotoDetailActivity() {
        if (null != gotoDetailListener) {
            gotoDetailListener.gotoDetail();
        }
    }

    private class DragHelperCallback extends ViewDragHelper.Callback {

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            if (changedView == topView) {
                processLinkageView();
            }
        }

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            if (child == topView) {
                return true;
            }
            return false;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            int currentTop = child.getTop();
            if (top > child.getTop()) {
                // 往下拉的时候，阻力最小
                return currentTop + (top - currentTop) / 2;
            }

            int result;
            if (currentTop > DECELERATE_THRESHOLD * 3) {
                result = currentTop + (top - currentTop) / 2;
            } else if (currentTop > DECELERATE_THRESHOLD * 2) {
                result = currentTop + (top - currentTop) / 4;
            } else if (currentTop > 0) {
                result = currentTop + (top - currentTop) / 8;
            } else if (currentTop > -DECELERATE_THRESHOLD) {
                result = currentTop + (top - currentTop) / 16;
            } else if (currentTop > -DECELERATE_THRESHOLD * 2) {
                result = currentTop + (top - currentTop) / 32;
            } else if (currentTop > -DECELERATE_THRESHOLD * 3) {
                result = currentTop + (top - currentTop) / 48;
            } else {
                result = currentTop + (top - currentTop) / 64;
            }
            return result;
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return child.getLeft();
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return 600;
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return 600;
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            int finalY = originY;
            if (downState == STATE_CLOSE) {
                // 按下的时候，状态为：初始状态
                if (originY - releasedChild.getTop() > DRAG_SWITCH_DISTANCE_THRESHOLD || yvel < -DRAG_SWITCH_VEL_THRESHOLD) {
                    finalY = dragTopDest;
                }
            } else {
                // 按下的时候，状态为：展开状态
                boolean gotoBottom = releasedChild.getTop() - dragTopDest > DRAG_SWITCH_DISTANCE_THRESHOLD || yvel > DRAG_SWITCH_VEL_THRESHOLD;
                if (!gotoBottom) {
                    finalY = dragTopDest;

                    // 如果按下时已经展开，又向上拖动了，就进入详情页
                    if (dragTopDest - releasedChild.getTop() > mTouchSlop) {
                        gotoDetailActivity();
                    }
                }
            }

            if (mDragHelper.smoothSlideViewTo(releasedChild, originX, finalY)) {
                ViewCompat.postInvalidateOnAnimation(DragLayout.this);
            }
        }
    }

    /**
     * 顶层ImageView位置变动，需要对底层的view进行缩放显示
     */
    private void processLinkageView() {
        if (topView.getTop() > originY) {
            bottomView.setAlpha(0);
        } else {
            bottomView.setAlpha(1);
            int maxDistance = originY - dragTopDest;
            int currentDistance = topView.getTop() - dragTopDest;
            float scaleRatio = 1;
            float distanceRatio = (float) currentDistance / maxDistance;
            if (currentDistance > 0) {
                scaleRatio = MIN_SCALE_RATIO + (MAX_SCALE_RATIO - MIN_SCALE_RATIO) * (1 - distanceRatio);
            }
            bottomView.setScaleX(scaleRatio);
            bottomView.setScaleY(scaleRatio);
        }
    }

    class MoveDetector extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx,
                                float dy) {
            // 拖动了，touch不往下传递
            return Math.abs(dy) + Math.abs(dx) > mTouchSlop;
        }
    }

    @Override
    public void computeScroll() {
        if (mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * 获取当前状态
     */
    private int getCurrentState() {
        int state;
        if (Math.abs(topView.getTop() - dragTopDest) <= mTouchSlop) {
            state = STATE_EXPANDED;
        } else {
            state = STATE_CLOSE;
        }
        return state;
    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!changed) {
            return;
        }

        super.onLayout(changed, left, top, right, bottom);
        originX = (int) topView.getX();
        originY = (int) topView.getY();
        dragTopDest = bottomView.getBottom() - bottomDragVisibleHeight - topView.getMeasuredHeight();
    }

    /* touch事件的拦截与处理都交给mDraghelper来处理 */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 1. detector和mDragHelper判断是否需要拦截
        boolean yScroll = moveDetector.onTouchEvent(ev);
        boolean shouldIntercept = false;
        try {
            shouldIntercept = mDragHelper.shouldInterceptTouchEvent(ev);
        } catch (Exception e) {
        }

        // 2. 触点按下的时候直接交给mDragHelper
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downState = getCurrentState();
            mDragHelper.processTouchEvent(ev);
        }

        return shouldIntercept && yScroll;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // 统一交给mDragHelper处理，由DragHelperCallback实现拖动效果
        try {
            mDragHelper.processTouchEvent(e);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return true;
    }

    public void setGotoDetailListener(GotoDetailListener gotoDetailListener) {
        this.gotoDetailListener = gotoDetailListener;
    }

    public interface GotoDetailListener {
        public void gotoDetail();
    }
}