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
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Pair;


import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;

/**
 * 🎯 YOLO 舌頭檢測器 - 專門用於檢測舌頭的 TensorFlow Lite 模型
 *
 * 模型格式: [1, 8, 8400]
 * - [center_x, center_y, width, height, heart_prob, left_lung_prob, right_lung_prob, tongue_prob]
 */
public class TongueYoloDetector {


    private static final String TAG = "TongueYoloDetector";

    // 🔧 模型設定
    //640
    //private static final String MODEL_FILE = "tongue_yolo.tflite";
    //private static final int INPUT_SIZE = 640;

    private static final int INPUT_SIZE = 320;
    private static final String MODEL_FILE = "tongue_yolo_fp16_320.tflite"; // or fp32

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

    private GpuDelegate gpuDelegate = null;
    private  NnApiDelegate nnApiDelegate = null;
    private String backend = "CPU";  // 用來在 logcat 顯示實際跑哪個後端

    private int numDet ;



    // 調整Yolo輸入圖像為方形，需調整縮放與補黑邊 : 前處理時記錄 letterbox 參數，給後處理還原用
    static final class LetterboxCtx {
        int inW, inH;     // 模型輸入邊長（這裡就是 INPUT_SIZE）
        float scale;      // 等比縮放係數
        int padX, padY;   // 左右/上下補邊像素
    }
    /**
     * 🏗️ 建構子：初始化模型
     */
    public TongueYoloDetector(Context context) {
        try {
            // 讀模型
            MappedByteBuffer modelBuffer = loadModelFile(context);

            boolean ok = false;

            // ===== 1) 先嘗試 GPU（不帶 Options，所有版本都可編）=====
            try {
                Interpreter.Options gpuOpts = new Interpreter.Options();
                gpuOpts.setNumThreads(4);
                gpuDelegate = new GpuDelegate();     // 舊新版本都支援的建構法
                gpuOpts.addDelegate(gpuDelegate);

                tflite = new Interpreter(modelBuffer, gpuOpts);
                backend = "GPU";
                ok = true;
                Log.d(TAG, "✅ TFLite Interpreter 建立成功（GPU）");
            } catch (Throwable ge) {
                Log.w(TAG, "⚠️ GPU delegate 失敗，改試 NNAPI。原因: " + ge.getMessage());
                if (gpuDelegate != null) {
                    try { gpuDelegate.close(); } catch (Throwable ignore) {}
                    gpuDelegate = null;
                }
            }

            // ===== 2) 再嘗試 NNAPI =====
            if (!ok) {
                try {
                    Interpreter.Options nnOpts = new Interpreter.Options();
                    nnOpts.setNumThreads(4);
                    nnApiDelegate = new NnApiDelegate();
                    nnOpts.addDelegate(nnApiDelegate);

                    tflite = new Interpreter(modelBuffer, nnOpts);
                    backend = "NNAPI";
                    ok = true;
                    Log.d(TAG, "✅ TFLite Interpreter 建立成功（NNAPI）");
                    Log.d(TAG, "input dtype=" + tflite.getInputTensor(0).dataType() +
                            ", shape=" + java.util.Arrays.toString(tflite.getInputTensor(0).shape()));
                } catch (Throwable ne) {
                    Log.w(TAG, "⚠️ NNAPI delegate 失敗，改用 CPU。原因: " + ne.getMessage());
                    if (nnApiDelegate != null) {
                        try { nnApiDelegate.close(); } catch (Throwable ignore) {}
                        nnApiDelegate = null;
                    }
                }
            }

            // ===== 3) 最後回落 CPU =====
            if (!ok) {
                Interpreter.Options cpuOpts = new Interpreter.Options();
                cpuOpts.setNumThreads(4);
                tflite = new Interpreter(modelBuffer, cpuOpts);
                backend = "CPU";
                Log.d(TAG, "✅ TFLite Interpreter 建立成功（CPU）");
            }

            // 建好後再配置 buffer
            //inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * CHANNEL_SIZE).order(ByteOrder.nativeOrder());
            //outputBuffer = new float[1][8][8400];

            // 建好 tflite 後再配置 buffer
            inputBuffer = ByteBuffer
                    .allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * CHANNEL_SIZE)
                    .order(ByteOrder.nativeOrder());

// 依模型實際輸出 shape 配置輸出緩衝區
            int[] outShape = tflite.getOutputTensor(0).shape(); // [1, 8, N]
            numDet = (outShape.length >= 3) ? outShape[2] : 8400; // 保險用
            outputBuffer = new float[outShape[0]][outShape[1]][numDet];
            numDet = outputBuffer[0][0].length;

            Log.d(TAG, "✅ 緩衝區初始化完成，backend=" + backend +
                    ", input=" + INPUT_SIZE + "x" + INPUT_SIZE +
                    ", numDet=" + numDet);

            Log.d(TAG, "✅ 緩衝區初始化完成，backend=" + backend);

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
     * @param
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
// 等比縮放 + 黑邊補齊到 INPUT_SIZE×INPUT_SIZE；回傳可直接丟給 TFLite 的 ByteBuffer 與 letterbox 參數
    private Pair<ByteBuffer, LetterboxCtx> preprocessLetterbox(Bitmap roiBmp, int imgSize) {
        int rw = roiBmp.getWidth();
        int rh = roiBmp.getHeight();
        float scale = Math.min(imgSize * 1f / rw, imgSize * 1f / rh);

        int nw = Math.round(rw * scale);
        int nh = Math.round(rh * scale);
        int padX = (imgSize - nw) / 2;
        int padY = (imgSize - nh) / 2;

        // 1) 等比縮放
        Bitmap scaled = Bitmap.createScaledBitmap(roiBmp, nw, nh, true);

        // 2) 貼到正方形畫布（黑邊）
        Bitmap canvas = Bitmap.createBitmap(imgSize, imgSize, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(canvas);
        c.drawColor(Color.BLACK);
        c.drawBitmap(scaled, padX, padY, null);

        // 3) 轉成模型輸入（沿用你現有的 inputBuffer）
        convertBitmapToByteBuffer(canvas);  // 直接把畫布丟進你原本的轉換函式

        LetterboxCtx ctx = new LetterboxCtx();
        ctx.inW = ctx.inH = imgSize;
        ctx.scale = scale;
        ctx.padX = padX;
        ctx.padY = padY;

        // 注意：inputBuffer 是類成員，已被剛才那行填好了
        return new Pair<>(inputBuffer, ctx);
    }
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
        for (int i = 0; i < numDet; i++) {
            maxProb = Math.max(maxProb, outputBuffer[0][7][i]);
        }
        Log.d(TAG, "最高舌頭概率: " + maxProb);
        // 🔥 測試代碼結束

        // 🔍 遍歷所有 8400 個檢測點
        for (int i = 0; i < numDet; i++) {
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
        }
        if (gpuDelegate != null) {
            try { gpuDelegate.close(); } catch (Throwable ignore) {}
            gpuDelegate = null;
        }
        if (nnApiDelegate != null) {
            try { nnApiDelegate.close(); } catch (Throwable ignore) {}
            nnApiDelegate = null;
        }
        Log.d(TAG, "✅ YOLO 資源已清理");
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

            // YOLO 推理，這裡先改成Pair
            //Bitmap resizedBitmap = preprocessImage(roiBitmap);
            //convertBitmapToByteBuffer(resizedBitmap);

            // ✅ 新：letterbox 前處理（會把 inputBuffer 填好），同時拿到 ctx
            Pair<ByteBuffer, LetterboxCtx> in = preprocessLetterbox(roiBitmap, INPUT_SIZE);


            long t0 = System.nanoTime();
            tflite.run(in.first, outputBuffer);  // in.first 就是 inputBuffer
            long t1 = System.nanoTime();
            float inferMs = (t1 - t0) / 1_000_000f;


            // 🔥 處理結果並轉換為真實座標
            // 🔥 處理結果並轉換為真實座標（需要傳入螢幕尺寸）
            // 暫時用固定值，稍後從 Activity 傳入
            // 🔥 處理結果並轉換為真實座標
            // ✅ 新：後處理要用 ctx 去除 padding/縮放，回到 Bitmap 座標
            DetectionResult result = postprocessWithRealCoordinates(roi, in.second);

            //DetectionResult result = postprocessWithRealCoordinates(roi, overlayWidth, overlayHeight);

            Log.d("METRICS",
                    "infer=" + String.format(java.util.Locale.US, "%.1f", inferMs) +
                            " ms, prob=" + String.format(java.util.Locale.US, "%.3f", result.confidence) +
                            ", backend=" + backend);

            // 清理記憶體
            if (roiBitmap != fullBitmap) roiBitmap.recycle();
            //if (resizedBitmap != roiBitmap) resizedBitmap.recycle();

            return result;

        } catch (Exception e) {
            Log.e(TAG, "❌ 真實座標檢測失敗: " + e.getMessage());
            return new DetectionResult(false);
        }
    }

    private DetectionResult postprocessWithRealCoordinates(Rect originalROI, LetterboxCtx ctx) {
        int bestIdx = -1;
        float bestProb = 0f;

        // 1) 找舌頭最大機率的框（類別=舌頭，在你模型是 channel index 7）
        for (int i = 0; i < numDet; i++) {
            float prob = outputBuffer[0][7][i];
            float wN = outputBuffer[0][2][i];
            float hN = outputBuffer[0][3][i];
            if (prob > DEFAULT_CONFIDENCE_THRESHOLD && wN > 0.01f && hN > 0.01f && prob > bestProb) {
                bestProb = prob;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) return new DetectionResult(false);

        // 2) 讀出（0~1）正規化座標（以 INPUT_SIZE 正方形為基準）
        float cxN = outputBuffer[0][0][bestIdx];
        float cyN = outputBuffer[0][1][bestIdx];
        float wN  = outputBuffer[0][2][bestIdx];
        float hN  = outputBuffer[0][3][bestIdx];

        // 3) 轉成「正方形像素座標」
        float cxS = cxN * ctx.inW;
        float cyS = cyN * ctx.inH;
        float wS  = wN  * ctx.inW;
        float hS  = hN  * ctx.inH;

        // 4) 去 padding（回縮放後的 ROI）
        float cxNoPad = cxS - ctx.padX;
        float cyNoPad = cyS - ctx.padY;

        // 5) 除以 scale（回原 ROI 大小，以像素計）
        float cxRoi = cxNoPad / ctx.scale;
        float cyRoi = cyNoPad / ctx.scale;
        float wRoi  = wS / ctx.scale;
        float hRoi  = hS / ctx.scale;

        // 6) 映回整張 Bitmap：加上 ROI 起點
        int left   = Math.round(originalROI.left + (cxRoi - wRoi / 2f));
        int top    = Math.round(originalROI.top  + (cyRoi - hRoi / 2f));
        int right  = Math.round(left + wRoi);
        int bottom = Math.round(top  + hRoi);

        // 7) 夾在 ROI 內，避免越界
        left   = Math.max(originalROI.left,   Math.min(left,   originalROI.right));
        top    = Math.max(originalROI.top,    Math.min(top,    originalROI.bottom));
        right  = Math.max(originalROI.left,   Math.min(right,  originalROI.right));
        bottom = Math.max(originalROI.top,    Math.min(bottom, originalROI.bottom));
        if (right <= left || bottom <= top) return new DetectionResult(false);

        Rect realBox = new Rect(left, top, right, bottom);
        Log.d(TAG, String.format("🎯 舌頭真實位置(Bitmap): %s (conf=%.3f)", realBox, bestProb));
        return new DetectionResult(true, bestProb, realBox);
    }

// 🔥 座標處理方法結束 ↑↑↑
    /**
     * 🔧 設定自訂檢測閾值
     */
    public boolean isInitialized() {
        return tflite != null;
    }
}