/*
 * Copyright (C) 2014 Roy Wang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dolphinwang.switchbutton;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

/**
 * Like lots of switch button.
 *
 * @author dolphinWang
 * @time 2014/07/08
 */
public class SwitchButton extends View {

    private static final String DEBUG_TAG = "SwitchButton.java";

    /**
     * Status of button
     */
    public enum STATUS {
        ON, OFF;
    }

    /**
     * Represent states.
     */
    private int[] mPressedState = new int[]{android.R.attr.state_pressed};
    private int[] mUnPressedState = new int[]{-android.R.attr.state_pressed};

    /**
     * Default is ON
     */
    private STATUS mStatus = STATUS.ON;

    /**
     * Switch cursor
     */
    private Drawable mCursor;

    /**
     * Shadow of switch cursor, GradientDrawable recommend
     */
    private Drawable mCursorShadow;

    private static final int DEFAULT_DURATION = 200;
    private static final int DEFAULT_SELECTED_BG_COLOR = Color.rgb(252, 87, 119);
    private static final int DEFAULT_UNSELECTED_BG_COLOR = Color.rgb(238, 238, 238);
    private static final int DEFAULT_TOUCH_EXPAND = 20;
    
    private static int TOUCH_SLOP;

    /**
     * Represent location of switch cursor
     */
    private float mCursorLocation;

    /**
     * The right edge cursor can reach
     */
    private float mCursorRightBoundary;

    /**
     * The left edge cursor can reach
     */
    private float mCursorLeftBoundary;

    /**
     * Expand touch edge of cursor
     */
    private int mCursorTouchExpand;
    private Rect mCursorTouchRect;

    private Rect mCursorRect;

    private RectF mTrackRectF;
    private RectF mTrackSelectedRectF;

    private int mCursorMoveDuration;

    private int mSelectedBGColor;
    private int mUnSelectedBGColor;

    private int mTrackWidth;

    private int mTrackPadding;

    private Paint mPaint;
    private float mTrackRadius;

    private Scroller mScroller;

    private int mShadowExpand;
    private int mShadowXDiff;
    private int mShadowYDiff;

    private int mLastX;
    private boolean mPressed;
    private boolean mClicked;
    private boolean mIsBeingDragged;

    private OnStatusChangeListener mListener;

    /**
     * For anti-alias
     */
    private PaintFlagsDrawFilter mPaintFlagsDrawFilter;

    public SwitchButton(Context context) {
        this(context, null, 0);
    }

    public SwitchButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwitchButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        applyConfiguration(context, attrs);

        mCursorRect = new Rect();
        mCursorTouchRect = new Rect();
        mTrackRectF = new RectF();
        mTrackSelectedRectF = new RectF();

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mScroller = new Scroller(context, new DecelerateInterpolator());

        mPaintFlagsDrawFilter = new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        
        TOUCH_SLOP = ViewConfiguration.get(context).getScaledTouchSlop();

        setWillNotDraw(false);
        setFocusable(true);
        setClickable(true);
    }

    private void applyConfiguration(Context context, AttributeSet attrs) {
        if (attrs == null) {
            return;
        }

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SwitchButton);

        mCursorMoveDuration = a.getInt(R.styleable.SwitchButton_moveDuration, DEFAULT_DURATION);

        if (mCursorMoveDuration < 0) {
            Log.w(DEBUG_TAG, "Set move duration of cursor less than 0!");
            mCursorMoveDuration = DEFAULT_DURATION;
        }

        mSelectedBGColor = a.getColor(R.styleable.SwitchButton_selectedBG, DEFAULT_SELECTED_BG_COLOR);
        mUnSelectedBGColor = a.getColor(R.styleable.SwitchButton_unselectedBG, DEFAULT_UNSELECTED_BG_COLOR);

        mCursor = a.getDrawable(R.styleable.SwitchButton_cursor);
        mCursorShadow = a.getDrawable(R.styleable.SwitchButton_shadow);

        mTrackWidth = (int) a.getDimension(R.styleable.SwitchButton_trackWidth, mCursor.getIntrinsicWidth());

        mTrackPadding = (int) a.getDimension(R.styleable.SwitchButton_trackPadding, 0);

        mCursorTouchExpand = (int) a.getDimension(R.styleable.SwitchButton_cursorTouchExpand, DEFAULT_TOUCH_EXPAND);

        mShadowExpand = (int) a.getDimension(R.styleable.SwitchButton_shadowExpand, 0);
        mShadowXDiff = (int) a.getDimension(R.styleable.SwitchButton_shadowXDiff, 0);
        mShadowYDiff = (int) a.getDimension(R.styleable.SwitchButton_shadowYDiff, 0);

        mStatus = STATUS.values()[a.getInt(R.styleable.SwitchButton_status, 0)];

        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mCursor == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            return;
        }

        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int paddingRight = getPaddingRight();
        final int paddingBottom = getPaddingBottom();

        final int cursorHeight = mCursor.getIntrinsicHeight();
        final int cursorWidth = mCursor.getIntrinsicWidth();

        int widthNeed = mTrackWidth + paddingLeft + paddingRight;
        int heightNeed = cursorHeight + paddingTop + paddingBottom + mTrackPadding * 2;

        mTrackRectF.left = paddingLeft;
        mTrackRectF.top = paddingTop;
        mTrackRectF.right = mTrackRectF.left + mTrackWidth;
        mTrackRectF.bottom = mTrackRectF.top + cursorHeight + mTrackPadding * 2;

        mCursorLeftBoundary = paddingLeft + mTrackPadding;
        mCursorRightBoundary = paddingLeft + mTrackWidth - cursorWidth - mTrackPadding;
        if (mStatus == STATUS.OFF) {
            mCursorLocation = mCursorLeftBoundary;
        } else {
            mCursorLocation = mCursorRightBoundary;
        }

        mTrackSelectedRectF.left = mTrackRectF.left;
        mTrackSelectedRectF.top = mTrackRectF.top;
        mTrackSelectedRectF.bottom = mTrackRectF.bottom;

        mCursorRect.top = paddingTop + mTrackPadding;
        mCursorRect.bottom = mCursorRect.top + cursorHeight;

        mCursorTouchRect.top = mCursorRect.top - mCursorTouchExpand;
        mCursorTouchRect.bottom = mCursorRect.bottom + mCursorTouchExpand;

        mTrackRadius = (mTrackRectF.bottom - mTrackRectF.top) / 2;

        widthMeasureSpec = MeasureSpec.makeMeasureSpec(widthNeed, MeasureSpec.EXACTLY);
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightNeed, MeasureSpec.EXACTLY);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mCursor == null) {
            return;
        }

        canvas.setDrawFilter(mPaintFlagsDrawFilter);
        final float trackRadius = mTrackRadius;

        mPaint.reset();

        /* Draw track first */
        // If status of button is "ON", so we don't need to draw the unselected part.
        // The same to status "OFF".
        if (mCursorLocation == mCursorLeftBoundary) {
            mPaint.setColor(mUnSelectedBGColor);
            canvas.drawRoundRect(mTrackRectF, trackRadius, trackRadius, mPaint);
        } else if (mCursorLocation == mCursorRightBoundary) {
            mPaint.setColor(mSelectedBGColor);
            canvas.drawRoundRect(mTrackRectF, trackRadius, trackRadius, mPaint);
        } else {
            // Draw unselected part first
            mPaint.setColor(mUnSelectedBGColor);
            canvas.drawRoundRect(mTrackRectF, trackRadius, trackRadius, mPaint);

            // Then draw the selected part
            mTrackSelectedRectF.right = mCursorLocation + mCursor.getIntrinsicWidth();
            mPaint.setColor(mSelectedBGColor);
            canvas.drawRoundRect(mTrackSelectedRectF, trackRadius, trackRadius, mPaint);
        }

        /* Draw the cursor and it's shadow */
        // Calculate location of cursor
        mCursorRect.left = (int) mCursorLocation;
        mCursorRect.right = (int) (mCursorLocation + mCursor.getIntrinsicWidth());
        mCursorTouchRect.left = mCursorRect.left - mCursorTouchExpand;
        mCursorTouchRect.right = mCursorRect.right + mCursorTouchExpand;

        // Draw shadow of cursor first
        if (mCursorShadow != null) {
            final int shadowLeft = mCursorRect.left - mShadowExpand;
            final int shadowTop = mCursorRect.top - mShadowExpand;
            mCursorShadow.setBounds(shadowLeft, shadowTop, shadowLeft + mCursor.getIntrinsicWidth() + mShadowExpand * 2
                    , shadowTop + mCursor.getIntrinsicHeight() + mShadowExpand * 2);
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.translate(mShadowXDiff, mShadowYDiff);
            mCursorShadow.draw(canvas);
            canvas.restore();
        }

        mCursor.setBounds(mCursorRect);
        mCursor.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mCursor == null) {
            return super.onTouchEvent(event);
        }

        final int action = event.getAction();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                handleTouchDown(event);
                break;
            case MotionEvent.ACTION_MOVE:
                handleTouchMove(event);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                handleTouchUp();
                mLastX = 0;
                mPressed = false;
                mClicked = false;
                mIsBeingDragged = false;
                break;
        }

        return super.onTouchEvent(event);
    }

    private void handleTouchDown(MotionEvent e) {
        final int x = (int) e.getX();
        final int y = (int) e.getY();

        //Check if on cursor
        if (mCursorTouchRect.contains(x, y)) {
            mCursor.setState(mPressedState);
            mCursor.invalidateSelf();
            mPressed = true;
            mClicked = true;
            if (mScroller.computeScrollOffset()) {
                mScroller.abortAnimation();
            }

            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        }

        mLastX = x;
        invalidate();
    }

    private void handleTouchMove(MotionEvent e) {
        final int x = (int) e.getX();

        if (!mPressed) {
            return;
        }

        final int deltaX = x - mLastX;
        mLastX = x;
        
        if (!mIsBeingDragged) {
            if (Math.abs(deltaX) > TOUCH_SLOP) {
                mIsBeingDragged = true;
                mClicked = false;
            } else {
                return;
            }
        }

        if (mCursorLocation + deltaX <= mCursorLeftBoundary) {
            mCursorLocation = mCursorLeftBoundary;
            invalidate();
            return;
        } else if (mCursorLocation + deltaX >= mCursorRightBoundary) {
            mCursorLocation = mCursorRightBoundary;
            invalidate();
            return;
        }

        mCursorLocation += deltaX;
        invalidate();
    }

    private void handleTouchUp() {
        mCursor.setState(mUnPressedState);
        mCursor.invalidateSelf();
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }

        if (mClicked) {
            if (mStatus == STATUS.ON) {
                mScroller.startScroll((int) mCursorLocation, 0, (int) (mCursorLeftBoundary - mCursorLocation), mCursorMoveDuration);
                changeStatus();
            } else {
                mScroller.startScroll((int) mCursorLocation, 0, (int) (mCursorRightBoundary - mCursorLocation), mCursorMoveDuration);
                changeStatus();
            }

            invalidate();
            return;
        }

        final float cursorTrackWidth = mCursorRightBoundary - mCursorLeftBoundary;

        if (mCursorLocation == mCursorLeftBoundary) {
            changeStatus();
            return;
        } else if (mCursorLocation == mCursorRightBoundary) {
            changeStatus();
            return;
        }

        if (mCursorLocation < cursorTrackWidth / 2) {
            // Scroll back
            mScroller.startScroll((int) mCursorLocation, 0, (int) (mCursorLeftBoundary - mCursorLocation), mCursorMoveDuration);
            changeStatus();
        } else {
            // Scroll forward
            mScroller.startScroll((int) mCursorLocation, 0, (int) (mCursorRightBoundary - mCursorLocation), mCursorMoveDuration);
            changeStatus();
        }

        invalidate();
    }

    private void changeStatus() {
        if (mStatus == STATUS.OFF) {
            if (mListener != null) {
                mListener.onChange(STATUS.ON);
            }
            mStatus = STATUS.ON;
        } else {
            if (mListener != null) {
                mListener.onChange(STATUS.OFF);
            }
            mStatus = STATUS.OFF;
        }
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            final int deltaX = mScroller.getCurrX();

            mCursorLocation = deltaX;

            invalidate();
        }

        super.computeScroll();
    }

    public void setCursorDrawable(Drawable cursor) {
        if (cursor == null) {
            return;
        }

        this.mCursor = cursor;
    }

    public void setShadowDrawable(GradientDrawable drawable) {
        if (drawable != null) {
            return;
        }

        this.mCursorShadow = drawable;
    }

    public void setCursorMoveDuration(int cursorMoveDuration) {
        if (cursorMoveDuration < 0) {
            cursorMoveDuration = DEFAULT_DURATION;
        }

        this.mCursorMoveDuration = cursorMoveDuration;
    }

    public void setSelectedBGColor(int selectedBGColor) {
        this.mSelectedBGColor = selectedBGColor;
    }

    public void setUnSelectedBGColor(int unSelectedBGColor) {
        this.mUnSelectedBGColor = unSelectedBGColor;
    }

    /**
     * This method should call after cursor set.
     *
     * @param width
     */
    public void setTrackWidth(int width) {
        final int need = mCursor.getIntrinsicWidth();

        if (width < need) {
            mTrackWidth = need;
            return;
        }

        mTrackWidth = width;
    }

    public void setShadowXDiff(int shadowXDiff) {
        this.mShadowXDiff = shadowXDiff;
    }

    public void setShadowExpand(int shadowExpand) {
        if (shadowExpand < 0) {
            return;
        }

        this.mShadowExpand = shadowExpand;
    }

    public void setShadowYDiff(int shadowYDiff) {
        this.mShadowYDiff = shadowYDiff;
    }

    public void setOnStatusChangeListener(OnStatusChangeListener l) {
        mListener = l;
    }

    public void setStatus(STATUS status) {
        if (mStatus == status) {
            return;
        }


        if (mCursorRightBoundary > 0) {
            if (status == STATUS.ON) {
                mScroller.startScroll((int) mCursorLocation, 0, (int) (mCursorRightBoundary - mCursorLocation), mCursorMoveDuration);
            } else {
                mScroller.startScroll((int) mCursorLocation, 0, (int) (mCursorLeftBoundary - mCursorLocation), mCursorMoveDuration);
            }
        }
        
        mStatus = status;
        if (mListener != null) {
            mListener.onChange(status);
        }

        invalidate();
    }

    public static interface OnStatusChangeListener {
        void onChange(STATUS status);
    }
}
