/*
 * Copyright (C) 2015 Darkkat
 * Copyright (C) 2016 Brett Rogers (rogersb11)
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

package com.android.systemui.vrtoxin.expansionview.panels;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Typeface;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.internal.util.vrtoxin.DeviceUtils;
import com.android.internal.util.vrtoxin.ExpansionViewColorHelper;

import com.android.systemui.BatteryMeterView;
import com.android.systemui.vrtoxin.NetworkTrafficController;
import com.android.systemui.statusbar.phone.ActivityStarter;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.SignalCallbackAdapter;
import com.android.systemui.R;

public class ExpansionViewActivityPanel extends RelativeLayout implements BatteryController.BatteryStateChangeCallback {

    private final Context mContext;
    private final boolean mSupportsMobileData;

    private final SignalCallback mSignalCallback = new SignalCallback();
    private ActivityStarter mActivityStarter;
    private BatteryController mBatteryController;
    private BatteryMeterView mBatteryMeterView;
    private NetworkController mNetworkController;

    private TextView mBatteryLevel;
    private TextView mCarrierLabel;
    private TextView mWifiLabel;
    private View mNetworkTraffic;
    private View mBatteryLayout;

    private String mCarrierDescription = null;
    private String mWifiDescription = null;

    private boolean mIsNoSims = false;
    private boolean mWifiEnabled = false;
    private boolean mWifiConnected = false;

    private boolean mExpansionViewVibrate = false;

    private boolean mListening = false;

    protected Vibrator mVibrator;

    public ExpansionViewActivityPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mSupportsMobileData = DeviceUtils.deviceSupportsMobileData(mContext);
    }

    public void setUp(ActivityStarter starter, NetworkController nc, BatteryController bc) {
        mActivityStarter = starter;
        mBatteryController = bc;
        if (mBatteryMeterView != null) {
            mBatteryMeterView.setBatteryController(bc);
        }
        mNetworkController = nc;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mBatteryMeterView = (BatteryMeterView) findViewById(R.id.expansion_view_battery_icon);
        mBatteryLayout = findViewById(R.id.expansion_view_battery_layout);
        mBatteryLevel = (TextView) findViewById(R.id.expansion_view_battery_level);
        mCarrierLabel = (TextView) findViewById(R.id.expansion_view_carrier_label);
        mWifiLabel = (TextView) findViewById(R.id.expansion_view_wifi_label);
        mNetworkTraffic = findViewById(R.id.expansion_view_traffic);

        mBatteryLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                startBatteryActivity();
            }
        });

        mBatteryLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                startBatteryLongClickActivity();
            return true;
            }
        });

        mCarrierLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                startCarrierActivity();
            }
        });

        mCarrierLabel.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                startCarrierLongActivity();
            return true;
            }
        });

        mNetworkTraffic.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                startTrafficLongClickActivity();
            return true;
            }
        });

        mWifiLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                startWifiActivity();
            }
        });

        mWifiLabel.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                startWifiLongActivity();
            return true;
            }
        });
    }

    public void setListening(boolean listening) {
        if (mNetworkController == null || mListening == listening) {
            return;
        }
        mListening = listening;
        if (mListening) {
            mBatteryController.addStateChangedCallback(this);
            mNetworkController.addSignalCallback(mSignalCallback);
        } else {
            mBatteryController.removeStateChangedCallback(this);
            mNetworkController.removeSignalCallback(mSignalCallback);
        }
    }

    private void updateViews() {
        final Resources res = mContext.getResources();
        if (mIsNoSims) {
            mCarrierDescription = res.getString(
                        R.string.quick_settings_rssi_emergency_only);
        }
        if (mCarrierDescription == null || mCarrierDescription.isEmpty()) {
            mCarrierDescription = res.getString(com.android.internal.R.string.lockscreen_carrier_default);
        }

        if (mWifiEnabled) {
            if (!mWifiConnected) {
                mWifiDescription = res.getString(R.string.accessibility_no_wifi);
            }
        } else {
            mWifiDescription = res.getString(R.string.accessibility_wifi_off);
        }
        mCarrierLabel.setText(mCarrierDescription);
        mWifiLabel.setText(mWifiDescription);
    }

    public static String removeTrailingPeriod(String string) {
        if (string == null) return null;
        final int length = string.length();
        if (string.endsWith(".")) {
            return string.substring(0, length - 1);
        }
        return string;
    }

    private static String removeDoubleQuotes(String string) {
        if (string == null) return null;
        final int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"') && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    public void setIconColor(int color) {
        // Do nothing at the moment
    }

    public void setRippleColor() {
        RippleDrawable wifiBackground =
                (RippleDrawable) mContext.getDrawable(R.drawable.ripple_drawable_rectangle).mutate();
        RippleDrawable carrierBackground =
                (RippleDrawable) mContext.getDrawable(R.drawable.ripple_drawable_rectangle).mutate();
        RippleDrawable rippleBackground =
                (RippleDrawable) mContext.getDrawable(R.drawable.ripple_drawable_oval).mutate();
        RippleDrawable trafficBackground =
                (RippleDrawable) mContext.getDrawable(R.drawable.ripple_drawable).mutate();
        RippleDrawable batteryBackground =
                (RippleDrawable) mContext.getDrawable(R.drawable.ripple_drawable).mutate();
        final int color = ExpansionViewColorHelper.getNormalRippleColor(mContext);
        batteryBackground.setColor(ColorStateList.valueOf(color));
        trafficBackground.setColor(ColorStateList.valueOf(color));
        wifiBackground.setColor(ColorStateList.valueOf(color));
        carrierBackground.setColor(ColorStateList.valueOf(color));
        mBatteryLayout.setBackground(batteryBackground);
        mCarrierLabel.setBackground(carrierBackground);
        mNetworkTraffic.setBackground(trafficBackground);
        mWifiLabel.setBackground(wifiBackground);
    }

    public void setTextColor(int color) {
        mCarrierLabel.setTextColor(color);
        mWifiLabel.setTextColor(color);
    }

    public void setTextSize(int size) {
        mCarrierLabel.setTextSize(size);
        mWifiLabel.setTextSize(size);
    }

    public void setTypeface(Typeface tf) {
        mCarrierLabel.setTypeface(tf);
        mWifiLabel.setTypeface(tf);
    }

    private final class SignalCallback extends SignalCallbackAdapter {
        @Override
        public void setWifiIndicators(boolean enabled, IconState statusIcon, IconState qsIcon,
                boolean activityIn, boolean activityOut, String description) {
            mWifiEnabled = enabled;
            mWifiConnected = enabled && (statusIcon.icon > 0) && (description != null);
            mWifiDescription = removeDoubleQuotes(description);
            updateViews();
        }

        @Override
        public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
                int qsType, boolean activityIn, boolean activityOut, String typeContentDescription,
                String description, boolean isWide, int subId) {
            if (statusIcon == null || !mSupportsMobileData) {
                return;
            }
            mCarrierDescription = removeTrailingPeriod(description);
            updateViews();
        }

        @Override
        public void setNoSims(boolean show) {
            if (!mSupportsMobileData) {
                return;
            }
            mIsNoSims = show;
            updateViews();
        }
    };

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        // could not care less
    }

    @Override
    public void onPowerSaveChanged() {
        // could not care less
    }

    public void setBatteryIndicator(int indicator) {
        mBatteryMeterView.updateBatteryIndicator(indicator);
    }

    public void setBatteryTextVisibility(boolean show) {
        mBatteryMeterView.setTextVisibility(show);
    }

    public void setBatteryCircleDots(int interval, int length) {
        mBatteryMeterView.updateCircleDots(interval, length);
    }

    public void setBatteryShowChargeAnimation(boolean show) {
        mBatteryMeterView.setShowChargeAnimation(show);
    }

    public void setBatteryCutOutBatteryText(boolean cutOut) {
        mBatteryMeterView.setCutOutText(cutOut);
    }

    public void setBatteryIconColor(int color) {
        mBatteryMeterView.setBatteryColors(color);
    }

    public void setBatteryIconColor() {
        final int iconColor =  ExpansionViewColorHelper.getBatteryIconColor(mContext);
        mBatteryMeterView.setBatteryColors(iconColor);
    }

    public void setBatteryTextColor(int color) {
        mBatteryMeterView.setTextColor(color);
    }

    private void startBatteryActivity() {
        mActivityStarter.startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
                true /* dismissShade */);
    }

    private void startBatteryLongClickActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$ExpansionViewBatterySettingsSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startCarrierActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$WirelessSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startCarrierLongActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$DataUsageSummaryActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startTrafficLongClickActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$ExpansionViewTrafficSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void startWifiActivity() {
        mActivityStarter.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS),
        true /* dismissShade */);
    }

    private void startWifiLongActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$TetherSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    public void vibrateOnClick(boolean vibrate) {
        mExpansionViewVibrate = vibrate;
    }

    public void vibrate(int duration) {
        if (mVibrator != null) {
            if (mVibrator.hasVibrator()) { mVibrator.vibrate(duration); }
        }
    }
}
