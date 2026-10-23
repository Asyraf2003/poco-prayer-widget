package com.asyraf.prayerwidget;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 44, 28, 24);
        root.setBackgroundColor(0xff171D2B);
        TextView title = text("Salat POCO", 27, 0xffE5C891);
        root.addView(title);
        TextView instructions = text("Widget 4×1 • lima waktu salat\nH-30 menit fokus otomatis. Ketuk untuk melihat sebelumnya, sesudahnya, lalu semua waktu.\n\nTambahkan melalui layar utama → Widget → Salat POCO.", 15, 0xffFFFFFF);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, -2);
        textParams.topMargin = 18;
        root.addView(instructions, textParams);
        status = text("", 14, 0xffC4D0DF);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = 20;
        root.addView(status, statusParams);
        Button location = button("Izinkan lokasi", () -> requestLocation());
        root.addView(location);
        root.addView(button("Perbarui jadwal sekarang", () -> {
            PrayerWorker.enqueue(this);
            status.setText("Pembaruan dijadwalkan. Buka aplikasi ini lagi untuk melihat status.");
        }));
        if (Build.VERSION.SDK_INT >= 31) {
            root.addView(button("Aktifkan alarm tepat waktu (opsional)", () -> {
                AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
                if (alarms != null && !alarms.canScheduleExactAlarms()) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName()));
                    try { startActivity(intent); }
                    catch (Exception e) { status.setText("Buka Setelan → Aplikasi → Akses khusus → Alarm & pengingat."); }
                } else {
                    status.setText("Alarm tepat waktu sudah diizinkan.");
                }
            }));
        }
        setContentView(root);
        PrayerWorker.schedule(this);
        if (!hasLocation()) requestLocation();
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocation() {
        if (!hasLocation()) requestPermissions(
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 44);
        else PrayerWorker.enqueue(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == 44 && hasLocation()) PrayerWorker.enqueue(this);
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        PrayerWidget.scheduleAlarms(this);
    }

    private void updateStatus() {
        if (status != null) status.setText(PrayerData.status(this));
    }

    private TextView text(String content, int size, int color) {
        TextView text = new TextView(this);
        text.setText(content);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setGravity(Gravity.START);
        return text;
    }

    private Button button(String label, Runnable run) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> run.run());
        return button;
    }
}
