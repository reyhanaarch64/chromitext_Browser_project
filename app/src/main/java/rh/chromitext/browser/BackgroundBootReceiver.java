package rh.chromitext.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/** Restarts the opt-in background service after boot, package replacement or task removal. */
public class BackgroundBootReceiver extends BroadcastReceiver {
    private static final String TAG = "ChromitextBackground";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        boolean taskRecovery = BackgroundKeepAliveService.ACTION_RESTART_AFTER_TASK_REMOVED.equals(action);
        if (!taskRecovery && !Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;

        if (!BackgroundKeepAliveService.isBackgroundEnabled(context)) {
            BackgroundKeepAliveService.cancelScheduledRestart(context);
            return;
        }

        Intent service = new Intent(context, BackgroundKeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
            Log.i(TAG, "Requested service start after " + action);
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not restart foreground service after " + action, error);
            // Vendor auto-start/battery controls can still block recovery; the next app launch can retry.
        }
    }
}
