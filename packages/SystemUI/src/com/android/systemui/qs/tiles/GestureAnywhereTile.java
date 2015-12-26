/*
 * Copyright (C) 2015 CyanideL
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.internal.logging.MetricsLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GestureAnywhereTile extends QSTile<QSTile.BooleanState> {
    private boolean mListening;
    private GestureAnywhereObserver mObserver;

    public GestureAnywhereTile(Host host) {
        super(host);
        mObserver = new GestureAnywhereObserver(mHandler);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        toggleState();
        refreshState();
        qsCollapsePanel();
    }

    @Override
    public void handleLongClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
            "com.android.settings.Settings$GestureAnywhereSettingsSettingsActivity");
        mHost.startActivityDismissingKeyguard(intent);
    }

 protected void toggleState() {
         Settings.System.putInt(mContext.getContentResolver(),
                 Settings.System.GESTURE_ANYWHERE_ENABLED, !GestureAnywhereEnabled() ? 1 : 0);
    }


    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
	if (GestureAnywhereEnabled()) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_gestures_on);
        state.label = mContext.getString(R.string.quick_settings_gesture_anywhere_on);
	} else {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_gestures_off);
		state.label = mContext.getString(R.string.quick_settings_gesture_anywhere_off);
	    }
	}

    private boolean GestureAnywhereEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.GESTURE_ANYWHERE_ENABLED, 1) == 1;
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mObserver.startObserving();
        } else {
            mObserver.endObserving();
        }
    }

    private class GestureAnywhereObserver extends ContentObserver {
        public GestureAnywhereObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.GESTURE_ANYWHERE_ENABLED),
                    false, this);
        }

        public void endObserving() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsLogger.VRTOXIN_QS_CONSTANTS;
    }
}

