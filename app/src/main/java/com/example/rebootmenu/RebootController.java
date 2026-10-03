package com.example.rebootmenu;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Shared execution path for the app screen and floating widget. */
final class RebootController {
    private RebootController() { }

    static void runRootCommand(Context context, PowerAction action) {
        final Context appContext = context.getApplicationContext();
        Thread worker = new Thread(() -> {
            String message;
            try {
                Process process = new ProcessBuilder("su", "-c", action.command)
                        .redirectErrorStream(true)
                        .start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (output.length() < 240) {
                            output.append(line).append(' ');
                        }
                    }
                }
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    message = action.title + " command sent.";
                } else {
                    message = "Command failed (exit " + exitCode + "). Is root access available?";
                    if (output.length() > 0) {
                        message += " " + output.toString().trim();
                    }
                }
            } catch (Exception error) {
                message = "Root access unavailable: " + error.getMessage();
            }

            final String result = message;
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(appContext, result, Toast.LENGTH_LONG).show());
        }, "reboot-command");
        worker.setDaemon(true);
        worker.start();
    }

    /** Lock immediately when device admin is active; otherwise request it from Android. */
    static void lockScreen(Context context) {
        DevicePolicyManager policy = (DevicePolicyManager)
                context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, AdminReceiver.class);
        if (policy != null && policy.isAdminActive(admin)) {
            policy.lockNow();
            return;
        }

        Intent request = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        request.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
        request.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Reboot Menu uses device administrator access only to lock your screen. "
                        + "After enabling it, tap Lock screen again.");
        if (!(context instanceof Activity)) {
            request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(request);
    }
}
