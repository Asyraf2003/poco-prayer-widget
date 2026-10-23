package com.asyraf.prayerwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class PrayerData {
    static final String[] NAMES = {"Subuh", "Zuhur", "Asar", "Magrib", "Isya"};
    static final String[] KEYS = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final String STORE = "prayer_schedule";

    private PrayerData() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    static String date(TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(zone);
        return format.format(new Date());
    }

    static TimeZone zone(Context context) {
        String id = prefs(context).getString("timezone", TimeZone.getDefault().getID());
        return TimeZone.getTimeZone(id);
    }

    static boolean isCurrent(Context context) {
        return date(zone(context)).equals(prefs(context).getString("day", ""));
    }

    static String time(Context context, int index) {
        if (!isCurrent(context)) return "--:--";
        return prefs(context).getString("prayer_" + index, "--:--");
    }

    static int minutes(String time) {
        if (time == null || !time.matches("[0-2][0-9]:[0-5][0-9]")) return -1;
        int hour = Integer.parseInt(time.substring(0, 2));
        if (hour > 23) return -1;
        return hour * 60 + Integer.parseInt(time.substring(3, 5));
    }

    static long instant(Context context, int index) {
        int minute = minutes(time(context, index));
        if (minute < 0) return -1;
        Calendar cal = Calendar.getInstance(zone(context));
        cal.set(Calendar.HOUR_OF_DAY, minute / 60);
        cal.set(Calendar.MINUTE, minute % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    static int upcoming(Context context, long now) {
        for (int i = 0; i < NAMES.length; i++) {
            long at = instant(context, i);
            if (at > now) return i;
        }
        return -1;
    }

    static int imminent(Context context, long now) {
        int index = upcoming(context, now);
        if (index < 0) return -1;
        long at = instant(context, index);
        return at - now <= 30 * 60_000L && at > now ? index : -1;
    }

    static long nextTransition(Context context, long now) {
        if (!isCurrent(context)) return -1;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < NAMES.length; i++) {
            long time = instant(context, i);
            if (time < 0) continue;
            for (long when : new long[]{time - 30 * 60_000L + 2000L, time + 2000L}) {
                if (when > now + 500L && when < best) best = when;
            }
        }
        return best == Long.MAX_VALUE ? -1 : best;
    }

    static int nextCycle(int cycle, boolean imminent) {
        if (cycle < 0) return imminent ? 1 : 0;
        return cycle >= 3 ? 0 : cycle + 1;
    }

    static String status(Context context) {
        SharedPreferences p = prefs(context);
        if (!isCurrent(context)) return "Jadwal hari ini belum tersedia. Aktifkan lokasi dan internet.";
        String kind = p.getBoolean("precise", false) ? "presisi" : "perkiraan";
        return "Jadwal " + date(zone(context)) + " • KEMENAG (AlAdhan) • lokasi " + kind;
    }
}
