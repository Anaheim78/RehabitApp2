package com.example.rehabilitationapp.ui.facecheck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class CircleOverlayView extends View {

    public enum Status {
        OK,              // 绿色
        OUT_OF_BOUND,    // 红色
        NO_FACE,         // 默认
        CALIBRATING      // 黄色
    }

    private Status status = Status.CALIBRATING;

    private Paint circlePaint;
    private Paint maskPaint;
    private Paint landmarkPaint; // 绘制所有关键点的画笔
    private Paint specialPointPaint; // 绘制特殊关键点的画笔

    // 存储所有468个关键点坐标
    private float[][] allLandmarks;
    private boolean hasLandmarks = false;

    // 特殊关键点的索引（用不同颜色标出）
    private int[] specialPoints = {10, 21, 251, 234, 454, 18}; // 额头、太阳穴、脸颊、下巴

    public CircleOverlayView(Context context) {
        super(context);
        init();
    }

    public CircleOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 圓形邊框畫筆
        circlePaint = new Paint();
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(12f);
        circlePaint.setAntiAlias(true);
        circlePaint.setColor(Color.YELLOW);

        // 遮罩畫筆
        maskPaint = new Paint();
        maskPaint.setColor(Color.argb(150, 0, 0, 0));
        maskPaint.setAntiAlias(true);

        // 普通关键点画笔（小白点）
        landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.WHITE);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setAntiAlias(true);

        // 特殊关键点画笔（大绿点）
        specialPointPaint = new Paint();
        specialPointPaint.setColor(Color.GREEN);
        specialPointPaint.setStyle(Paint.Style.FILL);
        specialPointPaint.setAntiAlias(true);
    }

    public void setStatus(Status status) {
        Log.d("CircleOverlay", "设置状态: " + status);
        this.status = status;
        invalidate();
    }

    // 设置所有468个关键点坐标
    public void setAllFaceLandmarks(float[][] landmarks) {
        this.allLandmarks = landmarks;
        this.hasLandmarks = true;
        invalidate();
    }

    // 清除关键点
    public void clearAllLandmarks() {
        this.hasLandmarks = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float radius = Math.min(centerX, centerY) - 80;

        // 1. 繪製全屏半透明遮罩
        canvas.drawPaint(maskPaint);

        // 2. 挖出圓形透明區域
        Paint clearPaint = new Paint();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawCircle(centerX, centerY, radius, clearPaint);

        // 3. 根据状态设置圆形边框颜色
        switch (status) {
            case OK:
                circlePaint.setColor(Color.GREEN);
                break;
            case OUT_OF_BOUND:
                circlePaint.setColor(Color.RED);
                break;
            case CALIBRATING:
                circlePaint.setColor(Color.YELLOW);
                break;
            case NO_FACE:
            default:
                circlePaint.setColor(Color.WHITE);
                break;
        }

        // 4. 繪製圓形邊框
        canvas.drawCircle(centerX, centerY, radius, circlePaint);

        // 5. 绘制所有468个关键点
        if (hasLandmarks && allLandmarks != null) {
            for (int i = 0; i < allLandmarks.length; i++) {
                float x = allLandmarks[i][0];
                float y = allLandmarks[i][1];

                // 检查是否是特殊关键点
                boolean isSpecial = false;
                for (int specialIndex : specialPoints) {
                    if (i == specialIndex) {
                        isSpecial = true;
                        break;
                    }
                }

                if (isSpecial) {
                    // 特殊关键点：大绿点
                    canvas.drawCircle(x, y, 8f, specialPointPaint);
                } else {
                    // 普通关键点：小白点
                    canvas.drawCircle(x, y, 2f, landmarkPaint);
                }
            }

            // 6. 在特殊关键点旁边标注编号（可选）
            Paint textPaint = new Paint();
            textPaint.setColor(Color.CYAN);
            textPaint.setTextSize(24f);
            textPaint.setAntiAlias(true);

            for (int specialIndex : specialPoints) {
                if (specialIndex < allLandmarks.length) {
                    float x = allLandmarks[specialIndex][0];
                    float y = allLandmarks[specialIndex][1];
                    canvas.drawText(String.valueOf(specialIndex), x + 15, y - 15, textPaint);
                }
            }
        }
    }

    // 兼容性方法（保持原有接口）
    public void setFaceKeyPoints(float[] forehead, float[] leftTemple, float[] rightTemple,
                                 float[] leftCheek, float[] rightCheek, float[] chin) {
        // 空方法，保持兼容性
    }

    public void clearFaceKeyPoints() {
        clearAllLandmarks();
    }
}