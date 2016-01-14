/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar;

import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.Icon;
import android.graphics.drawable.InsetDrawable;
import android.graphics.PorterDuff.Mode;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewDebug;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.util.vrtoxin.ImageHelper;
import com.android.systemui.R;

import java.text.NumberFormat;
import java.util.ArrayList;

public class StatusBarIconView extends AnimatedImageView {
    private static final String TAG = "StatusBarIconView";

    private StatusBarIcon mIcon;
    @ViewDebug.ExportedProperty private String mSlot;
    private Drawable mNumberBackground;
    private Paint mNumberPaint;
    private int mNumberX;
    private int mNumberY;
    private String mNumberText;
    private Notification mNotification;
    private final boolean mBlocked;
    private boolean mShowNotificationCount;
    private int mIconColor;
    private int mNotifCountColor;
    private int mNotifCountTextColor;
    private GlobalSettingsObserver mObserver;

    public StatusBarIconView(Context context, String slot, Notification notification) {
        this(context, slot, notification, false);
    }

    public StatusBarIconView(Context context, String slot, Notification notification,
            boolean blocked) {
        super(context);
        final Resources res = context.getResources();
        mBlocked = blocked;
        final float densityMultiplier = res.getDisplayMetrics().density;
        final float scaledPx = 8 * densityMultiplier;
        mSlot = slot;
        updateColors();
        mNumberPaint = new Paint();
        mNumberPaint.setTextAlign(Paint.Align.CENTER);
        mNumberPaint.setAntiAlias(true);
        mNumberPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mNumberPaint.setTextSize(scaledPx);

        mObserver = GlobalSettingsObserver.getInstance(context);
        // We do not resize and scale system icons (on the right), only notification icons (on the
        // left).
        if (notification != null) {
            final int outerBounds = res.getDimensionPixelSize(R.dimen.status_bar_icon_size);
            final int imageBounds = res.getDimensionPixelSize(R.dimen.status_bar_icon_drawing_size);
            final float scale = (float)imageBounds / (float)outerBounds;
            setScaleX(scale);
            setScaleY(scale);
        }

        setScaleType(ImageView.ScaleType.CENTER);
    }

    public void setNotification(Notification notification) {
        mNotification = notification;
        updateColors();
        setContentDescription(notification);
    }

    public StatusBarIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBlocked = false;
        final Resources res = context.getResources();
        final int outerBounds = res.getDimensionPixelSize(R.dimen.status_bar_icon_size);
        final int imageBounds = res.getDimensionPixelSize(R.dimen.status_bar_icon_drawing_size);
        final float scale = (float)imageBounds / (float)outerBounds;
        setScaleX(scale);
        setScaleY(scale);
    }

    private static boolean streq(String a, String b) {
        if (a == b) {
            return true;
        }
        if (a == null && b != null) {
            return false;
        }
        if (a != null && b == null) {
            return false;
        }
        return a.equals(b);
    }

    public boolean equalIcons(Icon a, Icon b) {
        if (a == b) return true;
        if (a.getType() != b.getType()) return false;
        switch (a.getType()) {
            case Icon.TYPE_RESOURCE:
                return a.getResPackage().equals(b.getResPackage()) && a.getResId() == b.getResId();
            case Icon.TYPE_URI:
                return a.getUriString().equals(b.getUriString());
            default:
                return false;
        }
    }
    /**
     * Returns whether the set succeeded.
     */
    public boolean set(StatusBarIcon icon) {
        return set(icon, false);
    }

    private boolean set(StatusBarIcon icon, boolean force) {
        final boolean iconEquals = mIcon != null && equalIcons(mIcon.icon, icon.icon);
        final boolean levelEquals = iconEquals
                && mIcon.iconLevel == icon.iconLevel;
        final boolean visibilityEquals = mIcon != null
                && mIcon.visible == icon.visible;
        final boolean numberEquals = mIcon != null
                && mIcon.number == icon.number;
        mIcon = icon.clone();
        updateColors();
        setContentDescription(icon.contentDescription);
        if (!iconEquals || force) {
            if (!updateDrawable(false /* no clear */)) return false;
        }
        if (!levelEquals || force) {
            setImageLevel(icon.iconLevel);
        }

        if (!numberEquals || force) {
            if (icon.number > 0 && mShowNotificationCount) {
                if (mNumberBackground == null) {
                    mNumberBackground = getContext().getResources().getDrawable(
                            R.drawable.ic_notification_overlay);
                }
                mNumberBackground.setColorFilter(null);
                mNumberBackground.setColorFilter(mNotifCountColor,
                        Mode.MULTIPLY);
                placeNumber();
            } else {
                mNumberBackground = null;
                mNumberText = null;
            }
            invalidate();
        }
        if (!visibilityEquals || force) {
            setVisibility(icon.visible && !mBlocked ? VISIBLE : GONE);
        }
        return true;
    }

    public void updateDrawable() {
        updateDrawable(true /* with clear */);
    }

    private boolean updateDrawable(boolean withClear) {
        if (mIcon == null) {
            return false;
        }
        Drawable drawable = getIcon(mIcon);
        if (drawable == null) {
            Log.w(TAG, "No icon for slot " + mSlot);
            return false;
        }
        if (withClear) {
            setImageDrawable(null);
        }
        setImageDrawable(drawable);

        return true;
    }

    private Drawable getIcon(StatusBarIcon icon) {
        return getIcon(getContext(), icon);
    }

    /**
     * Returns the right icon to use for this item
     *
     * @param context Context to use to get resources
     * @return Drawable for this item, or null if the package or item could not
     *         be found
     */
    public static Drawable getIcon(Context context, StatusBarIcon icon) {
        int userId = icon.user.getIdentifier();
        if (userId == UserHandle.USER_ALL) {
            userId = UserHandle.USER_OWNER;
        }
        return icon.icon.loadDrawableAsUser(context, userId);
    }

    public StatusBarIcon getStatusBarIcon() {
        return mIcon;
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        if (mNotification != null) {
            event.setParcelableData(mNotification);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mNumberBackground != null) {
            placeNumber();
        }
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateDrawable();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mNumberBackground != null) {
            mNumberBackground.draw(canvas);
            canvas.drawText(mNumberText, mNumberX, mNumberY, mNumberPaint);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mObserver != null) {
            mObserver.attach(this);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mObserver != null) {
            mObserver.detach(this);
        }
    }

    @Override
    protected void debug(int depth) {
        super.debug(depth);
        Log.d("View", debugIndent(depth) + "slot=" + mSlot);
        Log.d("View", debugIndent(depth) + "icon=" + mIcon);
    }

    void placeNumber() {
        final String str;
        final int tooBig = getContext().getResources().getInteger(
                android.R.integer.status_bar_notification_info_maxnum);
        if (mIcon.number > tooBig) {
            str = getContext().getResources().getString(
                        android.R.string.status_bar_notification_info_overflow);
        } else {
            NumberFormat f = NumberFormat.getIntegerInstance();
            str = f.format(mIcon.number);
        }
        mNumberText = str;

        final int w = getWidth();
        final int h = getHeight();
        final Rect r = new Rect();
        mNumberPaint.getTextBounds(str, 0, str.length(), r);
        final int tw = r.right - r.left;
        final int th = r.bottom - r.top;
        mNumberBackground.getPadding(r);
        int dw = r.left + tw + r.right;
        if (dw < mNumberBackground.getMinimumWidth()) {
            dw = mNumberBackground.getMinimumWidth();
        }
        mNumberX = w-r.right-((dw-r.right-r.left)/2);
        int dh = r.top + th + r.bottom;
        if (dh < mNumberBackground.getMinimumWidth()) {
            dh = mNumberBackground.getMinimumWidth();
        }
        mNumberY = h-r.bottom-((dh-r.top-th-r.bottom)/2);
        mNumberBackground.setBounds(w-dw, h-dh, w, h);
    }

    private void setContentDescription(Notification notification) {
        if (notification != null) {
            CharSequence tickerText = notification.tickerText;
            if (!TextUtils.isEmpty(tickerText)) {
                setContentDescription(tickerText);
            }
        }
    }

    public String toString() {
        return "StatusBarIconView(slot=" + mSlot + " icon=" + mIcon
            + " notification=" + mNotification + ")";
    }

    public String getSlot() {
        return mSlot;
    }

    static class GlobalSettingsObserver extends ContentObserver {
        private static GlobalSettingsObserver sInstance;
        private ArrayList<StatusBarIconView> mIconViews = new ArrayList<StatusBarIconView>();
        private Context mContext;

        GlobalSettingsObserver(Handler handler, Context context) {
            super(handler);
            mContext = context.getApplicationContext();
        }

        static GlobalSettingsObserver getInstance(Context context) {
            if (sInstance == null) {
                sInstance = new GlobalSettingsObserver(new Handler(), context);
            }
            return sInstance;
        }

        void attach(StatusBarIconView sbiv) {
            if (mIconViews.isEmpty()) {
                observe();
            }
            mIconViews.add(sbiv);
            sbiv.setColorFilter(null);
            Drawable icon = sbiv.getIcon(sbiv.mIcon);
            if (icon != null) {
                if (icon instanceof InsetDrawable) {
                    Drawable d = ((InsetDrawable) icon).getDrawable();
                    if (d != null) {
                        sbiv.setColorFilter(sbiv.mIconColor, Mode.MULTIPLY);
                    }
                } else {
                    sbiv.setColorFilter(sbiv.mIconColor, Mode.MULTIPLY);
                }
            }
        }

        void detach(StatusBarIconView sbiv) {
            mIconViews.remove(sbiv);
            if (mIconViews.isEmpty()) {
                unobserve();
            }
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_NOTIF_COUNT),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_TICKER_ICON_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_NOTIF_COUNT_ICON_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_NOTIF_COUNT_TEXT_COLOR),
                    false, this);
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            for (StatusBarIconView sbiv : mIconViews) {
                sbiv.updateColors();
                sbiv.set(sbiv.mIcon, true);
                sbiv.setColorFilter(null);
                Drawable icon = sbiv.getIcon(sbiv.mIcon);
                if (icon != null) {
                    if (icon instanceof InsetDrawable) {
                        Drawable d = ((InsetDrawable) icon).getDrawable();
                        if (d != null) {
                            sbiv.setColorFilter(sbiv.mIconColor, Mode.MULTIPLY);
                        }
                    } else {
                        sbiv.setColorFilter(sbiv.mIconColor, Mode.MULTIPLY);
                    }
                }
                sbiv.mNumberPaint.setColor(sbiv.mNotifCountTextColor);
            }
        }
    }

    private void updateColors() {
        ContentResolver resolver = mContext.getContentResolver();

        mShowNotificationCount = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_NOTIF_COUNT,
                    mContext.getResources().getBoolean(
                    R.bool.config_statusBarShowNumber) ? 0 : 0) == 1;
        mIconColor = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_TICKER_ICON_COLOR,
                0xffffffff);
        mNotifCountColor = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_NOTIF_COUNT_ICON_COLOR,
                0xffffffff);
        mNotifCountTextColor = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_NOTIF_COUNT_TEXT_COLOR,
                0xffffffff);
    }
}

