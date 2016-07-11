/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.IUiModeManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.dreams.Sandman;
import android.util.Slog;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import com.android.internal.R;
import com.android.internal.app.DisableCarModeActivity;
import com.android.internal.statusbar.IStatusBarService;
import com.android.server.twilight.TwilightListener;
import com.android.server.twilight.TwilightManager;
import com.android.server.twilight.TwilightState;

final class UiModeManagerService extends SystemService
        implements SensorEventListener {
    private static final String TAG = UiModeManager.class.getSimpleName();
    private static final boolean LOG = false;

    private static float LIGHT_CONDITION = 25f;
    private static float DARK_CONDITION  = 2.5f;

    // Enable launching of applications when entering the dock.
    private static final boolean ENABLE_LAUNCH_CAR_DOCK_APP = true;
    private static final boolean ENABLE_LAUNCH_DESK_DOCK_APP = true;

    final Object mLock = new Object();
    private int mDockState = Intent.EXTRA_DOCK_STATE_UNDOCKED;

    private int mLastBroadcastState = Intent.EXTRA_DOCK_STATE_UNDOCKED;
    private int mNightMode = UiModeManager.MODE_NIGHT_NO;

    private boolean mCarModeEnabled = false;
    private boolean mCharging = false;
    private int mDefaultUiModeType;
    private int mNightAutoMode;
    private boolean mCarModeKeepsScreenOn;
    private boolean mDeskModeKeepsScreenOn;
    private boolean mTelevision;
    private boolean mWatch;
    private boolean mComputedNightMode;
    private int mCarModeEnableFlags;

    int mCurUiMode = 0;
    private int mSetUiMode = 0;
    private int mSetUiNightMode = 0;
    private boolean mAllowConfigChange = true;
    private float mCurrentSwitchLevel = DARK_CONDITION;
    private boolean mHoldingConfiguration = false;

    private Configuration mConfiguration = new Configuration();
    boolean mSystemReady;

    private final Handler mHandler = new Handler();

    private TwilightManager mTwilightManager;
    private NotificationManager mNotificationManager;
    private StatusBarManager mStatusBarManager;

    private SensorManager mSensorManager;
    private Context mContext;

    private PowerManager.WakeLock mWakeLock;

    public UiModeManagerService(Context context) {
        super(context);
    }

    private static Intent buildHomeIntent(String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }

    // The broadcast receiver which receives the result of the ordered broadcast sent when
    // the dock state changes. The original ordered broadcast is sent with an initial result
    // code of RESULT_OK. If any of the registered broadcast receivers changes this value, e.g.,
    // to RESULT_CANCELED, then the intent to start a dock app will not be sent.
    private final BroadcastReceiver mResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getResultCode() != Activity.RESULT_OK) {
                if (LOG) {
                    Slog.v(TAG, "Handling broadcast result for action " + intent.getAction()
                            + ": canceled: " + getResultCode());
                }
                return;
            }

            final int enableFlags = intent.getIntExtra("enableFlags", 0);
            final int disableFlags = intent.getIntExtra("disableFlags", 0);
            synchronized (mLock) {
                updateAfterBroadcastLocked(intent.getAction(), enableFlags, disableFlags);
            }
        }
    };

    private final BroadcastReceiver mDockModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                    Intent.EXTRA_DOCK_STATE_UNDOCKED);
            updateDockState(state);
        }
    };

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mCharging = (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0);
            synchronized (mLock) {
                if (mSystemReady) {
                    updateLocked(0, 0);
                }
            }
        }
    };

    private boolean mAttached;
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            mContext = context;
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                registerLightSensor();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                unregisterLightSensor();
            }
        }
    };

    private void registerLightSensor() {
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (sensor != null) {
            mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void unregisterLightSensor() {
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (sensor != null) {
            mSensorManager.unregisterListener(this, sensor);
        }
    }

    Runnable mReleaseUiNightModeBlock = new Runnable() {
        public void run() {
            mAllowConfigChange = true;
            synchronized (mLock) {
                if (mSystemReady) {
                    sendConfigurationLocked();
                }
            }
        }
    };

    private final TwilightListener mTwilightListener = new TwilightListener() {
        @Override
        public void onTwilightStateChanged() {
            updateTwilight();
        }
    };

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.UI_NIGHT_AUTO_MODE),
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateUiNightMode();
        }
    }

    @Override
    public void onStart() {
        final Context context = getContext();
        mContext = context;

        final PowerManager powerManager =
                (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG);

        mSensorManager = (SensorManager)(context.getSystemService(Context.SENSOR_SERVICE));

        context.registerReceiver(mDockModeReceiver,
                new IntentFilter(Intent.ACTION_DOCK_EVENT));
        context.registerReceiver(mBatteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        mConfiguration.setToDefaults();
        updateThemeColors();

        // Register settings observer
        SettingsObserver settingsObserver = new SettingsObserver(new Handler());
        settingsObserver.observe();

        final Resources res = context.getResources();
        mDefaultUiModeType = res.getInteger(
                com.android.internal.R.integer.config_defaultUiModeType);
        mCarModeKeepsScreenOn = (res.getInteger(
                com.android.internal.R.integer.config_carDockKeepsScreenOn) == 1);
        mDeskModeKeepsScreenOn = (res.getInteger(
                com.android.internal.R.integer.config_deskDockKeepsScreenOn) == 1);

        final PackageManager pm = context.getPackageManager();
        mTelevision = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        mWatch = pm.hasSystemFeature(PackageManager.FEATURE_WATCH);

        final int defaultNightMode = res.getInteger(
                com.android.internal.R.integer.config_defaultNightMode);
        mNightMode = Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.UI_NIGHT_MODE, defaultNightMode);

        // Update the initial, static configurations.
        synchronized (this) {
            updateConfigurationLocked();
            sendConfigurationLocked();
        }

        publishBinderService(Context.UI_MODE_SERVICE, mService);
    }

    private void updateUiNightMode() {
        mNightAutoMode = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.UI_NIGHT_AUTO_MODE, 0);

        if (mNightAutoMode == 1) {
            if (!mAttached) {
                mAttached = true;
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_SCREEN_OFF);
                filter.addAction(Intent.ACTION_SCREEN_ON);
                mContext.registerReceiver(mBroadcastReceiver, filter);
                registerLightSensor();
                return;
            }
        } else {
            if (mAttached) {
                mAttached = false;
                mContext.unregisterReceiver(mBroadcastReceiver);
                unregisterLightSensor();
                mHandler.removeCallbacks(mReleaseUiNightModeBlock);
            }
        }

        if (mNightAutoMode == 2) {
            updateTwilight();
            return;
        }

        synchronized (mLock) {
            if (mSystemReady) {
                sendConfigurationLocked();
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        boolean eventToastDisplayed = false;
        if (type == Sensor.TYPE_LIGHT) {
            if (event.values[0] <= mCurrentSwitchLevel) {
                mCurrentSwitchLevel = LIGHT_CONDITION;
                mConfiguration.uiMode = Configuration.UI_MODE_NIGHT_YES;
            } else {
                mCurrentSwitchLevel = DARK_CONDITION;
                mConfiguration.uiMode = Configuration.UI_MODE_NIGHT_NO;
            }
            if (mAllowConfigChange) {
                mAllowConfigChange = false;
                mHandler.postDelayed(mReleaseUiNightModeBlock, 2500);
                    synchronized (mLock) {
                        if (mSystemReady) {
                            sendConfigurationLocked();
                        }
                    }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private final IBinder mService = new IUiModeManager.Stub() {
        @Override
        public void enableCarMode(int flags) {
            final long ident = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    setCarModeLocked(true, flags);
                    if (mSystemReady) {
                        updateLocked(flags, 0);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void disableCarMode(int flags) {
            final long ident = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    setCarModeLocked(false, 0);
                    if (mSystemReady) {
                        updateLocked(0, flags);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public int getCurrentModeType() {
            final long ident = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    return mCurUiMode & Configuration.UI_MODE_TYPE_MASK;
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public void setNightMode(int mode) {
            switch (mode) {
                case UiModeManager.MODE_NIGHT_NO:
                case UiModeManager.MODE_NIGHT_YES:
                case UiModeManager.MODE_NIGHT_BLACKOUT:
                case UiModeManager.MODE_NIGHT_RANDOM:
                case UiModeManager.MODE_NIGHT_CUSTOM_2:
                case UiModeManager.MODE_NIGHT_CUSTOM_3:
                case UiModeManager.MODE_NIGHT_CUSTOM_4:
                case UiModeManager.MODE_NIGHT_AUTO:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown mode: " + mode);
            }

            final long ident = Binder.clearCallingIdentity();
            try {
                synchronized (mLock) {
                    if (mNightMode != mode) {
                        Settings.Secure.putInt(getContext().getContentResolver(),
                                Settings.Secure.UI_NIGHT_MODE, mode);
                        mNightMode = mode;
                        updateLocked(0, 0);
                    }
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        public int getNightMode() {
            synchronized (mLock) {
                return mNightMode;
            }
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (getContext().checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {

                pw.println("Permission Denial: can't dump uimode service from from pid="
                        + Binder.getCallingPid()
                        + ", uid=" + Binder.getCallingUid());
                return;
            }

            dumpImpl(pw);
        }
    };

    void dumpImpl(PrintWriter pw) {
        synchronized (mLock) {
            pw.println("Current UI Mode Service state:");
            pw.print("  mDockState="); pw.print(mDockState);
                    pw.print(" mLastBroadcastState="); pw.println(mLastBroadcastState);
            pw.print("  mNightMode="); pw.print(mNightMode);
                    pw.print(" mCarModeEnabled="); pw.print(mCarModeEnabled);
                    pw.print(" mComputedNightMode="); pw.print(mComputedNightMode);
                    pw.print(" mCarModeEnableFlags="); pw.println(mCarModeEnableFlags);
            pw.print("  mCurUiMode=0x"); pw.print(Integer.toHexString(mCurUiMode));
                    pw.print(" mSetUiMode=0x"); pw.println(Integer.toHexString(mSetUiMode));
                    pw.print(" mSetUiNightMode=0x");
                    pw.println(Integer.toHexString(mSetUiNightMode));
            pw.print("  mHoldingConfiguration="); pw.print(mHoldingConfiguration);
                    pw.print(" mSystemReady="); pw.println(mSystemReady);
            if (mTwilightManager != null) {
                // We may not have a TwilightManager.
                pw.print("  mTwilightService.getCurrentState()=");
                pw.println(mTwilightManager.getCurrentState());
            }
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            synchronized (mLock) {
                mTwilightManager = getLocalService(TwilightManager.class);
                if (mTwilightManager != null) {
                    mTwilightManager.registerListener(mTwilightListener, mHandler);
                }
                mSystemReady = true;
                mCarModeEnabled = mDockState == Intent.EXTRA_DOCK_STATE_CAR;
                updateComputedNightModeLocked();
                updateUiNightMode();
                updateLocked(0, 0);
            }
        }
    }

    void setCarModeLocked(boolean enabled, int flags) {
        if (mCarModeEnabled != enabled) {
            mCarModeEnabled = enabled;
        }
        mCarModeEnableFlags = flags;
    }

    private void updateDockState(int newState) {
        synchronized (mLock) {
            if (newState != mDockState) {
                mDockState = newState;
                setCarModeLocked(mDockState == Intent.EXTRA_DOCK_STATE_CAR, 0);
                if (mSystemReady) {
                    updateLocked(UiModeManager.ENABLE_CAR_MODE_GO_CAR_HOME, 0);
                }
            }
        }
    }

    private static boolean isDeskDockState(int state) {
        switch (state) {
            case Intent.EXTRA_DOCK_STATE_DESK:
            case Intent.EXTRA_DOCK_STATE_LE_DESK:
            case Intent.EXTRA_DOCK_STATE_HE_DESK:
                return true;
            default:
                return false;
        }
    }

    private void updateConfigurationLocked() {
        int uiMode = mDefaultUiModeType;
        if (mTelevision) {
            uiMode = Configuration.UI_MODE_TYPE_TELEVISION;
        } else if (mWatch) {
            uiMode = Configuration.UI_MODE_TYPE_WATCH;
        } else if (mCarModeEnabled) {
            uiMode = Configuration.UI_MODE_TYPE_CAR;
        } else if (isDeskDockState(mDockState)) {
            uiMode = Configuration.UI_MODE_TYPE_DESK;
        }

        uiMode |= mNightMode << 4;

        if (LOG) {
            Slog.d(TAG,
                "updateConfigurationLocked: mDockState=" + mDockState
                + "; mCarMode=" + mCarModeEnabled
                + "; mNightMode=" + mNightMode
                + "; uiMode=" + uiMode);
        }

        mCurUiMode = uiMode;
        if (!mHoldingConfiguration) {
            mConfiguration.uiMode = uiMode;
        }
    }

    private void sendConfigurationLocked() {
        if (mSetUiMode != mConfiguration.uiMode
                || mSetUiNightMode != mNightMode) {
            mSetUiMode = mConfiguration.uiMode;

            if (mSetUiNightMode != mNightMode) {
                final IStatusBarService barService = IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(Context.STATUS_BAR_SERVICE));
                try {
                    if (barService != null) {
                        barService.collapsePanels();
                    }
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failure communicating with statusbar service", e);
                }

                mSetUiNightMode = mNightMode;
                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.UI_NIGHT_MODE, mSetUiNightMode);
            }
            try {
                ActivityManagerNative.getDefault().updateConfiguration(mConfiguration);
                updateThemeColors();
            } catch (RemoteException e) {
                Slog.w(TAG, "Failure communicating with activity manager", e);
            }
        }
    }

    private void updateThemeColors() {

        final int mOverrideCustomColors = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.OVERRIDE_CUSTOM_COLORS, 0);

        if (mOverrideCustomColors == 0) {
            if (mNightMode == UiModeManager.MODE_NIGHT_NO) {
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.NOTIFICATION_BG_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.NOTIFICATION_TEXT_COLOR, 0xff000000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.QS_BACKGROUND_COLOR, 0xff263238);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_BG_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_TEXT_COLOR, 0xff000000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_ICON_NORMAL_COLOR, 0xff00ff00);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_ICON_ENABLED_SELECTED_COLOR, 0xff1976D2);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.STATUS_BAR_EXPANDED_HEADER_BG_COLOR, 0xff384248);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.BOOT_DIALOG_TEXT_COLOR, 0xff000000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.BOOT_DIALOG_BACKGROUND_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VOLUME_DIALOG_BG_COLOR, 0xff37474f);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VOLUME_DIALOG_ICON_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VOLUME_DIALOG_SLIDER_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VOLUME_DIALOG_SLIDER_INACTIVE_COLOR, 0xffffffff);
                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.DOZE_NOTIFICATION_INVERT_ENABLED, 1);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_BG_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_ICON_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_TITLE_TEXT_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_CATEGORY_TEXT_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MODS_DIVIDER_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MODS_UNDERLINE_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MODS_TAB_TEXT_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_CAT_SPACE_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_TOOLBAR_BG_COLOR, 0xff263238);
            } else if (mNightMode == UiModeManager.MODE_NIGHT_YES) {
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.NOTIFICATION_BG_COLOR, 0xff1b1f23);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.NOTIFICATION_TEXT_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.QS_BACKGROUND_COLOR, 0xff1b1f23);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_BG_COLOR, 0xff1b1f23);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_TEXT_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_ICON_NORMAL_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_ICON_ENABLED_SELECTED_COLOR, 0xff33b5e5);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.STATUS_BAR_EXPANDED_HEADER_BG_COLOR, 0xff263238);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.BOOT_DIALOG_TEXT_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.BOOT_DIALOG_BACKGROUND_COLOR, 0xff1b1f23);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VOLUME_DIALOG_BG_COLOR, 0xff1b1f23);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VOLUME_DIALOG_ICON_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VOLUME_DIALOG_SLIDER_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VOLUME_DIALOG_SLIDER_INACTIVE_COLOR, 0xffffffff);
                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.DOZE_NOTIFICATION_INVERT_ENABLED, 0);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_BG_COLOR, 0xff1b1f23);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_ICON_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_TITLE_TEXT_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_CATEGORY_TEXT_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MODS_DIVIDER_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MODS_UNDERLINE_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MODS_TAB_TEXT_COLOR, 0xff009688);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_CAT_SPACE_COLOR, 0xff000000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_TOOLBAR_BG_COLOR, 0xff263238);
            } else if (mNightMode == UiModeManager.MODE_NIGHT_BLACKOUT) {
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.NOTIFICATION_BG_COLOR, 0xff000000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.NOTIFICATION_TEXT_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.QS_BACKGROUND_COLOR, 0xff000000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_BG_COLOR, 0xff000000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_TEXT_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_ICON_NORMAL_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_ICON_ENABLED_SELECTED_COLOR, 0xff1976D2);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.STATUS_BAR_EXPANDED_HEADER_BG_COLOR, 0xff000000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.BOOT_DIALOG_TEXT_COLOR, 0xffffffff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.BOOT_DIALOG_BACKGROUND_COLOR, 0xff000000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VOLUME_DIALOG_BG_COLOR, 0xff000000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VOLUME_DIALOG_ICON_COLOR, 0xff00ff00);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VOLUME_DIALOG_SLIDER_COLOR, 0xff1976D2);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VOLUME_DIALOG_SLIDER_INACTIVE_COLOR, 0xffff0000);
                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.DOZE_NOTIFICATION_INVERT_ENABLED, 0);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_BG_COLOR, 0xff000000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_ICON_COLOR, 0xff1976D2);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_TITLE_TEXT_COLOR, 0xff1976D2);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_CATEGORY_TEXT_COLOR, 0xff00ff00);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MODS_DIVIDER_COLOR, 0xff1976D2);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MODS_UNDERLINE_COLOR, 0xff1976D2);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.MODS_TAB_TEXT_COLOR, 0xff1976D2);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_CAT_SPACE_COLOR, 0xff000000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_TOOLBAR_BG_COLOR, 0xff000000);
            } else if (mNightMode == UiModeManager.MODE_NIGHT_RANDOM) {
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.NOTIFICATION_BG_COLOR, 0xff0f2e4c);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.NOTIFICATION_GUTS_BG_COLOR, 0xff0f2e4c);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.NOTIFICATION_ICON_COLOR, 0xfffff400);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.NOTIFICATION_TEXT_COLOR, 0xfffff400);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.QS_BACKGROUND_COLOR, 0xff0f2e4c);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.QS_ICON_COLOR, 0xfffff400);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.QS_TEXT_COLOR, 0xff00ff00);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.QS_BRIGHTNESS_SLIDER_BG_COLOR, 0xffff0000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.QS_BRIGHTNESS_SLIDER_COLOR, 0xff00ff00);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.QS_BRIGHTNESS_SLIDER_ICON_COLOR, 0xffff0000);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_BG_COLOR, 0xff0f2e4c);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_TEXT_COLOR, 0xfffff400);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_ICON_NORMAL_COLOR, 0xfffff400);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.POWER_MENU_ICON_ENABLED_SELECTED_COLOR, 0xff00bbff);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.STATUS_BAR_EXPANDED_HEADER_TEXT_COLOR, 0xfffff400);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.STATUS_BAR_EXPANDED_HEADER_ICON_COLOR, 0xfffff400);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.STATUS_BAR_EXPANDED_HEADER_BG_COLOR, 0xff0f2e4c);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_BG_COLOR, 0xff0f2e4c);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_ICON_COLOR, 0xfffff400);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_TITLE_TEXT_COLOR, 0xff00ff00);
                Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.SETTINGS_CATEGORY_TEXT_COLOR, 0xff0f2e4c);
//            } else if (mNightMode == UiModeManager.MODE_NIGHT_CUSTOM_2) { *enable this after theme resources are implemented
//            } else if (mNightMode == UiModeManager.MODE_NIGHT_CUSTOM_3) { *enable this after theme resources are implemented
//            } else if (mNightMode == UiModeManager.MODE_NIGHT_CUSTOM_4) { *enable this after theme resources are implemented
            }
        }
    }

    void updateLocked(int enableFlags, int disableFlags) {
        String action = null;
        String oldAction = null;
        if (mLastBroadcastState == Intent.EXTRA_DOCK_STATE_CAR) {
            adjustStatusBarCarModeLocked();
            oldAction = UiModeManager.ACTION_EXIT_CAR_MODE;
        } else if (isDeskDockState(mLastBroadcastState)) {
            oldAction = UiModeManager.ACTION_EXIT_DESK_MODE;
        }

        if (mCarModeEnabled) {
            if (mLastBroadcastState != Intent.EXTRA_DOCK_STATE_CAR) {
                adjustStatusBarCarModeLocked();

                if (oldAction != null) {
                    getContext().sendBroadcastAsUser(new Intent(oldAction), UserHandle.ALL);
                }
                mLastBroadcastState = Intent.EXTRA_DOCK_STATE_CAR;
                action = UiModeManager.ACTION_ENTER_CAR_MODE;
            }
        } else if (isDeskDockState(mDockState)) {
            if (!isDeskDockState(mLastBroadcastState)) {
                if (oldAction != null) {
                    getContext().sendBroadcastAsUser(new Intent(oldAction), UserHandle.ALL);
                }
                mLastBroadcastState = mDockState;
                action = UiModeManager.ACTION_ENTER_DESK_MODE;
            }
        } else {
            mLastBroadcastState = Intent.EXTRA_DOCK_STATE_UNDOCKED;
            action = oldAction;
        }

        if (action != null) {
            if (LOG) {
                Slog.v(TAG, String.format(
                    "updateLocked: preparing broadcast: action=%s enable=0x%08x disable=0x%08x",
                    action, enableFlags, disableFlags));
            }

            // Send the ordered broadcast; the result receiver will receive after all
            // broadcasts have been sent. If any broadcast receiver changes the result
            // code from the initial value of RESULT_OK, then the result receiver will
            // not launch the corresponding dock application. This gives apps a chance
            // to override the behavior and stay in their app even when the device is
            // placed into a dock.
            Intent intent = new Intent(action);
            intent.putExtra("enableFlags", enableFlags);
            intent.putExtra("disableFlags", disableFlags);
            getContext().sendOrderedBroadcastAsUser(intent, UserHandle.CURRENT, null,
                    mResultReceiver, null, Activity.RESULT_OK, null, null);

            // Attempting to make this transition a little more clean, we are going
            // to hold off on doing a configuration change until we have finished
            // the broadcast and started the home activity.
            mHoldingConfiguration = true;
            updateConfigurationLocked();
        } else {
            String category = null;
            if (mCarModeEnabled) {
                if (ENABLE_LAUNCH_CAR_DOCK_APP
                        && (enableFlags & UiModeManager.ENABLE_CAR_MODE_GO_CAR_HOME) != 0) {
                    category = Intent.CATEGORY_CAR_DOCK;
                }
            } else if (isDeskDockState(mDockState)) {
                if (ENABLE_LAUNCH_DESK_DOCK_APP
                        && (enableFlags & UiModeManager.ENABLE_CAR_MODE_GO_CAR_HOME) != 0) {
                    category = Intent.CATEGORY_DESK_DOCK;
                }
            } else {
                if ((disableFlags & UiModeManager.DISABLE_CAR_MODE_GO_HOME) != 0) {
                    category = Intent.CATEGORY_HOME;
                }
            }

            if (LOG) {
                Slog.v(TAG, "updateLocked: null action, mDockState="
                        + mDockState +", category=" + category);
            }

            sendConfigurationAndStartDreamOrDockAppLocked(category);
        }

        // keep screen on when charging and in car mode
        boolean keepScreenOn = mCharging &&
                ((mCarModeEnabled && mCarModeKeepsScreenOn &&
                  (mCarModeEnableFlags & UiModeManager.ENABLE_CAR_MODE_ALLOW_SLEEP) == 0) ||
                 (mCurUiMode == Configuration.UI_MODE_TYPE_DESK && mDeskModeKeepsScreenOn));
        if (keepScreenOn != mWakeLock.isHeld()) {
            if (keepScreenOn) {
                mWakeLock.acquire();
            } else {
                mWakeLock.release();
            }
        }
    }

    private void updateAfterBroadcastLocked(String action, int enableFlags, int disableFlags) {
        // Launch a dock activity
        String category = null;
        if (UiModeManager.ACTION_ENTER_CAR_MODE.equals(action)) {
            // Only launch car home when car mode is enabled and the caller
            // has asked us to switch to it.
            if (ENABLE_LAUNCH_CAR_DOCK_APP
                    && (enableFlags & UiModeManager.ENABLE_CAR_MODE_GO_CAR_HOME) != 0) {
                category = Intent.CATEGORY_CAR_DOCK;
            }
        } else if (UiModeManager.ACTION_ENTER_DESK_MODE.equals(action)) {
            // Only launch car home when desk mode is enabled and the caller
            // has asked us to switch to it.  Currently re-using the car
            // mode flag since we don't have a formal API for "desk mode".
            if (ENABLE_LAUNCH_DESK_DOCK_APP
                    && (enableFlags & UiModeManager.ENABLE_CAR_MODE_GO_CAR_HOME) != 0) {
                category = Intent.CATEGORY_DESK_DOCK;
            }
        } else {
            // Launch the standard home app if requested.
            if ((disableFlags & UiModeManager.DISABLE_CAR_MODE_GO_HOME) != 0) {
                category = Intent.CATEGORY_HOME;
            }
        }

        if (LOG) {
            Slog.v(TAG, String.format(
                "Handling broadcast result for action %s: enable=0x%08x, disable=0x%08x, "
                    + "category=%s",
                action, enableFlags, disableFlags, category));
        }

        sendConfigurationAndStartDreamOrDockAppLocked(category);
    }

    private void sendConfigurationAndStartDreamOrDockAppLocked(String category) {
        // Update the configuration but don't send it yet.
        mHoldingConfiguration = false;
        updateConfigurationLocked();

        // Start the dock app, if there is one.
        boolean dockAppStarted = false;
        if (category != null) {
            // Now we are going to be careful about switching the
            // configuration and starting the activity -- we need to
            // do this in a specific order under control of the
            // activity manager, to do it cleanly.  So compute the
            // new config, but don't set it yet, and let the
            // activity manager take care of both the start and config
            // change.
            Intent homeIntent = buildHomeIntent(category);
            if (Sandman.shouldStartDockApp(getContext(), homeIntent)) {
                try {
                    int result = ActivityManagerNative.getDefault().startActivityWithConfig(
                            null, null, homeIntent, null, null, null, 0, 0,
                            mConfiguration, null, UserHandle.USER_CURRENT);
                    if (result >= ActivityManager.START_SUCCESS) {
                        dockAppStarted = true;
                    } else if (result != ActivityManager.START_INTENT_NOT_RESOLVED) {
                        Slog.e(TAG, "Could not start dock app: " + homeIntent
                                + ", startActivityWithConfig result " + result);
                    }
                } catch (RemoteException ex) {
                    Slog.e(TAG, "Could not start dock app: " + homeIntent, ex);
                }
            }
        }

        // Send the new configuration.
        sendConfigurationLocked();

        // If we did not start a dock app, then start dreaming if supported.
        if (category != null && !dockAppStarted) {
            Sandman.startDreamWhenDockedIfAppropriate(getContext());
        }
    }

    private void adjustStatusBarCarModeLocked() {
        final Context context = getContext();
        if (mStatusBarManager == null) {
            mStatusBarManager = (StatusBarManager)
                    context.getSystemService(Context.STATUS_BAR_SERVICE);
        }

        // Fear not: StatusBarManagerService manages a list of requests to disable
        // features of the status bar; these are ORed together to form the
        // active disabled list. So if (for example) the device is locked and
        // the status bar should be totally disabled, the calls below will
        // have no effect until the device is unlocked.
        if (mStatusBarManager != null) {
            mStatusBarManager.disable(mCarModeEnabled
                ? StatusBarManager.DISABLE_NOTIFICATION_TICKER
                : StatusBarManager.DISABLE_NONE);
        }

        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
        }

        if (mNotificationManager != null) {
            if (mCarModeEnabled) {
                Intent carModeOffIntent = new Intent(context, DisableCarModeActivity.class);

                Notification.Builder n = new Notification.Builder(context)
                        .setSmallIcon(R.drawable.stat_notify_car_mode)
                        .setDefaults(Notification.DEFAULT_LIGHTS)
                        .setOngoing(true)
                        .setWhen(0)
                        .setColor(context.getColor(
                                com.android.internal.R.color.system_notification_accent_color))
                        .setContentTitle(
                                context.getString(R.string.car_mode_disable_notification_title))
                        .setContentText(
                                context.getString(R.string.car_mode_disable_notification_message))
                        .setContentIntent(
                                PendingIntent.getActivityAsUser(context, 0, carModeOffIntent, 0,
                                        null, UserHandle.CURRENT));
                mNotificationManager.notifyAsUser(null,
                        R.string.car_mode_disable_notification_title, n.build(), UserHandle.ALL);
            } else {
                mNotificationManager.cancelAsUser(null,
                        R.string.car_mode_disable_notification_title, UserHandle.ALL);
            }
        }
    }

    private void updateTwilight() {
        synchronized (mLock) {
            if (mSystemReady) {
                if (mNightMode == UiModeManager.MODE_NIGHT_AUTO) {
                    updateComputedNightModeLocked();
                    updateLocked(0, 0);
                }
                if (mNightAutoMode == 2) {
                    updateTwilightNightAutoMode();
                    sendConfigurationLocked();
                }
            }
        }
    }

    private void updateTwilightNightAutoMode() {
        updateComputedNightModeLocked();
        mConfiguration.uiMode = mComputedNightMode
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;
    }

    private void updateComputedNightModeLocked() {
        TwilightState state = mTwilightManager.getCurrentState();
        if (state != null) {
            mComputedNightMode = state.isNight();
        }
    }


}
