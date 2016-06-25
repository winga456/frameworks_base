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

package com.android.systemui.vrtoxin.expansionview.panels;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.PorterDuff.Mode;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.statusbar.phone.ActivityStarter;

import com.android.systemui.R;

import com.android.internal.util.vrtoxin.ExpansionViewColorHelper;

import java.io.InputStream;

public class ExpansionViewCustomPanel extends RelativeLayout {

    private static Context mContext;
    private static ContentResolver mResolver;
    private static SparseArray<Drawable> mCache = new SparseArray<Drawable>();
    private ActivityStarter mActivityStarter;

    private ExpansionViewActivityPanel mActivityPanel;
    private ExpansionViewWeatherPanel mWeatherPanel;
    private View mCustomPanel;
    private View mLogoPanel;
    private View mShortcutBar;
    private View mShortcutBarContainer;

    private View mPanelOne;
    private View mPanelTwo;
    private View mPanelThree;
    private View mPanelFour;

    private ImageView mLayoutChanger;
    private ImageView mShadeRomLogo;
    private TextView mCustomText;

    protected Vibrator mVibrator;
 
    private boolean mExpansionViewVibrate = false;
    private boolean mShowShortcutPanel = false;

    private int panelOne;
    private int panelTwo;
    private int panelThree;
    private int panelFour;

    public static int CUSTOM_PANEL;
    public static int WEATHER_PANEL;
    public static int ACTIVITY_PANEL;
    public static int LOGO_PANEL;

    public final static int PANEL_ONE     = 0;
    public final static int PANEL_TWO     = 1;
    public final static int PANEL_THREE   = 2;
    public final static int PANEL_FOUR    = 3;
 
    public static int NEXT_VISIBLE_PANEL = PANEL_ONE;

    private static final int DEFAULT_LOGO = R.drawable.cyanide_logo;

    private boolean mListening = false;

    public ExpansionViewCustomPanel(Context context) {
        this(context, null);
    }

    public ExpansionViewCustomPanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExpansionViewCustomPanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mResolver = mContext.getContentResolver();
    }

    public void setUp(ActivityStarter starter) {
        mActivityStarter = starter;
        updatePanelViews();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
 
        mActivityPanel = (ExpansionViewActivityPanel) findViewById(R.id.expansion_view_activity_panel);
        mCustomPanel = findViewById(R.id.expansion_view_custom_panel);
        mCustomText = (TextView) findViewById(R.id.custom_text);
        mLayoutChanger = (ImageView) findViewById(R.id.expansion_view_layout_changer);
        mLogoPanel = findViewById(R.id.expansion_view_logo_panel);
        mShadeRomLogo = (ImageView) findViewById(R.id.expansion_view_rom_logo);
        mShortcutBar = findViewById(R.id.expansion_view_shortcut_bar_container);
        mShortcutBarContainer = findViewById(R.id.expansion_view_shortcut_bar_container);
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mWeatherPanel = (ExpansionViewWeatherPanel) findViewById(R.id.expansion_view_weather_container);

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

    public void updatePanelViews() {
        panelOne = Settings.System.getInt(
                mResolver, Settings.System.EXPANSION_VIEW_PANEL_ONE, 1);
        panelTwo = Settings.System.getInt(
                mResolver, Settings.System.EXPANSION_VIEW_PANEL_TWO, 0);
        panelThree = Settings.System.getInt(
                mResolver, Settings.System.EXPANSION_VIEW_PANEL_THREE, 3);
        panelFour = Settings.System.getInt(
                mResolver, Settings.System.EXPANSION_VIEW_PANEL_FOUR, 2);
 
        if (panelOne == 0) {
            mPanelOne = mCustomPanel;
        } else if (panelOne == 1) {
            mPanelOne = mWeatherPanel;
        } else if (panelOne == 2) {
            mPanelOne = mActivityPanel;
        } else if (panelOne == 3) {
            mPanelOne = mLogoPanel;
        }
 
        if (panelTwo == 0) {
            mPanelTwo = mCustomPanel;
        } else if (panelTwo == 1) {
            mPanelTwo = mWeatherPanel;
        } else if (panelTwo == 2) {
            mPanelTwo = mActivityPanel;
        } else if (panelTwo == 3) {
            mPanelTwo = mLogoPanel;
        }
 
        if (panelThree == 0) {
            mPanelThree = mCustomPanel;
        } else if (panelThree == 1) {
            mPanelThree = mWeatherPanel;
        } else if (panelThree == 2) {
            mPanelThree = mActivityPanel;
        } else if (panelThree == 3) {
            mPanelThree = mLogoPanel;
        // HACK: Fix animation and NPE if only 2 panels are selected
        // TO DO: FIX THIS
        } else if (panelThree == 4) {
            mPanelThree = mPanelTwo;
        }
 
        if (panelFour == 0) {
            mPanelFour = mCustomPanel;
        } else if (panelFour == 1) {
            mPanelFour = mWeatherPanel;
        } else if (panelFour == 2) {
            mPanelFour = mActivityPanel;
        } else if (panelFour == 3) {
            mPanelFour = mLogoPanel;
        }

        if (panelTwo == 4) {
            if (NEXT_VISIBLE_PANEL == 1) {
                NEXT_VISIBLE_PANEL = 0;
            }
            mLayoutChanger.setVisibility(View.GONE);
        } else if (panelThree == 4) {
            if (NEXT_VISIBLE_PANEL == 2) {
                NEXT_VISIBLE_PANEL = 0;
            }
            mLayoutChanger.setVisibility(View.VISIBLE);
        } else if (panelFour == 4) {
            if (NEXT_VISIBLE_PANEL == 3) {
                NEXT_VISIBLE_PANEL = 0;
            }
            mLayoutChanger.setVisibility(View.VISIBLE);
        } else {
            if (NEXT_VISIBLE_PANEL == 4) {
                NEXT_VISIBLE_PANEL = 0;
            }
            mLayoutChanger.setVisibility(View.VISIBLE);
        }
    }

    public void changeView(boolean animate) {
        updatePanelViews();
        boolean activityVisible = NEXT_VISIBLE_PANEL == PANEL_ONE && panelOne == 2 || NEXT_VISIBLE_PANEL == PANEL_TWO && panelTwo == 2 || NEXT_VISIBLE_PANEL == PANEL_THREE && panelThree == 2 || NEXT_VISIBLE_PANEL == PANEL_FOUR && panelFour == 2;
        boolean customVisible = NEXT_VISIBLE_PANEL == PANEL_ONE && panelOne == 0 || NEXT_VISIBLE_PANEL == PANEL_TWO && panelTwo == 0 || NEXT_VISIBLE_PANEL == PANEL_THREE && panelThree == 0 || NEXT_VISIBLE_PANEL == PANEL_FOUR && panelFour == 0;
        boolean logoVisible = NEXT_VISIBLE_PANEL == PANEL_ONE && panelOne == 3 || NEXT_VISIBLE_PANEL == PANEL_TWO && panelTwo == 3 || NEXT_VISIBLE_PANEL == PANEL_THREE && panelThree == 3 || NEXT_VISIBLE_PANEL == PANEL_FOUR && panelFour == 3;
        boolean weatherVisible = NEXT_VISIBLE_PANEL == PANEL_ONE && panelOne == 1 || NEXT_VISIBLE_PANEL == PANEL_TWO && panelTwo == 1 || NEXT_VISIBLE_PANEL == PANEL_THREE && panelThree == 1 || NEXT_VISIBLE_PANEL == PANEL_FOUR && panelFour == 1;
        mActivityPanel.setVisibility(activityVisible
                ? View.VISIBLE : View.GONE);
        mCustomPanel.setVisibility(customVisible
                ? View.VISIBLE : View.GONE);
        mLogoPanel.setVisibility(logoVisible
                ? View.VISIBLE : View.GONE);
        mWeatherPanel.setVisibility(weatherVisible
                ? View.VISIBLE : View.GONE);

        if (panelFour == 4) {
            if (NEXT_VISIBLE_PANEL == PANEL_ONE && animate) {
                mPanelOne.startAnimation(getAnimation(true));
                mPanelThree.startAnimation(getAnimation(false));
            } else if (NEXT_VISIBLE_PANEL == PANEL_TWO && animate) {
                mPanelTwo.startAnimation(getAnimation(true));
                mPanelOne.startAnimation(getAnimation(false));
            } else if (NEXT_VISIBLE_PANEL == PANEL_THREE && animate) {
                mPanelThree.startAnimation(getAnimation(true));
                mPanelTwo.startAnimation(getAnimation(false));
            }
        } else if (panelThree == 4) {
            if (NEXT_VISIBLE_PANEL == PANEL_ONE && animate) {
                mPanelOne.startAnimation(getAnimation(true));
                mPanelTwo.startAnimation(getAnimation(false));
            } else if (NEXT_VISIBLE_PANEL == PANEL_TWO && animate) {
                mPanelTwo.startAnimation(getAnimation(true));
                mPanelOne.startAnimation(getAnimation(false));
            }
        } else if (panelTwo == 4) {
            if (NEXT_VISIBLE_PANEL == PANEL_ONE && animate) {
                // Do nothing here
            }
        } else {
            if (NEXT_VISIBLE_PANEL == PANEL_ONE && animate) {
                mPanelOne.startAnimation(getAnimation(true));
                mPanelFour.startAnimation(getAnimation(false));
            } else if (NEXT_VISIBLE_PANEL == PANEL_TWO && animate) {
                mPanelTwo.startAnimation(getAnimation(true));
                mPanelOne.startAnimation(getAnimation(false));
            } else if (NEXT_VISIBLE_PANEL == PANEL_THREE && animate) {
                mPanelThree.startAnimation(getAnimation(true));
                mPanelTwo.startAnimation(getAnimation(false));
            } else if (NEXT_VISIBLE_PANEL == PANEL_FOUR && animate) {
                mPanelFour.startAnimation(getAnimation(true));
                mPanelThree.startAnimation(getAnimation(false));
            }
        }
        NEXT_VISIBLE_PANEL += 1;
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

    public void setStroke() {
        final int mStroke = Settings.System.getInt(
                mResolver, Settings.System.EXPANSION_VIEW_STROKE, 1);
        final int mStrokeColor = Settings.System.getInt(
                mResolver, Settings.System.EXPANSION_VIEW_STROKE_COLOR, 0xffffffff);
        final int mStrokeThickness = Settings.System.getInt(
                mResolver, Settings.System.EXPANSION_VIEW_STROKE_THICKNESS, 0);
        final int mCornerRadius = Settings.System.getInt(
                mResolver, Settings.System.EXPANSION_VIEW_CORNER_RADIUS, 2);
        final int mCustomDashWidth = Settings.System.getInt(
                mResolver, Settings.System.EXPANSION_VIEW_STROKE_DASH_GAP, 0);
        final int mCustomDashGap = Settings.System.getInt(
                mResolver, Settings.System.EXPANSION_VIEW_STROKE_DASH_WIDTH, 10);
        final int backgroundColor = ExpansionViewColorHelper.getBackgroundColor(mContext);
        final GradientDrawable gradientDrawable = new GradientDrawable();
        if (mStroke == 0) { // Disable by setting border thickness to 0
            gradientDrawable.setColor(backgroundColor);
            gradientDrawable.setStroke(0, mStrokeColor);
            gradientDrawable.setCornerRadius(mCornerRadius);
            setBackground(gradientDrawable);
        } else if (mStroke == 1) { // use accent color for border
            gradientDrawable.setColor(backgroundColor);
            gradientDrawable.setStroke(mStrokeThickness, mContext.getResources().getColor(R.color.system_accent_color),
                    mCustomDashWidth, mCustomDashGap);
        } else if (mStroke == 2) { // use custom border color
            gradientDrawable.setColor(backgroundColor);
            gradientDrawable.setStroke(mStrokeThickness, mStrokeColor, mCustomDashWidth, mCustomDashGap);
        }

        if (mStroke != 0) {
            gradientDrawable.setCornerRadius(mCornerRadius);
            setBackground(gradientDrawable);
        }
    }

    public void updateBackgroundImage() {
        final String customLogo = Settings.System.getString(mResolver,
                Settings.System.EXPANSION_VIEW_CUSTOM_LOGO);
        if (customLogo != null && !(new String("").equals(customLogo))) {
            try {
                InputStream input = mResolver.openInputStream(Uri.parse(customLogo));
                mShadeRomLogo.setImageDrawable(Drawable.createFromStream(input, customLogo));
            } catch (Exception ugh) {
                mShadeRomLogo.setImageDrawable(getDefaultImage());
            }
        } else {
            mShadeRomLogo.setImageDrawable(getDefaultImage());
        }
    }

    private Drawable getDefaultImage() {
        return loadOrFetch(DEFAULT_LOGO);
    }

    private static Drawable loadOrFetch(int resId) {
        Drawable res = mCache.get(resId);

        if (res == null) {
            // We don't have this drawable cached, do it!
            final Resources r = mContext.getResources();
            res = r.getDrawable(resId);
            mCache.put(resId, res);
        }
        return res;
    }

    public void setCustomText(String text) {
        mCustomText.setText(text);
    }

    public void updateIconColor(int color) {
        if (mLayoutChanger != null) {
            mLayoutChanger.setColorFilter(color, Mode.MULTIPLY);
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
