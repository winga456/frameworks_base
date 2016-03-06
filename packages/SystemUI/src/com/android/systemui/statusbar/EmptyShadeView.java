/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * 
 * Copyright (C) 2016 The VRToxin Project
 *     Highly modified this view and changed basically everything about it
 *     so add copyright since I (rogersb11) much more code in here than google at this point
 *     If I open up source and this is removed you can go fuck yourself LOL
 */

package com.android.systemui.statusbar;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.HapticFeedbackConstants;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.statusbar.phone.ActivityStarter;

import com.android.systemui.R;

public class EmptyShadeView extends StackScrollerDecorView {

    private ActivityStarter mActivityStarter;

    private TextView mEmptyShadeText;
    private ImageView mShadeRomLogo;
 
    private boolean mShow = false;

    public EmptyShadeView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setUp(ActivityStarter starter) {
        mActivityStarter = starter;
    }

    @Override
    protected View findContentView() {
        return findViewById(R.id.empty_shade);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
 
        mEmptyShadeText = (TextView) findViewById(R.id.no_notifications);
        mShadeRomLogo = (ImageView) findViewById(R.id.empty_shade_rom_logo);
        //mShadeRomText = (TextView) findViewById(R.id.empty_shade_rom_text);

        mEmptyShadeText.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                doHapticKeyClick(HapticFeedbackConstants.VIRTUAL_KEY);
                handleTextLongClick();
            return true;
            }
        });

        mShadeRomLogo.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                doHapticKeyClick(HapticFeedbackConstants.VIRTUAL_KEY);
                handleLogoLongClick();
            return true;
            }
        });
    }

    public void showRomLogo(boolean show) {
        mShow = show;
        if (mShow) {
            mShadeRomLogo.setVisibility(View.VISIBLE);
        } else {
            mShadeRomLogo.setVisibility(View.GONE);
        }
    }

    /*public void showRomText(boolean showRomText) {
        mShowRomText = showRomText;
        if (mShowRomText && getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
        } else if (!mShowRomText && getVisibility() != View.GONE) {
            setVisibility(View.GONE);
        }
    }*/
 
    public void setCustomText(String text) {
        ((TextView) findViewById(R.id.no_notifications)).setText(text);
    }

    public void updateTextColor(int color) {
        ((TextView) findViewById(R.id.no_notifications)).setTextColor(color);
        //((TextView) findViewById(R.id.rom_logo_text)).setTextColor(color);
    }

    public void setTextSize(int size) {
        ((TextView) findViewById(R.id.no_notifications)).setTextSize(size);
    }

    public void setTypeface(Typeface tf) {
        ((TextView)findViewById(R.id.no_notifications)).setTypeface(tf);
    }

    private void handleTextLongClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$EmptyShadeSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void handleLogoLongClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$MainSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void doHapticKeyClick(int type) {
        performHapticFeedback(type,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                | HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }
}
