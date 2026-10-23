package rh.chromitext.browser;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/** Maintains an explicit, user-enabled foreground process while browser UI is not visible. */
public class BackgroundKeepAliveService extends Service {
    private static final String TAG = "ChromitextBackground";
    private static final int NOTIFICATION_ID = 47201;
    private static final int RESTART_REQUEST_CODE = 47203;
    private static final String CHANNEL_ID = "chromitext_background";
    private static final String PREFS_NAME = "chromitext_background_runtime";
    private static final String PREF_ENABLED = "enabled";
    static final String ACTION_RESTART_AFTER_TASK_REMOVED = "rh.chromitext.browser.ACTION_RESTART_BACKGROUND_SERVICE";
    private boolean foregroundStarted;

    public static void start(Context context) {
        Intent intent = new Intent(context, BackgroundKeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to start foreground service", error);
        }
    }

    public static void stop(Context context) {
        cancelScheduledRestart(context);
        try {
            context.stopService(new Intent(context, BackgroundKeepAliveService.class));
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to stop background service", error);
        }
    }

    /** Mirror the explicit user choice so the service can still read it if SQLite temporarily fails. */
    public static void saveEnabledMirror(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_ENABLED, enabled).commit();
    }

    /** SQLite remains authoritative; preferences are a fallback for transient database errors. */
    public static boolean isBackgroundEnabled(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean fallback = preferences.getBoolean(PREF_ENABLED, false);
        BrowserDatabase database = null;
        try {
            database = new BrowserDatabase(context);
            boolean enabled = database.getBoolean("background_enabled", fallback);
            preferences.edit().putBoolean(PREF_ENABLED, enabled).commit();
            return enabled;
        } catch (Exception error) {
            Log.w(TAG, "Could not read service preference from SQLite; using saved fallback", error);
            return fallback;
        } finally {
            if (database != null) database.close();
        }
    }

    static void cancelScheduledRestart(Context context) {
        try {
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pending = restartPendingIntent(context);
            if (alarm != null) alarm.cancel(pending);
            pending.cancel();
        } catch (Exception error) {
            Log.w(TAG, "Could not cancel task-removal restart", error);
        }
    }

    private static void scheduleRestart(Context context) {
        if (!isBackgroundEnabled(context)) return;
        try {
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarm == null) return;
            PendingIntent pending = restartPendingIntent(context);
            long triggerAt = SystemClock.elapsedRealtime() + 1500L;
            if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending);
            else alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending);
            Log.i(TAG, "Scheduled a one-shot service recovery attempt");
        } catch (Exception error) {
            Log.w(TAG, "Could not schedule task-removal recovery", error);
        }
    }

    private static PendingIntent restartPendingIntent(Context context) {
        Intent intent = new Intent(context, BackgroundBootReceiver.class);
        intent.setAction(ACTION_RESTART_AFTER_TASK_REMOVED);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, RESTART_REQUEST_CODE, intent, flags);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        // Promote immediately, following the foreground-first lifecycle used by the reference service.
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
            foregroundStarted = true;
        } catch (Exception firstError) {
            Log.e(TAG, "First startForeground attempt failed", firstError);
            try {
                startForeground(NOTIFICATION_ID, buildNotification());
                foregroundStarted = true;
            } catch (Exception secondError) {
                Log.e(TAG, "Foreground promotion failed on retry", secondError);
            }
        }
        Log.i(TAG, "Foreground service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foregroundStarted) {
            Log.e(TAG, "Foreground promotion failed; refusing to keep a background-only service alive");
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (!isBackgroundEnabled(this)) {
            cancelScheduledRestart(this);
            stopForeground(true);
            stopSelf(startId);
            Log.i(TAG, "Service stopped because the saved user setting is disabled");
            return START_NOT_STICKY;
        }
        Log.i(TAG, "Background service active; START_STICKY enabled");
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // stopWithTask=false lets this callback run without automatically stopping this service.
        // Schedule one bounded recovery attempt for vendors that kill the process with the task.
        scheduleRestart(this);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "Foreground service destroyed");
        // If Android/OEM explicitly destroys the service, make one recovery attempt.
        // A normal user opt-out stores enabled=false first, so this does not undo Stop.
        if (foregroundStarted && isBackgroundEnabled(this)) scheduleRestart(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Chromitext background", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Status layanan browser di latar belakang");
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent launch = new Intent(this, MainActivity.class);
        launch.setAction(Intent.ACTION_MAIN);
        launch.addCategory(Intent.CATEGORY_LAUNCHER);
        launch.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 47202, launch, pendingFlags);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) builder = new Notification.Builder(this, CHANNEL_ID);
        else builder = new Notification.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Chromitext berjalan di latar belakang")
            .setContentText("Ketuk untuk membuka kembali browser")
            .setContentIntent(pending)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true);
        if (Build.VERSION.SDK_INT >= 21) builder.setCategory(Notification.CATEGORY_SERVICE);
        return builder.build();
    }
}
