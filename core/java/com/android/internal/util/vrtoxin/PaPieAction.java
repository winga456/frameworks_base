/*
* Copyright (C) 2013-2015 ParanoidAndroid Project
* Portions Copyright (C) 2015-2016 BlackDragon & Brett Rogers
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.internal.util.vrtoxin;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.IUiModeManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.res.Configuration;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.content.ContentResolver;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.IWindowManager;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.InputDevice;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerPolicyControl;
import android.widget.Toast;

import com.android.internal.statusbar.IStatusBarService;

import java.net.URISyntaxException;

public class PaPieAction {

    private static final int MSG_INJECT_KEY_DOWN = 1066;
    private static final int MSG_INJECT_KEY_UP = 1067;

    public static void processAction(Context context, String action, boolean isLongpress) {
        processActionWithOptions(context, action, isLongpress);
    }

    public static void processActionWithOptions(Context context,
            String action, boolean isLongpress) {

            if (action == null || action.equals(PaPieConstants.NULL_BUTTON)) {
                return;
            }

            boolean isKeyguardShowing = false;
            try {
                isKeyguardShowing =
                        WindowManagerGlobal.getWindowManagerService().isKeyguardLocked();
            } catch (RemoteException e) {
                Log.w("Action", "Error getting window manager service", e);
            }

            final IStatusBarService barService = IStatusBarService.Stub.asInterface(
                    ServiceManager.getService(Context.STATUS_BAR_SERVICE));
            if (barService == null) {
                return; // ouch
            }

            final IWindowManager windowManagerService = IWindowManager.Stub.asInterface(
                    ServiceManager.getService(Context.WINDOW_SERVICE));
            if (windowManagerService == null) {
                return; // ouch
            }

            boolean isKeyguardSecure = false;
            try {
                isKeyguardSecure = windowManagerService.isKeyguardSecure();
            } catch (RemoteException e) {
                Log.w("Action", "Error getting window manager service", e);
            }

            // process the actions
            if (action.equals(PaPieConstants.HOME_BUTTON)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_HOME, isLongpress);
                return;
            } else if (action.equals(PaPieConstants.BACK_BUTTON)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_BACK, isLongpress);
                return;
            } else if (action.equals(PaPieConstants.MENU_BUTTON)
                    || action.equals(PaPieConstants.MENU_BIG_BUTTON)) {
                triggerVirtualKeypress(KeyEvent.KEYCODE_MENU, isLongpress);
                return;
            } else if (action.equals(PaPieConstants.KILL_TASK_BUTTON)) {
                if (isKeyguardShowing) {
                    return;
                }
                try {
                    barService.toggleKillApp();
                } catch (RemoteException e) {
                }
                return;
            } else if (action.equals(PaPieConstants.LAST_APP_BUTTON)) {
                if (isKeyguardShowing) {
                    return;
                }
                try {
                    barService.toggleLastApp();
                } catch (RemoteException e) {
                }
                return;
            } else if (action.equals(PaPieConstants.RECENT_BUTTON)) {
                if (isKeyguardShowing) {
                    return;
                }
                try {
                    barService.toggleRecentApps();
                } catch (RemoteException e) {
                }
                return;
            } else if (action.equals(PaPieConstants.NOW_ON_TAP)) {
                if (barService != null) {
                    try {
                        barService.startAssist(new Bundle());
                       } catch (RemoteException e) {
                       }
                   }
                return;
            } else if (action.equals(PaPieConstants.POWER_BUTTON)) {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                pm.goToSleep(SystemClock.uptimeMillis());
                return;
            } else if (action.equals(PaPieConstants.POWER_MENU_BUTTON)) {
                try {
                    windowManagerService.toggleGlobalMenu();
                } catch (RemoteException e) {
                }
                return;
            } else if (action.equals(PaPieConstants.EXPANDED_DESKTOP_BUTTON)) {
                ContentResolver cr = context.getContentResolver();
                String value = Settings.Global.getString(cr, Settings.Global.POLICY_CONTROL);
                boolean isExpanded = "immersive.full=*".equals(value);
                Settings.Global.putString(cr, Settings.Global.POLICY_CONTROL,
                        isExpanded ? "" : "immersive.full=*");
                if (isExpanded)
                    WindowManagerPolicyControl.reloadFromSetting(context);
                return;
            } else if (action.equals(PaPieConstants.SCREENSHOT_BUTTON)) {
                try {
                    barService.toggleScreenshot();
                } catch (RemoteException e) {
                }
                return;
            } else {
                // we must have a custom uri
                Intent intent = null;
                try {
                    intent = Intent.parseUri(action, 0);
                } catch (URISyntaxException e) {
                    Log.e("PieAction:", "URISyntaxException: [" + action + "]");
                    return;
                }
                startActivity(context, intent, barService, isKeyguardShowing);
                return;
            }

    }

    public static boolean isActionKeyEvent(String action) {
        if (action.equals(PaPieConstants.HOME_BUTTON)
                || action.equals(PaPieConstants.BACK_BUTTON)
                || action.equals(PaPieConstants.MENU_BUTTON)
                || action.equals(PaPieConstants.MENU_BIG_BUTTON)
                || action.equals(PaPieConstants.NULL_BUTTON)) {
            return true;
        }
        return false;
    }

    private static void startActivity(Context context, Intent intent,
            IStatusBarService barService, boolean isKeyguardShowing) {
        if (intent == null) {
            return;
        }
    }

    public static void triggerVirtualKeypress(final int keyCode, boolean longpress) {
        InputManager im = InputManager.getInstance();
        long now = SystemClock.uptimeMillis();
        int downflags = 0;
        int upflags = 0;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
            || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            || keyCode == KeyEvent.KEYCODE_DPAD_UP
            || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            downflags = upflags = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;
        } else {
            downflags = upflags = KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY;
        }
        if (longpress) {
            downflags |= KeyEvent.FLAG_LONG_PRESS;
        }

        final KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                downflags,
                InputDevice.SOURCE_KEYBOARD);
        im.injectInputEvent(downEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);

        final KeyEvent upEvent = new KeyEvent(now, now, KeyEvent.ACTION_UP,
                keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                upflags,
                InputDevice.SOURCE_KEYBOARD);
        im.injectInputEvent(upEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

}
