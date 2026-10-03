package com.example.rebootmenu;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A power operation shown in the app and the floating controls. */
final class PowerAction {
    final String id;
    final String title;
    final String subtitle;
    final String icon;
    final String command;
    final boolean requiresRoot;

    private PowerAction(String id, String title, String subtitle, String icon,
                        String command, boolean requiresRoot) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
        this.command = command;
        this.requiresRoot = requiresRoot;
    }

    static final PowerAction REBOOT = new PowerAction(
            "reboot", "Reboot", "Restart device", "↻", "reboot", true);
    static final PowerAction SOFT_REBOOT = new PowerAction(
            "soft-reboot", "Soft reboot", "Restart Android UI", "⟳", "killall system_server", true);
    static final PowerAction RECOVERY = new PowerAction(
            "recovery", "Recovery", "Reboot to recovery", "⌘", "reboot recovery", true);
    static final PowerAction BOOTLOADER = new PowerAction(
            "bootloader", "Bootloader", "Reboot to bootloader", "⇧", "reboot bootloader", true);
    static final PowerAction FASTBOOT = new PowerAction(
            "fastboot", "Fastbootd", "Reboot to fastbootd", "⚡", "reboot fastboot", true);
    static final PowerAction DOWNLOAD = new PowerAction(
            "download", "Download mode", "Samsung devices", "↓", "reboot download", true);
    static final PowerAction SAFE_MODE = new PowerAction(
            "safe-mode", "Safe mode", "Restart in safe mode", "◉",
            "setprop persist.sys.safemode 1 && reboot", true);
    static final PowerAction POWER_OFF = new PowerAction(
            "power-off", "Power off", "Shut down device", "⏻", "reboot -p", true);
    static final PowerAction LOCK_SCREEN = new PowerAction(
            "lock", "Lock screen", "Lock this device", "▣", null, false);

    static final List<PowerAction> ALL = Collections.unmodifiableList(Arrays.asList(
            REBOOT, RECOVERY, BOOTLOADER, FASTBOOT, SOFT_REBOOT,
            DOWNLOAD, SAFE_MODE, POWER_OFF, LOCK_SCREEN));

    static final List<PowerAction> FLOATING = Collections.unmodifiableList(Arrays.asList(
            REBOOT, RECOVERY, BOOTLOADER, FASTBOOT, POWER_OFF, LOCK_SCREEN));

    String confirmationMessage() {
        if (this == LOCK_SCREEN) {
            return "Lock the screen now? If device admin is not enabled, Android will ask you to enable it first.";
        }
        if (this == POWER_OFF) {
            return "This shuts the device down immediately. You will need to press the power button to turn it back on.";
        }
        if (this == SAFE_MODE) {
            return "This sets the safe-mode boot property and restarts the device. Availability depends on your ROM.";
        }
        if (this == DOWNLOAD) {
            return "This command is intended for Samsung devices. Other devices may ignore it or respond differently.";
        }
        if (this == SOFT_REBOOT) {
            return "This restarts Android's system server and user interface. The screen may go black briefly.";
        }
        return "The device will leave the current session and enter " + subtitle.toLowerCase() + ".";
    }
}
