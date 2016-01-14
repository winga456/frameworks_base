/*
 * Copyright (C) 2016 VRToxin Project
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
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.vrtoxin.QuickAccess.QuickAccessBar;

public class STweaksButton extends QabButton {

    private final ContentObserver mSTweaksObserver;
    private final ContentResolver mResolver;

    private boolean mEnabled;

    public STweaksButton(Context context, QuickAccessBar bar, Drawable iconEnabled,
            Drawable iconDisabled) {
        super(context, bar, iconEnabled, iconDisabled);

        mSTweaksObserver = new ContentObserver(new Handler()) {
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
            mResolver.unregisterContentObserver(mSTweaksObserver);
        }
    }

    @Override
    public void handleClick() {
        if (mEnabled) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName("com.gokhanmoral.stweaks.app",
            "com.gokhanmoral.stweaks.app.MainActivity");
            mBar.startSettingsActivity(intent);
        }
    }
}
