package com.example.rehabilitationapp.ui.facecheck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CircleOverlayView extends View {

    public enum Status {
        CALIBRATING,   // 黃色（校正中）
        OK,            // 綠色（成功）
        OUT_OF_BOUND   // 紅色（超出）
    }

    private Paint circlePaint;
    private Status currentStatus = Status.CALIBRATING;

    public CircleOverlayView(Context context) {
        super(context);
        init();
    }

    public CircleOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        circlePaint = new Paint();
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(12f);
        circlePaint.setAntiAlias(true);
        updateColor();
    }

    public void setStatus(Status status) {
        this.currentStatus = status;
        updateColor();
        invalidate();  // 重新繪製
    }

    private void updateColor() {
        switch (currentStatus) {
            case CALIBRATING:
                circlePaint.setColor(Color.YELLOW);
                break;
            case OK:
                circlePaint.setColor(Color.GREEN);
                break;
            case OUT_OF_BOUND:
                circlePaint.setColor(Color.RED);
                break;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        // 以螢幕寬度較小值為基準，留一些邊距
        float radius = Math.min(centerX, centerY) - 80;

        canvas.drawCircle(centerX, centerY, radius, circlePaint);
    }
}
