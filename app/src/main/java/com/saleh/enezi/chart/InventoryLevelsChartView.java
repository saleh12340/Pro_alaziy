package com.saleh.enezi.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.saleh.enezi.manager.AnalyticsManager.InventoryStockData;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class InventoryLevelsChartView extends View {

    private final List<InventoryStockData> dataList = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int selectedIndex = -1;
    private OnItemClickListener onItemClickListener;

    private static final int COLOR_GOOD = Color.rgb(46, 125, 50);    // Dark Green
    private static final int COLOR_LOW = Color.rgb(242, 142, 54);     // Orange
    private static final int COLOR_EMPTY = Color.rgb(198, 40, 40);    // Red
    private static final int TEXT_COLOR = Color.rgb(32, 45, 60);

    public interface OnItemClickListener {
        void onItemClick(InventoryStockData item);
    }

    public InventoryLevelsChartView(Context context) {
        super(context);
        init();
    }

    public InventoryLevelsChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint.setStyle(Paint.Style.FILL);

        thresholdPaint.setColor(Color.rgb(180, 50, 50));
        thresholdPaint.setStyle(Paint.Style.STROKE);
        thresholdPaint.setStrokeWidth(dpToPx(2f));

        textPaint.setColor(TEXT_COLOR);
        textPaint.setTextSize(dpToPx(12f));
        textPaint.setTextAlign(Paint.Align.RIGHT);

        numPaint.setColor(TEXT_COLOR);
        numPaint.setTextSize(dpToPx(11f));
        numPaint.setTextAlign(Paint.Align.LEFT);

        bgBarPaint.setColor(Color.rgb(235, 240, 245));
        bgBarPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<InventoryStockData> newItems) {
        dataList.clear();
        if (newItems != null) {
            dataList.addAll(newItems);
        }
        selectedIndex = -1;
        invalidate();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataList.isEmpty()) {
            drawEmptyState(canvas);
            return;
        }

        int w = getWidth();

        float paddingRight = dpToPx(120f); // Space for item name on RTL
        float paddingLeft = dpToPx(65f);   // Space for quantity value on left
        float paddingTop = dpToPx(12f);
        float itemHeight = dpToPx(38f);

        float barAreaW = w - paddingRight - paddingLeft;
        if (barAreaW < dpToPx(50f)) barAreaW = dpToPx(50f);

        double maxQty = 0;
        for (InventoryStockData item : dataList) {
            double ref = Math.max(item.quantity, item.minQuantity * 1.5);
            if (ref > maxQty) maxQty = ref;
        }
        if (maxQty <= 0) maxQty = 10;

        DecimalFormat df = new DecimalFormat("#,##0.##");

        for (int i = 0; i < dataList.size(); i++) {
            InventoryStockData item = dataList.get(i);
            float topY = paddingTop + (i * itemHeight);
            float barY = topY + dpToPx(8f);
            float barH = dpToPx(18f);

            // Item Name on Right (RTL)
            textPaint.setTextAlign(Paint.Align.RIGHT);
            textPaint.setColor(TEXT_COLOR);
            String displayTitle = item.itemName;
            if (displayTitle.length() > 14) {
                displayTitle = displayTitle.substring(0, 12) + "…";
            }
            canvas.drawText(displayTitle, w - dpToPx(8f), barY + dpToPx(13f), textPaint);

            // Background Bar
            float barRight = w - paddingRight;
            float barLeft = barRight - barAreaW;
            RectF bgRect = new RectF(barLeft, barY, barRight, barY + barH);
            canvas.drawRoundRect(bgRect, dpToPx(6f), dpToPx(6f), bgBarPaint);

            // Actual Stock Fill
            float fillRatio = (float) Math.max(0, Math.min(1.0, item.quantity / maxQty));
            float fillLeft = barRight - (barAreaW * fillRatio);

            if (item.quantity > 0) {
                RectF fillRect = new RectF(fillLeft, barY, barRight, barY + barH);
                barPaint.setColor(item.status == 2 ? COLOR_EMPTY : (item.status == 1 ? COLOR_LOW : COLOR_GOOD));
                canvas.drawRoundRect(fillRect, dpToPx(6f), dpToPx(6f), barPaint);
            }

            // Minimum stock marker line
            if (item.minQuantity > 0) {
                float minRatio = (float) Math.min(1.0, item.minQuantity / maxQty);
                float minX = barRight - (barAreaW * minRatio);
                canvas.drawLine(minX, barY - dpToPx(2f), minX, barY + barH + dpToPx(2f), thresholdPaint);
            }

            // Quantity text on Left
            numPaint.setTextAlign(Paint.Align.LEFT);
            String qtyStr = df.format(item.quantity);
            if (item.status == 2) {
                numPaint.setColor(COLOR_EMPTY);
                qtyStr += " (نفذ)";
            } else if (item.status == 1) {
                numPaint.setColor(COLOR_LOW);
            } else {
                numPaint.setColor(COLOR_GOOD);
            }
            canvas.drawText(qtyStr, dpToPx(8f), barY + dpToPx(13f), numPaint);
        }
    }

    private void drawEmptyState(Canvas canvas) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dpToPx(13f));
        textPaint.setColor(TEXT_COLOR);
        canvas.drawText("لا توجد بيانات مخزون متوفرة للرسم البياني", getWidth() / 2f, getHeight() / 2f, textPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = dataList.size();
        float itemHeight = dpToPx(38f);
        int calcHeight = (int) (dpToPx(24f) + (count * itemHeight));
        int finalHeight = Math.max(calcHeight, (int) dpToPx(120f));

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                resolveSize(finalHeight, heightMeasureSpec));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (dataList.isEmpty()) return false;

            float touchY = event.getY();
            float paddingTop = dpToPx(12f);
            float itemHeight = dpToPx(38f);

            int index = (int) ((touchY - paddingTop) / itemHeight);
            if (index >= 0 && index < dataList.size()) {
                selectedIndex = index;
                invalidate();
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(dataList.get(index));
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
