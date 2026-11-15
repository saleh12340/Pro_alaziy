package com.saleh.enezi.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

/**
 * نظام التصميم الموحد (Material 3 Design System)
 * يدعم الوضع الليلي والفاتح تلقائياً، الاستجابة لجميع الأحجام، الحركات السلسة، وإمكانية الوصول.
 * 100% Free & Open Source - Standard Android Platform APIs.
 */
public final class DesignSystem {
    public static final int THEME_AUTO = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public enum WindowSizeClass { COMPACT, MEDIUM, EXPANDED }

    private DesignSystem() {}

    public static int getThemeMode(Context c) {
        return THEME_LIGHT;
    }

    public static void setThemeMode(Context c, int mode) {
        if (c == null) return;
        c.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().putInt("theme_mode", THEME_LIGHT).apply();
    }

    public static boolean isDark(Context c) {
        // Commercial Light Mode locked as confirmed by user requirements
        return false;
    }

    public static String themeLabel(Context c) {
        return "الوضع الفاتح الموحد";
    }

    // Unified Commercial Light Color Tokens
    public static int primary(Context c) {
        return Color.rgb(27, 59, 139); // Deep Royal Navy #1B3B8B
    }

    public static int primaryContainer(Context c) {
        return Color.rgb(235, 243, 254); // #EBF3FE
    }

    public static int topBarBg(Context c) {
        return Color.rgb(27, 59, 139); // Deep Royal Navy #1B3B8B
    }

    public static int background(Context c) {
        return Color.rgb(244, 246, 249); // Soft cool gray #F4F6F9
    }

    public static int card(Context c) {
        return Color.WHITE; // #FFFFFF
    }

    public static int surfaceAlt(Context c) {
        return Color.rgb(235, 243, 254); // #EBF3FE
    }

    public static int surfaceNav(Context c) {
        return Color.WHITE;
    }

    public static int textPrimary(Context c) {
        return Color.rgb(15, 23, 42); // #0F172A
    }

    public static int textMuted(Context c) {
        return Color.rgb(100, 116, 139); // #64748B
    }

    public static int border(Context c) {
        return Color.rgb(208, 215, 226); // #D0D7E2
    }

    public static int success(Context c) {
        return Color.rgb(22, 163, 74); // #16A34A (مدفوع / له / ربح)
    }

    public static int successSoft(Context c) {
        return Color.rgb(232, 248, 238); // #E8F8EE
    }

    public static int danger(Context c) {
        return Color.rgb(220, 38, 38); // #DC2626 (عليه / ديون / نقص)
    }

    public static int dangerSoft(Context c) {
        return Color.rgb(254, 235, 235); // #FEEBEB
    }

    public static int warning(Context c) {
        return Color.rgb(217, 119, 6); // #D97706 (تحذيرات)
    }

    public static int warningSoft(Context c) {
        return Color.rgb(254, 243, 199); // #FEF3C7
    }

    public static int dp(Context c, float dp) {
        float density = c == null ? 2f : c.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    public static WindowSizeClass windowSizeClass(Context c) {
        if (c == null) return WindowSizeClass.COMPACT;
        int widthDp = c.getResources().getConfiguration().screenWidthDp;
        if (widthDp >= 840) return WindowSizeClass.EXPANDED;
        if (widthDp >= 600) return WindowSizeClass.MEDIUM;
        return WindowSizeClass.COMPACT;
    }

    public static int gridColumns(Context c) {
        WindowSizeClass wsc = windowSizeClass(c);
        if (wsc == WindowSizeClass.EXPANDED) return 4;
        if (wsc == WindowSizeClass.MEDIUM) return 3;
        return 2;
    }

    public static boolean useNavigationRail(Context c) {
        return false;
    }

    public static int maxContentWidthPx(Context c) {
        WindowSizeClass wsc = windowSizeClass(c);
        if (wsc == WindowSizeClass.COMPACT) return ViewGroup.LayoutParams.MATCH_PARENT;
        return dp(c, wsc == WindowSizeClass.EXPANDED ? 860 : 660);
    }

    public static void applyWindowSystemBars(Activity a) {
        if (a == null) return;
        try {
            Window w = a.getWindow();
            if (w == null) return;
            w.setStatusBarColor(topBarBg(a));
            w.setNavigationBarColor(surfaceNav(a));
            if (Build.VERSION.SDK_INT >= 26) {
                View decor = w.getDecorView();
                int flags = decor.getSystemUiVisibility();
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                decor.setSystemUiVisibility(flags);
            }
        } catch (Throwable ignored) {}
    }

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable outlined(int fillColor, int strokeColor, int strokePx, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fillColor);
        g.setStroke(strokePx, strokeColor);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static void applyInteractiveFeedback(View v, Context c, int bgColor, int strokeColor, float radiusDp) {
        if (v == null || c == null) return;
        float rad = dp(c, radiusDp);
        GradientDrawable base = strokeColor != Color.TRANSPARENT
                ? outlined(bgColor, strokeColor, dp(c, 1), rad)
                : rounded(bgColor, rad);
        if (Build.VERSION.SDK_INT >= 21) {
            int rippleColor = isDark(c) ? Color.argb(45, 255, 255, 255) : Color.argb(35, 27, 59, 139);
            v.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), base, null));
        } else {
            v.setBackground(base);
        }
        v.setMinimumHeight(Math.max(v.getMinimumHeight(), dp(c, 48)));
        v.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.975f).scaleY(0.975f).setDuration(90).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            return false;
        });
    }

    public static void applyAccessibility(View v, String description, boolean isHeading) {
        if (v == null) return;
        if (description != null && !description.isEmpty()) {
            v.setContentDescription(description);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            try { v.setAccessibilityHeading(isHeading); } catch (Throwable ignored) {}
        }
    }

    public static void animateEntrance(View v) {
        if (v == null) return;
        try {
            v.setAlpha(0f);
            v.setTranslationY(dp(v.getContext(), 10));
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        } catch (Throwable ignored) {
            v.setAlpha(1f);
            v.setTranslationY(0f);
        }
    }

    public static LinearLayout createSkeletonCard(Context c, int lines) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        card.setBackground(outlined(card(c), border(c), dp(c, 1), dp(c, 14)));
        int boneColor = isDark(c) ? Color.rgb(38, 52, 74) : Color.rgb(226, 232, 240);

        for (int i = 0; i < Math.max(1, lines); i++) {
            View bar = new View(c);
            bar.setBackground(rounded(boneColor, dp(c, 6)));
            int w = i == 0 ? ViewGroup.LayoutParams.MATCH_PARENT : dp(c, i % 2 == 1 ? 210 : 140);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, dp(c, i == 0 ? 16 : 12));
            if (i > 0) lp.topMargin = dp(c, 8);
            card.addView(bar, lp);

            ObjectAnimator pulse = ObjectAnimator.ofFloat(bar, "alpha", 0.45f, 1.0f);
            pulse.setDuration(750);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.start();
        }
        return card;
    }

    public static LinearLayout createSkeletonList(Context c, int cardCount) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (int i = 0; i < Math.max(1, cardCount); i++) {
            LinearLayout sk = createSkeletonCard(c, 3);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(c, 8);
            box.addView(sk, lp);
        }
        return box;
    }

    public static LinearLayout createEmptyState(Context c, String icon, String title, String subtitle, String actionLabel, View.OnClickListener onAction) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(c, 20), dp(c, 24), dp(c, 20), dp(c, 24));
        box.setBackground(outlined(card(c), border(c), dp(c, 1), dp(c, 16)));

        TextView ic = new TextView(c);
        ic.setText(icon == null ? "📭" : icon);
        ic.setTextSize(36f);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(rounded(surfaceAlt(c), dp(c, 28)));
        box.addView(ic, new LinearLayout.LayoutParams(dp(c, 64), dp(c, 64)));

        Space sp1 = new Space(c);
        box.addView(sp1, new LinearLayout.LayoutParams(1, dp(c, 10)));

        TextView t = new TextView(c);
        t.setText(title == null ? "لا توجد بيانات" : title);
        t.setTextSize(16f);
        t.setTextColor(textPrimary(c));
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        box.addView(t, new LinearLayout.LayoutParams(-1, -2));

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(c);
            sub.setText(subtitle);
            sub.setTextSize(13f);
            sub.setTextColor(textMuted(c));
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
            box.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        }

        if (actionLabel != null && onAction != null) {
            Button btn = new Button(c);
            btn.setText(actionLabel);
            btn.setAllCaps(false);
            btn.setTextSize(14f);
            btn.setTextColor(Color.WHITE);
            btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            applyInteractiveFeedback(btn, c, primary(c), Color.TRANSPARENT, 12);
            btn.setOnClickListener(onAction);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, dp(c, 48));
            bp.topMargin = dp(c, 10);
            btn.setPadding(dp(c, 20), 0, dp(c, 20), 0);
            box.addView(btn, bp);
        }
        return box;
    }

    public static LinearLayout createErrorState(Context c, String title, String message, String retryLabel, Runnable onRetry) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(c, 18), dp(c, 20), dp(c, 18), dp(c, 20));
        box.setBackground(outlined(dangerSoft(c), danger(c), dp(c, 1), dp(c, 16)));

        TextView ic = new TextView(c);
        ic.setText("⚠️");
        ic.setTextSize(32f);
        ic.setGravity(Gravity.CENTER);
        box.addView(ic, new LinearLayout.LayoutParams(-1, -2));

        TextView t = new TextView(c);
        t.setText(title == null ? "تعذر تحميل البيانات" : title);
        t.setTextSize(15.5f);
        t.setTextColor(danger(c));
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(c, 6), 0, dp(c, 4));
        box.addView(t, new LinearLayout.LayoutParams(-1, -2));

        if (message != null && !message.isEmpty()) {
            TextView msg = new TextView(c);
            msg.setText(message);
            msg.setTextSize(12.5f);
            msg.setTextColor(textPrimary(c));
            msg.setGravity(Gravity.CENTER);
            box.addView(msg, new LinearLayout.LayoutParams(-1, -2));
        }

        if (onRetry != null) {
            Button btn = new Button(c);
            btn.setText(retryLabel == null ? "🔄 إعادة المحاولة" : retryLabel);
            btn.setAllCaps(false);
            btn.setTextSize(13.5f);
            btn.setTextColor(Color.WHITE);
            btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            applyInteractiveFeedback(btn, c, danger(c), Color.TRANSPARENT, 10);
            btn.setOnClickListener(v -> onRetry.run());
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, dp(c, 48));
            bp.topMargin = dp(c, 10);
            btn.setPadding(dp(c, 18), 0, dp(c, 18), 0);
            box.addView(btn, bp);
        }
        return box;
    }
}
