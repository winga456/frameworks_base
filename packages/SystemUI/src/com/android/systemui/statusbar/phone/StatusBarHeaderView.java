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

package com.android.systemui.statusbar.phone;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.MathUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.util.vrtoxin.FontHelper;
import com.android.internal.util.vrtoxin.SBEHeaderColorHelper;
import com.android.internal.util.vrtoxin.QSColorHelper;
import com.android.internal.util.vrtoxin.WeatherServiceController;
import com.android.internal.util.vrtoxin.WeatherServiceControllerImpl;
import com.android.keyguard.KeyguardStatusView;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.vrtoxin.StatusBarHeaderMachine;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.NetworkControllerImpl.EmergencyListener;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.SignalClusterView;

import java.net.URISyntaxException;
import java.text.NumberFormat;

/**
 * The view to manage the header area in the expanded status bar.
 */
public class StatusBarHeaderView extends RelativeLayout implements View.OnClickListener, View.OnLongClickListener,
        BatteryController.BatteryStateChangeCallback, NextAlarmController.NextAlarmChangeCallback,
        EmergencyListener, WeatherServiceController.Callback, StatusBarHeaderMachine.IStatusBarHeaderMachineObserver {

    private static final int STATUS_BAR_POWER_MENU_OFF = 0;
    private static final int STATUS_BAR_POWER_MENU_DEFAULT = 1;
    private static final int STATUS_BAR_POWER_MENU_INVERTED = 2;

    private boolean mExpanded;
    private boolean mListening;

    private ViewGroup mSystemIconsContainer;
    private ViewGroup mWeatherContainer;
    private View mSystemIconsSuperContainer;
    private BatteryMeterView mBatteryMeterView;
    private View mDateGroup;
    private View mClock;
    private TextView mTime;
    private TextView mAmPm;
    private MultiUserSwitch mMultiUserSwitch;
    private ImageView mMultiUserAvatar;
    private TextView mDateCollapsed;
    private TextView mDateExpanded;
    private LinearLayout mSystemIcons;
    private SignalClusterView mSignalCluster;
    private View mSettingsButton;
    private View mTaskManagerButton;
    private View mQsDetailHeader;
    private TextView mQsDetailHeaderTitle;
    private Switch mQsDetailHeaderSwitch;
    private ImageView mQsDetailHeaderProgress;
    private TextView mEmergencyCallsOnly;
    private TextView mBatteryLevel;
    private TextView mAlarmStatus;
    private TextView mWeatherLine1, mWeatherLine2;
    private ImageView mPowerMenuButton;

    private boolean mShowEmergencyCallsOnly;
    private boolean mAlarmShowing;
    private AlarmManager.AlarmClockInfo mNextAlarm;

    private int mCollapsedHeight;
    private int mExpandedHeight;

    private int mMultiUserExpandedMargin;
    private int mMultiUserCollapsedMargin;

    private int mClockMarginBottomExpanded;
    private int mClockMarginBottomCollapsed;
    private int mMultiUserSwitchWidthCollapsed;
    private int mMultiUserSwitchWidthExpanded;

    private int mClockCollapsedSize;
    private int mClockExpandedSize;
    private int mHeaderFontStyle = FontHelper.FONT_NORMAL;

    // HeadsUp button
    private View mVRToxinButton;
    private boolean mShowVRToxinButton;

    /**
     * In collapsed QS, the clock is scaled down a bit post-layout to allow for a nice
     * transition. These values determine that factor.
     */
    private float mClockCollapsedScaleFactor;
    private float mAvatarCollapsedScaleFactor;

    private ActivityStarter mActivityStarter;
    private BatteryController mBatteryController;
    private NextAlarmController mNextAlarmController;
    private WeatherServiceController mWeatherController;
    private QSPanel mQSPanel;

    private final Rect mClipBounds = new Rect();

    private boolean mCaptureValues;
    private boolean mSignalClusterDetached;
    private final LayoutValues mCollapsedValues = new LayoutValues();
    private final LayoutValues mExpandedValues = new LayoutValues();
    private final LayoutValues mCurrentValues = new LayoutValues();

    private float mCurrentT;
    private boolean mShowingDetail;
    private boolean mDetailTransitioning;

    private ImageView mBackgroundImage;
    private Drawable mCurrentBackground;
    private float mLastHeight;

    private SettingsObserver mSettingsObserver;
    private boolean mShowWeather;
    private boolean mShowWeatherLocation;

    private ContentObserver mContentObserver = new ContentObserver(new Handler()) {
   	 @Override
    	 public void onChange(boolean selfChange, Uri uri) {
		 showPowerMenuButton();
	    }
    };


    public StatusBarHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        showPowerMenuButton();  
        mContext = context;

    }

    private int mPowerMenuButtonStyle;

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setOnLongClickListener(this);
        mSystemIconsSuperContainer = findViewById(R.id.system_icons_super_container);
        mSystemIconsContainer = (ViewGroup) findViewById(R.id.system_icons_container);
        mSystemIconsSuperContainer.setOnClickListener(this);
        mSystemIconsSuperContainer.setOnLongClickListener(this);
        mBatteryMeterView = (BatteryMeterView) findViewById(R.id.battery);
        mDateGroup = findViewById(R.id.date_group);
        mDateGroup.setOnClickListener(this);
        mDateGroup.setOnLongClickListener(this);
        mClock = findViewById(R.id.clock);
        mClock.setOnClickListener(this);
        mClock.setOnLongClickListener(this);
        mTime = (TextView) findViewById(R.id.time_view);
        mAmPm = (TextView) findViewById(R.id.am_pm_view);
        mMultiUserSwitch = (MultiUserSwitch) findViewById(R.id.multi_user_switch);
        mMultiUserSwitch.setOnLongClickListener(this);
        mMultiUserAvatar = (ImageView) findViewById(R.id.multi_user_avatar);
        mDateCollapsed = (TextView) findViewById(R.id.date_collapsed);
        mDateExpanded = (TextView) findViewById(R.id.date_expanded);
        mSettingsButton = findViewById(R.id.settings_button);
        mSettingsButton.setOnClickListener(this);
        mSettingsButton.setOnLongClickListener(this);
        mVRToxinButton = findViewById(R.id.vrtoxin_button);
        mVRToxinButton.setOnClickListener(this);
        mVRToxinButton.setOnLongClickListener(this);
        mPowerMenuButton = (ImageView) findViewById(R.id.power_menu_button);
        mPowerMenuButton.setOnClickListener(this);
        mPowerMenuButton.setOnLongClickListener(this);
        if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.ENABLE_TASK_MANAGER, 0) == 1) {
            mTaskManagerButton = findViewById(R.id.task_manager_button);
        }
        mQsDetailHeader = findViewById(R.id.qs_detail_header);
        mQsDetailHeader.setAlpha(0);
        mQsDetailHeaderTitle = (TextView) mQsDetailHeader.findViewById(android.R.id.title);
        mQsDetailHeaderSwitch = (Switch) mQsDetailHeader.findViewById(android.R.id.toggle);
        mQsDetailHeaderProgress = (ImageView) findViewById(R.id.qs_detail_header_progress);
        mEmergencyCallsOnly = (TextView) findViewById(R.id.header_emergency_calls_only);
        mBatteryLevel = (TextView) findViewById(R.id.battery_level);
        mAlarmStatus = (TextView) findViewById(R.id.alarm_status);
        mAlarmStatus.setOnClickListener(this);
        mSignalCluster = (SignalClusterView) findViewById(R.id.signal_cluster);
        mSystemIcons = (LinearLayout) findViewById(R.id.system_icons);
        mWeatherContainer = (LinearLayout) findViewById(R.id.weather_container);
        mWeatherContainer.setOnClickListener(this);
        mWeatherContainer.setOnLongClickListener(this);
        mWeatherLine1 = (TextView) findViewById(R.id.weather_line_1);
        mWeatherLine2 = (TextView) findViewById(R.id.weather_line_2);
        mSettingsObserver = new SettingsObserver(new Handler());
        mBackgroundImage = (ImageView) findViewById(R.id.background_image);
        loadDimens();
        updatePowerMenuButtonVisibility();
        updateVisibilities();
        updateClockScale();
        updateAvatarScale();
        addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if ((right - left) != (oldRight - oldLeft)) {
                    // width changed, update clipping
                    setClipping(getHeight());
                }
                boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
                mTime.setPivotX(rtl ? mTime.getWidth() : 0);
                mTime.setPivotY(mTime.getBaseline());
                updateAmPmTranslation();
            }
        });
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRect(mClipBounds);
            }
        });
        requestCaptureValues();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mCaptureValues) {
            if (mExpanded) {
                captureLayoutValues(mExpandedValues);
            } else {
                captureLayoutValues(mCollapsedValues);
            }
            mCaptureValues = false;
            updateLayoutValues(mCurrentT);
        }
        mAlarmStatus.setX(mDateGroup.getLeft() + mDateCollapsed.getRight());
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        FontSizeUtils.updateFontSize(mBatteryLevel, R.dimen.battery_level_text_size);
        FontSizeUtils.updateFontSize(mEmergencyCallsOnly,
                R.dimen.qs_emergency_calls_only_text_size);
        FontSizeUtils.updateFontSize(mDateCollapsed, R.dimen.qs_date_collapsed_size);
        FontSizeUtils.updateFontSize(mDateExpanded, R.dimen.qs_date_collapsed_size);
        FontSizeUtils.updateFontSize(mAlarmStatus, R.dimen.qs_date_collapsed_size);
        FontSizeUtils.updateFontSize(this, android.R.id.title, R.dimen.qs_detail_header_text_size);
        FontSizeUtils.updateFontSize(this, android.R.id.toggle, R.dimen.qs_detail_header_text_size);
        FontSizeUtils.updateFontSize(mAmPm, R.dimen.qs_time_collapsed_size);
        FontSizeUtils.updateFontSize(this, R.id.empty_time_view, R.dimen.qs_time_expanded_size);

        mEmergencyCallsOnly.setText(com.android.internal.R.string.emergency_calls_only);

        mClockCollapsedSize = getResources().getDimensionPixelSize(R.dimen.qs_time_collapsed_size);
        mClockExpandedSize = getResources().getDimensionPixelSize(R.dimen.qs_time_expanded_size);
        mClockCollapsedScaleFactor = (float) mClockCollapsedSize / (float) mClockExpandedSize;
        updateClockScale();
        updateClockCollapsedMargin();
    }

    private void updateClockCollapsedMargin() {
        Resources res = getResources();
        int padding = res.getDimensionPixelSize(R.dimen.clock_collapsed_bottom_margin);
        int largePadding = res.getDimensionPixelSize(
                R.dimen.clock_collapsed_bottom_margin_large_text);
        float largeFactor = (MathUtils.constrain(getResources().getConfiguration().fontScale, 1.0f,
                FontSizeUtils.LARGE_TEXT_SCALE) - 1f) / (FontSizeUtils.LARGE_TEXT_SCALE - 1f);
        mClockMarginBottomCollapsed = Math.round((1 - largeFactor) * padding + largeFactor * largePadding);
        requestLayout();
    }

    private void requestCaptureValues() {
        mCaptureValues = true;
        requestLayout();
    }

    private void loadDimens() {
        mCollapsedHeight = getResources().getDimensionPixelSize(R.dimen.status_bar_header_height);
        mExpandedHeight = getResources().getDimensionPixelSize(
                R.dimen.status_bar_header_height_expanded);
        mMultiUserExpandedMargin =
                getResources().getDimensionPixelSize(R.dimen.multi_user_switch_expanded_margin);
        mMultiUserCollapsedMargin =
                getResources().getDimensionPixelSize(R.dimen.multi_user_switch_collapsed_margin);
        mClockMarginBottomExpanded =
                getResources().getDimensionPixelSize(R.dimen.clock_expanded_bottom_margin);
        updateClockCollapsedMargin();
        mMultiUserSwitchWidthCollapsed =
                getResources().getDimensionPixelSize(R.dimen.multi_user_switch_width_collapsed);
        mMultiUserSwitchWidthExpanded =
                getResources().getDimensionPixelSize(R.dimen.multi_user_switch_width_expanded);
        mAvatarCollapsedScaleFactor =
                getResources().getDimensionPixelSize(R.dimen.multi_user_avatar_collapsed_size)
                / (float) mMultiUserAvatar.getLayoutParams().width;
        mClockCollapsedSize = getResources().getDimensionPixelSize(R.dimen.qs_time_collapsed_size);
        mClockExpandedSize = getResources().getDimensionPixelSize(R.dimen.qs_time_expanded_size);
        mClockCollapsedScaleFactor = (float) mClockCollapsedSize / (float) mClockExpandedSize;

    }

    public void setActivityStarter(ActivityStarter activityStarter) {
        mActivityStarter = activityStarter;
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        if (mBatteryMeterView != null) {
            mBatteryMeterView.setBatteryController(batteryController);
        }
    }

    public void setNextAlarmController(NextAlarmController nextAlarmController) {
        mNextAlarmController = nextAlarmController;
    }

    public void setWeatherController(WeatherServiceController weatherController) {
        mWeatherController = weatherController;
    }

    public int getCollapsedHeight() {
        return mCollapsedHeight;
    }

    public int getExpandedHeight() {
        return mExpandedHeight;
    }

    public void setListening(boolean listening) {
        if (listening == mListening) {
            return;
        }
        mListening = listening;
        updateListeners();
    }

    public void setExpanded(boolean expanded) {
        boolean changed = expanded != mExpanded;
        mExpanded = expanded;
        if (changed) {
            updateEverything();
        }
    }

    public boolean getExpanded() {
        return mExpanded;
    }

    public boolean getSettingsButtonVisibility() {
        return mSettingsButton.getVisibility() == View.VISIBLE;
    }

    public void updateEverything() {
        updateHeights();
        updateVisibilities();
        updateSystemIconsLayoutParams();
        updateClickTargets();
        updateMultiUserSwitch();
        updatePowerMenuButtonVisibility();
        updateClockScale();
        updateAvatarScale();
        updateClockLp();
        requestCaptureValues();
    }

    private void updateHeights() {
        int height = mExpanded ? mExpandedHeight : mCollapsedHeight;
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp.height != height) {
            lp.height = height;
            setLayoutParams(lp);
        }
    }

    private void updateVisibilities() {
        mDateCollapsed.setVisibility(mExpanded && mAlarmShowing ? View.VISIBLE : View.INVISIBLE);
        mDateExpanded.setVisibility(mExpanded && mAlarmShowing ? View.INVISIBLE : View.VISIBLE);
        mAlarmStatus.setVisibility(mExpanded && mAlarmShowing ? View.VISIBLE : View.INVISIBLE);
        mSettingsButton.setVisibility(mExpanded ? View.VISIBLE : View.INVISIBLE);
        if (mTaskManagerButton != null) {
            mTaskManagerButton.setVisibility(mExpanded ? View.VISIBLE : View.GONE);
        }
        mQsDetailHeader.setVisibility(mExpanded && mShowingDetail? View.VISIBLE : View.INVISIBLE);
        if (mSignalCluster != null) {
            updateSignalClusterDetachment();
        }
        mEmergencyCallsOnly.setVisibility(mExpanded && mShowEmergencyCallsOnly ? VISIBLE : GONE);
        mBatteryLevel.setVisibility(mExpanded ? View.VISIBLE : View.GONE);
        updateVRToxinButtonVisibility();
        updateWeatherVisibility();
        applyHeaderBackgroundShadow();
    }

    private void updateVRToxinButtonVisibility() {
        mVRToxinButton.setVisibility(mExpanded && mShowVRToxinButton ? View.VISIBLE : View.GONE);
    }

    private void updateWeatherVisibility() {
        mWeatherContainer.setVisibility(mExpanded && mShowWeather ? View.VISIBLE : View.GONE);
        mWeatherLine2.setVisibility(mExpanded && mShowWeather && mShowWeatherLocation ? View.VISIBLE : View.GONE);
    }

    private void updateSignalClusterDetachment() {
        boolean detached = mExpanded;
        if (detached != mSignalClusterDetached) {
            if (detached) {
                getOverlay().add(mSignalCluster);
            } else {
                reattachSignalCluster();
            }
        }
        mSignalClusterDetached = detached;
    }

    private void reattachSignalCluster() {
        getOverlay().remove(mSignalCluster);
        mSystemIcons.addView(mSignalCluster, 1);
    }

    private void updateSystemIconsLayoutParams() {
        RelativeLayout.LayoutParams lp = (LayoutParams) mSystemIconsSuperContainer.getLayoutParams();
        int vrtoxin = mVRToxinButton.getVisibility() != View.GONE
                ? mVRToxinButton.getId() : mSettingsButton.getId();
        int rule = mExpanded
                ? vrtoxin
                : mMultiUserSwitch.getId();
        if (rule != lp.getRules()[RelativeLayout.START_OF]) {
            lp.addRule(RelativeLayout.START_OF, rule);
            mSystemIconsSuperContainer.setLayoutParams(lp);
        }
    }

    private void updateListeners() {
        if (mListening) {
            mSettingsObserver.observe();
            mBatteryController.addStateChangedCallback(this);
            mNextAlarmController.addStateChangedCallback(this);
            mWeatherController.addCallback(this);
        } else {
            mBatteryController.removeStateChangedCallback(this);
            mNextAlarmController.removeStateChangedCallback(this);
            mWeatherController.removeCallback(this);
            mSettingsObserver.unobserve();
        }
    }

    private void updateAvatarScale() {
        if (mExpanded) {
            mMultiUserAvatar.setScaleX(1f);
            mMultiUserAvatar.setScaleY(1f);
        } else {
            mMultiUserAvatar.setScaleX(mAvatarCollapsedScaleFactor);
            mMultiUserAvatar.setScaleY(mAvatarCollapsedScaleFactor);
        }
    }

    private void updateClockScale() {
        mTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, mExpanded
                ? mClockExpandedSize
                : mClockCollapsedSize);
        mTime.setScaleX(1f);
        mTime.setScaleY(1f);
        updateAmPmTranslation();
    }

    private void updateAmPmTranslation() {
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        mAmPm.setTranslationX((rtl ? 1 : -1) * mTime.getWidth() * (1 - mTime.getScaleX()));
    }

    private void updatePowerMenuButtonVisibility() {
        mPowerMenuButton.setVisibility(mExpanded
                && (mPowerMenuButtonStyle != STATUS_BAR_POWER_MENU_OFF) ? View.VISIBLE
                : View.GONE);
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        // could not care less
    }

    @Override
    public void onPowerSaveChanged() {
        // could not care less
    }

    @Override
    public void onNextAlarmChanged(AlarmManager.AlarmClockInfo nextAlarm) {
        mNextAlarm = nextAlarm;
        if (nextAlarm != null) {
            mAlarmStatus.setText(KeyguardStatusView.formatNextAlarm(getContext(), nextAlarm));
        }
        mAlarmShowing = nextAlarm != null;
        updateEverything();
        requestCaptureValues();
    }

    @Override
    public void onWeatherChanged(WeatherServiceController.WeatherInfo info) {
        if (info.temp == null || info.condition == null) {
            mWeatherLine1.setText(mContext.getString(R.string.weather_info_not_available));
            mWeatherLine2.setText(null);
        } else {
            mWeatherLine1.setText(mContext.getString(
                    R.string.status_bar_expanded_header_weather_format,
                    info.temp,
                    info.condition));
            mWeatherLine2.setText(info.city);
        }
    }

    private void updateClickTargets() {
        mMultiUserSwitch.setClickable(mExpanded);
        mMultiUserSwitch.setFocusable(mExpanded);
        mSystemIconsSuperContainer.setClickable(mExpanded);
        mSystemIconsSuperContainer.setFocusable(mExpanded);
        mAlarmStatus.setClickable(mNextAlarm != null && mNextAlarm.getShowIntent() != null);
    }

    private void updateClockLp() {
        int marginBottom = mExpanded
                ? mClockMarginBottomExpanded
                : mClockMarginBottomCollapsed;
        LayoutParams lp = (LayoutParams) mDateGroup.getLayoutParams();
        if (marginBottom != lp.bottomMargin) {
            lp.bottomMargin = marginBottom;
            mDateGroup.setLayoutParams(lp);
        }
    }

    private void updateMultiUserSwitch() {
        int marginEnd;
        int width;
        if (mExpanded) {
            marginEnd = mMultiUserExpandedMargin;
            width = mMultiUserSwitchWidthExpanded;
        } else {
            marginEnd = mMultiUserCollapsedMargin;
            width = mMultiUserSwitchWidthCollapsed;
        }
        MarginLayoutParams lp = (MarginLayoutParams) mMultiUserSwitch.getLayoutParams();
        if (marginEnd != lp.getMarginEnd() || lp.width != width) {
            lp.setMarginEnd(marginEnd);
            lp.width = width;
            mMultiUserSwitch.setLayoutParams(lp);
        }
    }

    public void setExpansion(float t) {
        if (!mExpanded) {
            t = 0f;
        }
        mCurrentT = t;
        float height = mCollapsedHeight + t * (mExpandedHeight - mCollapsedHeight);
        if (height != mLastHeight) {
            if (height < mCollapsedHeight) {
                height = mCollapsedHeight;
            }
            if (height > mExpandedHeight) {
                height = mExpandedHeight;
            }
            final float heightFinal = height;
            setClipping(heightFinal);

            post(new Runnable() {
                 public void run() {
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBackgroundImage.getLayoutParams();
                    params.height = (int)heightFinal;
                    mBackgroundImage.setLayoutParams(params);
                }
            });

            updateLayoutValues(t);
            mLastHeight = heightFinal;
        }
    }

    private void updateLayoutValues(float t) {
        if (mCaptureValues) {
            return;
        }
        mCurrentValues.interpoloate(mCollapsedValues, mExpandedValues, t);
        applyLayoutValues(mCurrentValues);
    }

    private void setClipping(float height) {
        mClipBounds.set(getPaddingLeft(), 0, getWidth() - getPaddingRight(), (int) height);
        setClipBounds(mClipBounds);
        invalidateOutline();
    }

    public void setUserInfoController(UserInfoController userInfoController) {
        userInfoController.addListener(new UserInfoController.OnUserInfoChangedListener() {
            @Override
            public void onUserInfoChanged(String name, Drawable picture) {
                mMultiUserAvatar.setImageDrawable(picture);
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v == mSettingsButton) {
            startSettingsActivity();
        } else if (v == mSystemIconsSuperContainer) {
            startBatteryActivity();
        } else if (v == mAlarmStatus && mNextAlarm != null) {
            PendingIntent showIntent = mNextAlarm.getShowIntent();
            if (showIntent != null) {
                mActivityStarter.startPendingIntentDismissingKeyguard(showIntent);
            }
        } else if (v == mClock) {
            startClockActivity();
        } else if (v == mDateGroup) {
            startDateActivity();
        } else if (v == mWeatherContainer) {
            startForecastActivity();
        } else if (v == mVRToxinButton) {
            startVRToxinActivity();
        } else if (v == mPowerMenuButton) {
            startPowerMenuAction();
        }
        mQSPanel.vibrateTile(20);
    }

    @Override
    public boolean onLongClick(View v) {
        if (v == mSettingsButton) {
            startSettingsLongClickActivity();
        } else if (v == mSystemIconsSuperContainer) {
            startBatteryLongClickActivity();
        } else if (v == mClock) {
            startClockLongClickActivity();
        } else if (v == mDateGroup) {
            startDateLongClickActivity();
        } else if (v == mWeatherContainer) {
            startForecastLongClickActivity();
        } else if (v == mMultiUserSwitch) {
            startUserLongClickActivity();
        } else if (v == mVRToxinButton) {
            startVRToxinLongClickActivity();
        } else if (mPowerMenuButtonStyle == STATUS_BAR_POWER_MENU_DEFAULT) {
            triggerPowerMenuDialog();
        } else if (mPowerMenuButtonStyle == STATUS_BAR_POWER_MENU_INVERTED) {
            goToSleep();
        } else if (v == this) {
            startExpandedActivity();
        }
        return false;
    }

    private void startExpandedActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$ExpandedHeaderSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startSettingsActivity() {
        mActivityStarter.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS),
                true /* dismissShade */);
    }

    private void startSettingsLongClickActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$StatusBarSettingsSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startBatteryActivity() {
        mActivityStarter.startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
                true /* dismissShade */);
    }

    private void startUserLongClickActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$UserSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startBatteryLongClickActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$BatterySaverSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startClockActivity() {
        Intent clockShortcutIntent = null;
        String clockShortcutIntentUri = Settings.System.getStringForUser(
                mContext.getContentResolver(), Settings.System.CLOCK_SHORTCUT, UserHandle.USER_CURRENT);
        if(clockShortcutIntentUri != null) {
            try {
                clockShortcutIntent = Intent.parseUri(clockShortcutIntentUri, 0);
            } catch (URISyntaxException e) {
                clockShortcutIntent = null;
            }
        }

        if(clockShortcutIntent != null) {
            mActivityStarter.startActivity(clockShortcutIntent, true);
        } else {
            mActivityStarter.startActivity(
                    new Intent(AlarmClock.ACTION_SHOW_ALARMS), true /* dismissShade */);
        }
    }

    private void startClockLongClickActivity() {
        Intent clockLongShortcutIntent = null;
        String clockLongShortcutIntentUri = Settings.System.getStringForUser(
                mContext.getContentResolver(), Settings.System.CLOCK_LONG_SHORTCUT, UserHandle.USER_CURRENT);
        if(clockLongShortcutIntentUri != null) {
            try {
                clockLongShortcutIntent = Intent.parseUri(clockLongShortcutIntentUri, 0);
            } catch (URISyntaxException e) {
                clockLongShortcutIntent = null;
            }
        }

        if(clockLongShortcutIntent != null) {
            mActivityStarter.startActivity(clockLongShortcutIntent, true);
        } else {
            mActivityStarter.startActivity(new Intent(AlarmClock.ACTION_SET_ALARM),
                    true /* dismissShade */);
        }
    }

    private void startDateActivity() {
        Intent calendarShortcutIntent = null;
        String calendarShortcutIntentUri = Settings.System.getStringForUser(
                mContext.getContentResolver(), Settings.System.CALENDAR_SHORTCUT, UserHandle.USER_CURRENT);
        if(calendarShortcutIntentUri != null) {
            try {
                calendarShortcutIntent = Intent.parseUri(calendarShortcutIntentUri, 0);
            } catch (URISyntaxException e) {
                calendarShortcutIntent = null;
            }
        }

        if(calendarShortcutIntent != null) {
            mActivityStarter.startActivity(calendarShortcutIntent, true);
        } else {
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            ContentUris.appendId(builder, System.currentTimeMillis());
            Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
            mActivityStarter.startActivity(intent, true /* dismissShade */);
        }
    }

    private void startDateLongClickActivity() {
        Intent calendarLongShortcutIntent = null;
        String calendarLongShortcutIntentUri = Settings.System.getStringForUser(
                mContext.getContentResolver(), Settings.System.CALENDAR_LONG_SHORTCUT, UserHandle.USER_CURRENT);
        if(calendarLongShortcutIntentUri != null) {
            try {
                calendarLongShortcutIntent = Intent.parseUri(calendarLongShortcutIntentUri, 0);
            } catch (URISyntaxException e) {
                calendarLongShortcutIntent = null;
            }
        }

        if(calendarLongShortcutIntent != null) {
            mActivityStarter.startActivity(calendarLongShortcutIntent, true);
        } else {
            Intent intent = new Intent(Intent.ACTION_INSERT);
                intent.setData(Events.CONTENT_URI);
            mActivityStarter.startActivity(intent, true /* dismissShade */);
        }
    }

    private void startForecastActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(WeatherServiceControllerImpl.COMPONENT_WEATHER_FORECAST);
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startForecastLongClickActivity() {
        Intent weatherLongShortcutIntent = null;
        String weatherLongShortcutIntentUri = Settings.System.getStringForUser(
                mContext.getContentResolver(), Settings.System.WEATHER_LONG_SHORTCUT, UserHandle.USER_CURRENT);
        if(weatherLongShortcutIntentUri != null) {
            try {
                weatherLongShortcutIntent = Intent.parseUri(weatherLongShortcutIntentUri, 0);
            } catch (URISyntaxException e) {
                weatherLongShortcutIntent = null;
            }
        }

        if(weatherLongShortcutIntent != null) {
            mActivityStarter.startActivity(weatherLongShortcutIntent, true);
        } else {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName("net.cyanide.weather",
            "net.cyanide.weather.SettingsActivity");
            mActivityStarter.startActivity(intent, true /* dismissShade */);
        }
    }

    private void startVRToxinActivity() {
        Intent vrtoxinShortcutIntent = null;
            String vrtoxinShortcutIntentUri = Settings.System.getStringForUser(
                    mContext.getContentResolver(), Settings.System.VRTOXIN_SHORTCUT, UserHandle.USER_CURRENT);
            if(vrtoxinShortcutIntentUri != null) {
                try {
                    vrtoxinShortcutIntent = Intent.parseUri(vrtoxinShortcutIntentUri, 0);
                } catch (URISyntaxException e) {
                    vrtoxinShortcutIntent = null;
                }
            }

            if(vrtoxinShortcutIntent != null) {
                mActivityStarter.startActivity(vrtoxinShortcutIntent, true);
            } else {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.settings",
                    "com.android.settings.Settings$MainSettingsActivity");
                mActivityStarter.startActivity(intent, true /* dismissShade */);
            }
    }

    private void startVRToxinLongClickActivity() {
        Intent vrtoxinLongShortcutIntent = null;
            String vrtoxinLongShortcutIntentUri = Settings.System.getStringForUser(
                    mContext.getContentResolver(), Settings.System.VRTOXIN_LONG_SHORTCUT, UserHandle.USER_CURRENT);
            if(vrtoxinLongShortcutIntentUri != null) {
                try {
                    vrtoxinLongShortcutIntent = Intent.parseUri(vrtoxinLongShortcutIntentUri, 0);
                } catch (URISyntaxException e) {
                    vrtoxinLongShortcutIntent = null;
                }
            }

            if(vrtoxinLongShortcutIntent != null) {
                mActivityStarter.startActivity(vrtoxinLongShortcutIntent, true);
            } else {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.settings",
                    "com.android.settings.Settings$ChangelogSettingsActivity");
                mActivityStarter.startActivity(intent, true /* dismissShade */);
            }
    }

    private void triggerPowerMenuDialog() {
        Intent intent = new Intent(Intent.ACTION_POWERMENU);
        mContext.sendBroadcast(intent); /* broadcast action */
        mActivityStarter.startActivity(intent,
                true /* dismissShade */);
    }

    private void startPowerMenuAction() {
        if (mPowerMenuButtonStyle == STATUS_BAR_POWER_MENU_DEFAULT) {
            goToSleep();
        } else if (mPowerMenuButtonStyle == STATUS_BAR_POWER_MENU_INVERTED) {
            triggerPowerMenuDialog();
        }
    }

    public void setQSPanel(QSPanel qsp) {
        mQSPanel = qsp;
        if (mQSPanel != null) {
            mQSPanel.setCallback(mQsPanelCallback);
        }
        mMultiUserSwitch.setQsPanel(qsp);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return true;
    }

    @Override
    public void setEmergencyCallsOnly(boolean show) {
        boolean changed = show != mShowEmergencyCallsOnly;
        if (changed) {
            mShowEmergencyCallsOnly = show;
            if (mExpanded) {
                updateEverything();
                requestCaptureValues();
            }
        }
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        // We don't want that everything lights up when we click on the header, so block the request
        // here.
    }

    private void captureLayoutValues(LayoutValues target) {
        target.timeScale = mExpanded ? 1f : mClockCollapsedScaleFactor;
        target.clockY = mClock.getBottom();
        target.dateY = mDateGroup.getTop();
        target.emergencyCallsOnlyAlpha = getAlphaForVisibility(mEmergencyCallsOnly);
        target.alarmStatusAlpha = getAlphaForVisibility(mAlarmStatus);
        target.dateCollapsedAlpha = getAlphaForVisibility(mDateCollapsed);
        target.dateExpandedAlpha = getAlphaForVisibility(mDateExpanded);
        target.avatarScale = mMultiUserAvatar.getScaleX();
        target.avatarX = mMultiUserSwitch.getLeft() + mMultiUserAvatar.getLeft();
        target.avatarY = mMultiUserSwitch.getTop() + mMultiUserAvatar.getTop();
        target.weatherY = mClock.getBottom() - mWeatherLine1.getHeight();
        if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
            target.batteryX = mSystemIconsSuperContainer.getLeft()
                    + mSystemIconsContainer.getRight();
        } else {
            target.batteryX = mSystemIconsSuperContainer.getLeft()
                    + mSystemIconsContainer.getLeft();
        }
        target.batteryY = mSystemIconsSuperContainer.getTop() + mSystemIconsContainer.getTop();
        target.batteryLevelAlpha = getAlphaForVisibility(mBatteryLevel);
        target.settingsAlpha = getAlphaForVisibility(mSettingsButton);
        target.settingsTranslation = mExpanded
                ? 0
                : mMultiUserSwitch.getLeft() - mSettingsButton.getLeft();
        target.settingsRotation = !mExpanded ? 90f : 0f;
        target.vrtoxinAlpha = getAlphaForVisibility(mVRToxinButton);
        target.vrtoxinTranslation = mExpanded
                ? 0
                :  mSettingsButton.getLeft() - mVRToxinButton.getLeft();
        target.powerMenuButtonAlpha = getAlphaForVisibility(mPowerMenuButton);
        if (mPowerMenuButton != null) {
            target.powerMenuButton = mExpanded
                    ? 0
                    : mSettingsButton.getLeft() - mPowerMenuButton.getLeft();
        }
        target.taskManagerAlpha = getAlphaForVisibility(mTaskManagerButton);
        if (mTaskManagerButton != null) {
            target.taskManagerTranslation = mExpanded
                    ? 0
                    : mSettingsButton.getLeft() - mTaskManagerButton.getLeft();
        }
        target.settingsRotation = !mExpanded ? 90f : 0f;
        target.signalClusterAlpha = mSignalClusterDetached ? 0f : 1f;
    }

    private float getAlphaForVisibility(View v) {
        return v == null || v.getVisibility() == View.VISIBLE ? 1f : 0f;
    }

    private void applyAlpha(View v, float alpha) {
        if (v == null || v.getVisibility() == View.GONE) {
            return;
        }
        if (alpha == 0f) {
            v.setVisibility(View.INVISIBLE);
        } else {
            v.setVisibility(View.VISIBLE);
            v.setAlpha(alpha);
        }
    }

    private void applyLayoutValues(LayoutValues values) {
        mTime.setScaleX(values.timeScale);
        mTime.setScaleY(values.timeScale);
        mClock.setY(values.clockY - mClock.getHeight());
        mDateGroup.setY(values.dateY);
        mAlarmStatus.setY(values.dateY - mAlarmStatus.getPaddingTop());
        mWeatherContainer.setY(values.weatherY);
        mMultiUserAvatar.setScaleX(values.avatarScale);
        mMultiUserAvatar.setScaleY(values.avatarScale);
        mMultiUserAvatar.setX(values.avatarX - mMultiUserSwitch.getLeft());
        mMultiUserAvatar.setY(values.avatarY - mMultiUserSwitch.getTop());
        if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
            mSystemIconsSuperContainer.setX(values.batteryX - mSystemIconsContainer.getRight());
        } else {
            mSystemIconsSuperContainer.setX(values.batteryX - mSystemIconsContainer.getLeft());
        }
        mSystemIconsSuperContainer.setY(values.batteryY - mSystemIconsContainer.getTop());
        if (mSignalCluster != null && mExpanded) {
            if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
                mSignalCluster.setX(mSystemIconsSuperContainer.getX()
                        - mSignalCluster.getWidth());
            } else {
                mSignalCluster.setX(mSystemIconsSuperContainer.getX()
                        + mSystemIconsSuperContainer.getWidth());
            }
            mSignalCluster.setY(
                    mSystemIconsSuperContainer.getY() + mSystemIconsSuperContainer.getHeight()/2
                            - mSignalCluster.getHeight()/2);
        } else if (mSignalCluster != null) {
            mSignalCluster.setTranslationX(0f);
            mSignalCluster.setTranslationY(0f);
        }
        mSettingsButton.setTranslationY(mSystemIconsSuperContainer.getTranslationY());
        mSettingsButton.setTranslationX(values.settingsTranslation);
        mSettingsButton.setRotation(values.settingsRotation);
        if (mVRToxinButton != null) {
            mVRToxinButton.setTranslationY(mSystemIconsSuperContainer.getTranslationY());
            mVRToxinButton.setTranslationX(values.vrtoxinTranslation);
            mVRToxinButton.setRotation(values.settingsRotation);
        }
        if (mPowerMenuButton != null) {
            mPowerMenuButton.setTranslationY(mSystemIconsSuperContainer.getTranslationY());
            mPowerMenuButton.setTranslationX(values.settingsTranslation);
            mPowerMenuButton.setRotation(values.settingsRotation);
       }
        if (mTaskManagerButton != null) {
            mTaskManagerButton.setTranslationY(mSystemIconsSuperContainer.getTranslationY());
            mTaskManagerButton.setTranslationX(values.settingsTranslation);
            mTaskManagerButton.setRotation(values.settingsRotation);
        }
        applyAlpha(mEmergencyCallsOnly, values.emergencyCallsOnlyAlpha);
        if (!mShowingDetail && !mDetailTransitioning) {
            // Otherwise it needs to stay invisible
            applyAlpha(mAlarmStatus, values.alarmStatusAlpha);
        }
        applyAlpha(mDateCollapsed, values.dateCollapsedAlpha);
        applyAlpha(mDateExpanded, values.dateExpandedAlpha);
        applyAlpha(mBatteryLevel, values.batteryLevelAlpha);
        applyAlpha(mSettingsButton, values.settingsAlpha);
        applyAlpha(mTaskManagerButton, values.taskManagerAlpha);
        applyAlpha(mWeatherLine1, values.settingsAlpha);
        applyAlpha(mWeatherLine2, values.settingsAlpha);
        applyAlpha(mVRToxinButton, values.vrtoxinAlpha);
        applyAlpha(mPowerMenuButton, values.powerMenuButtonAlpha);
        applyAlpha(mSignalCluster, values.signalClusterAlpha);
        if (!mExpanded) {
            mTime.setScaleX(1f);
            mTime.setScaleY(1f);
        }
        updateAmPmTranslation();
    }

    /**
     * Captures all layout values (position, visibility) for a certain state. This is used for
     * animations.
     */
    private static final class LayoutValues {

        float dateExpandedAlpha;
        float dateCollapsedAlpha;
        float emergencyCallsOnlyAlpha;
        float alarmStatusAlpha;
        float timeScale = 1f;
        float clockY;
        float dateY;
        float avatarScale;
        float avatarX;
        float avatarY;
        float batteryX;
        float batteryY;
        float batteryLevelAlpha;
        float taskManagerAlpha;
        float taskManagerTranslation;
        float settingsAlpha;
        float settingsTranslation;
        float signalClusterAlpha;
        float settingsRotation;
        float weatherY;
        float vrtoxinAlpha;
        float vrtoxinTranslation;
        float powerMenuButton;
        float powerMenuButtonAlpha;

        public void interpoloate(LayoutValues v1, LayoutValues v2, float t) {
            timeScale = v1.timeScale * (1 - t) + v2.timeScale * t;
            clockY = v1.clockY * (1 - t) + v2.clockY * t;
            dateY = v1.dateY * (1 - t) + v2.dateY * t;
            avatarScale = v1.avatarScale * (1 - t) + v2.avatarScale * t;
            avatarX = v1.avatarX * (1 - t) + v2.avatarX * t;
            avatarY = v1.avatarY * (1 - t) + v2.avatarY * t;
            batteryX = v1.batteryX * (1 - t) + v2.batteryX * t;
            batteryY = v1.batteryY * (1 - t) + v2.batteryY * t;
            vrtoxinTranslation =
                    v1.vrtoxinTranslation * (1 - t) + v2.vrtoxinTranslation * t;
            powerMenuButton = v1.powerMenuButton * (1 - t) + v2.powerMenuButton * t;
            taskManagerTranslation =
                    v1.taskManagerTranslation * (1 - t) + v2.taskManagerTranslation * t;
            settingsTranslation = v1.settingsTranslation * (1 - t) + v2.settingsTranslation * t;
            weatherY = v1.weatherY * (1 - t) + v2.weatherY * t;

            float t1 = Math.max(0, t - 0.5f) * 2;
            settingsRotation = v1.settingsRotation * (1 - t1) + v2.settingsRotation * t1;
            emergencyCallsOnlyAlpha =
                    v1.emergencyCallsOnlyAlpha * (1 - t1) + v2.emergencyCallsOnlyAlpha * t1;

            float t2 = Math.min(1, 2 * t);
            signalClusterAlpha = v1.signalClusterAlpha * (1 - t2) + v2.signalClusterAlpha * t2;

            float t3 = Math.max(0, t - 0.7f) / 0.3f;
            batteryLevelAlpha = v1.batteryLevelAlpha * (1 - t3) + v2.batteryLevelAlpha * t3;
            vrtoxinAlpha = v1.vrtoxinAlpha * (1 - t3) + v2.vrtoxinAlpha * t3;
            powerMenuButtonAlpha = v1.powerMenuButtonAlpha * (1 - t3) + v2.powerMenuButtonAlpha * t3;
            taskManagerAlpha = v1.taskManagerAlpha * (1 - t3) + v2.taskManagerAlpha * t3;
            settingsAlpha = v1.settingsAlpha * (1 - t3) + v2.settingsAlpha * t3;
            dateExpandedAlpha = v1.dateExpandedAlpha * (1 - t3) + v2.dateExpandedAlpha * t3;
            dateCollapsedAlpha = v1.dateCollapsedAlpha * (1 - t3) + v2.dateCollapsedAlpha * t3;
            alarmStatusAlpha = v1.alarmStatusAlpha * (1 - t3) + v2.alarmStatusAlpha * t3;
        }
    }

    private final QSPanel.Callback mQsPanelCallback = new QSPanel.Callback() {
        private boolean mScanState;

        @Override
        public void onToggleStateChanged(final boolean state) {
            post(new Runnable() {
                @Override
                public void run() {
                    handleToggleStateChanged(state);
                }
            });
        }

        @Override
        public void onShowingDetail(final QSTile.DetailAdapter detail) {
            mDetailTransitioning = true;
            post(new Runnable() {
                @Override
                public void run() {
                    handleShowingDetail(detail);
                }
            });
        }

        @Override
        public void onScanStateChanged(final boolean state) {
            post(new Runnable() {
                @Override
                public void run() {
                    handleScanStateChanged(state);
                }
            });
        }

        private void handleToggleStateChanged(boolean state) {
            mQsDetailHeaderSwitch.setChecked(state);
        }

        private void handleScanStateChanged(boolean state) {
            if (mScanState == state) return;
            mScanState = state;
            final Animatable anim = (Animatable) mQsDetailHeaderProgress.getDrawable();
            if (state) {
                mQsDetailHeaderProgress.animate().alpha(1f);
                anim.start();
            } else {
                mQsDetailHeaderProgress.animate().alpha(0f);
                anim.stop();
            }
        }

        private void handleShowingDetail(final QSTile.DetailAdapter detail) {
            final boolean showingDetail = detail != null;
            transition(mClock, !showingDetail);
            transition(mDateGroup, !showingDetail);
            transition(mWeatherContainer, !showingDetail);
            transition(mPowerMenuButton, !showingDetail);
            if (mAlarmShowing) {
                transition(mAlarmStatus, !showingDetail);
            }
            transition(mQsDetailHeader, showingDetail);
            mShowingDetail = showingDetail;
            if (showingDetail) {
                mQsDetailHeaderTitle.setText(detail.getTitle());
                mQsDetailHeaderTitle.setTextColor(QSColorHelper.getTextColor(mContext));
                final Boolean toggleState = detail.getToggleState();
                if (toggleState == null) {
                    mQsDetailHeaderSwitch.setVisibility(INVISIBLE);
                    mQsDetailHeader.setClickable(false);
                } else {
                    mQsDetailHeaderSwitch.setVisibility(VISIBLE);
                    mQsDetailHeaderSwitch.setChecked(toggleState);
                    mQsDetailHeader.setClickable(true);
                    mQsDetailHeader.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            boolean checked = !mQsDetailHeaderSwitch.isChecked();
                            mQsDetailHeaderSwitch.setChecked(checked);
                            detail.setToggleState(checked);
                        }
                    });
                }
            } else {
                mQsDetailHeader.setClickable(false);
            }
            updatePowerMenuButtonVisibility();
        }

        private void transition(final View v, final boolean in) {
            if (in) {
                v.bringToFront();
                v.setVisibility(VISIBLE);
            }
            if (v.hasOverlappingRendering()) {
                v.animate().withLayer();
            }
            v.animate()
                    .alpha(in ? 1 : 0)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (!in) {
                                v.setVisibility(INVISIBLE);
                            }
                            mDetailTransitioning = false;
                        }
                    })
                    .start();
        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_SHOW_WEATHER),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_SHOW_WEATHER_LOCATION),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_BG_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_RIPPLE_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_TEXT_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_ICON_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_ALARM_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_CLOCK_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_DATE_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_WEATHER_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_ICON_INDICATOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_SHOW_TEXT),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_CIRCLE_DOT_INTERVAL),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_CIRCLE_DOT_LENGTH),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_SHOW_CHARGE_ANIMATION),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_CUT_OUT_TEXT),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_TEXT_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_NETWORK_ICONS_NO_SIM_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_NETWORK_ICONS_AIRPLANE_MODE_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_HEADER_FONT_STYLE),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.VRTOXIN_BUTTON),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_BATTERY_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE_COLOR),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE_THICKNESS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_CORNER_RADIUS),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE_DASH_WIDTH),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE_DASH_GAP),
                    false, this, UserHandle.USER_ALL);
            updateSettings();
        }

        void unobserve() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_SHOW_WEATHER))
                || uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_SHOW_WEATHER_LOCATION))) {
                updateWeatherSettings();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_RIPPLE_COLOR))) {
                updateBackgroundColor();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_ALARM_COLOR))) {
                updateAlarmColor();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_CLOCK_COLOR))) {
                updateClockColor();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_DATE_COLOR))) {
                updateDateColor();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_WEATHER_COLOR))) {
                updateWeatherColor();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_TEXT_COLOR))) {
                updateTextColor();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_ICON_COLOR))
                || uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_NETWORK_ICONS_NO_SIM_COLOR))
                || uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_NETWORK_ICONS_AIRPLANE_MODE_COLOR))) {
                updateIconColor();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_BATTERY_COLOR))) {
                updateBatteryColor();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_ICON_INDICATOR))) {
                updateBatteryIndicator();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_SHOW_TEXT))) {
                updateBatteryTextVisibility();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_CIRCLE_DOT_INTERVAL))
                || uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_CIRCLE_DOT_LENGTH))) {
                updateBatteryCircleDots();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_SHOW_CHARGE_ANIMATION))) {
                updateShowChargeAnimation();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_CUT_OUT_TEXT))) {
                updateCutOutBatteryText();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_BATTERY_TEXT_COLOR))) {
                updateBatteryTextColor();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_HEADER_FONT_STYLE))) {
                updateHeaderFontStyle();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.VRTOXIN_BUTTON))) {
                updateVRToxinButton();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_BG_COLOR))
                || uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE))
                || uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE_COLOR))
                || uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE_THICKNESS))
                || uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_CORNER_RADIUS))
                || uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE_DASH_WIDTH))
                || uri.equals(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE_DASH_GAP))) {
                setSBEHStroke();
            }

        }

        void updateSettings() {
            updateWeatherSettings();
            updateBackgroundColor();
            updateTextColor();
            updateIconColor();
            updateBatteryIndicator();
            updateBatteryTextVisibility();
            updateBatteryCircleDots();
            updateShowChargeAnimation();
            updateCutOutBatteryText();
            updateCutOutBatteryText();
            updateBatteryTextColor();
            updateHeaderFontStyle();
            updateVRToxinButton();
            updateWeatherColor();
            updateAlarmColor();
            updateBatteryColor();
            updateClockColor();
            updateDateColor();
            setSBEHStroke();
        }
    }

    private void updateBackgroundColor() {
        mMultiUserSwitch.setBackground(getColoredBackgroundDrawable(
                mContext.getDrawable(R.drawable.ripple_drawable), false));
        mSettingsButton.setBackground(getColoredBackgroundDrawable(
                mContext.getDrawable(R.drawable.ripple_drawable), false));
        mVRToxinButton.setBackground(getColoredBackgroundDrawable(
                mContext.getDrawable(R.drawable.ripple_drawable), false));
        mPowerMenuButton.setBackground(getColoredBackgroundDrawable(
                mContext.getDrawable(R.drawable.ripple_drawable), false));
        mSystemIconsSuperContainer.setBackground(getColoredBackgroundDrawable(
                mContext.getDrawable(R.drawable.ripple_drawable), false));
        mAlarmStatus.setBackground(getColoredBackgroundDrawable(
                mContext.getDrawable(R.drawable.ripple_drawable), false));
    }

    private void setSBEHStroke() {
        ContentResolver resolver = mContext.getContentResolver();
        final int mSBEHStroke = Settings.System.getInt(
                resolver, Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE, 1);
        final int mSBEHStrokeColor = Settings.System.getInt(
                resolver, Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE_COLOR, mContext.getResources().getColor(R.color.system_accent_color));
        final int mSBEHStrokeThickness = Settings.System.getInt(
                resolver, Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE_THICKNESS, 4);
        final int mSBEHCornerRadius = Settings.System.getInt(
                resolver, Settings.System.STATUS_BAR_EXPANDED_HEADER_CORNER_RADIUS, 0);
        final int mSBEHCustomDashWidth = Settings.System.getInt(
                resolver, Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE_DASH_GAP, 0);
        final int mSBEHCustomDashGap = Settings.System.getInt(
                resolver, Settings.System.STATUS_BAR_EXPANDED_HEADER_STROKE_DASH_WIDTH, 10);
        final int backgroundColor = SBEHeaderColorHelper.getBackgroundColor(mContext);
        final GradientDrawable gradientDrawable = new GradientDrawable();
        if (mSBEHStroke == 0) { // Disable by setting border color to match bg color
            gradientDrawable.setColor(backgroundColor);
            gradientDrawable.setStroke(0, mSBEHStrokeColor);
            gradientDrawable.setCornerRadius(mSBEHCornerRadius);
            setBackground(gradientDrawable);
        } else if (mSBEHStroke == 1) { // use accent color for border
            gradientDrawable.setColor(backgroundColor);
            gradientDrawable.setStroke(mSBEHStrokeThickness, mContext.getResources().getColor(R.color.system_accent_color),
                    mSBEHCustomDashWidth, mSBEHCustomDashGap);
        } else if (mSBEHStroke == 2) { // use custom border color
            gradientDrawable.setColor(backgroundColor);
            gradientDrawable.setStroke(mSBEHStrokeThickness, mSBEHStrokeColor, mSBEHCustomDashWidth, mSBEHCustomDashGap);
        }

        if (mSBEHStroke != 0) {
            gradientDrawable.setCornerRadius(mSBEHCornerRadius);
            setBackground(gradientDrawable);
        }
    }

    private void updateTextColor() {
        mBatteryLevel.setTextColor(
                SBEHeaderColorHelper.getTextColor(mContext, 255));
        mEmergencyCallsOnly.setTextColor(
                SBEHeaderColorHelper.getTextColor(mContext, 102));
    }

    private void updateWeatherColor() {
        final int weatherColor = SBEHeaderColorHelper.getWeatherColor(mContext);
        mWeatherLine1.setTextColor(weatherColor);
        mWeatherLine2.setTextColor(weatherColor);
    }

    private void updateAlarmColor() {
        final int alarmColor = SBEHeaderColorHelper.getAlarmColor(mContext);
        mAlarmStatus.setTextColor(alarmColor);
        Drawable alarmIcon =
                getResources().getDrawable(R.drawable.ic_access_alarms_small).mutate();
        alarmIcon.setTintList(ColorStateList.valueOf(alarmColor));
        mAlarmStatus.setCompoundDrawablesWithIntrinsicBounds(alarmIcon, null, null, null);
    }

    private void updateBatteryColor() {
        final int batteryColor = SBEHeaderColorHelper.getBatteryColor(mContext);
        mBatteryMeterView.setBatteryColors(batteryColor);
    }

    private void updateClockColor() {
        final int clockColor = SBEHeaderColorHelper.getClockColor(mContext);
        mTime.setTextColor(clockColor);
        mAmPm.setTextColor(clockColor);
    }

    private void updateDateColor() {
        final int dateColor = SBEHeaderColorHelper.getDateColor(mContext);
        mDateCollapsed.setTextColor(dateColor);
        mDateExpanded.setTextColor(dateColor);
    }

    private void updateVRToxinButton() {
        ContentResolver resolver = mContext.getContentResolver();
        mShowVRToxinButton = Settings.System.getInt(resolver,
                Settings.System.VRTOXIN_BUTTON, 1) == 1;
        updateVRToxinButtonVisibility();
    }

    private void updateWeatherSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mShowWeather = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_EXPANDED_HEADER_SHOW_WEATHER, 0) == 1;
        mShowWeatherLocation = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_EXPANDED_HEADER_SHOW_WEATHER_LOCATION, 1) == 1;
        updateWeatherVisibility();
    }

    private void updateIconColor() {
        final int iconColor = SBEHeaderColorHelper.getIconColor(mContext);
        final int settingsIconColor = SBEHeaderColorHelper.getSettingsColor(mContext);
        final int vrtoxinButtonColor = SBEHeaderColorHelper.getVRToxinColor(mContext);
        final int powerMenuColor = SBEHeaderColorHelper.getPowerMenuColor(mContext);
        final int noSimIconColor = SBEHeaderColorHelper.getNoSimIconColor(mContext);
        final int airplaneModeIconColor = SBEHeaderColorHelper.getAirplaneModeIconColor(mContext);

        mSignalCluster.setIconTint(
                iconColor, noSimIconColor, airplaneModeIconColor);
        ((ImageView) mSettingsButton).setImageTintList(ColorStateList.valueOf(settingsIconColor));
        if (mVRToxinButton != null) {
            ((ImageView) mVRToxinButton).setImageTintList(ColorStateList.valueOf(vrtoxinButtonColor));
        }
        if (mPowerMenuButton != null) {
            ((ImageView)mPowerMenuButton).setImageTintList(ColorStateList.valueOf(powerMenuColor));
        }
    }

    private void updateBatteryIndicator() {
        final int indicator = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_EXPANDED_BATTERY_ICON_INDICATOR, 0);

        mBatteryMeterView.updateBatteryIndicator(indicator);
    }

    private void updateBatteryTextVisibility() {
        final boolean show = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_EXPANDED_BATTERY_SHOW_TEXT, 0) == 1;

        mBatteryMeterView.setTextVisibility(show);
    }

    private void updateBatteryCircleDots() {
        final int interval = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_EXPANDED_BATTERY_CIRCLE_DOT_INTERVAL, 0);
        final int length = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_EXPANDED_BATTERY_CIRCLE_DOT_LENGTH, 0);

        mBatteryMeterView.updateCircleDots(interval, length);
    }
    private void updateShowChargeAnimation() {
        final boolean show = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_EXPANDED_BATTERY_SHOW_CHARGE_ANIMATION, 0) == 1;

        mBatteryMeterView.setShowChargeAnimation(show);
    }

    private void updateCutOutBatteryText() {
        final boolean cutOut = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_EXPANDED_BATTERY_CUT_OUT_TEXT, 1) == 1;

        mBatteryMeterView.setCutOutText(cutOut);
    }

    private void updateBatteryTextColor() {
        final int textColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_EXPANDED_BATTERY_TEXT_COLOR, 0xffffffff);

        mBatteryMeterView.setTextColor(textColor);
    }

    private void updateHeaderFontStyle() {
        final int mHeaderFontStyle = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_HEADER_FONT_STYLE, 0);

        getFontStyle(mHeaderFontStyle);
    }

    public void getFontStyle(int font) {
        switch (font) {
            case FontHelper.FONT_NORMAL:
            default:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                mAmPm.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                mTime.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FontHelper.FONT_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                mAmPm.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                mTime.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FontHelper.FONT_BOLD:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                mAmPm.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                mTime.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                mDateExpanded.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FontHelper.FONT_BOLD_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                mAmPm.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                mTime.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FontHelper.FONT_LIGHT:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                mAmPm.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                mTime.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FontHelper.FONT_LIGHT_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                mAmPm.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                mTime.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FontHelper.FONT_THIN:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                mAmPm.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                mTime.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FontHelper.FONT_THIN_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                mAmPm.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                mTime.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FontHelper.FONT_CONDENSED:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                mAmPm.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                mTime.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FontHelper.FONT_CONDENSED_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                mAmPm.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                mTime.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FontHelper.FONT_CONDENSED_LIGHT:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                mAmPm.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                mTime.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FontHelper.FONT_CONDENSED_LIGHT_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                mAmPm.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                mTime.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FontHelper.FONT_CONDENSED_BOLD:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                mAmPm.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                mTime.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FontHelper.FONT_CONDENSED_BOLD_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                mAmPm.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                mTime.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FontHelper.FONT_MEDIUM:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                mAmPm.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                mTime.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FontHelper.FONT_MEDIUM_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mAmPm.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mTime.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FontHelper.FONT_BLACK:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                mAmPm.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                mTime.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FontHelper.FONT_BLACK_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                mAmPm.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                mTime.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                mDateCollapsed.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                mAlarmStatus.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FontHelper.FONT_DANCINGSCRIPT:
                mWeatherLine1.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                mAmPm.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                mTime.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                mDateCollapsed.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                mAlarmStatus.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case FontHelper.FONT_DANCINGSCRIPT_BOLD:
                mWeatherLine1.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                mWeatherLine2.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                mAmPm.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                mTime.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                mDateCollapsed.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                mDateExpanded.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                mAlarmStatus.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case FontHelper.FONT_COMINGSOON:
                mWeatherLine1.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                mAmPm.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                mTime.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                mDateCollapsed.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                mAlarmStatus.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case FontHelper.FONT_NOTOSERIF:
                mWeatherLine1.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                mWeatherLine2.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                mAmPm.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                mTime.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                mDateCollapsed.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                mDateExpanded.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                mAlarmStatus.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case FontHelper.FONT_NOTOSERIF_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                mAmPm.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                mTime.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                mDateCollapsed.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                mDateExpanded.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                mAlarmStatus.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case FontHelper.FONT_NOTOSERIF_BOLD:
                mWeatherLine1.setTypeface(Typeface.create("serif", Typeface.BOLD));
                mWeatherLine2.setTypeface(Typeface.create("serif", Typeface.BOLD));
                mAmPm.setTypeface(Typeface.create("serif", Typeface.BOLD));
                mTime.setTypeface(Typeface.create("serif", Typeface.BOLD));
                mDateCollapsed.setTypeface(Typeface.create("serif", Typeface.BOLD));
                mDateExpanded.setTypeface(Typeface.create("serif", Typeface.BOLD));
                mAlarmStatus.setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case FontHelper.FONT_NOTOSERIF_BOLD_ITALIC:
                mWeatherLine1.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                mWeatherLine2.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                mAmPm.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                mTime.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                mDateCollapsed.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                mDateExpanded.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                mAlarmStatus.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
        }
    }

    private RippleDrawable getColoredBackgroundDrawable(Drawable rd, boolean applyBackgroundColor) {
        RippleDrawable background = (RippleDrawable) rd.mutate();

        background.setColor(ColorStateList.valueOf(
                SBEHeaderColorHelper.getRippleColor(mContext)));
        return background;
    }

    private int getQSType() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QS_TYPE, 0);
    }

@Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        ContentResolver resolver = getContext().getContentResolver();
        // status bar power menu
        resolver.registerContentObserver(Settings.System.getUriFor(
                Settings.System.POWER_MENU_BUTTON), false, mContentObserver);
    }

    private void showPowerMenuButton() {
        ContentResolver resolver = getContext().getContentResolver();
        mPowerMenuButtonStyle = Settings.System.getInt(resolver,
                Settings.System.POWER_MENU_BUTTON, 2);
    }
 
    private void goToSleep() {
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        pm.goToSleep(SystemClock.uptimeMillis());
    }    

    private void doUpdateStatusBarCustomHeader(final Drawable next, final boolean force) {
        if (next != null) {
            if (next != mCurrentBackground) {
                mBackgroundImage.setVisibility(View.VISIBLE);
                setNotificationPanelHeaderBackground(next, force);
                mCurrentBackground = next;
            }
        } else {
            mCurrentBackground = null;
            mBackgroundImage.setVisibility(View.GONE);
        }
    }

    private void setNotificationPanelHeaderBackground(final Drawable dw, final boolean force) {
        if (mBackgroundImage.getDrawable() != null && !force) {
            Drawable[] arrayDrawable = new Drawable[2];
            arrayDrawable[0] = mBackgroundImage.getDrawable();
            arrayDrawable[1] = dw;

            TransitionDrawable transitionDrawable = new TransitionDrawable(arrayDrawable);
            transitionDrawable.setCrossFadeEnabled(true);
            mBackgroundImage.setImageDrawable(transitionDrawable);
            transitionDrawable.startTransition(1000);
        } else {
            mBackgroundImage.setImageDrawable(dw);
        }
        applyHeaderBackgroundShadow();
    }

    private void applyHeaderBackgroundShadow() {
        final int headerShadow = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_CUSTOM_HEADER_SHADOW, 0,
                UserHandle.USER_CURRENT);

        if (mBackgroundImage != null) {
            ColorDrawable shadow = new ColorDrawable(Color.BLACK);
            shadow.setAlpha(headerShadow);
            mBackgroundImage.setForeground(shadow);
        }
    }

    @Override
    public void updateHeader(final Drawable headerImage, final boolean force) {
        post(new Runnable() {
             public void run() {
                 doUpdateStatusBarCustomHeader(headerImage, force);
            }
        });
    }

    @Override
    public void disableHeader() {
        post(new Runnable() {
             public void run() {
                mCurrentBackground = null;
                mBackgroundImage.setVisibility(View.GONE);
            }
        });
    }
}
