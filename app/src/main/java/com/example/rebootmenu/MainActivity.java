package com.example.rebootmenu;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** The Reboot Menu control center. */
public class MainActivity extends Activity {
    static final String PREFS_NAME = "reboot_menu_prefs";
    static final String PREF_FLOATING_ENABLED = "floating_enabled";

    private static final int REQUEST_OVERLAY_PERMISSION = 4102;
    private static final int COLOR_BACKGROUND = Color.rgb(246, 245, 250);
    private static final int COLOR_INK = Color.rgb(35, 31, 52);
    private static final int COLOR_MUTED = Color.rgb(117, 112, 132);
    private static final int COLOR_PURPLE = Color.rgb(103, 72, 235);

    private TextView widgetStatus;
    private TextView widgetButton;
    private TextView widgetHint;
    private boolean pendingWidgetEnable;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility()
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingWidgetEnable && canDrawOverlays()) {
            pendingWidgetEnable = false;
            startFloatingWidget();
        }
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (preferences.getBoolean(PREF_FLOATING_ENABLED, false) && !canDrawOverlays()) {
            preferences.edit().putBoolean(PREF_FLOATING_ENABLED, false).apply();
            stopService(new Intent(this, FloatingWidgetService.class));
        }
        updateWidgetCard();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION && pendingWidgetEnable) {
            if (canDrawOverlays()) {
                pendingWidgetEnable = false;
                startFloatingWidget();
            } else {
                pendingWidgetEnable = false;
                Toast.makeText(this, "Allow display over other apps to enable the floating widget.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(16), dp(20), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        page.addView(buildHero(), fullWidth(0, LinearLayout.LayoutParams.WRAP_CONTENT));
        page.addView(buildWidgetCard(), fullWidth(dp(18), LinearLayout.LayoutParams.WRAP_CONTENT));
        addSectionHeading(page, "POWER ACTIONS", "Choose where your device should go next.");
        addActionGrid(page);
        page.addView(buildFooter(), fullWidth(dp(18), LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(22), dp(21), dp(22), dp(20));
        hero.setBackground(gradient(new int[]{Color.rgb(38, 27, 81), Color.rgb(95, 66, 210)}, dp(28)));
        hero.setElevation(dp(3));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("⏻", 20, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(Color.rgb(117, 91, 225), dp(15)));
        brandRow.addView(mark, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout brandCopy = new LinearLayout(this);
        brandCopy.setOrientation(LinearLayout.VERTICAL);
        brandCopy.setPadding(dp(10), 0, 0, 0);
        TextView brand = text("REBOOT MENU", 12, Color.WHITE, true);
        brand.setLetterSpacing(0.13f);
        TextView brandSub = text("DEVICE CONTROL CENTER", 9, 0xFFCFC7F2, true);
        brandSub.setLetterSpacing(0.12f);
        brandCopy.addView(brand);
        brandCopy.addView(brandSub, topMargin(dp(3)));
        brandRow.addView(brandCopy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView version = text("ROOT TOOLS", 9, Color.WHITE, true);
        version.setLetterSpacing(0.08f);
        version.setPadding(dp(10), dp(7), dp(10), dp(7));
        version.setBackground(round(0x33FFFFFF, dp(14)));
        brandRow.addView(version);
        hero.addView(brandRow);

        TextView title = text("Power,\non your terms.", 31, Color.WHITE, true);
        title.setLineSpacing(dp(1), 1.0f);
        hero.addView(title, topMargin(dp(22)));

        TextView description = text("Restart, recover, or lock your device\nfrom one calm control center.",
                13, 0xFFE4DFF9, false);
        description.setLineSpacing(dp(4), 1.0f);
        hero.addView(description, topMargin(dp(10)));

        LinearLayout tags = new LinearLayout(this);
        tags.setGravity(Gravity.CENTER_VERTICAL);
        TextView tagOne = heroTag("9 POWER ACTIONS");
        TextView tagTwo = heroTag("FLOATING SHORTCUT");
        tags.addView(tagOne);
        tags.addView(tagTwo, leftMargin(dp(8)));
        hero.addView(tags, topMargin(dp(17)));
        return hero;
    }

    private TextView heroTag(String label) {
        TextView tag = text(label, 9, Color.WHITE, true);
        tag.setLetterSpacing(0.05f);
        tag.setPadding(dp(9), dp(7), dp(9), dp(7));
        tag.setBackground(round(0x2AFFFFFF, dp(13)));
        return tag;
    }

    private View buildWidgetCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(cardBackground());
        card.setElevation(dp(2));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text("⏻", 23, COLOR_PURPLE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(0xFFF0ECFF, dp(17)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        TextView eyebrow = text("FLOATING WIDGET", 9, COLOR_PURPLE, true);
        eyebrow.setLetterSpacing(0.11f);
        TextView title = text("Power at a glance", 16, COLOR_INK, true);
        copy.addView(eyebrow);
        copy.addView(title, topMargin(dp(4)));
        row.addView(copy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        widgetStatus = text("READY TO ENABLE", 9, COLOR_MUTED, true);
        widgetStatus.setGravity(Gravity.CENTER);
        widgetStatus.setLetterSpacing(0.04f);
        widgetStatus.setPadding(dp(9), dp(7), dp(9), dp(7));
        widgetStatus.setBackground(round(0xFFF2F0F7, dp(14)));
        row.addView(widgetStatus);
        card.addView(row);

        widgetHint = text("Keep your favorite power actions one tap away, even over other apps.",
                12, COLOR_MUTED, false);
        widgetHint.setLineSpacing(dp(3), 1.0f);
        card.addView(widgetHint, topMargin(dp(12)));

        widgetButton = text("ENABLE FLOATING WIDGET", 11, Color.WHITE, true);
        widgetButton.setGravity(Gravity.CENTER);
        widgetButton.setLetterSpacing(0.07f);
        widgetButton.setBackground(ripple(round(COLOR_PURPLE, dp(15)), 0x44FFFFFF));
        widgetButton.setClickable(true);
        widgetButton.setFocusable(true);
        widgetButton.setOnClickListener(view -> toggleFloatingWidget());
        card.addView(widgetButton, fullWidth(dp(14), dp(48)));
        return card;
    }

    private void updateWidgetCard() {
        if (widgetStatus == null || widgetButton == null || widgetHint == null) return;
        boolean active = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_FLOATING_ENABLED, false);
        widgetStatus.setText(active ? "WIDGET ACTIVE" : "READY TO ENABLE");
        widgetStatus.setTextColor(active ? Color.rgb(28, 133, 105) : COLOR_MUTED);
        widgetStatus.setBackground(round(active ? 0xFFE8F6F1 : 0xFFF2F0F7, dp(14)));
        widgetButton.setText(active ? "TURN OFF FLOATING WIDGET" : "ENABLE FLOATING WIDGET");
        widgetButton.setBackground(ripple(
                round(active ? Color.rgb(55, 67, 85) : COLOR_PURPLE, dp(15)), 0x44FFFFFF));
        widgetHint.setText(active
                ? "The quick menu is floating above your apps. Tap its power button to open it."
                : "Keep your favorite power actions one tap away, even over other apps.");
    }

    private void toggleFloatingWidget() {
        boolean active = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_FLOATING_ENABLED, false);
        if (active) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_FLOATING_ENABLED, false).apply();
            stopService(new Intent(this, FloatingWidgetService.class));
            updateWidgetCard();
            Toast.makeText(this, "Floating widget turned off.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            pendingWidgetEnable = true;
            try {
                Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(permission, REQUEST_OVERLAY_PERMISSION);
            } catch (Exception error) {
                pendingWidgetEnable = false;
                Toast.makeText(this, "Open Android settings and allow display over other apps.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        startFloatingWidget();
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void startFloatingWidget() {
        try {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_FLOATING_ENABLED, true).apply();
            Intent service = new Intent(this, FloatingWidgetService.class)
                    .setAction(FloatingWidgetService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
            updateWidgetCard();
            Toast.makeText(this, "Floating controls are ready.", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_FLOATING_ENABLED, false).apply();
            updateWidgetCard();
            Toast.makeText(this, "Could not start the floating widget: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void addSectionHeading(LinearLayout page, String title, String subtitle) {
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView label = text(title, 11, COLOR_PURPLE, true);
        label.setLetterSpacing(0.14f);
        TextView detail = text(subtitle, 12, COLOR_MUTED, false);
        heading.addView(label);
        heading.addView(detail, topMargin(dp(5)));
        page.addView(heading, fullWidth(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addActionGrid(LinearLayout page) {
        for (int i = 0; i < PowerAction.ALL.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            PowerAction first = PowerAction.ALL.get(i);
            row.addView(createActionCard(first), gridCellParams());
            if (i + 1 < PowerAction.ALL.size()) {
                row.addView(createActionCard(PowerAction.ALL.get(i + 1)), gridCellParams());
            } else {
                View spacer = new View(this);
                row.addView(spacer, gridCellParams());
            }
            LinearLayout.LayoutParams rowParams = fullWidth(0, dp(139));
            rowParams.leftMargin = -dp(6);
            rowParams.rightMargin = -dp(6);
            if (i > 0) rowParams.topMargin = dp(10);
            page.addView(row, rowParams);
        }
    }

    private LinearLayout.LayoutParams gridCellParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(139), 1f);
        params.leftMargin = dp(6);
        params.rightMargin = dp(6);
        return params;
    }

    private View createActionCard(PowerAction action) {
        int accent = accentFor(action);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(10), dp(11));
        card.setBackground(ripple(cardBackground(), 0x1A6848EB));
        card.setClickable(true);
        card.setFocusable(true);
        card.setElevation(dp(1));

        TextView icon = text(action.icon, 20, accent, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(round(withAlpha(accent, 0.13f), dp(14)));
        card.addView(icon, new LinearLayout.LayoutParams(dp(39), dp(39)));

        TextView title = text(action.title, 14, COLOR_INK, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title, topMargin(dp(11)));

        TextView detail = text(action.subtitle, 10, COLOR_MUTED, false);
        detail.setSingleLine(true);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(detail, topMargin(dp(3)));

        card.setOnClickListener(view -> showConfirmation(action));
        return card;
    }

    private View buildFooter() {
        LinearLayout note = new LinearLayout(this);
        note.setOrientation(LinearLayout.VERTICAL);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(14), dp(14), dp(14), dp(14));
        note.setBackground(round(0xFFEDEBF3, dp(18)));
        TextView title = text("A LITTLE SYSTEM, A LOT OF CONTROL", 9, COLOR_INK, true);
        title.setLetterSpacing(0.11f);
        title.setGravity(Gravity.CENTER);
        TextView detail = text("Power actions need root access. Screen lock uses Android Device Admin.",
                11, COLOR_MUTED, false);
        detail.setGravity(Gravity.CENTER);
        detail.setLineSpacing(dp(3), 1.0f);
        note.addView(title);
        note.addView(detail, topMargin(dp(6)));
        return note;
    }

    private void showConfirmation(PowerAction action) {
        Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(22), dp(22), dp(18));
        content.setBackground(round(Color.WHITE, dp(26)));

        TextView eyebrow = text(action.requiresRoot ? "ROOT POWER ACTION" : "DEVICE ACTION",
                9, COLOR_PURPLE, true);
        eyebrow.setLetterSpacing(0.13f);
        content.addView(eyebrow);

        TextView title = text(action.title, 24, COLOR_INK, true);
        content.addView(title, topMargin(dp(8)));

        TextView message = text(action.confirmationMessage(), 13, COLOR_MUTED, false);
        message.setLineSpacing(dp(4), 1.0f);
        content.addView(message, topMargin(dp(10)));

        TextView warning = text(action.requiresRoot
                        ? "Android may ask your root manager to approve this command."
                        : "If asked, enable Reboot Menu as a device administrator.",
                11, Color.rgb(91, 75, 139), false);
        warning.setLineSpacing(dp(3), 1.0f);
        warning.setPadding(dp(12), dp(11), dp(12), dp(11));
        warning.setBackground(round(0xFFF3F0FF, dp(14)));
        content.addView(warning, topMargin(dp(16)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        TextView cancel = dialogButton("NOT NOW", Color.rgb(240, 238, 246), COLOR_INK);
        TextView proceed = dialogButton(action == PowerAction.POWER_OFF ? "POWER OFF" :
                (action == PowerAction.LOCK_SCREEN ? "LOCK SCREEN" : "CONTINUE"), COLOR_PURPLE, Color.WHITE);
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(49), 1f));
        LinearLayout.LayoutParams proceedParams = new LinearLayout.LayoutParams(0, dp(49), 1f);
        proceedParams.leftMargin = dp(10);
        buttons.addView(proceed, proceedParams);
        content.addView(buttons, topMargin(dp(20)));

        cancel.setOnClickListener(view -> dialog.dismiss());
        proceed.setOnClickListener(view -> {
            dialog.dismiss();
            if (action == PowerAction.LOCK_SCREEN) {
                try {
                    RebootController.lockScreen(this);
                } catch (Exception error) {
                    Toast.makeText(this, "Could not open device admin settings.", Toast.LENGTH_LONG).show();
                }
            } else {
                RebootController.runRootCommand(this, action);
            }
        });

        dialog.setContentView(content);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams layout = window.getAttributes();
            layout.width = getResources().getDisplayMetrics().widthPixels - dp(36);
            layout.height = WindowManager.LayoutParams.WRAP_CONTENT;
            layout.gravity = Gravity.CENTER;
            layout.dimAmount = 0.46f;
            window.setAttributes(layout);
        }
    }

    private TextView dialogButton(String label, int background, int foreground) {
        TextView button = text(label, 10, foreground, true);
        button.setLetterSpacing(0.08f);
        button.setGravity(Gravity.CENTER);
        button.setBackground(ripple(round(background, dp(15)), 0x33FFFFFF));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private int accentFor(PowerAction action) {
        if (action == PowerAction.POWER_OFF) return Color.rgb(217, 92, 106);
        if (action == PowerAction.LOCK_SCREEN) return Color.rgb(48, 154, 130);
        if (action == PowerAction.BOOTLOADER) return Color.rgb(223, 150, 54);
        if (action == PowerAction.FASTBOOT) return Color.rgb(64, 127, 220);
        if (action == PowerAction.RECOVERY) return Color.rgb(135, 94, 218);
        if (action == PowerAction.DOWNLOAD) return Color.rgb(44, 152, 174);
        if (action == PowerAction.SAFE_MODE) return Color.rgb(89, 155, 95);
        return COLOR_PURPLE;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("kern");
        if (bold) view.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private Drawable cardBackground() {
        GradientDrawable background = round(Color.WHITE, dp(21));
        background.setStroke(dp(1), Color.rgb(235, 232, 243));
        return background;
    }

    private Drawable gradient(int[] colors, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private Drawable ripple(Drawable content, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    private int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(255 * alpha), Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams fullWidth(int top, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height);
        params.topMargin = top;
        return params;
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = margin;
        return params;
    }

    private LinearLayout.LayoutParams leftMargin(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = margin;
        return params;
    }
}
