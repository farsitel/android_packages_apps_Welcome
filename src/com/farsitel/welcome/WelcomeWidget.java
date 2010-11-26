/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.farsitel.welcome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.format.Time;
import android.text.Html;
import android.text.Spannable;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.widget.RemoteViews;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Random;

/** Mister Widget appears on your home screen to provide helpful tips. */
public class WelcomeWidget extends AppWidgetProvider {
    public static final String ACTION_NEXT_TIP = "com.android.misterwidget.NEXT_TIP";

    public static final String EXTRA_TIMES = "times";

    public static final String PREFS_NAME = "Welcome";
    public static final String PREFS_TIP_NUMBER = "widget_tip";

    private static Random sRNG = new Random();

    private static final Pattern sNewlineRegex = Pattern.compile(" *\\n *");
    private static final Pattern sDrawableRegex = Pattern.compile(" *@(drawable/[a-z0-9_]+) *");

    // initial appearance: eyes closed, no bubble
    private int mIconRes = R.drawable.droidman_down_open;
    private int mMessage = 0;

    private AppWidgetManager mWidgetManager = null;
    private int[] mWidgetIds;
    private Context mContext;

    private CharSequence[] mTips;

    private void setup(Context context) {
        mContext = context;
        mWidgetManager = AppWidgetManager.getInstance(context);
        mWidgetIds = mWidgetManager.getAppWidgetIds(new ComponentName(context, WelcomeWidget.class));

        SharedPreferences pref = context.getSharedPreferences(PREFS_NAME, 0);
        mMessage = pref.getInt(PREFS_TIP_NUMBER, 0);

        mTips = context.getResources().getTextArray(R.array.tips);

        if (mTips != null) {
            if (mMessage >= mTips.length) mMessage = 0;
        } else {
            mMessage = -1;
        }

    }

    public void goodmorning() {
        mMessage = -1;
        try {
            setIcon(R.drawable.droidman_down_closed);
            Thread.sleep(500);
            setIcon(R.drawable.droidman_down_open);
            Thread.sleep(200);
            setIcon(R.drawable.droidman_down_closed);
            Thread.sleep(100);
            setIcon(R.drawable.droidman_down_open);
            Thread.sleep(600);
        } catch (InterruptedException ex) {
        }
        mMessage = 0;
        mIconRes = R.drawable.droidman_down_open;
        refresh();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        setup(context);

        if (intent.getAction().equals(ACTION_NEXT_TIP)) {
            mMessage = getNextMessageIndex();
            SharedPreferences.Editor pref = context.getSharedPreferences(PREFS_NAME, 0).edit();
            pref.putInt(PREFS_TIP_NUMBER, mMessage);
            pref.commit();
            refresh();
//        } else if (intent.getAction().equals(AppWidgetManager.ACTION_APPWIDGET_ENABLED)) {
//            goodmorning();
        } else {
//            mIconRes = R.drawable.droidman_down_open;
            refresh();
        }
    }

    private void refresh() {
        RemoteViews rv = buildUpdate(mContext);
        for (int i : mWidgetIds) {
            mWidgetManager.updateAppWidget(i, rv);
        }
        
        AnimationSet farsiTelLogoAnimation = new AnimationSet(true);
        RotateAnimation rotate = new RotateAnimation(0, 360, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        rotate.setFillAfter(true);
        rotate.setDuration(1000);
        farsiTelLogoAnimation.addAnimation(rotate);
    }

    private void setIcon(int resId) {
        mIconRes = resId;
        refresh();
    }

    private int getNextMessageIndex() {
        return (mMessage + 1) % mTips.length;
    }

    private void blink(int blinks) {
        // don't blink if no bubble showing or if goodmorning() is happening
        if (mMessage < 0) return;

        setIcon(R.drawable.droidman_down_closed);
        try {
            Thread.sleep(100);
            while (0<--blinks) {
                setIcon(R.drawable.droidman_down_open);
                Thread.sleep(200);
                setIcon(R.drawable.droidman_down_closed);
                Thread.sleep(100);
            }
        } catch (InterruptedException ex) { }
        setIcon(R.drawable.droidman_down_open);
    }

    public RemoteViews buildUpdate(Context context) {
        RemoteViews updateViews = new RemoteViews(
            context.getPackageName(), R.layout.widget);

        // Action for tap on bubble
        Intent bcast = new Intent(context, WelcomeWidget.class);
        bcast.setAction(ACTION_NEXT_TIP);
        PendingIntent pending = PendingIntent.getBroadcast(
            context, 0, bcast, PendingIntent.FLAG_UPDATE_CURRENT);
        updateViews.setOnClickPendingIntent(R.id.widget, pending);
        
        // Tip bubble text
        if (mMessage >= 0) {
            String[] parts = sNewlineRegex.split(mTips[mMessage], 2);
            String title = parts[0];
            String text = parts.length > 1 ? parts[1] : "";

            // Look for a callout graphic referenced in the text
            Matcher m = sDrawableRegex.matcher(text);
            if (m.find()) {
                String imageName = m.group(1);
                int resId = context.getResources().getIdentifier(

                    imageName, null, context.getPackageName());
                updateViews.setImageViewResource(R.id.tip_callout, resId);
                updateViews.setViewVisibility(R.id.tip_callout, View.VISIBLE);
                text = m.replaceFirst("");
            } else {
                updateViews.setImageViewResource(R.id.tip_callout, 0);
                updateViews.setViewVisibility(R.id.tip_callout, View.GONE);
            }

            updateViews.setTextViewText(R.id.tip_message,
                Html.fromHtml(text));
            updateViews.setTextViewText(R.id.tip_header,
                title);
            updateViews.setTextViewText(R.id.tip_footer,
                context.getResources().getString(
                    R.string.pager_footer,
                    (1+mMessage), mTips.length));
            updateViews.setViewVisibility(R.id.tip_bubble, View.VISIBLE);
        } else {
            updateViews.setViewVisibility(R.id.tip_bubble, View.INVISIBLE);
        }

//        updateViews.setImageViewResource(R.id.bugdroid, mIconRes);

        return updateViews;
    }
}
