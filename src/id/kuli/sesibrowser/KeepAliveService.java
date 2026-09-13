package id.kuli.sesibrowser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Foreground service ringan yang dinyalakan saat aplikasi ke latar belakang: menjaga proses (dan
 * semua WebView/sesi) tidak dimatikan sistem walau pengguna lama berpindah aplikasi. Dimatikan lagi
 * saat aplikasi kembali ke depan.
 */
public class KeepAliveService extends Service {
    private static final String CH = "sesi_keepalive";
    private static final int ID = 7;

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Throwable ignored) {}
    }
    public static void stop(Context c) { try { c.stopService(new Intent(c, KeepAliveService.class)); } catch (Throwable ignored) {} }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CH, "Sesi tetap aktif", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Sesi Browser tetap aktif")
                .setContentText("Sesi & tab dijaga di latar belakang")
                .setContentIntent(pi).setOngoing(true).setShowWhen(false)
                .setPriority(Notification.PRIORITY_MIN).setVisibility(Notification.VISIBILITY_SECRET)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ID, n);
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onTaskRemoved(Intent root) { stopSelf(); }
}
