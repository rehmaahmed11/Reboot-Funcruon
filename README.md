# Reboot Menu

A polished Android control center for reboot and device power actions, with an optional draggable floating shortcut for quick access from any app.

## Highlights

- A redesigned, scrollable control center with clear action cards and confirm-before-run prompts.
- Floating overlay bubble that snaps to either side of the screen and opens a compact quick-action panel.
- Quick actions for reboot, recovery, bootloader, fastbootd, power off, and screen lock; the full app includes soft reboot, Samsung download mode, and safe mode.
- Root commands run through `su`; screen lock uses Android Device Admin and does not require root.
- The widget runs as a foreground service; when notifications are allowed, its ongoing notification includes a turn-off action.

## Build

Open this repository's root folder in Android Studio and sync the Gradle project. It uses Android Gradle Plugin 8.5, Java 17, and compile/target SDK 34.

## Permissions and safety

- Android's **Display over other apps** permission is needed only when enabling the floating widget.
- Root is required for reboot, recovery, bootloader, fastbootd, download mode, safe mode, and power-off commands. A compatible root manager must approve `su` access.
- The **Lock screen** action asks for Device Admin only if needed. Android provides its own confirmation screen for enabling that capability.
- Power and reboot operations are immediate and device/ROM-specific. Review the in-app confirmation before proceeding; Samsung Download mode and Safe mode may not be supported on every device.
