/*
 * Copyright (C) 2015 CyanideL && Fusion
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

package com.android.systemui.vrtoxin.QuickAccess.buttons;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Handler;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import com.android.systemui.R;
import com.android.systemui.vrtoxin.QuickAccess.QuickAccessBar;

public class PowerMenuButton extends QabButton {

    private final ContentObserver mPowerMenuObserver;
    private final ContentResolver mResolver;

    private boolean mEnabled;

    public PowerMenuButton(Context context, QuickAccessBar bar, Drawable iconEnabled,
            Drawable iconDisabled) {
        super(context, bar, iconEnabled, iconDisabled);

        mPowerMenuObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                mEnabled = true;
                updateState(mEnabled);
            }
        };
        mResolver = mContext.getContentResolver();
        mEnabled = true;
        updateState(mEnabled);
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mEnabled = true;
        } else {
            mResolver.unregisterContentObserver(mPowerMenuObserver);
        }
    }

    @Override
    public void handleClick() {
        Intent intent = new Intent(Intent.ACTION_POWERMENU);
        mContext.sendBroadcast(intent);
    }

    @Override
    public void handleLongClick() {
        Intent intent = new Intent(Intent.ACTION_POWERMENU_REBOOT);
        mContext.sendBroadcast(intent);
    }
}
