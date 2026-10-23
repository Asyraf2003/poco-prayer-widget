package com.asyraf.prayerwidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.TimeZone;

public class PrayerWidget extends AppWidgetProvider {
    static final String CYCLE = "com.asyraf.prayerwidget.CYCLE";
    static final String ALARM = "com.asyraf.prayerwidget.ALARM";
    static final String DAILY = "com.asyraf.prayerwidget.DAILY";
    private static final int[] NAME_IDS = {R.id.name0, R.id.name1, R.id.name2, R.id.name3, R.id.name4};
    private static final int[] TIME_IDS = {R.id.time0, R.id.time1, R.id.time2, R.id.time3, R.id.time4};

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) render(context, manager, id);
        PrayerWorker.schedule(context);
        scheduleAlarms(context);
    }

    @Override
    public void onEnabled(Context context) {
        PrayerWorker.schedule(context);
        scheduleAlarms(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (CYCLE.equals(action)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (id >= 0) {
                int current = PrayerData.prefs(context).getInt("cycle_" + id, -1);
                boolean imminent = PrayerData.imminent(context, System.currentTimeMillis()) >= 0;
                PrayerData.prefs(context).edit().putInt("cycle_" + id, PrayerData.nextCycle(current, imminent)).apply();
                render(context, AppWidgetManager.getInstance(context), id);
            }
        } else if (ALARM.equals(action)) {
            resetCycles(context);
            renderAll(context);
            scheduleAlarms(context);
        } else if (DAILY.equals(action) ||
                Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_TIME_CHANGED.equals(action) ||
                Intent.ACTION_TIMEZONE_CHANGED.equals(action) ||
                Intent.ACTION_DATE_CHANGED.equals(action)) {
            resetCycles(context);
            renderAll(context);
            PrayerWorker.enqueue(context);
            scheduleAlarms(context);
        }
        super.onReceive(context, intent);
    }

    static void renderAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PrayerWidget.class));
        for (int id : ids) render(context, manager, id);
    }

    private static void resetCycles(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : manager.getAppWidgetIds(new ComponentName(context, PrayerWidget.class))) {
            PrayerData.prefs(context).edit().remove("cycle_" + id).apply();
        }
    }

    static void render(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.prayer_widget);
        long now = System.currentTimeMillis();
        boolean current = PrayerData.isCurrent(context);
        int impending = PrayerData.imminent(context, now);
        int cycle = PrayerData.prefs(context).getInt("cycle_" + widgetId, -1);
        int upcoming = PrayerData.upcoming(context, now);
        int base = impending >= 0 ? impending : (upcoming >= 0 ? upcoming : 0);
        int selected = -1;
        if (current && (cycle == 0 || (cycle < 0 && impending >= 0))) {
            selected = base;
        } else if (current && cycle == 1) {
            selected = (base + 4) % 5;
        } else if (current && cycle == 2) {
            selected = (base + 1) % 5;
        }
        boolean focus = selected >= 0;
        v.setViewVisibility(R.id.prayer_row, focus ? View.GONE : View.VISIBLE);
        v.setViewVisibility(R.id.focus_group, focus ? View.VISIBLE : View.GONE);
        if (focus) {
            v.setTextViewText(R.id.focus_name, PrayerData.NAMES[selected]);
            v.setTextViewText(R.id.focus_time, PrayerData.time(context, selected));
        } else {
            for (int i = 0; i < 5; i++) {
                v.setTextViewText(NAME_IDS[i], PrayerData.NAMES[i]);
                v.setTextViewText(TIME_IDS[i], PrayerData.time(context, i));
            }
        }
        Intent click;
        if (!current) {
            click = new Intent(context, MainActivity.class);
            click.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            click = new Intent(context, PrayerWidget.class).setAction(CYCLE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        }
        PendingIntent tap = !current
                ? PendingIntent.getActivity(context, widgetId, click, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.getBroadcast(context, widgetId, click, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, tap);
        manager.updateAppWidget(widgetId, v);
    }

    private static PendingIntent alarmIntent(Context c, String action, int code) {
        return PendingIntent.getBroadcast(c, code,
                new Intent(c, PrayerWidget.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleAlarms(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long now = System.currentTimeMillis();
        PendingIntent transition = alarmIntent(context, ALARM, 707);
        manager.cancel(transition);
        long next = PrayerData.nextTransition(context, now);
        if (next > now) {
            if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) {
                try {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, transition);
                } catch (SecurityException ignored) {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, transition);
                }
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, transition);
            }
        }
        Calendar midnight = Calendar.getInstance(TimeZone.getDefault());
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 5);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        if (midnight.getTimeInMillis() <= now) midnight.add(Calendar.DAY_OF_MONTH, 1);
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight.getTimeInMillis(),
                alarmIntent(context, DAILY, 708));
    }
}
