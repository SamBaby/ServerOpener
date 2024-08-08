package com.example.serveropener;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class MyBackgroundService extends Service {

    private static final String TAG = "MyBackgroundService";

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started after boot");
        // Your background task code here
        executeCommand("mysqld", new String[]{});
        executeCommand("rm", new String[]{"/data/data/com.termux/files/usr/var/run/apache2/httpd.pid"});
        executeCommand("apachectl", new String[]{"start"});
        executeCommand("python", new String[]{"/storage/sdcard/carema_server_led_test.py"});
        Toast.makeText(this, "NotifyingDailyService", Toast.LENGTH_LONG).show();
        // If we get killed, after returning from here, restart
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }


    private void executeCommand(String command, String[] ags) {
        String LOG_TAG = "command";
        Intent intent = new Intent();
        intent.setClassName(TermuxConstants.TERMUX_PACKAGE_NAME, TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE_NAME);
        intent.setAction(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND);
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/" + command);
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS, ags);
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_WORKDIR, "/data/data/com.termux/files/home");
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_BACKGROUND, true);
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_SESSION_ACTION, "0");
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL, "top command");
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_COMMAND_DESCRIPTION, "Runs the top command to show processes using the most resources.");

// Create the intent for the IntentService class that should be sent the result by TermuxService
        Intent pluginResultsServiceIntent = new Intent(this, PluginResultsService.class);

// Generate a unique execution id for this execution command
        int executionId = PluginResultsService.getNextExecutionId();

// Optional put an extra that uniquely identifies the command internally for your app.
// This can be an Intent extra as well with more extras instead of just an int.
        pluginResultsServiceIntent.putExtra(PluginResultsService.EXTRA_EXECUTION_ID, executionId);

// Create the PendingIntent that will be used by TermuxService to send result of
// commands back to the IntentService
// Note that the requestCode (currently executionId) must be unique for each pending
// intent, even if extras are different, otherwise only the result of only the first
// execution will be returned since pending intent will be cancelled by android
// after the first result has been sent back via the pending intent and termux
// will not be able to send more.
        PendingIntent pendingIntent = PendingIntent.getService(this, executionId,
                pluginResultsServiceIntent,
                PendingIntent.FLAG_ONE_SHOT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0));
        intent.putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_PENDING_INTENT, pendingIntent);

        try {
            // Send command intent for execution
            Log.d(LOG_TAG, "Sending execution command with id " + executionId);
            startService(intent);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to start execution command with id " + executionId + ": " + e.getMessage());
        }
    }
}