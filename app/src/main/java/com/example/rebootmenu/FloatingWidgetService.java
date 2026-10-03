package com.example.rebootmenu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Always-on-top shortcut bubble with a compact set of safe, confirm-before-run controls. */
public class FloatingWidgetService extends Service {
    public static final String ACTION_START = "com.example.rebootmenu.action.START_FLOATING";
    public static final String ACTION_STOP = "com.example.rebootmenu.action.STOP_FLOATING";

    private static final String CHANNEL_ID = "floating_controls";
    private static final int NOTIFICATION_ID = 7301;
    private static final int COLOR_PURPLE = Color.rgb(104, 73, 237);
    private static final int COLOR_PANEL = Color.rgb(27, 24, 39);
    private static final int COLOR_PANEL_TILE = Color.rgb(43, 39, 58);
    private static final int COLOR_PANEL_MUTED = Color.rgb(176, 169, 197);

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private FrameLayout overlayRoot;
    private TextView bubble;

    private int screenWidth;
    private int screenHeight;
    private int bubbleSize;
    private int panelWidth;
    private int anchorX;
    private int anchorY;
    private boolean expanded;
    private boolean foregroundReady;
    private boolean panelOnRight;
    private boolean bubbleAtBottom;
    private boolean viewAdded;
    private boolean dragging;
    private float touchDownX;
    private float touchDownY;
    private int touchStartX;
    private int touchStartY;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startAsForegroundService();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        bubbleSize = dp(62);
        panelWidth = dp(286);
        updateScreenSize();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foregroundReady) {
            setEnabled(false);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            setEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!canDrawOverlays()) {
            setEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!viewAdded) attachBubble();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        setEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        super.onDestroy();
    }

    private void startAsForegroundService() {
        try {
            Notification notification = createNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            foregroundReady = true;
        } catch (Exception error) {
            Toast.makeText(this, "Floating controls could not start.", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Floating power controls", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps the Reboot Menu floating shortcut available.");
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification createNotification() {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 1, openIntent,
                pendingIntentFlags());
        Intent stopIntent = new Intent(this, FloatingWidgetService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? PendingIntent.getForegroundService(this, 2, stopIntent, pendingIntentFlags())
                : PendingIntent.getService(this, 2, stopIntent, pendingIntentFlags());

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_power_notification)
                .setContentTitle("Reboot Menu is ready")
                .setContentText("Tap the floating power button to open quick actions.")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setContentIntent(openPending)
                .addAction(0, "Turn off", stopPending)
                .setColor(COLOR_PURPLE);
        return builder.build();
    }

    private int pendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    private void attachBubble() {
        try {
            overlayRoot = new FrameLayout(this);
            overlayRoot.setClipChildren(false);
            overlayRoot.setClipToPadding(false);
            bubble = createBubble();
            overlayRoot.addView(bubble, new FrameLayout.LayoutParams(bubbleSize, bubbleSize));

            int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            windowParams = new WindowManager.LayoutParams(bubbleSize, bubbleSize, windowType,
                    flags, PixelFormat.TRANSLUCENT);
            windowParams.gravity = Gravity.TOP | Gravity.LEFT;
            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
            windowParams.x = clamp(prefs.getInt("floating_x", screenWidth - bubbleSize - dp(14)),
                    dp(8), Math.max(dp(8), screenWidth - bubbleSize - dp(8)));
            windowParams.y = clamp(prefs.getInt("floating_y", screenHeight / 2),
                    dp(36), Math.max(dp(36), screenHeight - bubbleSize - dp(48)));
            windowManager.addView(overlayRoot, windowParams);
            viewAdded = true;
            setEnabled(true);
        } catch (Exception error) {
            Toast.makeText(this, "Could not show the floating controls. Check overlay permission.",
                    Toast.LENGTH_LONG).show();
            setEnabled(false);
            stopSelf();
        }
    }

    private TextView createBubble() {
        TextView view = new TextView(this);
        view.setText("⏻");
        view.setTextSize(29);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        view.setContentDescription("Open floating power controls");
        view.setBackground(bubbleBackground());
        view.setElevation(dp(10));
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(target -> {
            if (expanded) collapse();
            else expand();
        });
        view.setOnTouchListener((target, event) -> handleBubbleTouch(target, event));
        return view;
    }

    private boolean handleBubbleTouch(View target, MotionEvent event) {
        if (expanded) {
            if (event.getAction() == MotionEvent.ACTION_UP) target.performClick();
            return true;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getRawX();
                touchDownY = event.getRawY();
                touchStartX = windowParams.x;
                touchStartY = windowParams.y;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - touchDownX;
                float dy = event.getRawY() - touchDownY;
                if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) dragging = true;
                if (dragging) {
                    windowParams.x = clamp(touchStartX + Math.round(dx), dp(8),
                            Math.max(dp(8), screenWidth - bubbleSize - dp(8)));
                    windowParams.y = clamp(touchStartY + Math.round(dy), dp(32),
                            Math.max(dp(32), screenHeight - bubbleSize - dp(48)));
                    updateWindow();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) snapAndSave();
                else target.performClick();
                dragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    private void snapAndSave() {
        updateScreenSize();
        boolean toRight = windowParams.x + bubbleSize / 2 >= screenWidth / 2;
        windowParams.x = toRight
                ? Math.max(dp(8), screenWidth - bubbleSize - dp(12)) : dp(12);
        windowParams.y = clamp(windowParams.y, dp(32),
                Math.max(dp(32), screenHeight - bubbleSize - dp(48)));
        getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE).edit()
                .putInt("floating_x", windowParams.x)
                .putInt("floating_y", windowParams.y)
                .apply();
        updateWindow();
    }

    private void expand() {
        if (!viewAdded || expanded) return;
        updateScreenSize();
        anchorX = windowParams.x;
        anchorY = windowParams.y;
        panelOnRight = anchorX + bubbleSize / 2 >= screenWidth / 2;
        bubbleAtBottom = anchorY + bubbleSize / 2 >= screenHeight / 2;
        expanded = true;
        showPanel(buildActionsPanel());
    }

    private void showConfirmation(PowerAction action) {
        if (!expanded) return;
        showPanel(buildConfirmationPanel(action));
    }

    private void showPanel(LinearLayout content) {
        if (!viewAdded || content == null) return;
        try {
            int maxPanelHeight = Math.max(dp(260), screenHeight - dp(90));
            content.measure(
                    View.MeasureSpec.makeMeasureSpec(panelWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maxPanelHeight, View.MeasureSpec.AT_MOST));
            int measuredPanelHeight = content.getMeasuredHeight();
            int gap = dp(11);
            int totalHeight = measuredPanelHeight + gap + bubbleSize;

            overlayRoot.removeAllViews();
            FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                    panelWidth, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
            FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(
                    bubbleSize, bubbleSize,
                    (bubbleAtBottom ? Gravity.BOTTOM : Gravity.TOP)
                            | (panelOnRight ? Gravity.RIGHT : Gravity.LEFT));
            if (!bubbleAtBottom) panelParams.topMargin = bubbleSize + gap;
            overlayRoot.addView(content, panelParams);
            overlayRoot.addView(bubble, bubbleParams);

            windowParams.width = panelWidth;
            windowParams.height = totalHeight;
            windowParams.x = panelOnRight
                    ? Math.max(dp(8), screenWidth - panelWidth - dp(10)) : dp(10);
            int targetY = bubbleAtBottom ? anchorY - measuredPanelHeight - gap : anchorY;
            windowParams.y = clamp(targetY, dp(28), Math.max(dp(28),
                    screenHeight - totalHeight - dp(38)));
            updateWindow();
            content.setAlpha(0f);
            content.setTranslationY(dp(bubbleAtBottom ? 8 : -8));
            content.animate().alpha(1f).translationY(0f).setDuration(170).start();
        } catch (Exception error) {
            Toast.makeText(this, "Floating menu could not be displayed.", Toast.LENGTH_SHORT).show();
            collapse();
        }
    }

    private LinearLayout buildActionsPanel() {
        LinearLayout panelView = panelBase();
        panelView.addView(panelHeader("Quick controls", "Available from any app", false));

        TextView section = panelText("QUICK ACTIONS", 9, COLOR_PANEL_MUTED, true);
        section.setLetterSpacing(0.12f);
        panelView.addView(section, topMargin(dp(13)));

        for (int i = 0; i < PowerAction.FLOATING.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            row.addView(createQuickAction(PowerAction.FLOATING.get(i)),
                    new LinearLayout.LayoutParams(0, dp(57), 1f));
            if (i + 1 < PowerAction.FLOATING.size()) {
                LinearLayout.LayoutParams next = new LinearLayout.LayoutParams(0, dp(57), 1f);
                next.leftMargin = dp(8);
                row.addView(createQuickAction(PowerAction.FLOATING.get(i + 1)), next);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(57));
            if (i > 0) rowParams.topMargin = dp(8);
            panelView.addView(row, rowParams);
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView openApp = panelFooterButton("OPEN APP", COLOR_PANEL_TILE, Color.WHITE);
        TextView turnOff = panelFooterButton("TURN OFF", 0xFF382B49, 0xFFE5C9D4);
        footer.addView(openApp, new LinearLayout.LayoutParams(0, dp(38), 1f));
        LinearLayout.LayoutParams offParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
        offParams.leftMargin = dp(8);
        footer.addView(turnOff, offParams);
        panelView.addView(footer, topMargin(dp(12)));

        openApp.setOnClickListener(view -> {
            Intent open = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(open);
            collapse();
        });
        turnOff.setOnClickListener(view -> {
            setEnabled(false);
            stopSelf();
        });
        return panelView;
    }

    private LinearLayout buildConfirmationPanel(PowerAction action) {
        LinearLayout panelView = panelBase();
        panelView.addView(panelHeader("Confirm action", action.title, true));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = panelText(action.icon, 25, accentFor(action), true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(withAlpha(accentFor(action), 0.18f), dp(17)));
        actionRow.addView(icon, new LinearLayout.LayoutParams(dp(49), dp(49)));
        LinearLayout actionCopy = new LinearLayout(this);
        actionCopy.setOrientation(LinearLayout.VERTICAL);
        actionCopy.setPadding(dp(12), 0, 0, 0);
        actionCopy.addView(panelText(action.title, 15, Color.WHITE, true));
        actionCopy.addView(panelText(action.subtitle, 10, COLOR_PANEL_MUTED, false), topMargin(dp(4)));
        actionRow.addView(actionCopy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        panelView.addView(actionRow, topMargin(dp(18)));

        TextView message = panelText(action.confirmationMessage(), 12, 0xFFD1CCDC, false);
        message.setLineSpacing(dp(4), 1f);
        message.setPadding(dp(13), dp(12), dp(13), dp(12));
        message.setBackground(round(COLOR_PANEL_TILE, dp(15)));
        panelView.addView(message, topMargin(dp(14)));

        TextView access = panelText(action.requiresRoot
                        ? "ROOT ACCESS REQUIRED  ·  MAY PROMPT FOR APPROVAL"
                        : "DEVICE ADMIN MAY BE REQUIRED",
                9, action.requiresRoot ? 0xFFE0B868 : 0xFF82D5BE, true);
        access.setLetterSpacing(0.04f);
        panelView.addView(access, topMargin(dp(13)));

        LinearLayout buttons = new LinearLayout(this);
        TextView cancel = panelFooterButton("CANCEL", COLOR_PANEL_TILE, Color.WHITE);
        TextView proceed = panelFooterButton(action == PowerAction.POWER_OFF ? "POWER OFF" : "CONTINUE",
                action == PowerAction.POWER_OFF ? Color.rgb(194, 71, 87) : COLOR_PURPLE, Color.WHITE);
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams proceedParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        proceedParams.leftMargin = dp(8);
        buttons.addView(proceed, proceedParams);
        panelView.addView(buttons, topMargin(dp(17)));
        cancel.setOnClickListener(view -> showPanel(buildActionsPanel()));
        proceed.setOnClickListener(view -> executeAction(action));
        return panelView;
    }

    private LinearLayout panelBase() {
        LinearLayout base = new LinearLayout(this);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setPadding(dp(15), dp(14), dp(15), dp(14));
        GradientDrawable background = round(COLOR_PANEL, dp(24));
        background.setStroke(dp(1), 0xFF4A435D);
        base.setBackground(background);
        base.setElevation(dp(16));
        return base;
    }

    private View panelHeader(String title, String subtitle, boolean showBack) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = panelText("⏻", 19, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(COLOR_PURPLE, dp(13)));
        header.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
        copy.addView(panelText(title, 14, Color.WHITE, true));
        copy.addView(panelText(subtitle, 9, COLOR_PANEL_MUTED, false), topMargin(dp(3)));
        header.addView(copy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = panelText(showBack ? "‹" : "×", 21, Color.WHITE, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(COLOR_PANEL_TILE, dp(13)));
        header.addView(close, new LinearLayout.LayoutParams(dp(35), dp(35)));
        close.setContentDescription(showBack ? "Back to quick actions" : "Close floating menu");
        close.setOnClickListener(view -> {
            if (showBack) {
                showPanel(buildActionsPanel());
            } else {
                collapse();
            }
        });
        return header;
    }

    private View createQuickAction(PowerAction action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(8), dp(6), dp(5), dp(6));
        tile.setBackground(ripple(round(COLOR_PANEL_TILE, dp(15)), 0x556F5DD6));
        tile.setClickable(true);
        tile.setFocusable(true);

        TextView icon = panelText(action.icon, 17, accentFor(action), true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(withAlpha(accentFor(action), 0.17f), dp(11)));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(31), dp(31)));

        TextView title = panelText(action.title, 10, Color.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(7), 0, 0, 0);
        copy.addView(title);
        copy.addView(panelText(action == PowerAction.LOCK_SCREEN ? "No root" : "Root command",
                8, COLOR_PANEL_MUTED, false), topMargin(dp(3)));
        tile.addView(copy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tile.setOnClickListener(view -> showConfirmation(action));
        return tile;
    }

    private TextView panelFooterButton(String label, int background, int foreground) {
        TextView button = panelText(label, 9, foreground, true);
        button.setGravity(Gravity.CENTER);
        button.setLetterSpacing(0.06f);
        button.setBackground(ripple(round(background, dp(13)), 0x44FFFFFF));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void executeAction(PowerAction action) {
        collapse();
        if (action == PowerAction.LOCK_SCREEN) {
            try {
                RebootController.lockScreen(this);
            } catch (Exception error) {
                Toast.makeText(this, "Could not open device admin settings.", Toast.LENGTH_LONG).show();
            }
        } else {
            RebootController.runRootCommand(this, action);
        }
    }

    private void collapse() {
        if (!expanded || !viewAdded) return;
        expanded = false;
        overlayRoot.removeAllViews();
        overlayRoot.addView(bubble, new FrameLayout.LayoutParams(bubbleSize, bubbleSize));
        windowParams.width = bubbleSize;
        windowParams.height = bubbleSize;
        windowParams.x = clamp(anchorX, dp(8), Math.max(dp(8), screenWidth - bubbleSize - dp(8)));
        windowParams.y = clamp(anchorY, dp(32), Math.max(dp(32), screenHeight - bubbleSize - dp(48)));
        updateWindow();
    }

    private void updateWindow() {
        if (!viewAdded || windowManager == null || overlayRoot == null) return;
        try {
            windowManager.updateViewLayout(overlayRoot, windowParams);
        } catch (Exception ignored) {
            // The system can remove overlays when permission is revoked while the app is open.
        }
    }

    private void removeOverlay() {
        if (viewAdded && windowManager != null && overlayRoot != null) {
            try {
                windowManager.removeView(overlayRoot);
            } catch (Exception ignored) {
                // Already removed by Android.
            }
        }
        viewAdded = false;
        overlayRoot = null;
        bubble = null;
    }

    private void setEnabled(boolean enabled) {
        getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(MainActivity.PREF_FLOATING_ENABLED, enabled).apply();
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void updateScreenSize() {
        if (windowManager == null) return;
        Point size = new Point();
        windowManager.getDefaultDisplay().getRealSize(size);
        screenWidth = size.x;
        screenHeight = size.y;
    }

    private int accentFor(PowerAction action) {
        if (action == PowerAction.POWER_OFF) return Color.rgb(230, 104, 117);
        if (action == PowerAction.LOCK_SCREEN) return Color.rgb(83, 202, 168);
        if (action == PowerAction.BOOTLOADER) return Color.rgb(240, 183, 93);
        if (action == PowerAction.FASTBOOT) return Color.rgb(101, 160, 248);
        if (action == PowerAction.RECOVERY) return Color.rgb(183, 146, 255);
        if (action == PowerAction.DOWNLOAD) return Color.rgb(82, 203, 220);
        return Color.rgb(147, 214, 137);
    }

    private TextView panelText(String value, float size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setFontFeatureSettings("kern");
        if (bold) text.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        return text;
    }

    private GradientDrawable bubbleBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(125, 99, 255), Color.rgb(84, 54, 214)});
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setStroke(dp(2), 0xBFFFFFFF);
        return drawable;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private android.graphics.drawable.Drawable ripple(android.graphics.drawable.Drawable content, int color) {
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(color), content, null);
    }

    private int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(255 * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = margin;
        return params;
    }
}
