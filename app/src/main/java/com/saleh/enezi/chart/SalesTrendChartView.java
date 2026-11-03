package com.saleh.enezi.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.saleh.enezi.manager.AnalyticsManager.DailySalesData;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class SalesTrendChartView extends View {

    private final List<DailySalesData> dataList = new ArrayList<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path linePath = new Path();
    private final Path areaPath = new Path();

    private int selectedIndex = -1;
    private OnPointClickListener onPointClickListener;

    private static final int PRIMARY_COLOR = Color.rgb(30, 91, 170); // BLUE_PRIMARY
    private static final int ACCENT_COLOR = Color.rgb(242, 142, 54); // ORANGE
    private static final int GRID_COLOR = Color.rgb(225, 235, 245);
    private static final int TEXT_COLOR = Color.rgb(80, 95, 110);

    public interface OnPointClickListener {
        void onPointClick(DailySalesData data);
    }

    public SalesTrendChartView(Context context) {
        super(context);
        init();
    }

    public SalesTrendChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint.setColor(PRIMARY_COLOR);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dpToPx(3f));
        linePaint.setPathEffect(new CornerPathEffect(dpToPx(12f)));

        areaPaint.setStyle(Paint.Style.FILL);

        barPaint.setColor(Color.argb(70, 242, 142, 54));
        barPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(GRID_COLOR);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dpToPx(1f));
        gridPaint.setPathEffect(new DashPathEffect(new float[]{dpToPx(4f), dpToPx(4f)}, 0));

        textPaint.setColor(TEXT_COLOR);
        textPaint.setTextSize(dpToPx(10f));
        textPaint.setTextAlign(Paint.Align.CENTER);

        dotPaint.setStyle(Paint.Style.FILL);

        tooltipPaint.setColor(Color.rgb(32, 45, 60));
        tooltipPaint.setStyle(Paint.Style.FILL);

        tooltipTextPaint.setColor(Color.WHITE);
        tooltipTextPaint.setTextSize(dpToPx(11f));
        tooltipTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<DailySalesData> newItems) {
        dataList.clear();
        if (newItems != null) {
            dataList.addAll(newItems);
        }
        selectedIndex = dataList.isEmpty() ? -1 : dataList.size() - 1;
        invalidate();
    }

    public void setOnPointClickListener(OnPointClickListener listener) {
        this.onPointClickListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataList.isEmpty()) {
            drawEmptyState(canvas);
            return;
        }

        int w = getWidth();
        int h = getHeight();

        float paddingLeft = dpToPx(48f);
        float paddingRight = dpToPx(16f);
        float paddingTop = dpToPx(32f);
        float paddingBottom = dpToPx(36f);

        float chartW = w - paddingLeft - paddingRight;
        float chartH = h - paddingTop - paddingBottom;

        double maxSales = 0;
        for (DailySalesData d : dataList) {
            if (d.totalSales > maxSales) maxSales = d.totalSales;
        }
        if (maxSales <= 0) maxSales = 1000;

        // Draw 4 horizontal gridlines with amounts
        int gridLines = 4;
        DecimalFormat df = new DecimalFormat("#,###");
        textPaint.setTextAlign(Paint.Align.LEFT);

        for (int i = 0; i <= gridLines; i++) {
            float y = paddingTop + (chartH - (i * (chartH / gridLines)));
            canvas.drawLine(paddingLeft, y, w - paddingRight, y, gridPaint);

            double val = (maxSales / gridLines) * i;
            String valStr = df.format(Math.round(val));
            canvas.drawText(valStr, dpToPx(6f), y + dpToPx(3f), textPaint);
        }

        int count = dataList.size();
        float stepX = count > 1 ? chartW / (count - 1) : chartW;

        linePath.reset();
        areaPath.reset();

        float[] xCoords = new float[count];
        float[] yCoords = new float[count];

        for (int i = 0; i < count; i++) {
            float x = paddingLeft + (i * stepX);
            double val = dataList.get(i).totalSales;
            float y = paddingTop + chartH - (float) ((val / maxSales) * chartH);

            xCoords[i] = x;
            yCoords[i] = y;

            if (i == 0) {
                linePath.moveTo(x, y);
                areaPath.moveTo(x, paddingTop + chartH);
                areaPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                areaPath.lineTo(x, y);
            }
        }

        if (count > 0) {
            areaPath.lineTo(xCoords[count - 1], paddingTop + chartH);
            areaPath.close();
        }

        // Area Gradient
        LinearGradient shader = new LinearGradient(0, paddingTop, 0, paddingTop + chartH,
                Color.argb(100, 30, 91, 170), Color.argb(10, 30, 91, 170), Shader.TileMode.CLAMP);
        areaPaint.setShader(shader);

        canvas.drawPath(areaPath, areaPaint);
        canvas.drawPath(linePath, linePaint);

        // Draw Bars & Date Labels below
        float barWidth = Math.min(dpToPx(16f), stepX * 0.5f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < count; i++) {
            DailySalesData item = dataList.get(i);
            float x = xCoords[i];
            float y = yCoords[i];

            // Revenue bar
            RectF barRect = new RectF(x - barWidth / 2, y, x + barWidth / 2, paddingTop + chartH);
            barPaint.setColor(i == selectedIndex ? ACCENT_COLOR : Color.argb(60, 30, 91, 170));
            canvas.drawRoundRect(barRect, dpToPx(4f), dpToPx(4f), barPaint);

            // Date label
            canvas.drawText(item.label, x, h - dpToPx(10f), textPaint);

            // Data point dot
            dotPaint.setColor(i == selectedIndex ? ACCENT_COLOR : PRIMARY_COLOR);
            canvas.drawCircle(x, y, dpToPx(i == selectedIndex ? 6f : 4f), dotPaint);

            if (i == selectedIndex) {
                dotPaint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, dpToPx(2.5f), dotPaint);
            }
        }

        // Selected Tooltip
        if (selectedIndex >= 0 && selectedIndex < count) {
            DailySalesData item = dataList.get(selectedIndex);
            float x = xCoords[selectedIndex];
            float y = yCoords[selectedIndex];

            String tipText = item.label + ": " + df.format(Math.round(item.totalSales)) + " ر.ي (" + item.invoiceCount + " فواتير)";
            float textWidth = tooltipTextPaint.measureText(tipText);
            float tipH = dpToPx(26f);
            float tipW = textWidth + dpToPx(20f);

            float tipX = Math.max(paddingLeft + tipW / 2, Math.min(w - paddingRight - tipW / 2, x));
            float tipY = Math.max(paddingTop + tipH + dpToPx(4f), y - dpToPx(12f));

            RectF tipRect = new RectF(tipX - tipW / 2, tipY - tipH, tipX + tipW / 2, tipY);
            canvas.drawRoundRect(tipRect, dpToPx(8f), dpToPx(8f), tooltipPaint);

            canvas.drawText(tipText, tipX, tipY - dpToPx(8f), tooltipTextPaint);
        }
    }

    private void drawEmptyState(Canvas canvas) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dpToPx(14f));
        textPaint.setColor(TEXT_COLOR);
        canvas.drawText("لا توجد بيانات مبيعات متوفرة للرسم البياني", getWidth() / 2f, getHeight() / 2f, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            if (dataList.isEmpty()) return false;

            float touchX = event.getX();
            int count = dataList.size();

            float paddingLeft = dpToPx(48f);
            float paddingRight = dpToPx(16f);
            float chartW = getWidth() - paddingLeft - paddingRight;
            float stepX = count > 1 ? chartW / (count - 1) : chartW;

            int closest = -1;
            float minDist = Float.MAX_VALUE;

            for (int i = 0; i < count; i++) {
                float x = paddingLeft + (i * stepX);
                float dist = Math.abs(x - touchX);
                if (dist < minDist) {
                    minDist = dist;
                    closest = i;
                }
            }

            if (closest != selectedIndex && closest >= 0) {
                selectedIndex = closest;
                invalidate();
                if (onPointClickListener != null) {
                    onPointClickListener.onPointClick(dataList.get(closest));
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
