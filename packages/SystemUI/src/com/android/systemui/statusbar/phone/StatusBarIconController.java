/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.util.NotificationColorUtil;
import com.android.internal.util.vrtoxin.ColorHelper;
import com.android.internal.util.vrtoxin.DeviceUtils;
import com.android.internal.util.vrtoxin.FontHelper;
import com.android.internal.util.vrtoxin.StatusBarColorHelper;
import com.android.keyguard.CarrierText;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.SignalClusterView;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.policy.Clock;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Controls everything regarding the icons in the status bar and on Keyguard, including, but not
 * limited to: notification icons, signal cluster, additional status icons, and clock in the status
 * bar.
 */
public class StatusBarIconController {

    public static final long DEFAULT_TINT_ANIMATION_DURATION = 120;

    public static final String ICON_BLACKLIST = "icon_blacklist";

    private static final int BATTERY_COLORS              = 0;
    private static final int STATUS_NETWORK_ICON_COLORS  = 1;

    private Context mContext;
    private View mStatusBar;
    private PhoneStatusBar mPhoneStatusBar;
    private KeyguardStatusBarView mKeyguardStatusBarView;
    private Interpolator mLinearOutSlowIn;
    private Interpolator mFastOutSlowIn;
    private DemoStatusIcons mDemoStatusIcons;
    private NotificationColorUtil mNotificationColorUtil;
    private Clock mCenterClock;

    private LinearLayout mSystemIconArea;
    private LinearLayout mStatusIcons;
    private LinearLayout mStatusIconsKeyguard;
    private SignalClusterView mSignalCluster;
    private SignalClusterView mSignalClusterKeyguard;
    private IconMerger mNotificationIcons;
    private View mNotificationIconArea;
    private ImageView mMoreIcon;
    private BatteryMeterView mBatteryMeterView;
    private BatteryMeterView mBatteryMeterViewKeyguard;
    private TextView mBatteryLevelKeyguard;
    private ClockController mClockController;
    private View mCenterClockLayout;
    private Ticker mTicker;
    private View mTickerView;

    private int mBatteryColor;
    private int mBatteryColorOld;
    private int mBatteryColorTint;
    private int mBatteryTextColor;
    private int mBatteryTextColorOld;
    private int mBatteryTextColorTint;
    private int mNetworkSignalColor;
    private int mNetworkSignalColorOld;
    private int mNetworkSignalColorTint;
    private int mNoSimColor;
    private int mNoSimColorOld;
    private int mNoSimColorTint;
    private int mAirplaneModeColor;
    private int mAirplaneModeColorOld;
    private int mAirplaneModeColorTint;
    private int mStatusIconColor;
    private int mStatusIconColorOld;
    private int mStatusIconColorTint;
    private int mNotificationIconColor;
    private int mNotificationIconColorTint;
//    private int mNotifCountIconColor;
//    private int mNotifCountIconColorTint;
//    private int mNotifCountTextColor;
//    private int mNotifCountTextColorTint;
    private int mTickerIconColor;
    private int mTickerIconColorTint;
    private int mTickerTextColor;
    private int mTickerTextColorTint;
    private float mDarkIntensity;

    private int mIconSize;
    private int mIconHPadding;

    private boolean mTransitionPending;
    private boolean mTintChangePending;
    private float mPendingDarkIntensity;

    private Animator mColorTransitionAnimator;
    private ValueAnimator mTintAnimator;

    private int mColorToChange;

    private boolean mShowTicker = false;
    private boolean mTicking;
    private boolean mTickingEnd = false;

    private final Handler mHandler;
    private boolean mTransitionDeferring;
    private long mTransitionDeferringStartTime;
    private long mTransitionDeferringDuration;

    private final ArraySet<String> mIconBlacklist = new ArraySet<>();

    private final Runnable mTransitionDeferringDoneRunnable = new Runnable() {
        @Override
        public void run() {
            mTransitionDeferring = false;
        }
    };

    public StatusBarIconController(Context context, View statusBar, KeyguardStatusBarView keyguardStatusBar,
            PhoneStatusBar phoneStatusBar) {
        mContext = context;
        mStatusBar = statusBar;
        mPhoneStatusBar = phoneStatusBar;
        mKeyguardStatusBarView = keyguardStatusBar;
        mNotificationColorUtil = NotificationColorUtil.getInstance(context);
        mSystemIconArea = (LinearLayout) statusBar.findViewById(R.id.system_icon_area);
        mStatusIcons = (LinearLayout) statusBar.findViewById(R.id.statusIcons);
        mSignalCluster = (SignalClusterView) statusBar.findViewById(R.id.signal_cluster);
        mSignalClusterKeyguard = (SignalClusterView) keyguardStatusBar.findViewById(R.id.signal_cluster);
        mNotificationIconArea = statusBar.findViewById(R.id.notification_icon_area_inner);
        mNotificationIcons = (IconMerger) statusBar.findViewById(R.id.notificationIcons);
        mMoreIcon = (ImageView) statusBar.findViewById(R.id.moreIcon);
        mNotificationIcons.setOverflowIndicator(mMoreIcon);
        mStatusIconsKeyguard = (LinearLayout) keyguardStatusBar.findViewById(R.id.statusIcons);
        mBatteryMeterView = (BatteryMeterView) statusBar.findViewById(R.id.battery);
        mBatteryMeterViewKeyguard = (BatteryMeterView) keyguardStatusBar.findViewById(R.id.battery);
        mBatteryLevelKeyguard = ((TextView) keyguardStatusBar.findViewById(R.id.battery_level));
        mLinearOutSlowIn = AnimationUtils.loadInterpolator(mContext,
                android.R.interpolator.linear_out_slow_in);
        mFastOutSlowIn = AnimationUtils.loadInterpolator(mContext,
                android.R.interpolator.fast_out_slow_in);
        mHandler = new Handler();
        mClockController = new ClockController(statusBar, mNotificationIcons, mHandler);
        mCenterClockLayout = statusBar.findViewById(R.id.center_clock_layout);
        mCenterClock = (Clock) statusBar.findViewById(R.id.center_clock);
        updateResources();

        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();

        setUpCustomColors();
        setCenterClockVisibility();
    }

    private void setUpCustomColors() {
        mBatteryColor = StatusBarColorHelper.getBatteryColor(mContext);
        mBatteryColorOld = mBatteryColor;
        mBatteryColorTint = mBatteryColor;
        mBatteryTextColor = StatusBarColorHelper.getBatteryTextColor(mContext);
        mBatteryTextColorOld = mBatteryTextColor;
        mBatteryTextColorTint = mBatteryTextColor;
        mNetworkSignalColor = StatusBarColorHelper.getNetworkSignalColor(mContext);
        mNetworkSignalColorOld = mNetworkSignalColor;
        mNetworkSignalColorTint = mNetworkSignalColor;
        mNoSimColor = StatusBarColorHelper.getNoSimColor(mContext);
        mNoSimColorOld = mNoSimColor;
        mNoSimColorTint = mNoSimColor;
        mAirplaneModeColor = StatusBarColorHelper.getAirplaneModeColor(mContext);
        mAirplaneModeColorOld = mAirplaneModeColor;
        mAirplaneModeColorTint = mAirplaneModeColor;
        mStatusIconColor = StatusBarColorHelper.getStatusIconColor(mContext);
        mStatusIconColorOld = mStatusIconColor;
        mStatusIconColorTint = mStatusIconColor;
        mNotificationIconColor = StatusBarColorHelper.getNotificationIconColor(mContext);
        mNotificationIconColorTint = mNotificationIconColor;
        /*mNotifCountIconColor = StatusBarColorHelper.getNotifCountIconColor(mContext);
        mNotifCountIconColorTint = mNotifCountIconColor;
        mNotifCountTextColor = StatusBarColorHelper.getNotifCountTextColor(mContext);
        mNotifCountTextColorTint = mNotifCountTextColor;*/
        mTickerIconColor = StatusBarColorHelper.getTickerIconColor(mContext);
        mTickerIconColorTint = mNotificationIconColor;
        mTickerTextColor = StatusBarColorHelper.getTickerTextColor(mContext);
        mTickerTextColorTint = mTickerTextColor;

        mColorTransitionAnimator = createColorTransitionAnimator(0, 1);
    }

    public void updateResources() {
        mIconSize = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.status_bar_icon_size);
        mIconHPadding = mContext.getResources().getDimensionPixelSize(
                R.dimen.status_bar_icon_padding);
        mClockController.updateFontSize();
        setCenterClockVisibility();
    }

    public void addSystemIcon(String slot, int index, int viewIndex, StatusBarIcon icon) {
        boolean blocked = mIconBlacklist.contains(slot);
        StatusBarIconView view = new StatusBarIconView(mContext, slot, null, blocked);
        view.set(icon);
        mStatusIcons.addView(view, viewIndex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, mIconSize));
        view = new StatusBarIconView(mContext, slot, null, blocked);
        view.set(icon);
        mStatusIconsKeyguard.addView(view, viewIndex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, mIconSize));
        applyIconTint();
        updateStatusIconKeyguardColor();
    }

    public void updateSystemIcon(String slot, int index, int viewIndex,
            StatusBarIcon old, StatusBarIcon icon) {
        StatusBarIconView view = (StatusBarIconView) mStatusIcons.getChildAt(viewIndex);
        view.set(icon);
        view = (StatusBarIconView) mStatusIconsKeyguard.getChildAt(viewIndex);
        view.set(icon);
        applyIconTint();
        updateStatusIconKeyguardColor();
    }

    public void removeSystemIcon(String slot, int index, int viewIndex) {
        mStatusIcons.removeViewAt(viewIndex);
        mStatusIconsKeyguard.removeViewAt(viewIndex);
    }

    public void updateNotificationIcons(NotificationData notificationData) {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                mIconSize + 2*mIconHPadding, mPhoneStatusBar.getStatusBarHeight());

        ArrayList<NotificationData.Entry> activeNotifications =
                notificationData.getActiveNotifications();
        final int N = activeNotifications.size();
        ArrayList<StatusBarIconView> toShow = new ArrayList<>(N);

        // Filter out ambient notifications and notification children.
        for (int i = 0; i < N; i++) {
            NotificationData.Entry ent = activeNotifications.get(i);
            if (notificationData.isAmbient(ent.key)
                    && !NotificationData.showNotificationEvenIfUnprovisioned(ent.notification)) {
                continue;
            }
            if (!PhoneStatusBar.isTopLevelChild(ent)) {
                continue;
            }
            toShow.add(ent.icon);
        }

        ArrayList<View> toRemove = new ArrayList<>();
        for (int i=0; i<mNotificationIcons.getChildCount(); i++) {
            View child = mNotificationIcons.getChildAt(i);
            if (!toShow.contains(child)) {
                toRemove.add(child);
            }
        }

        final int toRemoveCount = toRemove.size();
        for (int i = 0; i < toRemoveCount; i++) {
            mNotificationIcons.removeView(toRemove.get(i));
        }

        for (int i=0; i<toShow.size(); i++) {
            View v = toShow.get(i);
            if (v.getParent() == null) {
                mNotificationIcons.addView(v, i, params);
            }
        }

        // Resort notification icons
        final int childCount = mNotificationIcons.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View actual = mNotificationIcons.getChildAt(i);
            StatusBarIconView expected = toShow.get(i);
            if (actual == expected) {
                continue;
            }
            mNotificationIcons.removeView(expected);
            mNotificationIcons.addView(expected, i);
        }

        applyNotificationIconsTint();
        setCenterClockVisibility();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CENTER_CLOCK_HIDE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_CENTER_CLOCK_NUMBER_OF_NOTIFICATION_ICONS),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);

            if (uri.equals(Settings.System.getUriFor(
                Settings.System.STATUS_BAR_CENTER_CLOCK_HIDE))
                || uri.equals(Settings.System.getUriFor(
                Settings.System.STATUS_BAR_CENTER_CLOCK_NUMBER_OF_NOTIFICATION_ICONS))) {
                setCenterClockVisibility();
            }
        }
    }

    public void hideSystemIconArea(boolean animate) {
        animateHide(mSystemIconArea, animate);
        if (ClockController.mClockLocation == ClockController.STYLE_CLOCK_CENTER) {
            animateHide(mCenterClock, false);
        }
    }

    public void showSystemIconArea(boolean animate) {
        animateShow(mSystemIconArea, animate);
        if (ClockController.mClockLocation == ClockController.STYLE_CLOCK_CENTER) {
            animateShow(mCenterClock, animate);
            setCenterClockVisibility();
        }
    }

    public void hideNotificationIconArea(boolean animate) {
        animateHide(mNotificationIconArea, animate);
    }

    public void showNotificationIconArea(boolean animate) {
        animateShow(mNotificationIconArea, animate);
    }

    public void setCenterClockVisibility() {
        final ContentResolver resolver = mContext.getContentResolver();

        final boolean centerClock = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CLOCK, 0) == 2;

        final boolean forceHide = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CENTER_CLOCK_HIDE, 1) == 1;
        final int maxAllowedIcons = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CENTER_CLOCK_NUMBER_OF_NOTIFICATION_ICONS, 3);
        boolean forceHideByNumberOfIcons = false;
        int currentVisibleNotificationIcons = 0;

        if (mNotificationIcons != null) {
            currentVisibleNotificationIcons = mNotificationIcons.getChildCount();
        }
        if (forceHide && currentVisibleNotificationIcons >= maxAllowedIcons) {
            forceHideByNumberOfIcons = true;
        }
        if (mCenterClock != null && centerClock) {
            mCenterClock.setVisibility(centerClock && !forceHideByNumberOfIcons
                    ? View.VISIBLE : View.GONE);
        }
    }

    public void setClockVisibility(boolean visible) {
        mClockController.setVisibility(visible);
    }

    public void dump(PrintWriter pw) {
        int N = mStatusIcons.getChildCount();
        pw.println("  system icons: " + N);
        for (int i=0; i<N; i++) {
            StatusBarIconView ic = (StatusBarIconView) mStatusIcons.getChildAt(i);
            pw.println("    [" + i + "] icon=" + ic);
        }
    }

    public void dispatchDemoCommand(String command, Bundle args) {
        if (mDemoStatusIcons == null) {
            mDemoStatusIcons = new DemoStatusIcons(mStatusIcons, mIconSize);
        }
        mDemoStatusIcons.dispatchDemoCommand(command, args);
    }

    /**
     * Hides a view.
     */
    private void animateHide(final View v, boolean animate) {
        v.animate().cancel();
        if (!animate) {
            v.setAlpha(0f);
            v.setVisibility(View.INVISIBLE);
            return;
        }
        v.animate()
                .alpha(0f)
                .setDuration(160)
                .setStartDelay(0)
                .setInterpolator(PhoneStatusBar.ALPHA_OUT)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.setVisibility(View.INVISIBLE);
                        if (mTickingEnd) {
                            mTicking = false;
                            mTickingEnd = false;
                        }
                    }
                });
    }

    /**
     * Shows a view, and synchronizes the animation with Keyguard exit animations, if applicable.
     */
    private void animateShow(View v, boolean animate) {
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (!animate) {
            v.setAlpha(1f);
            return;
        }
        v.animate()
                .alpha(1f)
                .setDuration(320)
                .setInterpolator(PhoneStatusBar.ALPHA_IN)
                .setStartDelay(50)

                // We need to clean up any pending end action from animateHide if we call
                // both hide and show in the same frame before the animation actually gets started.
                // cancel() doesn't really remove the end action.
                .withEndAction(null);

        // Synchronize the motion with the Keyguard fading if necessary.
        if (mPhoneStatusBar.isKeyguardFadingAway()) {
            v.animate()
                    .setDuration(mPhoneStatusBar.getKeyguardFadingAwayDuration())
                    .setInterpolator(mLinearOutSlowIn)
                    .setStartDelay(mPhoneStatusBar.getKeyguardFadingAwayDelay())
                    .start();
        }
    }

    public void setIconsDark(boolean dark, boolean animate) {
        if (!animate) {
            setIconTintInternal(dark ? 1.0f : 0.0f);
        } else if (mTransitionPending) {
            deferIconTintChange(dark ? 1.0f : 0.0f);
        } else if (mTransitionDeferring) {
            animateIconTint(dark ? 1.0f : 0.0f,
                    Math.max(0, mTransitionDeferringStartTime - SystemClock.uptimeMillis()),
                    mTransitionDeferringDuration);
        } else {
            animateIconTint(dark ? 1.0f : 0.0f, 0 /* delay */, DEFAULT_TINT_ANIMATION_DURATION);
        }
    }

    private void animateIconTint(float targetDarkIntensity, long delay,
            long duration) {
        if (mTintAnimator != null) {
            mTintAnimator.cancel();
        }
        if (mDarkIntensity == targetDarkIntensity) {
            return;
        }
        mTintAnimator = ValueAnimator.ofFloat(mDarkIntensity, targetDarkIntensity);
        mTintAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setIconTintInternal((Float) animation.getAnimatedValue());
            }
        });
        mTintAnimator.setDuration(duration);
        mTintAnimator.setStartDelay(delay);
        mTintAnimator.setInterpolator(mFastOutSlowIn);
        mTintAnimator.start();
    }

    private void setIconTintInternal(float darkIntensity) {
        mDarkIntensity = darkIntensity;
        mBatteryColorTint = (int) ArgbEvaluator.getInstance().evaluate(mDarkIntensity,
                mBatteryColor, StatusBarColorHelper.getBatteryColorDark(mContext));
        mBatteryTextColorTint = (int) ArgbEvaluator.getInstance().evaluate(mDarkIntensity,
                mBatteryTextColor, StatusBarColorHelper.getBatteryTextColorDark(mContext));
        mNetworkSignalColorTint = (int) ArgbEvaluator.getInstance().evaluate(mDarkIntensity,
                mNetworkSignalColor, StatusBarColorHelper.getNetworkSignalColorDark(mContext));
        mNoSimColorTint = (int) ArgbEvaluator.getInstance().evaluate(mDarkIntensity,
                mNoSimColor, StatusBarColorHelper.getNoSimColorDark(mContext));
        mAirplaneModeColorTint = (int) ArgbEvaluator.getInstance().evaluate(mDarkIntensity,
                mAirplaneModeColor, StatusBarColorHelper.getAirplaneModeColorDark(mContext));
        mStatusIconColorTint = (int) ArgbEvaluator.getInstance().evaluate(mDarkIntensity,
                mStatusIconColor, StatusBarColorHelper.getStatusIconColorDark(mContext));
        mNotificationIconColorTint = (int) ArgbEvaluator.getInstance().evaluate(mDarkIntensity,
                mNotificationIconColor, StatusBarColorHelper.getNotificationIconColorDark(mContext));
        /*mNotifCountIconColorTint = (int) ArgbEvaluator.getInstance().evaluate(mDarkIntensity,
                mNotifCountIconColor, StatusBarColorHelper.getNotifCountIconColorDark(mContext));
        mNotifCountTextColorTint = (int) ArgbEvaluator.getInstance().evaluate(mDarkIntensity,
                mNotifCountTextColor, StatusBarColorHelper.getNotifCountTextColorDark(mContext));*/
        mTickerIconColorTint = (int) ArgbEvaluator.getInstance().evaluate(mDarkIntensity,
                mTickerIconColor, StatusBarColorHelper.getTickerIconColorDark(mContext));
        mTickerTextColorTint = (int) ArgbEvaluator.getInstance().evaluate(mDarkIntensity,
                mTickerTextColor, StatusBarColorHelper.getTickerTextColorDark(mContext));
        applyIconTint();
    }

    private void deferIconTintChange(float darkIntensity) {
        if (mTintChangePending && darkIntensity == mPendingDarkIntensity) {
            return;
        }
        mTintChangePending = true;
        mPendingDarkIntensity = darkIntensity;
    }

    private void applyIconTint() {
        mBatteryMeterView.setBatteryColors(mBatteryColorTint);
        mBatteryMeterView.setTextColor(mBatteryTextColorTint);
        mSignalCluster.setIconTint(
                mNetworkSignalColorTint, mNoSimColorTint, mAirplaneModeColorTint);
        for (int i = 0; i < mStatusIcons.getChildCount(); i++) {
            StatusBarIconView v = (StatusBarIconView) mStatusIcons.getChildAt(i);
            v.setColorFilter(mStatusIconColorTint, Mode.MULTIPLY);
        }
        mMoreIcon.setColorFilter(mNotificationIconColorTint, Mode.MULTIPLY);
        applyNotificationIconsTint();
        if (mTicker != null && mTickerView != null) {
            mTicker.setTextColor(mTickerTextColorTint);
        }
    }

    private void applyNotificationIconsTint() {
        for (int i = 0; i < mNotificationIcons.getChildCount(); i++) {
            StatusBarIconView v = (StatusBarIconView) mNotificationIcons.getChildAt(i);
            boolean isPreL = Boolean.TRUE.equals(v.getTag(R.id.icon_is_pre_L));
            boolean colorize = !isPreL || isGrayscale(v);
            if (colorize) {
                v.setImageTintList(ColorStateList.valueOf(mNotificationIconColorTint));
            }
        }
        if (mTicker != null && mTickerView != null) {
            mTicker.setIconColorTint(ColorStateList.valueOf(mTickerIconColorTint));
        }
    }

    private boolean isGrayscale(StatusBarIconView v) {
        Object isGrayscale = v.getTag(R.id.icon_is_grayscale);
        if (isGrayscale != null) {
            return Boolean.TRUE.equals(isGrayscale);
        }
        boolean grayscale = mNotificationColorUtil.isGrayscaleIcon(v.getDrawable());
        v.setTag(R.id.icon_is_grayscale, grayscale);
        return grayscale;
    }

    public void appTransitionPending() {
        mTransitionPending = true;
    }

    public void appTransitionCancelled() {
        if (mTransitionPending && mTintChangePending) {
            mTintChangePending = false;
            animateIconTint(mPendingDarkIntensity, 0 /* delay */, DEFAULT_TINT_ANIMATION_DURATION);
        }
        mTransitionPending = false;
    }

    public void appTransitionStarting(long startTime, long duration) {
        if (mTransitionPending && mTintChangePending) {
            mTintChangePending = false;
            animateIconTint(mPendingDarkIntensity,
                    Math.max(0, startTime - SystemClock.uptimeMillis()),
                    duration);

        } else if (mTransitionPending) {

            // If we don't have a pending tint change yet, the change might come in the future until
            // startTime is reached.
            mTransitionDeferring = true;
            mTransitionDeferringStartTime = startTime;
            mTransitionDeferringDuration = duration;
            mHandler.removeCallbacks(mTransitionDeferringDoneRunnable);
            mHandler.postAtTime(mTransitionDeferringDoneRunnable, startTime);
        }
        mTransitionPending = false;
    }

    public static ArraySet<String> getIconBlacklist(String blackListStr) {
        ArraySet<String> ret = new ArraySet<String>();
        if (blackListStr != null) {
            String[] blacklist = blackListStr.split(",");
            for (String slot : blacklist) {
                if (!TextUtils.isEmpty(slot)) {
                    ret.add(slot);
                }
            }
        }
        return ret;
    }

    private ValueAnimator createColorTransitionAnimator(float start, float end) {
        ValueAnimator animator = ValueAnimator.ofFloat(start, end);

        animator.setDuration(500);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                float position = animation.getAnimatedFraction();
                if (mColorToChange == BATTERY_COLORS) {
                    final int blended = ColorHelper.getBlendColor(
                            mBatteryColorOld, mBatteryColor, position);
                    final int blendedText = ColorHelper.getBlendColor(
                            mBatteryTextColorOld, mBatteryTextColor, position);
                    mBatteryMeterView.setBatteryColors(blended);
                    mBatteryMeterView.setTextColor(blendedText);
                } else if (mColorToChange == STATUS_NETWORK_ICON_COLORS) {
                    final int blendedStatus = ColorHelper.getBlendColor(
                            mStatusIconColorOld, mStatusIconColor, position);
                    final int blendedSignal = ColorHelper.getBlendColor(
                            mNetworkSignalColorOld, mNetworkSignalColor, position);
                    final int blendedNoSim = ColorHelper.getBlendColor(
                            mNoSimColorOld, mNoSimColor, position);
                    final int blendedAirPlaneMode = ColorHelper.getBlendColor(
                            mAirplaneModeColorOld, mAirplaneModeColor, position);
                    for (int i = 0; i < mStatusIcons.getChildCount(); i++) {
                        StatusBarIconView v = (StatusBarIconView) mStatusIcons.getChildAt(i);
                        v.setColorFilter(blendedStatus, Mode.MULTIPLY);
                    }
                    mSignalCluster.setIconTint(
                            blendedSignal, blendedNoSim, blendedAirPlaneMode);
                }
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mColorToChange == BATTERY_COLORS) {
                    mBatteryColorOld = mBatteryColor;
                    mBatteryTextColorOld = mBatteryTextColor;
                    mBatteryColorTint = mBatteryColor;
                    mBatteryTextColorTint = mBatteryTextColor;
                } else if (mColorToChange == STATUS_NETWORK_ICON_COLORS) {
                    mStatusIconColorOld = mStatusIconColor;
                    mNetworkSignalColorOld = mNetworkSignalColor;
                    mNoSimColorOld = mNoSimColor;
                    mAirplaneModeColorOld = mAirplaneModeColor;
                    mStatusIconColorTint = mStatusIconColor;
                    mNetworkSignalColorTint = mNetworkSignalColor;
                    mNoSimColorTint = mNoSimColor;
                    mAirplaneModeColorTint = mAirplaneModeColor;
                }
            }
        });
        return animator;
    }

    public void updateBatteryIndicator(int indicator) {
        mBatteryMeterView.updateBatteryIndicator(indicator);
        mBatteryMeterViewKeyguard.updateBatteryIndicator(indicator);
        mKeyguardStatusBarView.updateBatteryLevelVisibility();
    }

    public void updateBatteryTextVisibility(boolean show) {
        mBatteryMeterView.setTextVisibility(show);
        mBatteryMeterViewKeyguard.setTextVisibility(show);
        mKeyguardStatusBarView.updateBatteryLevelVisibility();
    }

    public void updateBatteryCircleDots(int interval, int length) {
        mBatteryMeterView.updateCircleDots(interval, length);
        mBatteryMeterViewKeyguard.updateCircleDots(interval, length);
    }

    public void updateShowChargeAnimation(boolean show) {
        mBatteryMeterView.setShowChargeAnimation(show);
        mBatteryMeterViewKeyguard.setShowChargeAnimation(show);
    }

    public void updateCutOutBatteryText(boolean cutOut) {
        mBatteryMeterView.setCutOutText(cutOut);
        mBatteryMeterViewKeyguard.setCutOutText(cutOut);
    }

    public void updateBatteryColors(boolean animate) {
        mBatteryColor = StatusBarColorHelper.getBatteryColor(mContext);
        mBatteryTextColor = StatusBarColorHelper.getBatteryTextColor(mContext);
        if (animate) {
            mColorToChange = BATTERY_COLORS;
            mColorTransitionAnimator.start();
        } else {
            mBatteryMeterView.setBatteryColors(mBatteryColor);
            mBatteryMeterView.setTextColor(mBatteryTextColor);
            mBatteryColorOld = mBatteryColor;
            mBatteryTextColorOld = mBatteryTextColor;
            mBatteryColorTint = mBatteryColor;
            mBatteryTextColorTint = mBatteryTextColor;
        }
        mBatteryMeterViewKeyguard.setBatteryColors(mBatteryColor);
        mBatteryMeterViewKeyguard.setTextColor(mBatteryTextColor);
        mBatteryLevelKeyguard.setTextColor(mBatteryTextColor);
    }

    public void updateStatusNetworkIconColors(boolean animate) {
        mStatusIconColor = StatusBarColorHelper.getStatusIconColor(mContext);
        mNetworkSignalColor = StatusBarColorHelper.getNetworkSignalColor(mContext);
        mNoSimColor = StatusBarColorHelper.getNoSimColor(mContext);
        mAirplaneModeColor = StatusBarColorHelper.getAirplaneModeColor(mContext);
        if (animate) {
            mColorToChange = STATUS_NETWORK_ICON_COLORS;
            mColorTransitionAnimator.start();
        } else {
            mSignalCluster.setIconTint(
                    mNetworkSignalColor, mNoSimColor, mAirplaneModeColor);
            mStatusIconColorOld = mStatusIconColor;
            mNetworkSignalColorOld = mNetworkSignalColor;
            mNoSimColorOld = mNoSimColor;
            mAirplaneModeColorOld = mAirplaneModeColor;
            mStatusIconColorTint = mStatusIconColor;
            mNetworkSignalColorTint = mNetworkSignalColor;
            mNoSimColorTint = mNoSimColor;
            mAirplaneModeColorTint = mAirplaneModeColor;
        }
        mSignalClusterKeyguard.setIconTint(
                mNetworkSignalColor, mNoSimColor, mAirplaneModeColor);
        updateStatusIconKeyguardColor();
    }

    private void updateStatusIconKeyguardColor() {
        if (mStatusIconsKeyguard.getChildCount() > 0) {
            for (int index = 0; index < mStatusIconsKeyguard.getChildCount(); index++) {
                StatusBarIconView v = (StatusBarIconView) mStatusIconsKeyguard.getChildAt(index);
                v.setColorFilter(mStatusIconColor, Mode.MULTIPLY);
            }
        }
    }

    public void updateShowTicker(boolean show) {
        mShowTicker = show;
        if (mShowTicker && (mTicker == null || mTickerView == null)) {
            inflateTickerView();
        }
    }

    public void updateNotificationIconColor() {
        mNotificationIconColor = StatusBarColorHelper.getNotificationIconColor(mContext);
        mNotificationIconColorTint = mNotificationIconColor;
        for (int i = 0; i < mNotificationIcons.getChildCount(); i++) {
            StatusBarIconView v = (StatusBarIconView) mNotificationIcons.getChildAt(i);
            boolean isPreL = Boolean.TRUE.equals(v.getTag(R.id.icon_is_pre_L));
            boolean colorize = !isPreL || isGrayscale(v);
            if (colorize) {
                v.setImageTintList(ColorStateList.valueOf(mNotificationIconColor));
            }
        }
        mMoreIcon.setColorFilter(mNotificationIconColor, Mode.MULTIPLY);
//        updateNotifCountIconColor(mNotifCountIconColor);
        updateTickerIconColor(mTickerIconColor);
    }

    private void updateTickerIconColor(int color) {
        if (mTicker != null && mTickerView != null) {
            mTicker.setIconColorTint(ColorStateList.valueOf(color));
        }
    }

    public void updateTickerTextColor() {
        mTickerTextColor = StatusBarColorHelper.getTickerTextColor(mContext);
        mTickerTextColorTint = mTickerTextColor;
        if (mTicker != null && mTickerView != null) {
            mTicker.setTextColor(mTickerTextColor);
        }
    }

    public int getCurrentVisibleNotificationIcons() {
        return mNotificationIcons.getChildCount();
    }

    private void inflateTickerView() {
        final ViewStub tickerStub = (ViewStub) mStatusBar.findViewById(R.id.ticker_stub);
        if (tickerStub != null) {
            mTickerView = tickerStub.inflate();
            mTicker = new MyTicker(mContext, mStatusBar);

            TickerView tickerView = (TickerView) mStatusBar.findViewById(R.id.tickerText);
            tickerView.mTicker = mTicker;
            updateTickerIconColor(mNotificationIconColor);
            updateTickerTextColor();
        } else {
            mShowTicker = false;
        }
    }

    public void addTickerEntry(StatusBarNotification n) {
        mTicker.addEntry(n);
    }

    public void removeTickerEntry(StatusBarNotification n) {
        mTicker.removeEntry(n);
    }

    public void haltTicker() {
        if (mTicking) {
            mTicker.halt();
        }
    }

    private class MyTicker extends Ticker {
        MyTicker(Context context, View sb) {
            super(context, sb);
        }

        @Override
        public void tickerStarting() {
            if (!mShowTicker) return;
            mTicking = true;
            hideSystemIconArea(true);
            hideNotificationIconArea(true);
            animateShow(mTickerView, true);
        }

        @Override
        public void tickerDone() {
            if (!mShowTicker) return;
            animateShow(mSystemIconArea, true);
            if (ClockController.mClockLocation == ClockController.STYLE_CLOCK_CENTER) {
                animateShow(mCenterClock, true);
                setCenterClockVisibility();
            }
            animateShow(mNotificationIconArea, true);
            mTickingEnd = true;
            animateHide(mTickerView, true);
        }

        public void tickerHalting() {
            if (!mShowTicker) return;
            animateShow(mSystemIconArea, true);
            if (ClockController.mClockLocation == ClockController.STYLE_CLOCK_CENTER) {
                animateShow(mCenterClock, true);
            }
            animateShow(mNotificationIconArea, true);
            // we do not animate the ticker away at this point, just get rid of it (b/6992707)
            mTickerView.setVisibility(View.GONE);
        }
    }
}
