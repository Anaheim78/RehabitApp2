package com.example.rehabilitationapp.ui.facecheck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.DashPathEffect;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class CircleOverlayView extends View {

    public enum Status {
        OK,              // 绿色
        OUT_OF_BOUND,    // 红色
        NO_FACE,         // 默认
        CALIBRATING,     // 黄色
        DEMO             // 藍色（示範段）
    }

    // 🔥 新增：顯示模式
    public enum DisplayMode {
        LANDMARKS,      // 顯示 MediaPipe 關鍵點（原模式）
        YOLO_DETECTION  // 顯示 YOLO 檢測結果（舌頭模式）
    }

    private Status status = Status.CALIBRATING;
    private int customCircleColor = 0;
    private DisplayMode currentDisplayMode = DisplayMode.LANDMARKS; // 🔥 新增

    private Paint circlePaint;
    private Paint maskPaint;
    private Paint landmarkPaint; // 绘制所有关键点的画笔
    private Paint specialPointPaint; // 绘制特殊关键点的画笔

    // 🔥 新增：YOLO 相關畫筆
    private Paint tongueBoxPaint;           // 舌頭邊界框畫筆
    private Paint roiBoxPaint;              // ROI 框畫筆
    private Paint confidenceTextPaint;      // 信心度文字畫筆

    // 存储所有468个关键点坐标
    private float[][] allLandmarks;
    private boolean hasLandmarks = false;

    // 🔥 新增：YOLO 檢測結果相關變數
    private boolean tongueDetected = false;
    private Rect tongueBox = null;          // 舌頭邊界框
    private Rect mouthROI = null;           // 嘴部 ROI 框
    private float tongueConfidence = 0.0f;   // 檢測信心度

    // 參考線相關變數
    private float eyeLx, eyeLy, eyeRx, eyeRy, noseX, noseY, browX, browY;
    private boolean showReferenceLines = false;


    private float arrowFromX = -1, arrowFromY = -1;
    private float arrowToX = -1, arrowToY = -1;
    private boolean showArrow = false;
    private Paint arrowPaint;


    private Paint referenceLinePaint;

    // 特殊关键点的索引（用不同颜色标出）
    private int[] specialPoints = {10, 21, 251, 234, 454, 18}; // 额头、太阳穴、脸颊、下巴

    // 🆕 橢圓比例設定（寬度:高度）- 高度維持原本大小，寬度縮短
    private float ovalWidthRatio = 0.85f;   // 橢圓寬度比例（< 1 變窄）
    private float ovalHeightRatio = 1.0f;   // 橢圓高度比例（= 1 維持原本直徑）

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

        // 🔥 初始化 YOLO 相關畫筆
        initializeYoloPaints();
    }

    /**
     * 🎨 初始化 YOLO 相關畫筆
     */
    private void initializeYoloPaints() {
        // 舌頭邊界框畫筆（綠色實線）
        tongueBoxPaint = new Paint();
        tongueBoxPaint.setColor(Color.GREEN);
        tongueBoxPaint.setStyle(Paint.Style.STROKE);
        tongueBoxPaint.setStrokeWidth(6.0f);
        tongueBoxPaint.setAntiAlias(true);

        // ROI 框畫筆（藍色虛線）
        roiBoxPaint = new Paint();
        roiBoxPaint.setColor(Color.BLUE);
        roiBoxPaint.setStyle(Paint.Style.STROKE);
        roiBoxPaint.setStrokeWidth(4.0f);
        roiBoxPaint.setPathEffect(new DashPathEffect(new float[]{15, 10}, 0)); // 虛線效果
        roiBoxPaint.setAntiAlias(true);

        // 信心度文字畫筆
        confidenceTextPaint = new Paint();
        confidenceTextPaint.setColor(Color.WHITE);
        confidenceTextPaint.setTextSize(42);
        confidenceTextPaint.setAntiAlias(true);
        confidenceTextPaint.setShadowLayer(4, 2, 2, Color.BLACK); // 文字陰影
        confidenceTextPaint.setStyle(Paint.Style.FILL);

        // 參考線畫筆
        referenceLinePaint = new Paint();
        referenceLinePaint.setColor(Color.YELLOW);
        referenceLinePaint.setStrokeWidth(3);
        referenceLinePaint.setAlpha(150);
        referenceLinePaint.setAntiAlias(true);

        //紅色箭頭
        arrowPaint = new Paint();
        arrowPaint.setColor(Color.RED);
        arrowPaint.setAlpha(160);
        arrowPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setAntiAlias(true);
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

    public void setReferenceLines(float eyeLx, float eyeLy, float eyeRx, float eyeRy,
                                  float noseX, float noseY, float browX, float browY) {
        this.eyeLx = eyeLx; this.eyeLy = eyeLy;
        this.eyeRx = eyeRx; this.eyeRy = eyeRy;
        this.noseX = noseX; this.noseY = noseY;
        this.browX = browX; this.browY = browY;
        this.showReferenceLines = true;
        invalidate();
    }

    // ==================== 🔥 新增 YOLO 相關方法 ====================

    /**
     * 🔄 設置顯示模式
     */
    public void setDisplayMode(DisplayMode mode) {
        if (currentDisplayMode != mode) {
            currentDisplayMode = mode;
            Log.d("CircleOverlay", "切換顯示模式: " + mode);
            invalidate(); // 重新繪製
        }
    }
// CircleOverlayView.java

    public void clearReferenceLines() {
        this.showReferenceLines = false;
        invalidate();
    }

    /**
     * 🎯 設置 YOLO 檢測結果
     *
     * @param detected 是否檢測到舌頭
     * @param confidence 檢測信心度 (0.0-1.0)
     * @param tongueBox 舌頭邊界框（可選）
     * @param roiBox 嘴部 ROI 框
     */
    public void setYoloDetectionResult(boolean detected, float confidence, Rect tongueBox, Rect roiBox) {
        this.tongueDetected = detected;
        this.tongueConfidence = confidence;
        this.tongueBox = tongueBox;
        this.mouthROI = roiBox;

        // 只有在 YOLO 模式下才重新繪製
        if (currentDisplayMode == DisplayMode.YOLO_DETECTION) {
            invalidate();
        }
    }

    /**
     * 🧹 清除 YOLO 檢測結果
     */
    public void clearYoloResults() {
        this.tongueDetected = false;
        this.tongueConfidence = 0.0f;
        this.tongueBox = null;
        this.mouthROI = null;

        if (currentDisplayMode == DisplayMode.YOLO_DETECTION) {
            invalidate();
        }
    }

    /**
     * 📋 取得當前顯示模式
     */
    public DisplayMode getCurrentDisplayMode() {
        return currentDisplayMode;
    }

    /**
     * 🆕 設定橢圓比例
     * @param widthRatio 寬度比例 (例如 0.85 表示比原本圓窄一點)
     * @param heightRatio 高度比例 (例如 1.1 表示比原本圓高一點)
     */
    public void setOvalRatio(float widthRatio, float heightRatio) {
        this.ovalWidthRatio = widthRatio;
        this.ovalHeightRatio = heightRatio;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float baseRadius = Math.min(centerX, centerY) - 80;

        // 🆕 計算橢圓的寬高
        float ovalWidth = baseRadius * 2 * ovalWidthRatio;
        float ovalHeight = baseRadius * 2 * ovalHeightRatio;

        // 橢圓的邊界矩形
        RectF ovalRect = new RectF(
                centerX - ovalWidth / 2,
                centerY - ovalHeight / 2,
                centerX + ovalWidth / 2,
                centerY + ovalHeight / 2
        );

        // 1. 繪製全屏半透明遮罩
        canvas.drawPaint(maskPaint);

        // 2. 🆕 挖出橢圓形透明區域（改用 Path）
        Paint clearPaint = new Paint();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setAntiAlias(true);

        Path ovalPath = new Path();
        ovalPath.addOval(ovalRect, Path.Direction.CW);
        canvas.drawPath(ovalPath, clearPaint);

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
            case DEMO:
                circlePaint.setColor(Color.BLUE);
                break;
            case NO_FACE:
            default:
                circlePaint.setColor(Color.WHITE);
                break;
        }

        // 4. 🆕 繪製橢圓形邊框
// 4. 繪製橢圓形邊框（訓練中用漸變色，其他用狀態色）
        if (customCircleColor != 0 && status == Status.OK) {
            circlePaint.setColor(customCircleColor);
        }
        canvas.drawOval(ovalRect, circlePaint);

        // 🔥 5. 根據顯示模式決定顯示內容
        switch (currentDisplayMode) {
            case LANDMARKS:
                drawAllFaceLandmarks(canvas); // 把現有的 landmark 繪製邏輯移到這裡
                break;
            case YOLO_DETECTION:
                drawYoloDetectionResults(canvas); // 新的 YOLO 顯示
                break;
        }

        if (showArrow && arrowFromX >= 0) {
            drawTaperedArrow(canvas, arrowFromX, arrowFromY, arrowToX, arrowToY);
        }
    }

    public void setCircleColor(int color) {
        this.customCircleColor = color;
        invalidate();
    }

    private void drawTaperedArrow(Canvas canvas, float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float len = (float) Math.hypot(dx, dy);
        if (len < 30) return;

        // 單位向量
        float ux = dx / len;
        float uy = dy / len;
        // 垂直向量
        float px = -uy;
        float py = ux;

        // 箭頭尺寸
        float headLen = Math.min(len * 0.3f, 80);
        float headWidth = headLen * 0.7f;
        float tailWidth = headLen * 0.08f;

        // 箭頭頂點
        float tipX = toX;
        float tipY = toY;
        // 箭頭底邊（頭跟身體交界）
        float baseX = toX - ux * headLen;
        float baseY = toY - uy * headLen;

        Path path = new Path();
        // 從尾巴尖端開始（細的那端）
        path.moveTo(fromX + px * tailWidth, fromY + py * tailWidth);
        // 沿身體到箭頭底邊
        path.lineTo(baseX + px * tailWidth, baseY + py * tailWidth);
        // 箭頭右翼
        path.lineTo(baseX + px * headWidth, baseY + py * headWidth);
        // 箭頭頂點
        path.lineTo(tipX, tipY);
        // 箭頭左翼
        path.lineTo(baseX - px * headWidth, baseY - py * headWidth);
        // 回到身體左邊
        path.lineTo(baseX - px * tailWidth, baseY - py * tailWidth);
        // 回到尾巴
        path.lineTo(fromX - px * tailWidth, fromY + py * tailWidth);
        path.close();

        canvas.drawPath(path, arrowPaint);
    }


    /**
     * 📍 繪製 MediaPipe 關鍵點（移動後的原有邏輯）
     */
    private void drawAllFaceLandmarks(Canvas canvas) {
        // 🔥 原本 onDraw 中的第 5 和第 6 段代碼移到這裡
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

            // 在特殊关键点旁边标注编号
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

    /**
     * 🎯 繪製 YOLO 檢測結果
     */
    private void drawYoloDetectionResults(Canvas canvas) {
        // 🟦 繪製 ROI 框（一律顯示，讓用戶知道檢測區域）
        if (mouthROI != null) {
            canvas.drawRect(mouthROI, roiBoxPaint);

            // ROI 標籤
            canvas.drawText("檢測區域",
                    mouthROI.left + 10,
                    mouthROI.top - 15,
                    confidenceTextPaint);
        }

        // 🟩 繪製舌頭邊界框（只有檢測到才顯示）
        if (tongueDetected && tongueBox != null) {
            canvas.drawRect(tongueBox, tongueBoxPaint);

            // 顯示信心度
            String confidenceText = String.format("舌頭 %.0f%%", tongueConfidence * 100);
            canvas.drawText(confidenceText,
                    tongueBox.left + 10,
                    tongueBox.top - 20,
                    confidenceTextPaint);
        }

        // 繪製參考線
        if (showReferenceLines) {
//            // X軸：眼睛連線
//            canvas.drawLine(eyeLx, eyeLy, eyeRx, eyeRy, referenceLinePaint);
//            // Y軸：眉心到鼻子
//            canvas.drawLine(browX, browY, noseX, noseY, referenceLinePaint);
//
//            // 標註
//            referenceLinePaint.setTextSize(24);
//            canvas.drawText("X軸", (eyeLx + eyeRx) / 2, eyeLy - 10, referenceLinePaint);
//            canvas.drawText("Y軸", browX + 10, (browY + noseY) / 2, referenceLinePaint);
            // 繪製參考線
            if (showReferenceLines) {
                // 延長線的係數
                float extendFactor = 3.5f; // 延長50%

                // X軸：眼睛連線（延長）
                float eyeCenterX = (eyeLx + eyeRx) / 2;
                float eyeCenterY = (eyeLy + eyeRy) / 2;
                float eyeLineLength = (float) Math.hypot(eyeRx - eyeLx, eyeRy - eyeLy);
                float eyeAngle = (float) Math.atan2(eyeRy - eyeLy, eyeRx - eyeLx);
                float extendedLength = eyeLineLength * extendFactor / 2;

                float eyeStartX = eyeCenterX - extendedLength * (float) Math.cos(eyeAngle);
                float eyeStartY = eyeCenterY - extendedLength * (float) Math.sin(eyeAngle);
                float eyeEndX = eyeCenterX + extendedLength * (float) Math.cos(eyeAngle);
                float eyeEndY = eyeCenterY + extendedLength * (float) Math.sin(eyeAngle);

                canvas.drawLine(eyeStartX, eyeStartY, eyeEndX, eyeEndY, referenceLinePaint);

                // Y軸：眉心到鼻子（延長）
                float noseBrowLength = (float) Math.hypot(noseX - browX, noseY - browY);
                float noseBrowAngle = (float) Math.atan2(noseY - browY, noseX - browX);
                float centerX = (browX + noseX) / 2;
                float centerY = (browY + noseY) / 2;
                float extendedNBLength = noseBrowLength * extendFactor / 2;

                float nbStartX = centerX - extendedNBLength * (float) Math.cos(noseBrowAngle);
                float nbStartY = centerY - extendedNBLength * (float) Math.sin(noseBrowAngle);
                float nbEndX = centerX + extendedNBLength * (float) Math.cos(noseBrowAngle);
                float nbEndY = centerY + extendedNBLength * (float) Math.sin(noseBrowAngle);

                canvas.drawLine(nbStartX, nbStartY, nbEndX, nbEndY, referenceLinePaint);
            }
        }

    }


    public void setArrowGuide(float fromX, float fromY, float toX, float toY) {
        this.arrowFromX = fromX;
        this.arrowFromY = fromY;
        this.arrowToX = toX;
        this.arrowToY = toY;
        this.showArrow = true;
        invalidate();
    }

    public void clearArrowGuide() {
        this.showArrow = false;
        invalidate();
    }

    // ==================== 兼容性方法（保持原有接口）====================

    public void setFaceKeyPoints(float[] forehead, float[] leftTemple, float[] rightTemple,
                                 float[] leftCheek, float[] rightCheek, float[] chin) {
        // 空方法，保持兼容性
    }
    public Status getStatus() { return status; }

    public void clearFaceKeyPoints() {
        clearAllLandmarks();
    }
}