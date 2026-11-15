package com.saleh.enezi.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.saleh.enezi.state.AppStateStore;

/**
 * أدوات العرض التفاعلية الحديثة (PresentationKit)
 * - عارض صور وتكبير كامل الشاشة (Pinch-to-Zoom + Double-Tap + Rotate)
 * - شريط تحكم موحد (تبديل شبكة/قائمة + الفرز + المفضلة + ملء الشاشة)
 */
public final class PresentationKit {

    private PresentationKit() {}

    public interface OnControlsChanged {
        void onChanged(AppStateStore.ViewMode mode, AppStateStore.SortOrder sort, boolean favOnly);
    }

    public static LinearLayout createViewControlBar(
            Activity a,
            String screenKey,
            boolean showGridToggle,
            boolean showFavFilter,
            Runnable onFullscreenToggle,
            OnControlsChanged listener) {

        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(DesignSystem.dp(a, 6), DesignSystem.dp(a, 4), DesignSystem.dp(a, 6), DesignSystem.dp(a, 4));
        bar.setBackground(DesignSystem.outlined(DesignSystem.card(a), DesignSystem.border(a), DesignSystem.dp(a, 1), DesignSystem.dp(a, 12)));

        final AppStateStore.ViewMode[] mode = {AppStateStore.getViewMode(a, screenKey)};
        final AppStateStore.SortOrder[] sort = {AppStateStore.getSortOrder(a, screenKey)};
        final boolean[] favOnly = {AppStateStore.isFavOnly(a, screenKey)};

        if (showGridToggle) {
            Button viewBtn = new Button(a);
            viewBtn.setAllCaps(false);
            viewBtn.setTextSize(12f);
            Runnable syncView = () -> {
                viewBtn.setText(mode[0] == AppStateStore.ViewMode.GRID ? "☷ شبكة" : "☰ قائمة");
                viewBtn.setTextColor(DesignSystem.primary(a));
                DesignSystem.applyInteractiveFeedback(viewBtn, a, DesignSystem.surfaceAlt(a), DesignSystem.border(a), 9);
            };
            syncView.run();
            viewBtn.setOnClickListener(v -> {
                mode[0] = mode[0] == AppStateStore.ViewMode.LIST ? AppStateStore.ViewMode.GRID : AppStateStore.ViewMode.LIST;
                AppStateStore.setViewMode(a, screenKey, mode[0]);
                syncView.run();
                if (listener != null) listener.onChanged(mode[0], sort[0], favOnly[0]);
            });
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, DesignSystem.dp(a, 40), 1f);
            vp.setMargins(DesignSystem.dp(a, 3), 0, 0, 0);
            bar.addView(viewBtn, vp);
        }

        Button sortBtn = new Button(a);
        sortBtn.setAllCaps(false);
        sortBtn.setTextSize(12f);
        Runnable syncSort = () -> {
            String lbl;
            switch (sort[0]) {
                case NAME_ASC: lbl = "↕️ الاسم"; break;
                case VALUE_DESC: lbl = "⬇️ الأعلى"; break;
                case VALUE_ASC: lbl = "⬆️ الأقل"; break;
                default: lbl = "↕️ فرز"; break;
            }
            sortBtn.setText(lbl);
            sortBtn.setTextColor(DesignSystem.textPrimary(a));
            DesignSystem.applyInteractiveFeedback(sortBtn, a, DesignSystem.card(a), DesignSystem.border(a), 9);
        };
        syncSort.run();
        sortBtn.setOnClickListener(v -> {
            AppStateStore.SortOrder[] vals = {
                    AppStateStore.SortOrder.DEFAULT,
                    AppStateStore.SortOrder.NAME_ASC,
                    AppStateStore.SortOrder.VALUE_DESC,
                    AppStateStore.SortOrder.VALUE_ASC
            };
            int nextIdx = 0;
            for (int i = 0; i < vals.length; i++) {
                if (vals[i] == sort[0]) {
                    nextIdx = (i + 1) % vals.length;
                    break;
                }
            }
            sort[0] = vals[nextIdx];
            AppStateStore.setSortOrder(a, screenKey, sort[0]);
            syncSort.run();
            if (listener != null) listener.onChanged(mode[0], sort[0], favOnly[0]);
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, DesignSystem.dp(a, 40), 1f);
        sp.setMargins(DesignSystem.dp(a, 3), 0, 0, 0);
        bar.addView(sortBtn, sp);

        if (showFavFilter) {
            Button favBtn = new Button(a);
            favBtn.setAllCaps(false);
            favBtn.setTextSize(12f);
            Runnable syncFav = () -> {
                favBtn.setText(favOnly[0] ? "⭐ المفضلة" : "☆ الكل");
                favBtn.setTextColor(favOnly[0] ? Color.WHITE : DesignSystem.textPrimary(a));
                DesignSystem.applyInteractiveFeedback(favBtn, a,
                        favOnly[0] ? DesignSystem.warning(a) : DesignSystem.card(a),
                        favOnly[0] ? Color.TRANSPARENT : DesignSystem.border(a), 9);
            };
            syncFav.run();
            favBtn.setOnClickListener(v -> {
                favOnly[0] = !favOnly[0];
                AppStateStore.setFavOnly(a, screenKey, favOnly[0]);
                syncFav.run();
                if (listener != null) listener.onChanged(mode[0], sort[0], favOnly[0]);
            });
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, DesignSystem.dp(a, 40), 1f);
            fp.setMargins(DesignSystem.dp(a, 3), 0, 0, 0);
            bar.addView(favBtn, fp);
        }

        if (onFullscreenToggle != null) {
            Button fsBtn = new Button(a);
            fsBtn.setText("⛶");
            fsBtn.setTextSize(15f);
            fsBtn.setContentDescription("وضع ملء الشاشة");
            fsBtn.setTextColor(DesignSystem.primary(a));
            DesignSystem.applyInteractiveFeedback(fsBtn, a, DesignSystem.surfaceAlt(a), DesignSystem.border(a), 9);
            fsBtn.setOnClickListener(v -> onFullscreenToggle.run());
            bar.addView(fsBtn, new LinearLayout.LayoutParams(DesignSystem.dp(a, 44), DesignSystem.dp(a, 40)));
        }

        return bar;
    }

    public static void showZoomableImageViewer(
            Activity a,
            String title,
            Bitmap bitmap,
            Runnable onPrint,
            Runnable onShare) {
        if (a == null || bitmap == null) {
            if (a != null) Toast.makeText(a, "لا توجد صورة للعرض", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dlg = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 15, 28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // Top Bar
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(DesignSystem.dp(a, 10), DesignSystem.dp(a, 8), DesignSystem.dp(a, 10), DesignSystem.dp(a, 8));
        top.setBackgroundColor(Color.argb(220, 15, 23, 42));

        Button close = new Button(a);
        close.setText("✕");
        close.setTextSize(18f);
        close.setTextColor(Color.WHITE);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(v -> dlg.dismiss());
        top.addView(close, new LinearLayout.LayoutParams(DesignSystem.dp(a, 48), DesignSystem.dp(a, 48)));

        TextView titleTv = new TextView(a);
        titleTv.setText(title == null ? "معاينة الصورة" : title);
        titleTv.setTextSize(15.5f);
        titleTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(titleTv, new LinearLayout.LayoutParams(0, -2, 1f));

        final Bitmap[] currentBmp = {bitmap};
        ImageView zoomView = new ImageView(a);
        zoomView.setScaleType(ImageView.ScaleType.MATRIX);
        zoomView.setImageBitmap(currentBmp[0]);
        zoomView.setBackgroundColor(Color.rgb(15, 23, 42));

        final Matrix matrix = new Matrix();
        final float[] scale = {1f};
        final float[] lastTouch = {0f, 0f};

        Runnable resetFit = () -> zoomView.post(() -> {
            int vw = zoomView.getWidth();
            int vh = zoomView.getHeight();
            int bw = currentBmp[0].getWidth();
            int bh = currentBmp[0].getHeight();
            if (vw > 0 && vh > 0 && bw > 0 && bh > 0) {
                float s = Math.min((float) vw / bw, (float) vh / bh);
                scale[0] = s;
                matrix.reset();
                matrix.postScale(s, s);
                matrix.postTranslate((vw - bw * s) / 2f, (vh - bh * s) / 2f);
                zoomView.setImageMatrix(matrix);
            }
        });

        ScaleGestureDetector scaleDetector = new ScaleGestureDetector(a, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float next = Math.max(0.4f, Math.min(5.0f, scale[0] * factor));
                float delta = next / scale[0];
                scale[0] = next;
                matrix.postScale(delta, delta, detector.getFocusX(), detector.getFocusY());
                zoomView.setImageMatrix(matrix);
                return true;
            }
        });

        GestureDetector tapDetector = new GestureDetector(a, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                resetFit.run();
                return true;
            }
        });

        zoomView.setOnTouchListener((v, ev) -> {
            scaleDetector.onTouchEvent(ev);
            tapDetector.onTouchEvent(ev);
            if (ev.getPointerCount() == 1) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    lastTouch[0] = ev.getX();
                    lastTouch[1] = ev.getY();
                } else if (ev.getAction() == MotionEvent.ACTION_MOVE && !scaleDetector.isInProgress()) {
                    float dx = ev.getX() - lastTouch[0];
                    float dy = ev.getY() - lastTouch[1];
                    matrix.postTranslate(dx, dy);
                    zoomView.setImageMatrix(matrix);
                    lastTouch[0] = ev.getX();
                    lastTouch[1] = ev.getY();
                }
            }
            return true;
        });

        Button rotateBtn = new Button(a);
        rotateBtn.setText("🔄");
        rotateBtn.setTextSize(16f);
        rotateBtn.setTextColor(Color.WHITE);
        rotateBtn.setContentDescription("تدوير 90 درجة");
        rotateBtn.setBackgroundColor(Color.TRANSPARENT);
        rotateBtn.setOnClickListener(v -> {
            Matrix rot = new Matrix();
            rot.postRotate(90f);
            currentBmp[0] = Bitmap.createBitmap(currentBmp[0], 0, 0, currentBmp[0].getWidth(), currentBmp[0].getHeight(), rot, true);
            zoomView.setImageBitmap(currentBmp[0]);
            resetFit.run();
        });
        top.addView(rotateBtn, new LinearLayout.LayoutParams(DesignSystem.dp(a, 48), DesignSystem.dp(a, 48)));

        root.addView(top, new LinearLayout.LayoutParams(-1, -2));
        root.addView(zoomView, new LinearLayout.LayoutParams(-1, 0, 1f));
        resetFit.run();

        TextView hintTv = new TextView(a);
        hintTv.setText("اسحب للتنقل • قرّب بإصبعين للتكبير • نقر مزدوج لإعادة الضبط");
        hintTv.setTextSize(11f);
        hintTv.setTextColor(Color.rgb(148, 163, 184));
        hintTv.setGravity(Gravity.CENTER);
        hintTv.setPadding(0, DesignSystem.dp(a, 4), 0, DesignSystem.dp(a, 4));
        root.addView(hintTv, new LinearLayout.LayoutParams(-1, -2));

        // Bottom Bar
        if (onPrint != null || onShare != null) {
            LinearLayout bbar = new LinearLayout(a);
            bbar.setOrientation(LinearLayout.HORIZONTAL);
            bbar.setPadding(DesignSystem.dp(a, 12), DesignSystem.dp(a, 8), DesignSystem.dp(a, 12), DesignSystem.dp(a, 12));
            bbar.setBackgroundColor(Color.argb(220, 15, 23, 42));

            if (onPrint != null) {
                Button pBtn = new Button(a);
                pBtn.setText("🖨️ طباعة");
                pBtn.setAllCaps(false);
                pBtn.setTextColor(Color.WHITE);
                pBtn.setTextSize(14f);
                DesignSystem.applyInteractiveFeedback(pBtn, a, DesignSystem.primary(a), Color.TRANSPARENT, 12);
                pBtn.setOnClickListener(v -> onPrint.run());
                LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, DesignSystem.dp(a, 48), 1f);
                pp.setMargins(DesignSystem.dp(a, 6), 0, 0, 0);
                bbar.addView(pBtn, pp);
            }
            if (onShare != null) {
                Button sBtn = new Button(a);
                sBtn.setText("📲 مشاركة");
                sBtn.setAllCaps(false);
                sBtn.setTextColor(Color.WHITE);
                sBtn.setTextSize(14f);
                DesignSystem.applyInteractiveFeedback(sBtn, a, DesignSystem.success(a), Color.TRANSPARENT, 12);
                sBtn.setOnClickListener(v -> onShare.run());
                bbar.addView(sBtn, new LinearLayout.LayoutParams(0, DesignSystem.dp(a, 48), 1f));
            }
            root.addView(bbar, new LinearLayout.LayoutParams(-1, -2));
        }

        dlg.setContentView(root);
        dlg.show();
    }
}
