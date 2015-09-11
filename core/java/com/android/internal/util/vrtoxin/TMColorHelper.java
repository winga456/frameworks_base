/*
* Copyright (C) 2016 Cyanide Android (rogersb11)
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
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import com.android.internal.util.NotificationColorUtil;

public class TMColorHelper {

    private static final int WHITE = 0xffffffff;
    private static final int BLACK = 0xff000000;
    private static final int CYANIDE_BLUE = 0xff1976D2;
    private static final int CYANIDE_GREEN = 0xff00ff00;
    private static final int TRANSLUCENT_BLACK = 0x7a000000;

    public static ColorStateList getSliderColorList(Context context) {
        return ColorStateList.valueOf(getSliderColor(context));
    }

    public static int getSliderColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.TASK_MANAGER_SLIDER_COLOR, 0xffff0000);
    }

    public static ColorStateList getSliderInactiveColorList(Context context) {
        return ColorStateList.valueOf(getSliderInactiveColor(context));
    }

    public static int getSliderInactiveColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.TASK_MANAGER_SLIDER_INACTIVE_COLOR, CYANIDE_GREEN);
    }

    public static ColorStateList getTaskAppIconColorList(Context context) {
        return ColorStateList.valueOf(getTaskAppIconColor(context));
    }

    public static int getTaskAppIconColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.TASK_MANAGER_APP_COLOR, WHITE);
    }

    public static ColorStateList getTaskKillIconColorList(Context context) {
        return ColorStateList.valueOf(getTaskKillIconColor(context));
    }

    public static int getTaskKillIconColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.TASK_MANAGER_TASK_KILL_BUTTON_COLOR, CYANIDE_BLUE);
    }

    public static ColorStateList getKillAllTextColorList(Context context) {
        return ColorStateList.valueOf(getKillAllTextColor(context));
    }

    public static int getKillAllTextColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.TASK_MANAGER_TASK_KILL_ALL_COLOR, CYANIDE_BLUE);
    }

    public static ColorStateList getTaskMemoryTextColorList(Context context) {
        return ColorStateList.valueOf(getTaskMemoryTextColor(context));
    }

    public static int getTaskMemoryTextColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.TASK_MANAGER_MEMORY_TEXT_COLOR, WHITE);
    }

    public static ColorStateList getTaskTextColorList(Context context) {
        return ColorStateList.valueOf(getTaskTextColor(context));
    }

    public static int getTaskTextColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.TASK_MANAGER_TASK_TEXT_COLOR, WHITE);
    }

    public static ColorStateList getTaskTitleTextColorList(Context context) {
        return ColorStateList.valueOf(getTaskTitleTextColor(context));
    }

    public static int getTaskTitleTextColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.TASK_MANAGER_TITLE_TEXT_COLOR, WHITE);
    }
}
