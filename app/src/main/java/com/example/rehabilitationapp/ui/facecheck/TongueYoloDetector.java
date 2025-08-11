package com.example.rehabilitationapp.ui.facecheck;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 🎯 YOLO 舌頭檢測器 - 專門用於檢測舌頭的 TensorFlow Lite 模型
 *
 * 模型格式: [1, 8, 8400]
 * - [center_x, center_y, width, height, heart_prob, left_lung_prob, right_lung_prob, tongue_prob]
 */
public class TongueYoloDetector {

    private static final String TAG = "TongueYoloDetector";

    // 🔧 模型設定
    private static final String MODEL_FILE = "tongue_yolo.tflite";
    private static final int INPUT_SIZE = 640;
    private static final int CHANNEL_SIZE = 3;
    private static final float DEFAULT_CONFIDENCE_THRESHOLD = 0.2f;


    // 🔥 在這裡加入新的檢測結果類 ↓↓↓
    public static class DetectionResult {
        public boolean detected;
        public float confidence;
        public Rect boundingBox;  // 真實位置的邊界框

        public DetectionResult(boolean detected) {
            this.detected = detected;
            this.confidence = 0;
            this.boundingBox = null;
        }

        public DetectionResult(boolean detected, float confidence, Rect box) {
            this.detected = detected;
            this.confidence = confidence;
            this.boundingBox = box;
        }
    }
    // 🔥 新的檢測結果類結束 ↑↑↑

    private Interpreter tflite;
    private ByteBuffer inputBuffer;
    private float[][][] outputBuffer; // [1][8][8400] 根據你的實際格式

    /**
     * 🏗️ 建構子：初始化模型
     */
    public TongueYoloDetector(Context context) {
        try {
            // 載入模型

            MappedByteBuffer modelBuffer = loadModelFile(context);
            //tflite = new Interpreter(modelBuffer);
            Interpreter.Options opts = new Interpreter.Options();
            opts.setNumThreads(4);            // 先設 4，看裝置再調
            tflite = new Interpreter(modelBuffer, opts);
            Log.d(TAG, "✅ YOLO 模型載入成功");

            // 初始化輸入緩衝區
            inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * CHANNEL_SIZE);
            inputBuffer.order(ByteOrder.nativeOrder());

            // 🔥 根據你的實際格式初始化輸出緩衝區 [1][8][8400]
            outputBuffer = new float[1][8][8400];

            Log.d(TAG, "✅ 緩衝區初始化完成");

        } catch (Exception e) {
            Log.e(TAG, "❌ YOLO 模型初始化失敗: " + e.getMessage());
        }
    }

    /**
     * 📂 從 assets 載入模型文件
     */
    /**
     * 📂 從 assets 載入模型文件 - 簡化版
     */
    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * 🎯 主要檢測方法：檢測 Bitmap 中是否有舌頭
     *
     * @param bitmap 輸入圖片
     * @return true 如果檢測到舌頭，false 反之

    public boolean detectTongue(Bitmap bitmap) {
        return detectTongue(bitmap, DEFAULT_CONFIDENCE_THRESHOLD);
    }     */

    /*
    public boolean detectTongue(Bitmap bitmap, float confidenceThreshold) {
        if (tflite == null || bitmap == null) {
            Log.w(TAG, "⚠️ 模型未初始化或輸入為空");
            return false;
        }

        try {
            long startTime = System.currentTimeMillis();

            // 🔄 Step 1: 預處理圖片
            Bitmap resizedBitmap = preprocessImage(bitmap);

            // 🔄 Step 2: 轉換為模型輸入格式
            convertBitmapToByteBuffer(resizedBitmap);

            // 🔄 Step 3: 執行推理

            tflite.run(inputBuffer, outputBuffer);

            // 🔄 Step 4: 後處理結果
            boolean tongueDetected = postprocessResults(confidenceThreshold);

            long endTime = System.currentTimeMillis();
            Log.d(TAG, String.format("🎯 YOLO 推理完成: %s (用時: %dms, 閾值: %.2f)",
                    tongueDetected ? "發現舌頭" : "未發現舌頭",
                    (endTime - startTime), confidenceThreshold));

            // 清理記憶體
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }

            return tongueDetected;

        } catch (Exception e) {
            Log.e(TAG, "❌ YOLO 推理過程發生錯誤: " + e.getMessage());
            return false;
        }
    } */

    /**
     * 🖼️ 圖片預處理：調整大小到 640x640
     */
    private Bitmap preprocessImage(Bitmap original) {
        return Bitmap.createScaledBitmap(original, INPUT_SIZE, INPUT_SIZE, true);
    }

    /**
     * 🔄 將 Bitmap 轉換為 ByteBuffer（YOLO 輸入格式）
     */
    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        inputBuffer.rewind();

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        // 🎨 轉換為 RGB 並正規化到 [0, 1]
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < INPUT_SIZE; j++) {
                int pixel = pixels[i * INPUT_SIZE + j];

                // 提取 RGB 並正規化
                inputBuffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f); // R
                inputBuffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);  // G
                inputBuffer.putFloat((pixel & 0xFF) / 255.0f);         // B
            }
        }
    }

    /**
     * 📊 後處理：解析 YOLO 輸出，判斷是否檢測到舌頭
     *
     * 根據你的測試結果，格式是：
     * outputBuffer[0][0][i] = center_x (0-1)
     * outputBuffer[0][1][i] = center_y (0-1)
     * outputBuffer[0][2][i] = width (0-1)
     * outputBuffer[0][3][i] = height (0-1)
     * outputBuffer[0][4][i] = heart_prob (0-1)
     * outputBuffer[0][5][i] = left_lung_prob (0-1)
     * outputBuffer[0][6][i] = right_lung_prob (0-1)
     * outputBuffer[0][7][i] = tongue_prob (0-1) ← 我們要的！
     */
    private boolean postprocessResults(float confidenceThreshold) {
        int bestDetectionIndex = -1;
        float bestTongueProb = 0;

        // 🔥 在這裡加入您的測試代碼
        float maxProb = 0;
        for (int i = 0; i < 8400; i++) {
            maxProb = Math.max(maxProb, outputBuffer[0][7][i]);
        }
        Log.d(TAG, "最高舌頭概率: " + maxProb);
        // 🔥 測試代碼結束

        // 🔍 遍歷所有 8400 個檢測點
        for (int i = 0; i < 8400; i++) {
            float tongueProb = outputBuffer[0][7][i]; // 舌頭概率
            float width = outputBuffer[0][2][i];      // 寬度
            float height = outputBuffer[0][3][i];     // 高度

            // 🎯 多重條件過濾
            if (tongueProb > confidenceThreshold &&
                    width > 0.02f &&    // 最小寬度過濾（避免雜點）
                    height > 0.02f) {   // 最小高度過濾

                if (tongueProb > bestTongueProb) {
                    bestTongueProb = tongueProb;
                    bestDetectionIndex = i;
                }
            }
        }

        if (bestDetectionIndex >= 0) {
            float centerX = outputBuffer[0][0][bestDetectionIndex];
            float centerY = outputBuffer[0][1][bestDetectionIndex];
            float width = outputBuffer[0][2][bestDetectionIndex];
            float height = outputBuffer[0][3][bestDetectionIndex];

            Log.d(TAG, String.format("✅ 最佳舌頭檢測 - 位置:(%.3f,%.3f), 大小:(%.3fx%.3f), 概率:%.3f",
                    centerX, centerY, width, height, bestTongueProb));
            return true;
        }

        return false;
    }

    /**
     * 🎯 ROI 版本：只檢測指定區域（嘴部 ROI）
     */
    /*
    public boolean detectTongueInROI(Bitmap fullBitmap, Rect roi) {
        if (fullBitmap == null || roi == null) {
            Log.w(TAG, "⚠️ ROI 檢測輸入為空");
            return false;
        }

        try {
            // 🔪 裁切 ROI 區域，並確保不超出邊界
            int left = Math.max(0, roi.left);
            int top = Math.max(0, roi.top);
            int right = Math.min(fullBitmap.getWidth(), roi.right);
            int bottom = Math.min(fullBitmap.getHeight(), roi.bottom);

            if (right <= left || bottom <= top) {
                Log.w(TAG, "⚠️ ROI 區域無效");
                return false;
            }

            Bitmap roiBitmap = Bitmap.createBitmap(
                    fullBitmap, left, top, right - left, bottom - top);

            Log.d(TAG, String.format("🔪 ROI 裁切: (%d,%d) → (%d,%d), 大小: %dx%d",
                    left, top, right, bottom, roiBitmap.getWidth(), roiBitmap.getHeight()));

            // 🎯 對 ROI 進行檢測（使用較低閾值，因為 ROI 更精確）
            boolean result = detectTongue(roiBitmap, DEFAULT_CONFIDENCE_THRESHOLD * 0.8f);

            // 🧹 清理記憶體
            if (roiBitmap != fullBitmap) {
                roiBitmap.recycle();
            }

            return result;

        } catch (Exception e) {
            Log.e(TAG, "❌ ROI 檢測失敗: " + e.getMessage());
            return false;
        }
    }*/

    /**
     * 📐 根據 MediaPipe landmarks 計算嘴部 ROI
     *
     * @param landmarks 所有臉部關鍵點 [468][2]
     * @param imageWidth 圖片寬度
     * @param imageHeight 圖片高度
     * @return 嘴部 ROI 矩形
     */
    public static Rect calculateMouthROI(float[][] landmarks, int imageWidth, int imageHeight) {
        try {
            // 🔥 MediaPipe 嘴部關鍵點索引（這些可能需要根據實際情況調整）
            int[] mouthIndices = {
                    61, 84, 17, 314, 405, 320, 307, 375, 321, 308, 324, 318, // 嘴唇外圍
                    78, 95, 88, 178, 87, 14, 317, 402, 415, 310, 311, 312   // 嘴唇內圍
            };

            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;

            // 🔍 找出嘴部區域的邊界
            for (int index : mouthIndices) {
                if (index < landmarks.length && landmarks[index] != null) {
                    float x = landmarks[index][0];
                    float y = landmarks[index][1];

                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }

            // 📏 擴大 ROI 範圍（增加 30% 邊距，確保舌頭不被截斷）
            float margin = 0.3f;
            float width = maxX - minX;
            float height = maxY - minY;

            minX -= width * margin;
            maxX += width * margin;
            minY -= height * margin;
            maxY += height * margin;

            // 📐 確保在圖片邊界內
            int left = Math.max(0, (int) minX);
            int top = Math.max(0, (int) minY);
            int right = Math.min(imageWidth, (int) maxX);
            int bottom = Math.min(imageHeight, (int) maxY);

            Rect roi = new Rect(left, top, right, bottom);
            Log.d(TAG, String.format("📐 計算嘴部 ROI: %s", roi.toString()));

            return roi;

        } catch (Exception e) {
            Log.e(TAG, "❌ 計算嘴部 ROI 失敗: " + e.getMessage());
            // 返回預設 ROI（圖片中央 1/4 區域）
            return new Rect(imageWidth / 4, imageHeight / 4,
                    imageWidth * 3 / 4, imageHeight * 3 / 4);
        }
    }

    /**
     * 🧹 清理資源
     */
    public void release() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
            Log.d(TAG, "✅ YOLO 資源已清理");
        }
    }
    public DetectionResult detectTongueWithRealPosition(Bitmap fullBitmap, Rect roi, int overlayWidth, int overlayHeight) {
        if (tflite == null || fullBitmap == null || roi == null) {
            Log.w(TAG, "⚠️ 檢測輸入為空");
            return new DetectionResult(false);
        }

        try {
            // 裁切 ROI
            int left = Math.max(0, roi.left);
            int top = Math.max(0, roi.top);
            int right = Math.min(fullBitmap.getWidth(), roi.right);
            int bottom = Math.min(fullBitmap.getHeight(), roi.bottom);

            if (right <= left || bottom <= top) {
                Log.w(TAG, "⚠️ ROI 區域無效");
                return new DetectionResult(false);
            }

            Bitmap roiBitmap = Bitmap.createBitmap(fullBitmap, left, top, right - left, bottom - top);

            Log.d(TAG, String.format("🔪 ROI 裁切: (%d,%d) → (%d,%d), 大小: %dx%d",
                    left, top, right, bottom, roiBitmap.getWidth(), roiBitmap.getHeight()));

            // YOLO 推理
            Bitmap resizedBitmap = preprocessImage(roiBitmap);
            convertBitmapToByteBuffer(resizedBitmap);
            long t0 = System.nanoTime();
            tflite.run(inputBuffer, outputBuffer);
            long t1 = System.nanoTime();
            float inferMs = (t1 - t0) / 1_000_000f;


            // 🔥 處理結果並轉換為真實座標
            // 🔥 處理結果並轉換為真實座標（需要傳入螢幕尺寸）
            // 暫時用固定值，稍後從 Activity 傳入
            // 🔥 處理結果並轉換為真實座標
            DetectionResult result = postprocessWithRealCoordinates(roi, overlayWidth, overlayHeight);

            Log.d("METRICS", String.format("infer=%.1f ms, prob=%.3f", inferMs, result.confidence));

            // 清理記憶體
            if (roiBitmap != fullBitmap) roiBitmap.recycle();
            if (resizedBitmap != roiBitmap) resizedBitmap.recycle();

            return result;

        } catch (Exception e) {
            Log.e(TAG, "❌ 真實座標檢測失敗: " + e.getMessage());
            return new DetectionResult(false);
        }
    }

    private DetectionResult postprocessWithRealCoordinates(Rect originalROI, int overlayWidth, int overlayHeight) {

        int bestDetectionIndex = -1;
        float bestTongueProb = 0f;

        // 1) 找最佳框
        for (int i = 0; i < 8400; i++) {
            float prob   = outputBuffer[0][7][i]; // 舌頭機率
            float wNorm  = outputBuffer[0][2][i];
            float hNorm  = outputBuffer[0][3][i];
            if (prob > DEFAULT_CONFIDENCE_THRESHOLD &&
                    wNorm > 0.01f && hNorm > 0.01f &&
                    prob > bestTongueProb) {
                bestTongueProb = prob;
                bestDetectionIndex = i;
            }
        }




        if (bestDetectionIndex < 0) {
            Log.d(TAG, "❌ 未檢測到舌頭");
            return new DetectionResult(false);
        }

        // 2) YOLO 輸出（相對 ROI 的 0~1）
        float xNorm = outputBuffer[0][0][bestDetectionIndex];
        float yNorm = outputBuffer[0][1][bestDetectionIndex];
        float wNorm = outputBuffer[0][2][bestDetectionIndex];
        float hNorm = outputBuffer[0][3][bestDetectionIndex];

        // ⚠️ 不要做任何 X 向比例補償（沒有 letterbox）

        // 3) 轉回「Bitmap 空間」絕對座標（先落在 ROI、再加 ROI 左上角）
        int roiW = originalROI.width();
        int roiH = originalROI.height();

        int cx = originalROI.left + Math.round(xNorm * roiW);
        int cy = originalROI.top  + Math.round(yNorm * roiH);
        int bw = Math.round(wNorm * roiW);
        int bh = Math.round(hNorm * roiH);

        int left   = cx - bw / 2;
        int top    = cy - bh / 2;
        int right  = left + bw;
        int bottom = top  + bh;

        // 4) 夾回 ROI 範圍，避免越界
        left   = Math.max(originalROI.left,   left);
        top    = Math.max(originalROI.top,    top);
        right  = Math.min(originalROI.right,  right);
        bottom = Math.min(originalROI.bottom, bottom);

        // 若夾完變成空框，直接視為沒偵測到
        if (right <= left || bottom <= top) {
            Log.d(TAG, "❌ 偵測框越界後為空，忽略此檢測");
            return new DetectionResult(false);
        }

        Rect realTongueBox = new Rect(left, top, right, bottom);
        Log.d(TAG, String.format("🎯 舌頭真實位置(Bitmap): %s (conf=%.3f)", realTongueBox, bestTongueProb));

        return new DetectionResult(true, bestTongueProb, realTongueBox);
    }

// 🔥 座標處理方法結束 ↑↑↑
    /**
     * 🔧 設定自訂檢測閾值
     */
    public boolean isInitialized() {
        return tflite != null;
    }
}