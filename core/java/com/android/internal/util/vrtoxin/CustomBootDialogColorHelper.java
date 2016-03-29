/*
* Copyright (C) 2016 Brett Rogers (rogersb11)
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

import android.content.Context;
import android.content.res.ColorStateList;
import android.provider.Settings;

public class CustomBootDialogColorHelper {

    private static final int VRTOXIN_BLUE = 0xff1976D2;
    private static final int WHITE = 0xffffffff;

    public static int getPackageNameTextColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.BOOT_DIALOG_PACKAGE_TEXT_COLOR, WHITE);
    }

    public static ColorStateList getProgressBarColorList(Context context) {
        return ColorStateList.valueOf(getProgressBarColor(context));
    }

    public static int getProgressBarColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.BOOT_DIALOG_PROGRESS_BAR_COLOR, WHITE);
    }

    public static ColorStateList getProgressBarInactiveColorList(Context context) {
        return ColorStateList.valueOf(getProgressBarInactiveColor(context));
    }

    public static int getProgressBarInactiveColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.BOOT_DIALOG_PROGRESS_BAR_INACTIVE_COLOR, WHITE);
    }

    public static int getTextColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.BOOT_DIALOG_TEXT_COLOR, WHITE);
    }
}
