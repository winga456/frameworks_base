/*
* Copyright (C) 2016 The VRToxin Project
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

import com.android.internal.R;

public class VolumeDialogColorHelper {

    private static int BLACK = 0xff000000;
    private static int WHITE = 0xffffffff;
    private static int MATERIAL_GREEN = 0xff009688;
    private static int MATERIAL_BLUE_GREY = 0xff37474f;

    public static int getBackgroundColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.VOLUME_DIALOG_BG_COLOR, MATERIAL_BLUE_GREY);
    }

    public static int getExpandButtonColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.VOLUME_DIALOG_EXPAND_BUTTON_COLOR, MATERIAL_GREEN);
    }

    public static ColorStateList getIconColorList(Context context) {
        return ColorStateList.valueOf(getIconColor(context));
    }

    public static int getIconColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.VOLUME_DIALOG_ICON_COLOR, MATERIAL_GREEN);
    }

    public static ColorStateList getSliderIconColorList(Context context) {
        return ColorStateList.valueOf(getSliderIconColor(context));
    }

    public static int getSliderIconColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.VOLUME_DIALOG_SLIDER_ICON_COLOR, MATERIAL_GREEN);
    }

    public static ColorStateList getSliderColorList(Context context) {
        return ColorStateList.valueOf(getSliderColor(context));
    }

    public static int getSliderColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.VOLUME_DIALOG_SLIDER_COLOR, MATERIAL_GREEN);
    }

    public static ColorStateList getSliderInactiveColorList(Context context) {
        return ColorStateList.valueOf(getSliderInactiveColor(context));
    }

    public static int getSliderInactiveColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.VOLUME_DIALOG_SLIDER_INACTIVE_COLOR, WHITE);
    }
}
