/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.qs;

import android.content.ContentResolver;
import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.android.systemui.vrtoxin.QuickAccess.QuickAccessBar;
import com.android.systemui.R;

/**
 * Wrapper view with background which contains {@link QSPanel}
 */
public class QSContainer extends FrameLayout {

    private static final int QS_TYPE_PANEL  = 0;
    private static final int QS_TYPE_BAR    = 1;
    private static final int QS_TYPE_HIDDEN = 2;

    private HorizontalScrollView mQSBarContainer;
    private QuickAccessBar mQSBar;
    private QSPanel mQSPanel;

    private boolean mTaskManagerShowing;
    private LinearLayout mTaskManagerPanel;

    private int mHeightOverride = -1;
    private final int mPadding;
    private final int mQSBarContainerHeight;

    private boolean mShowBrightnessSlider;
    private int mQSType;

    public QSContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        final ContentResolver resolver = context.getContentResolver();
        mPadding = context.getResources().getDimensionPixelSize(R.dimen.qs_container_padding_top_bottom);
        mQSBarContainerHeight = context.getResources().getDimensionPixelSize(R.dimen.qab_button_size);
        mQSType = Settings.System.getIntForUser(resolver,
                Settings.System.QS_TYPE, 1, UserHandle.USER_CURRENT);
        mShowBrightnessSlider = Settings.System.getIntForUser(resolver,
                Settings.System.QS_SHOW_BRIGHTNESS_SLIDER, 1, UserHandle.USER_CURRENT) == 1;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mQSPanel = (QSPanel) findViewById(R.id.quick_settings_panel);
        mQSBarContainer =
                (HorizontalScrollView) findViewById(R.id.quick_access_bar_container);
        mQSBar = (QuickAccessBar) findViewById(R.id.quick_access_bar);
        mTaskManagerPanel = (LinearLayout) findViewById(R.id.task_manager_panel);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        mQSPanel.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        mQSBarContainer.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        mTaskManagerPanel.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        int height = 0;
        if (mShowBrightnessSlider || mQSType == QS_TYPE_PANEL) {
            height += mQSPanel.getMeasuredHeight();
        }
        if (mQSType == QS_TYPE_BAR) {
            height += mQSBarContainer.getMeasuredHeight();
        }
        if (mShowBrightnessSlider || mQSType != QS_TYPE_HIDDEN && !mTaskManagerShowing) {
            height += mPadding * 2;
        }
        if (mTaskManagerShowing) {
            height += mTaskManagerPanel.getMeasuredHeight();
        }
        setMeasuredDimension(width, height);
    }

    private static int exactly(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int qsPanelheight = mQSPanel.getMeasuredHeight();
        final int qsBarTop = mPadding + (!mShowBrightnessSlider ? 0 : qsPanelheight);
        mQSPanel.layout(0, mPadding, mQSPanel.getMeasuredWidth(),
                mPadding + mQSPanel.getMeasuredHeight());
        mQSBarContainer.layout(0, qsBarTop, mQSBarContainer.getMeasuredWidth(),
                qsBarTop + mQSBarContainer.getMeasuredHeight());
        mTaskManagerPanel.layout(0, mPadding, mTaskManagerPanel.getMeasuredWidth(),
                mPadding + mTaskManagerPanel.getMeasuredHeight());
        updateBottom();
    }

    public void setQSType(int qsType) {
        mQSType = qsType;
        if (mQSType == QS_TYPE_PANEL) {
            mQSBarContainer.setVisibility(View.GONE);
            mQSBar.setVisibility(View.GONE);
            mQSPanel.setVisibility(View.INVISIBLE);
            setVisibility(View.INVISIBLE);
        } else if (mQSType == QS_TYPE_BAR) {
            mQSPanel.setVisibility(mShowBrightnessSlider ? View.INVISIBLE : View.GONE);
            mQSBarContainer.setVisibility(View.INVISIBLE);
            mQSBar.setVisibility(View.INVISIBLE);
            setVisibility(View.INVISIBLE);
        } else {
            mQSBarContainer.setVisibility(View.GONE);
            mQSBar.setVisibility(View.GONE);
            mQSPanel.setVisibility(mShowBrightnessSlider ? View.INVISIBLE : View.GONE);
            setVisibility(mShowBrightnessSlider ? View.INVISIBLE : View.INVISIBLE);
        }
        requestLayout();
    }

    public void setShowBrightnessSlider(boolean ShowBrightnessSlider) {
        mShowBrightnessSlider = ShowBrightnessSlider;
        setVisibility(mQSType != QS_TYPE_HIDDEN || mShowBrightnessSlider
                ? View.INVISIBLE : View.GONE);
        requestLayout();
    }

    public void updateVisibility(boolean keyguardShowing, boolean visible, boolean taskManagerShowing) {
        if (mQSType == QS_TYPE_HIDDEN && !mShowBrightnessSlider && !mTaskManagerShowing) {
            return;
        }
        mTaskManagerShowing = taskManagerShowing;
        setVisibility(keyguardShowing && !visible ? View.INVISIBLE : View.VISIBLE);
        if (mQSType == QS_TYPE_PANEL) {
            mQSPanel.setVisibility(visible && !mTaskManagerShowing ?  View.VISIBLE : View.GONE);
            mTaskManagerPanel.setVisibility(visible && mTaskManagerShowing ?  View.VISIBLE : View.GONE);
        } else if (mQSType == QS_TYPE_BAR) {
            mQSBarContainer.setVisibility(visible && !mTaskManagerShowing ?  View.VISIBLE : View.GONE);
            mQSBar.setVisibility(visible && !mTaskManagerShowing ?  View.VISIBLE : View.GONE);
            mQSPanel.setVisibility(visible && !mTaskManagerShowing && mShowBrightnessSlider ? View.VISIBLE : View.GONE);
            mTaskManagerPanel.setVisibility(visible && mTaskManagerShowing ?  View.VISIBLE : View.GONE);
        } else {
            mQSPanel.setVisibility(mShowBrightnessSlider || !mTaskManagerShowing ? View.VISIBLE : View.GONE);
            mTaskManagerPanel.setVisibility(mTaskManagerShowing ?  View.VISIBLE : View.GONE);
        }
        requestLayout();
    }

    public void setListening(boolean listening) {
        mQSPanel.setListening(listening && (mQSType == QS_TYPE_PANEL || mShowBrightnessSlider));
        mQSBar.setListening(listening && mQSType == QS_TYPE_BAR);
    }

    /**
     * Overrides the height of this view (post-layout), so that the content is clipped to that
     * height and the background is set to that height.
     *
     * @param heightOverride the overridden height
     */
    public void setHeightOverride(int heightOverride) {
        mHeightOverride = heightOverride;
        updateBottom();
    }

    /**
     * The height this view wants to be. This is different from {@link #getMeasuredHeight} such that
     * during closing the detail panel, this already returns the smaller height.
     */
    public int getDesiredHeight() {
        if (mQSPanel.isClosingDetail()) {
            return mQSPanel.getGridHeight() + getPaddingTop() + getPaddingBottom();
        } else if (mQSType == QS_TYPE_HIDDEN && !mShowBrightnessSlider) {
            return 0;
        } else if (mTaskManagerShowing) {
            return mTaskManagerPanel.getMeasuredHeight() + mPadding;
        } else {
            return getMeasuredHeight();
        }
    }

    private void updateBottom() {
        int height = mHeightOverride != -1 ? mHeightOverride : getMeasuredHeight();
        int taskHeight = mTaskManagerPanel.getMeasuredHeight() + mPadding;
        if (mTaskManagerShowing) {
            setBottom(getTop() + taskHeight);
        } else {
            setBottom(getTop() + height);
        }
    }
}
