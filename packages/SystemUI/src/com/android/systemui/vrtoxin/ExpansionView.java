/*
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

package com.android.systemui.vrtoxin;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.PorterDuff.Mode;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.statusbar.phone.ActivityStarter;

import com.android.systemui.R;

import com.android.internal.util.vrtoxin.ExpansionViewColorHelper;

public class ExpansionView extends RelativeLayout {

    private static Context mContext;
    private static ContentResolver mResolver;
    private ActivityStarter mActivityStarter;

    // Layouts
    private ExpansionViewActivityPanel mActivityPanel;
    private View mCustomPanel;
    private View mLogoPanel;
    private View mShortcutBar;
    private View mShortcutBarContainer;
    private WeatherBarContainer mWeatherPanel;

    // Views
    private TextView mCustomText;
    private ImageView mShadeRomLogo;
    private ImageView mLayoutChanger;
 
    // On/Off Switches
    private boolean mExpansionViewVibrate = false;
    private boolean mShowChanger = false;
    private boolean mShowShortcutPanel = false;
    private boolean mShowText = false;

    protected Vibrator mVibrator;

    // Panels
    private final static int CUSTOM_PANEL    = 0;
    private final static int WEATHER_PANEL   = 1;
    private final static int ACTIVITY_PANEL  = 2;
    private final static int LOGO_PANEL      = 3;
 
    private static int NEXT_VISIBLE_PANEL = CUSTOM_PANEL;

    private boolean mListening = false;

    public ExpansionView(Context context) {
        this(context, null);
    }

    public ExpansionView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExpansionView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mResolver = mContext.getContentResolver();
    }

    public void setUp(ActivityStarter starter) {
        mActivityStarter = starter;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
 
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mActivityPanel = (ExpansionViewActivityPanel) findViewById(R.id.expansion_view_activity_panel);
        mCustomPanel = findViewById(R.id.expansion_view_custom_panel);
        mCustomText = (TextView) findViewById(R.id.custom_text);
        mLayoutChanger = (ImageView) findViewById(R.id.expansion_view_layout_changer);
        mLogoPanel = findViewById(R.id.expansion_view_logo_panel);
        mShadeRomLogo = (ImageView) findViewById(R.id.expansion_view_rom_logo);
        mShortcutBar = findViewById(R.id.expansion_view_shortcut_bar_container);
        mShortcutBarContainer = findViewById(R.id.expansion_view_shortcut_bar_container);
        mWeatherPanel = (WeatherBarContainer) findViewById(R.id.expansion_view_weather_container);

        mCustomText.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                handleTextLongClick();
            return true;
            }
        });

        mShadeRomLogo.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                handleLogoLongClick();
            return true;
            }
        });

        mLayoutChanger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                changeView(true);
            }
        });

        mLayoutChanger.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                handleChangeLongClick();
            return true;
            }
        });

        changeView(false);
    }

    public void changeView(boolean animate) {
        mActivityPanel.setVisibility(NEXT_VISIBLE_PANEL == ACTIVITY_PANEL
                ? View.VISIBLE : View.GONE);
        mCustomPanel.setVisibility(NEXT_VISIBLE_PANEL == CUSTOM_PANEL
                ? View.VISIBLE : View.GONE);
        mLogoPanel.setVisibility(NEXT_VISIBLE_PANEL == LOGO_PANEL
                ? View.VISIBLE : View.GONE);
        mWeatherPanel.setVisibility(NEXT_VISIBLE_PANEL == WEATHER_PANEL
                ? View.VISIBLE : View.GONE);
 
        if (NEXT_VISIBLE_PANEL == ACTIVITY_PANEL && animate) {
            mActivityPanel.startAnimation(getAnimation(true));
            mWeatherPanel.startAnimation(getAnimation(false));
        } else if (NEXT_VISIBLE_PANEL == WEATHER_PANEL && animate) {
            mWeatherPanel.startAnimation(getAnimation(true));
            mCustomPanel.startAnimation(getAnimation(false));
        } else if (NEXT_VISIBLE_PANEL == LOGO_PANEL && animate) {
            mLogoPanel.startAnimation(getAnimation(true));
            mActivityPanel.startAnimation(getAnimation(false));
        } else if (NEXT_VISIBLE_PANEL == CUSTOM_PANEL && animate) {
            mCustomPanel.startAnimation(getAnimation(true));
            mLogoPanel.startAnimation(getAnimation(false));
        }
 
        mWeatherPanel.setListening(NEXT_VISIBLE_PANEL == WEATHER_PANEL);
        mActivityPanel.setListening(NEXT_VISIBLE_PANEL == ACTIVITY_PANEL);
        NEXT_VISIBLE_PANEL += 1;
        if (NEXT_VISIBLE_PANEL == 4) {
            NEXT_VISIBLE_PANEL = 0;
        }
    }

    private static Animation getAnimation(boolean isIn) {
        int animationResId = 0;
        final int style = Settings.System.getInt(mResolver,
                Settings.System.EXPANSION_VIEW_ANIMATION, 0);
 
        if (style == 0) {
            animationResId = isIn ? R.anim.push_down_in : R.anim.push_down_out;
        } else if (style == 1) {
            animationResId = isIn ? R.anim.last_app_in : R.anim.last_app_out;
        } else if (style == 2) {
            animationResId = isIn ? R.anim.push_left_in : R.anim.push_right_out;
        } else if (style == 3) {
            animationResId = isIn ? R.anim.push_right_in : R.anim.push_left_out;
        } else if (style == 4) {
            animationResId = isIn ? R.anim.rotate : R.anim.push_down_out;
        } else if (style == 5) {
            animationResId = isIn ? R.anim.turn_in : R.anim.turn_out;
        } else if (style == 6) {
            animationResId = isIn ? R.anim.push_up_in : R.anim.push_up_out;
        }
        return AnimationUtils.loadAnimation(mContext, animationResId);
    }

    public void setBackgroundColor() {
        final int backgroundColor = ExpansionViewColorHelper.getBackgroundColor(mContext);
        final boolean showBackground = Settings.System.getInt(mResolver,
                Settings.System.EXPANSION_VIEW_BACKGROUND, 0) == 1;
        ((Drawable) getBackground()).setTint(backgroundColor);
        ((Drawable) getBackground()).setAlpha(showBackground ? 255 : 0);
    }

    public void setCustomText(String text) {
        mCustomText.setText(text);
    }

    public void updateIconColor(int color) {
        if (mLayoutChanger != null) {
            mLayoutChanger.setColorFilter(color, Mode.MULTIPLY);
        }
    }

    public void showLayoutChanger(boolean showChanger) {
        mShowChanger = showChanger;
        if (mShowChanger) {
            mLayoutChanger.setVisibility(View.VISIBLE);
        } else {
            mLayoutChanger.setVisibility(View.GONE);
        }
    }

    public void showShortcutPanel(boolean showShortcuts) {
        mShowShortcutPanel = showShortcuts;
        if (mShowShortcutPanel) {
            mShortcutBar.setVisibility(View.VISIBLE);
        } else {
            mShortcutBar.setVisibility(View.GONE);
        }
    }

    public void showText(boolean text) {
        mShowText = text;
        if (mShowText) {
            mCustomText.setVisibility(View.VISIBLE);
        } else {
            mCustomText.setVisibility(View.GONE);
        }
        //updateShortcutsLocation(mShowText);
    }

    public void setRippleColor() {
        RippleDrawable logoBackground =
                (RippleDrawable) mContext.getDrawable(R.drawable.ripple_drawable_rectangle).mutate();
        RippleDrawable textBackground =
                (RippleDrawable) mContext.getDrawable(R.drawable.ripple_drawable_rectangle).mutate();
        RippleDrawable rippleBackground =
                (RippleDrawable) mContext.getDrawable(R.drawable.ripple_drawable_oval).mutate();
        final int color = ExpansionViewColorHelper.getNormalRippleColor(mContext);
        logoBackground.setColor(ColorStateList.valueOf(color));
        textBackground.setColor(ColorStateList.valueOf(color));
        rippleBackground.setColor(ColorStateList.valueOf(color));
        mCustomText.setBackground(textBackground);
        mLayoutChanger.setBackground(rippleBackground);
        mShadeRomLogo.setBackground(logoBackground);
    }

    /*public void updateShortcutsLocation (boolean text) {
        boolean canWeMoveUp = !text;

        int paddingBottom = mContext.getResources().getDimensionPixelSize(
                R.dimen.expansion_view_shortcut_panel_padding_bottom);
        int paddingTop = canWeMoveUp
                ? mContext.getResources().getDimensionPixelSize(
                        R.dimen.expansion_view_shortcut_panel_padding_top)
                : paddingBottom;

        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mShortcutBarContainer.getLayoutParams();
        lp.removeRule(canWeMoveUp ? RelativeLayout.ALIGN_BOTTOM : RelativeLayout.CENTER_VERTICAL);
        lp.addRule(canWeMoveUp ? RelativeLayout.CENTER_VERTICAL : RelativeLayout.ALIGN_BOTTOM);
        mShortcutBarContainer.setLayoutParams(lp);
        mShortcutBarContainer.setPadding(0, paddingBottom, 0, paddingTop);
    }*/

    public void setTextColor(int color) {
        mCustomText.setTextColor(color);
    }

    public void setTextSize(int size) {
        mCustomText.setTextSize(size);
    }

    public void setTypeface(Typeface tf) {
        mCustomText.setTypeface(tf);
    }

    private void handleTextLongClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$ExpansionViewSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void handleLogoLongClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$MainSettingsActivity");
        mActivityStarter.startActivity(intent, true /* dismissShade */);
    }

    private void handleChangeLongClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$ExpansionViewSettingsActivity");
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
