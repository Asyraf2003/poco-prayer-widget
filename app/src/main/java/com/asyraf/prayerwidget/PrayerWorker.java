package com.asyraf.prayerwidget;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

public class PrayerWorker extends Worker {
    public PrayerWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static void enqueue(Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                "prayer-refresh", ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(PrayerWorker.class).build());
    }

    static void schedule(Context context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "prayer-daily", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(PrayerWorker.class, 24, TimeUnit.HOURS).build());
        enqueue(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        try {
            SharedPreferences prefs = PrayerData.prefs(context);
            String localDay = PrayerData.date(PrayerData.zone(context));
            boolean hasLocationPermission = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (!hasLocationPermission) {
                PrayerWidget.renderAll(context);
                return Result.success();
            }

            double latitude;
            double longitude;
            boolean precise;
            boolean saved = prefs.getBoolean("has_coords", false);
            String checked = prefs.getString("location_day", "");
            if (checked.equals(localDay) && saved) {
                latitude = Double.longBitsToDouble(prefs.getLong("latitude_bits", 0));
                longitude = Double.longBitsToDouble(prefs.getLong("longitude_bits", 0));
                precise = prefs.getBoolean("precise", false);
            } else {
                Location location = findLocation(context);
                if (location != null) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    precise = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            && location.hasAccuracy() && location.getAccuracy() < 1000f;
                    prefs.edit()
                            .putLong("latitude_bits", Double.doubleToLongBits(latitude))
                            .putLong("longitude_bits", Double.doubleToLongBits(longitude))
                            .putString("location_day", localDay)
                            .putBoolean("has_coords", true).putBoolean("precise", precise).apply();
                } else if (saved) {
                    latitude = Double.longBitsToDouble(prefs.getLong("latitude_bits", 0));
                    longitude = Double.longBitsToDouble(prefs.getLong("longitude_bits", 0));
                    precise = prefs.getBoolean("precise", false);
                } else {
                    PrayerWidget.renderAll(context);
                    return Result.retry();
                }
            }
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude) ||
                    Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return Result.failure();

            TimeZone zone = PrayerData.zone(context);
            SimpleDateFormat apiFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
            apiFormat.setTimeZone(zone);
            String requestDate = apiFormat.format(new Date());
            String path = String.format(Locale.US,
                    "https://api.aladhan.com/v1/timings/%s?latitude=%.6f&longitude=%.6f&method=20&school=0",
                    requestDate, latitude, longitude);
            HttpURLConnection connection = (HttpURLConnection) new URL(path).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(12_000);
            connection.setRequestProperty("Accept", "application/json");
            try {
                if (connection.getResponseCode() != 200) return Result.retry();
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                        if (body.length() > 250_000) return Result.failure();
                    }
                }
                JSONObject payload = new JSONObject(body.toString());
                if (payload.getInt("code") != 200) return Result.retry();
                JSONObject result = payload.getJSONObject("data");
                JSONObject timings = result.getJSONObject("timings");
                String[] times = new String[5];
                for (int i = 0; i < 5; i++) {
                    String original = timings.getString(PrayerData.KEYS[i]);
                    if (original.length() < 5) return Result.retry();
                    String normalized = original.substring(0, 5);
                    if (PrayerData.minutes(normalized) < 0) return Result.retry();
                    times[i] = normalized;
                }
                String timezone = result.getJSONObject("meta").getString("timezone");
                TimeZone responseZone = TimeZone.getTimeZone(timezone);
                String date = PrayerData.date(responseZone);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putString("day", date).putString("timezone", timezone)
                        .putBoolean("precise", precise);
                for (int i = 0; i < 5; i++) edit.putString("prayer_" + i, times[i]);
                edit.apply();
            } finally {
                connection.disconnect();
            }
            PrayerWidget.renderAll(context);
            PrayerWidget.scheduleAlarms(context);
            return Result.success();
        } catch (Exception e) {
            PrayerWidget.renderAll(context);
            return Result.retry();
        }
    }

    private static Location findLocation(Context context) {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) return null;
        Location best = null;
        try {
            for (String provider : manager.getProviders(true)) {
                Location location = manager.getLastKnownLocation(provider);
                if (location == null) continue;
                long age = SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos();
                if (age >= 0 && age <= TimeUnit.HOURS.toNanos(6)
                        && (best == null || location.getTime() > best.getTime())) best = location;
            }
        } catch (SecurityException ignored) {
            return null;
        }
        if (best != null) return best;
        for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}) {
            try {
                if (!manager.isProviderEnabled(provider)) continue;
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<Location> latest = new AtomicReference<>();
                manager.getCurrentLocation(provider, null, Runnable::run, location -> {
                    latest.set(location);
                    latch.countDown();
                });
                latch.await(8, TimeUnit.SECONDS);
                if (latest.get() != null) return latest.get();
            } catch (SecurityException | IllegalArgumentException ignored) {
                // Provider not available or permission revoked.
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }
}
