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

public class RecentsColorHelper {

    private static final int WHITE = 0xffffffff;
    private static final int BLACK = 0xff000000;
    private static final int TRANSLUCENT_BLACK = 0x7a000000;

    public static int getRecentsAppIconColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.RECENTS_APP_ICON_COLOR, WHITE);
    }

    public static ColorStateList getRecentsBackgroundColorList(Context context) {
        return ColorStateList.valueOf(getRecentsBackgroundColor(context));
    }

    public static int getRecentsBackgroundColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.RECENTS_BG_COLOR,
                BLACK);
    }

    public static ColorStateList getRecentsDarkModeBackgroundColorList(Context context) {
        return ColorStateList.valueOf(getRecentsDarkModeBackgroundColor(context));
    }

    public static int getRecentsDarkModeBackgroundColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.RECENTS_DARK_MODE_BG_COLOR,
                WHITE);
    }

    public static ColorStateList getRecentsIconColorList(Context context) {
        return ColorStateList.valueOf(getRecentsIconColor(context));
    }

    public static int getRecentsIconColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.RECENTS_ICON_COLOR, WHITE);
    }

    public static ColorStateList getRecentsDarkModeIconColorList(Context context) {
        return ColorStateList.valueOf(getRecentsDarkModeIconColor(context));
    }

    public static int getRecentsDarkModeIconColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.RECENTS_DARK_MODE_ICON_COLOR, BLACK);
    }

    public static ColorStateList getRecentsTextColorList(Context context) {
        return ColorStateList.valueOf(getRecentsTextColor(context));
    }

    public static int getRecentsTextColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.RECENTS_TEXT_COLOR, WHITE);
    }

    public static ColorStateList getRecentsDarkModeTextColorList(Context context) {
        return ColorStateList.valueOf(getRecentsDarkModeTextColor(context));
    }

    public static int getRecentsDarkModeTextColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.RECENTS_DARK_MODE_TEXT_COLOR, BLACK);
    }

    public static int getAppIconColor(Context context, Drawable icon) {
        if (colorizeAppIcon(context, icon)) {
           return getRecentsAppIconColor(context);
        } else {
            return 0;
        }
    }

    public static boolean colorizeAppIcon(Context context, Drawable d) {
        if (d == null) {
            return false;
        }

        NotificationColorUtil cu = NotificationColorUtil.getInstance(context);
        final int appIconColorMode = getRecentsAppIconColorMode(context);
        final boolean isGreyscale = cu.isGrayscaleIcon(d);

        if (appIconColorMode == 0) {
            return false;
        } else if (appIconColorMode == 1) {
            return isGreyscale;
        } else {
            return true;
        }
    }

    private static int getRecentsAppIconColorMode(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.RECENTS_APP_ICON_COLOR_MODE, 0);
    }

    public static int getRecentsRippleColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.RECENTS_RIPPLE_COLOR, WHITE);
    }

    public static int getRecentsDarkModeRippleColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.RECENTS_DARK_MODE_RIPPLE_COLOR, TRANSLUCENT_BLACK);
    }

    public static int getRecentsClockColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.RECENTS_FULL_SCREEN_CLOCK_COLOR, WHITE);
    }

    public static int getRecentsDateColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.RECENTS_FULL_SCREEN_DATE_COLOR, WHITE);
    }

    public static int getMemBarTextColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.MEM_TEXT_COLOR, WHITE);
    }

    public static int getMemBarColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.MEMORY_BAR_COLOR, WHITE);
    }

    public static int getMemBarUsedColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.MEMORY_BAR_USED_COLOR, TRANSLUCENT_BLACK);
    }
}
