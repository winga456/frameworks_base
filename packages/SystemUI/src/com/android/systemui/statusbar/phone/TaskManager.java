/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.systemui.statusbar.phone;

import android.app.ActionBar.LayoutParams;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.systemui.R;

import com.android.internal.util.vrtoxin.FontHelper;
import com.android.internal.util.vrtoxin.RandomColorHelper;
import com.android.internal.util.vrtoxin.TMColorHelper;

import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimerTask;
import java.util.Timer;

public class TaskManager {
    final boolean DEBUG = false;
    final String TAG = "TaskManager";

    private static final int MSG_KILL_ONE = 1;
    private static final int MSG_KILL_ALL = 2;
    private static final int MSG_START_TASK = 3;
    private final int MAX_TASK_NUM = 100;
    private final int FLOAT_VIEW_DISPLAY_TIME = 2000;

    private Context mContext;
    private TaskManagerHandler mHandler = new TaskManagerHandler();
    private ActivityStarter mActivityStarter;
    private Intent mHomeIntent;
    ActivityManager mActivityManager;
    PackageManager mPackageManager;
    private LinearLayout mTaskManagerPanel;
    private LinearLayout mTaskManagerList;
    private Button killAllButton;
    private TextView taskManagerTitle;
    private TextView memoryUsageText;
    private TextView taskNameTextView;

    private static final Object sLock = new Object();
    private ArrayList<DetailProcess> showTaskList = new ArrayList<DetailProcess>();
    private HashMap<String, View> childs = new HashMap<String, View>();

    private boolean mTipsShowing = false;

    public TaskManager(Context context, LinearLayout taskMangerPanel) {
        mContext = context;
        mTaskManagerPanel = taskMangerPanel;
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mPackageManager = context.getPackageManager();
        mHomeIntent = new Intent(Intent.ACTION_MAIN);
        mHomeIntent.addCategory(Intent.CATEGORY_HOME);

        taskManagerTitle =
                (TextView) mTaskManagerPanel.findViewById(R.id.task_manager_title);
        mTaskManagerList = (LinearLayout) mTaskManagerPanel.findViewById(R.id.task_manager_list);
        killAllButton = (Button) mTaskManagerPanel.findViewById(R.id.kill_all_button);
        killAllButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mHandler.sendEmptyMessage(MSG_KILL_ALL);
            }
        });
    }

    public void refreshTaskManagerView () {
        if (DEBUG) Log.e(TAG, "refreshTaskManagerView");
        loadRunningTasks();
        refreshMemoryUsagePanel();
        inflateTaskListView();
    }

    private void loadRunningTasks() {
        // get running task
        List<RunningTaskInfo> runningTaskList = mActivityManager.getRunningTasks(100);
        List<ResolveInfo> mHomeResolveList = mPackageManager.queryIntentActivities(mHomeIntent,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER);

        showTaskList.clear();
        for(RunningTaskInfo runningTaskInfo : runningTaskList) {
            boolean needshow = true;
            DetailProcess detailProcess = new DetailProcess(mContext, runningTaskInfo);
            final String packageName = detailProcess.getPackageName();

            if (appNeedHide(packageName)) {
                needshow = false;
            }

            if (needshow && mHomeResolveList!= null) {
                final int count = mHomeResolveList.size();
                for (int i = 0; i < count; i++) {
                    if (mHomeResolveList.get(i).activityInfo.packageName.equals(packageName)) {
                        detailProcess.setHome(true);
                        break;
                    }
                }
            }

            if (needshow && !detailProcess.filter()) {
                showTaskList.add(detailProcess);
            }
        }
    }

    private void refreshMemoryUsagePanel() {
        final ColorStateList memoryTextColor = TMColorHelper.getTaskMemoryTextColorList(mContext);
        final ColorStateList sliderColor = TMColorHelper.getSliderColorList(mContext);
        final ColorStateList sliderInactiveColor = TMColorHelper.getSliderInactiveColorList(mContext);
        final int mBarThickness = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.TASK_MANAGER_BAR_THICKNESS, 1);
        memoryUsageText =
                (TextView) mTaskManagerPanel.findViewById(R.id.memory_usage_text);
        final ProgressBar memoryUsageBar =
                (ProgressBar)mTaskManagerPanel.findViewById(R.id.memory_usage_Bar);
        memoryUsageBar.setProgressTintList(sliderColor);
        memoryUsageBar.setProgressBackgroundTintList(sliderInactiveColor);
        memoryUsageBar.setScaleY(mBarThickness);
        refreshMemoryusageText(memoryUsageText);
        memoryUsageText.setTextColor(memoryTextColor);
        refreshMemoryUsageBar(memoryUsageBar);
        refreshTitleBox();
        getTaskFontStyle();
    }

    private void inflateTaskListView() {
        if (mTaskManagerList != null) {
            // clear task manager view
            childs.clear();
            mTaskManagerList.removeAllViews();

            // refresh task list
            for(DetailProcess detailProcess : showTaskList) {
                inflateTaskItemView(detailProcess);
            }
        }
    }

    private void inflateTaskItemView(DetailProcess detailProcess) {
        final Drawable taskIcon = detailProcess.getIcon();
        final String taskName = detailProcess.getTitle();
        final String packageName = detailProcess.getPackageName();
        final ColorStateList appIconColor = TMColorHelper.getTaskAppIconColorList(mContext);

        if (childs != null && childs.containsKey(packageName)) {
            return;
        }

        final View itemView = View.inflate(mContext, R.layout.task_item, null);
        ImageView taskIconImageView = (ImageView) itemView.findViewById(R.id.task_icon);
        taskIconImageView.setImageDrawable(taskIcon);
        taskIconImageView.setImageTintList(appIconColor);
        taskIconImageView.setImageTintMode(Mode.MULTIPLY);
        taskIconImageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Message msg = Message.obtain();
                msg.what = MSG_START_TASK;
                Bundle bundle = new Bundle();
                bundle.putString("packagename", packageName);
                msg.setData(bundle);
                mHandler.sendMessage(msg);
            }
        });
        final ColorStateList taskTextColor = TMColorHelper.getTaskTextColorList(mContext);
        taskNameTextView = (TextView) itemView.findViewById(R.id.task_name);
        taskNameTextView.setText(taskName);
        taskNameTextView.setTextColor(taskTextColor);
        taskNameTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Message msg = Message.obtain();
                msg.what = MSG_START_TASK;
                Bundle bundle = new Bundle();
                bundle.putString("packagename", packageName);
                msg.setData(bundle);
                mHandler.sendMessage(msg);
            }
        });
        final ColorStateList killTaskColor = TMColorHelper.getTaskKillIconColorList(mContext);
        ImageView killButton = (ImageView) itemView.findViewById(R.id.kill_task);
        killButton.setImageTintList(killTaskColor);
        killButton.setImageTintMode(Mode.MULTIPLY);
        killButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Message msg = Message.obtain();
                msg.what = MSG_KILL_ONE;
                Bundle bundle = new Bundle();
                bundle.putString("packagename", packageName);
                msg.setData(bundle);
                mHandler.sendMessage(msg);
            }
        });

        childs.put(packageName, itemView);
        mTaskManagerList.addView(itemView);
        getTaskFontStyle();
    }

    private void refreshTitleBox() {
        final ColorStateList titleTextColor = TMColorHelper.getTaskTitleTextColorList(mContext);
        final ColorStateList killAllTextColor = TMColorHelper.getKillAllTextColorList(mContext);
        taskManagerTitle.setTextColor(titleTextColor);
        killAllButton.setTextColor(killAllTextColor);
    }

    private void refreshMemoryusageText(TextView textView) {
        String availMem = Formatter.formatShortFileSize(mContext, readAvailMem());
        String totalMem = Formatter.formatShortFileSize(mContext, readTotalMem());
        String toast = mContext.getString(
                R.string.tasklist_memory_usage,
                availMem,
                totalMem);
        textView.setText(toast);
    }

    private void refreshMemoryUsageBar(ProgressBar progressBar) {
        if (progressBar != null) {
            /*
             * 1024 bytes      == 1 kilobyte
             * 1024 kilobytes  == 1 megabyte
             * 1024 * 1024     == 1048576
             */
            int max = (int)(readTotalMem() / 1048576L);
            int currentMem = (int)(readAvailMem() / 1048576L);
            progressBar.setMax(max);
            progressBar.setProgress(currentMem);
        }
    }

    private long extractMemValue(byte[] buffer, int index) {
        while (index < buffer.length && buffer[index] != '\n') {
            if (buffer[index] >= '0' && buffer[index] <= '9') {
                int start = index;
                index++;
                while (index < buffer.length && buffer[index] >= '0'
                    && buffer[index] <= '9') {
                    index++;
                }
                String str = new String(buffer, 0, start, index-start);
                return ((long)Integer.parseInt(str)) * 1024;
            }
            index++;
        }
        return 0;
    }

    private boolean matchText(byte[] buffer, int index, String text) {
        int N = text.length();
        if ((index+N) >= buffer.length) {
            return false;
        }
        for (int i=0; i<N; i++) {
            if (buffer[index+i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private long readAvailMem() {
        MemoryInfo mi = new MemoryInfo();
        mActivityManager.getMemoryInfo(mi);
        long availableMem = mi.availMem;
        return availableMem;
    }

    private long readTotalMem() {
        MemoryInfo mi = new MemoryInfo();
        mActivityManager.getMemoryInfo(mi);
        long totalMem = mi.totalMem;
        return totalMem;
    }

    private void killChildByName(String packageName) {
        synchronized (sLock) {
            if (packageName == null) {
                Log.e(TAG,"killChildByName got packageName null");
                return;
            }

            final DetailProcess detailProcess = getDetailProcess(packageName);
            if (detailProcess.isHome()) {
                showTips(R.string.message_keep_one_launcher);
                return;
            }

            mActivityManager.forceStopPackage(packageName);
            View itemView = childs.get(packageName);
            if (itemView != null) {
                mTaskManagerList.removeView(itemView);
            }
        }
    }

    private void killAllChild() {
        synchronized (sLock) {
            if (showTaskList != null) {
                for (final DetailProcess detailProcess : showTaskList) {
                    if (!detailProcess.isHome()) {
                        final String packageName = detailProcess.getPackageName();
                        killChildByName(packageName);
                    }
                }
            }
        }
    }

    public class DetailProcess implements Comparable {
        private ActivityInfo activityInfo = null;
        private String pkgName = null;
        private ActivityManager.RunningTaskInfo taskinfo = null;
        private String title = null;
        private Drawable icon;
        private PackageManager pm;
        private Intent intent = null;
        private boolean isHome;

        public DetailProcess(Context ctx, ActivityManager.RunningTaskInfo info) {
            taskinfo = info;
            pm = ctx.getPackageManager();
            pkgName = info.baseActivity.getPackageName();
        }

        public void setHome(boolean ishome) {
            isHome = ishome;
        }

        public boolean isHome() {
            return isHome;
        }

        public String getPackageName() {
            return pkgName != null ? pkgName:null;
        }

        public ComponentName getBaseActivity() {
            if (taskinfo != null) {
                return taskinfo.baseActivity;
            }
            return null;
        }

        public ComponentName getTopActivity() {
            if (taskinfo != null) {
                return taskinfo.topActivity;
            }
            return null;
        }

        public Intent getIntent() {
            if (intent != null) {
                return intent;
            }
            intent = new Intent();
            intent.setComponent(getBaseActivity());
            intent = intent.cloneFilter();
            intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
            return intent;
        }

        public String getTitle() {
            if (title == null) {
                if (activityInfo == null) {
                    try {
                        activityInfo = pm.getActivityInfo(getBaseActivity(), 0);
                    } catch (NameNotFoundException e) {
                        Log.e(TAG,"name not found when get getTitle");
                    }
                }

                if (activityInfo != null) {
                    title = activityInfo.loadLabel(pm).toString();
                }
            }
            return title != null ? title:"";
        }

        public Drawable getIcon() {
            if (icon == null) {
                if (getBaseActivity() != null) {
                    try {
                        icon = pm.getActivityIcon(getBaseActivity());
                    } catch (NameNotFoundException e) {
                        Log.e(TAG,"name not found when get Icon");
                    }
                }
            }
            return icon;
        }

        public boolean filter() {
            if (pkgName != null) {
                return pkgName.equals("com.android.phone")
                        || pkgName.equals("com.android.dialer")
                        || pkgName.equals("com.quicinc.fmradio");
            }
            return true;
        }

        public int compareTo(Object another) {
            if (another instanceof DetailProcess && another != null) {
                return this.getTitle().compareTo(((DetailProcess) another).getTitle());
            }
            return -1;
        }

        public String dump() {
            StringBuilder sb = new StringBuilder();
            return sb.toString();
        }
    }

    public void setActivityStarter(ActivityStarter activityStarter) {
        mActivityStarter = activityStarter;
    }

    private void startTaskByName(String packageName) {
        boolean success = false;
        synchronized (sLock) {
            DetailProcess detailProcess = getDetailProcess(packageName);
            Intent intent = getBringtoFrontIntent(packageName);
            if (intent == null && detailProcess != null) {
                intent = detailProcess.getIntent();
            }
            if (intent != null) {
                try {
                    intent.setFlags((intent.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            | Intent.FLAG_ACTIVITY_NEW_TASK);
                    mActivityStarter.startActivity(intent, true);
                    success = true;
                } catch (Exception ee) {
                    Log.d(TAG,"start activity meets exception " + ee.getMessage());
                }
            }
            if (!success && detailProcess != null) {
                intent = new Intent();
                intent.setComponent(detailProcess.getBaseActivity());
                try {
                    intent.setFlags((intent.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            | Intent.FLAG_ACTIVITY_NEW_TASK);
                    mActivityStarter.startActivity(intent, true);
                } catch (Exception ee) {
                    Log.d(TAG,"start activity meets exception " + ee.getMessage());
                }
            }
        }
    }

    private DetailProcess getDetailProcess(String packageName) {
        if (showTaskList != null) {
            for (DetailProcess detailProcess : showTaskList) {
                if (detailProcess.getPackageName().equals(packageName)) {
                    return detailProcess;
                }
            }
        }
        return null;
    }

    /**
     * get bring to front intent for a running app
     * we get it from recent task to make sure it works
     */
    public Intent getBringtoFrontIntent(String packagename) {
        List<ActivityManager.RecentTaskInfo> recentTasks;
        recentTasks = mActivityManager.getRecentTasks(MAX_TASK_NUM,
                ActivityManager.RECENT_IGNORE_UNAVAILABLE);
        int numTasks = recentTasks.size();
        for (int i = 0; i < numTasks ; ++i) {
            final ActivityManager.RecentTaskInfo info = recentTasks.get(i);
            Intent intent = new Intent(info.baseIntent);
            if (info.origActivity != null) {
                intent.setComponent(info.origActivity);
            }
            intent.setFlags((intent.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            final ResolveInfo resolveInfo = mPackageManager.resolveActivity(intent, 0);
            if (resolveInfo != null && packagename.equals(resolveInfo.activityInfo.packageName)) {
                return intent;
            } else {
                continue;
            }
        }
        return null;
    }

    public boolean appNeedHide(String psPackageName) {
        if ("com.android.stk".equals(psPackageName)
                || "com.android.settings".equals(psPackageName)
                || "com.android.bluetooth".equals(psPackageName)
                || "com.android.systemui".equals(psPackageName)) {
            return true;
        } else {
            for (DetailProcess detailProcess : showTaskList) {
                String packageName = detailProcess.getPackageName();
                if (packageName.equals(psPackageName)) {
                    return true;
                }
            }
            return false;
        }
    }

    private void getTaskFontStyle() {
        final int mTaskFontStyle = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.TASK_MANAGER_FONT_STYLE, FontHelper.FONT_NORMAL);

        getFontStyle(mTaskFontStyle);
    }

    public void getFontStyle(int font) {
        if (taskManagerTitle == null || memoryUsageText == null || taskNameTextView == null || killAllButton == null) {
            return;
        }
        switch (font) {
            case FontHelper.FONT_NORMAL:
            default:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                memoryUsageText.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                taskNameTextView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                killAllButton.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FontHelper.FONT_ITALIC:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                memoryUsageText.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                taskNameTextView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                killAllButton.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FontHelper.FONT_BOLD:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                memoryUsageText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                taskNameTextView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                killAllButton.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FontHelper.FONT_BOLD_ITALIC:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                memoryUsageText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                taskNameTextView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                killAllButton.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FontHelper.FONT_LIGHT:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                killAllButton.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FontHelper.FONT_LIGHT_ITALIC:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                killAllButton.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FontHelper.FONT_THIN:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                killAllButton.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FontHelper.FONT_THIN_ITALIC:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                killAllButton.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FontHelper.FONT_CONDENSED:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                killAllButton.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FontHelper.FONT_CONDENSED_ITALIC:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                killAllButton.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FontHelper.FONT_CONDENSED_LIGHT:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                killAllButton.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FontHelper.FONT_CONDENSED_LIGHT_ITALIC:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                killAllButton.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FontHelper.FONT_CONDENSED_BOLD:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                killAllButton.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FontHelper.FONT_CONDENSED_BOLD_ITALIC:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                killAllButton.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FontHelper.FONT_MEDIUM:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                killAllButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FontHelper.FONT_MEDIUM_ITALIC:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                killAllButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FontHelper.FONT_BLACK:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                killAllButton.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FontHelper.FONT_BLACK_ITALIC:
                taskManagerTitle.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                memoryUsageText.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                taskNameTextView.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                killAllButton.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FontHelper.FONT_DANCINGSCRIPT:
                taskManagerTitle.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                memoryUsageText.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                taskNameTextView.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                killAllButton.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case FontHelper.FONT_DANCINGSCRIPT_BOLD:
                taskManagerTitle.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                memoryUsageText.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                taskNameTextView.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                killAllButton.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case FontHelper.FONT_COMINGSOON:
                taskManagerTitle.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                memoryUsageText.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                taskNameTextView.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                killAllButton.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case FontHelper.FONT_NOTOSERIF:
                taskManagerTitle.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                memoryUsageText.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                taskNameTextView.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                killAllButton.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case FontHelper.FONT_NOTOSERIF_ITALIC:
                taskManagerTitle.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                memoryUsageText.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                taskNameTextView.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                killAllButton.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case FontHelper.FONT_NOTOSERIF_BOLD:
                taskManagerTitle.setTypeface(Typeface.create("serif", Typeface.BOLD));
                memoryUsageText.setTypeface(Typeface.create("serif", Typeface.BOLD));
                taskNameTextView.setTypeface(Typeface.create("serif", Typeface.BOLD));
                killAllButton.setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case FontHelper.FONT_NOTOSERIF_BOLD_ITALIC:
                taskManagerTitle.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                memoryUsageText.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                taskNameTextView.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                killAllButton.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
        }
    }

    /**
     * show tips before quick settings panel.
     */
    private void showTips(int resid) {
        if (mTipsShowing) {
            Log.w(TAG,"The floating window is showing, stop showing another one.");
            return;
        }

        //Android toast is not able to be shown on top of notifications, so
        //implement a floating window to replace it.
        LayoutInflater inflate = (LayoutInflater)
                mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View floatView = inflate.inflate(
                com.android.internal.R.layout.transient_notification, null);
        final TextView textView = (TextView) floatView
               .findViewById(com.android.internal.R.id.message);

        final ColorStateList toastTextColor = RandomColorHelper.getToastTextColorList(mContext);
        textView.setText(mContext.getString(resid));
        textView.setTextColor(toastTextColor);
        final WindowManager windowManager =
                (WindowManager) mContext.getSystemService(mContext.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL;
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.y = windowManager.getDefaultDisplay().getHeight()/3;
        params.windowAnimations = com.android.internal.R.style.Animation_Toast;
        windowManager.addView(floatView, params);
        mTipsShowing = true;

        //close tips in two seconds
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                windowManager.removeView(floatView);
                mTipsShowing = false;
            }
        }, FLOAT_VIEW_DISPLAY_TIME);
     }

    private class TaskManagerHandler extends Handler {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_TASK:
                    String startTaskName = msg.getData().getString("packagename");
                    startTaskByName(startTaskName);
                    break;
                case MSG_KILL_ONE:
                    String packageName = msg.getData().getString("packagename");
                    killChildByName(packageName);
                    refreshMemoryUsagePanel();
                    break;
                case MSG_KILL_ALL:
                    killAllChild();
                    refreshMemoryUsagePanel();
                    break;
                default:
                    break;
            }
        }
    }
}
