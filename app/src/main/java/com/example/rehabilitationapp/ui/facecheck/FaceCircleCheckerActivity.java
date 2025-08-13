package com.example.rehabilitationapp.ui.facecheck;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.ui.results.AnalysisResultActivity;
import com.example.rehabilitationapp.ui.analysis.CSVPeakAnalyzer;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceCircleCheckerActivity extends AppCompatActivity {

    // 相機權限用
    private static final int PERMISSION_REQUEST_CODE = 123;
    // LOG 的 Tag
    private static final String TAG = "FaceCircleChecker";

    // 計時常數
    private static final int CALIBRATION_TIME = 5000;         // 5 秒校正
    private static final int MAINTAIN_TIME_TOTAL = 30000;     // 30 秒維持
    private static final int PROGRESS_UPDATE_INTERVAL = 50;   // 進度條更新間隔

    // ★★★ 頻率控制（可自行調整）★★★
    private static final int FACE_MESH_EVERY = 5;   // 每 5 幀更新一次「嘴巴 ROI」
    private static final int YOLO_EVERY      = 3;   // 每 3 幀跑一次 YOLO

    // android.camera.core
    private PreviewView cameraView;
    private FaceLandmarker faceLandmarker;
    private ProcessCameraProvider cameraProvider;

    // 執行緒
    private ExecutorService cameraExecutor;

    // UI
    private CircleOverlayView overlayView;
    private TextView statusText;
    private TextView timerText;
    private ProgressBar progressBar;

    // 訓練資訊
    private String trainingLabel = "訓練";
    private int trainingType = -1;

    // 記錄器
    private FaceDataRecorder dataRecorder;

    // YOLO
    private TongueYoloDetector tongueDetector;
    private boolean isYoloEnabled = false;

    // 幀計數與統計
    private int frameId = 0;
    private long firstMetricTime = 0;

    // ROI 快取（Overlay/Bitmap 兩套座標系）
    private Rect lastOverlayRoi = null;
    private Rect lastBitmapRoi  = null;

    // 狀態管理
    private enum AppState { CALIBRATING, MAINTAINING, OUT_OF_BOUNDS }
    private AppState currentState = AppState.CALIBRATING;

    // 主執行緒 handler 與計時任務
    private Handler mainHandler;
    private Runnable calibrationTimer;
    private Runnable maintainTimer;
    private Runnable progressUpdater;

    // 計時
    private long calibrationStartTime = 0;
    private long maintainStartTime = 0;
    private long maintainTotalTime = 0;
    private boolean isTrainingCompleted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_circle_checker);

        trainingType = getIntent().getIntExtra("training_type", -1);
        trainingLabel = getIntent().getStringExtra("training_label");
        if (trainingLabel == null) trainingLabel = "訓練";
        Log.d(TAG, "接收到訓練類型: " + trainingType + ", 標籤: " + trainingLabel);

        if ("舌頭".equals(trainingLabel)) {
            initializeTongueDetector();
            Log.d(TAG, "✅ 舌頭模式：啟用 YOLO 檢測 + YOLO 顯示");
        } else {
            Log.d(TAG, "✅ 嘴唇模式：使用 MediaPipe 關鍵點顯示");
        }

        dataRecorder = new FaceDataRecorder(this, trainingLabel, trainingType);
        Log.d(TAG, "資料記錄器初始化完成");

        cameraView = findViewById(R.id.camera_view);
        overlayView = findViewById(R.id.overlay_view);
        statusText = findViewById(R.id.status_text);
        timerText = findViewById(R.id.timer_text);
        progressBar = findViewById(R.id.progress_bar);

        if ("舌頭".equals(trainingLabel)) {
            overlayView.setDisplayMode(CircleOverlayView.DisplayMode.YOLO_DETECTION);
        } else {
            overlayView.setDisplayMode(CircleOverlayView.DisplayMode.LANDMARKS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        testCameraPermission();
        setupFaceLandmarker();
        initializeUI();

        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    // 初始化舌頭檢測器
    private void initializeTongueDetector() {
        try {
            tongueDetector = new TongueYoloDetector(this);
            isYoloEnabled = tongueDetector.isInitialized();
            if (!isYoloEnabled) {
                Log.e(TAG, "❌ 舌頭檢測器初始化失敗");
                Toast.makeText(this, "舌頭檢測器初始化失敗，將使用一般模式", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ 舌頭檢測器初始化錯誤: " + e.getMessage());
            isYoloEnabled = false;
            Toast.makeText(this, "舌頭檢測器載入失敗：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeUI() {
        progressBar.setMax(100);
        progressBar.setProgress(0);
        updateStatusDisplay();
        updateTimerDisplay();
        startProgressUpdater();
    }

    // 初始化 FaceLandmarker
    private void setupFaceLandmarker() {
        try {
            Log.d(TAG, "try to FaceLandmarker 初始化");
            FaceLandmarker.FaceLandmarkerOptions options = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder()
                            .setModelAssetPath("face_landmarker.task")
                            .build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumFaces(1)
                    .build();
            faceLandmarker = FaceLandmarker.createFromOptions(this, options);
            Log.d(TAG, "FaceLandmarker 初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "FaceLandmarker 初始化錯誤: " + e.getMessage());
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        Log.d("FaceCircleCheckerActivity","in to requestCameraPermission");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "需要相機權限才能使用此功能", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "相機初始化失敗", e);
                Toast.makeText(this, "相機初始化失敗", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(cameraView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            Log.d(TAG, "相機綁定成功");
        } catch (Exception e) {
            Log.e(TAG, "相機綁定失敗", e);
            Toast.makeText(this, "相機啟動失敗", Toast.LENGTH_SHORT).show();
        }
    }

    // 圖像分析
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (faceLandmarker == null) {
            imageProxy.close();
            return;
        }

        try {
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            Bitmap rawBitmap = imageProxyToBitmap(imageProxy);
            if (rawBitmap != null) {
                Bitmap rotatedBitmap = rotateBitmap(rawBitmap, rotationDegrees);
                Bitmap mirroredBitmap = mirrorBitmap(rotatedBitmap);

                MPImage mpImage = new BitmapImageBuilder(mirroredBitmap).build();
                FaceLandmarkerResult result = faceLandmarker.detect(mpImage);

                if (result != null && !result.faceLandmarks().isEmpty()) {
                    Log.d(TAG, "檢測到人臉，關鍵點數量: " + result.faceLandmarks().get(0).size());
                }
                checkFacePosition(result, mirroredBitmap.getWidth(), mirroredBitmap.getHeight(), mirroredBitmap);

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    rawBitmap.recycle();
                    if (rotatedBitmap != rawBitmap) rotatedBitmap.recycle();
                    // mirroredBitmap 交由 GC
                }, 100);
            }
        } catch (Exception e) {
            Log.e(TAG, "圖像分析錯誤", e);
        } finally {
            // ⭐ 每幀結束自增（統一幀節奏）
            frameId++;
            imageProxy.close();
        }
    }

    private Bitmap rotateBitmap(Bitmap original, int degrees) {
        if (degrees == 0) return original;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matrix, true);
    }

    private Bitmap mirrorBitmap(Bitmap original) {
        Matrix matrix = new Matrix();
        matrix.preScale(-1.0f, 1.0f);
        return Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matrix, false);
    }

    private Bitmap imageProxyToBitmap(@NonNull ImageProxy imageProxy) {
        try {
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);

            byte[] uvPixelBuffer = new byte[uSize + vSize];
            vBuffer.get(uvPixelBuffer, 0, vSize);
            uBuffer.get(uvPixelBuffer, vSize, uSize);

            int uvPixelCount = 0;
            for (int i = ySize; i < nv21.length; i += 2) {
                nv21[i] = uvPixelBuffer[uvPixelCount];
                nv21[i + 1] = uvPixelBuffer[uvPixelCount + 1];
                uvPixelCount += 2;
            }

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    imageProxy.getWidth(), imageProxy.getHeight(), null);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0,
                    imageProxy.getWidth(), imageProxy.getHeight()), 100, outputStream);
            byte[] imageBytes = outputStream.toByteArray();

            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        } catch (Exception e) {
            Log.e(TAG, "ImageProxy轉換錯誤", e);
            return null;
        }
    }

    // 加入 YOLO 整合
    private void checkFacePosition(FaceLandmarkerResult result, int bitmapWidth, int bitmapHeight, Bitmap mirroredBitmap) {
        boolean faceDetected = result != null && !result.faceLandmarks().isEmpty();

        if (faceDetected) {
            try {
                runOnUiThread(() -> {
                    int overlayWidth = overlayView.getWidth();
                    int overlayHeight = overlayView.getHeight();

                    if (overlayWidth > 0 && overlayHeight > 0) {
                        float inputAspect = 480f / 640f; // Bitmap 寬高比
                        float viewAspect = overlayWidth / (float) overlayHeight; // Overlay 寬高比
                        float scaleX = inputAspect / viewAspect;

                        int landmarkCount = result.faceLandmarks().get(0).size();
                        float[][] allPoints = new float[landmarkCount][2];

                        for (int i = 0; i < landmarkCount; i++) {
                            float x = result.faceLandmarks().get(0).get(i).x();
                            float y = result.faceLandmarks().get(0).get(i).y();
                            x = (x - 0.5f) * scaleX + 0.5f;  // X 軸比例補償（只影響畫面顯示）
                            allPoints[i][0] = x * overlayWidth;
                            allPoints[i][1] = y * overlayHeight;
                        }

                        if ("舌頭".equals(trainingLabel) && isYoloEnabled) {
                            // ★ 每 FACE_MESH_EVERY 幀更新一次 ROI（Overlay→Bitmap）
                            boolean needFaceMesh = (lastOverlayRoi == null) || (frameId % FACE_MESH_EVERY == 0);
                            if (needFaceMesh) {
                                Rect overlayRoi = TongueYoloDetector.calculateMouthROI(allPoints, overlayWidth, overlayHeight);
                                lastOverlayRoi = overlayRoi;

                                float sx = (float) mirroredBitmap.getWidth()  / overlayWidth;
                                float sy = (float) mirroredBitmap.getHeight() / overlayHeight;
                                lastBitmapRoi = new Rect(
                                        Math.round(overlayRoi.left   * sx),
                                        Math.round(overlayRoi.top    * sy),
                                        Math.round(overlayRoi.right  * sx),
                                        Math.round(overlayRoi.bottom * sy)
                                );
                            }

                            // 把快取 ROI 傳給 YOLO（不一定每幀更新 ROI）
                            handleTongueMode(allPoints, mirroredBitmap, bitmapWidth, bitmapHeight,
                                    lastOverlayRoi, lastBitmapRoi);

                        } else {
                            // 嘴唇模式
                            handleLipMode(allPoints);
                        }

                        // 鼻尖 for 圓框狀態（顯示層用）
                        float noseRelativeX = result.faceLandmarks().get(0).get(1).x();
                        float noseRelativeY = result.faceLandmarks().get(0).get(1).y();
                        float noseCorrectedX = (noseRelativeX - 0.5f) * scaleX + 0.5f;

                        float noseScreenX = noseCorrectedX * overlayWidth;
                        float noseScreenY = noseRelativeY * overlayHeight;

                        float centerX = overlayWidth / 2f;
                        float centerY = overlayHeight / 2f;
                        float radius = Math.min(centerX, centerY) - 80;

                        float dx = noseScreenX - centerX;
                        float dy = noseScreenY - centerY;
                        boolean noseInside = (dx * dx + dy * dy) <= (radius * radius);

                        handleFacePosition(noseInside);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "檢查臉部位置時發生錯誤", e);
                runOnUiThread(() -> handleFacePosition(false));
            }
        } else {
            runOnUiThread(() -> {
                overlayView.clearAllLandmarks();
                overlayView.clearYoloResults();
                handleFacePosition(false);
                Log.d(TAG, "未檢測到人臉");
            });
        }
    }

    /**
     * 舌頭模式：用快取好的 ROI + 節流 YOLO
     */
    private void handleTongueMode(float[][] allPoints,
                                  Bitmap mirroredBitmap,
                                  int bitmapWidth,
                                  int bitmapHeight,
                                  Rect overlayRoi,   // ← 使用快取 Overlay ROI
                                  Rect bitmapRoi) {  // ← 使用快取 Bitmap ROI
        try {
            // ★ 每 YOLO_EVERY 幀跑一次 YOLO
            if ((frameId % YOLO_EVERY) != 0) return;
            if (overlayRoi == null || bitmapRoi == null) return;

            int overlayWidth = overlayView.getWidth();
            int overlayHeight = overlayView.getHeight();

            final Rect mouthROIFinal = new Rect(overlayRoi);
            final float[][] allPointsFinal = allPoints;
            final Rect bitmapROIFinal = new Rect(bitmapRoi);

            cameraExecutor.execute(() -> {
                long t0 = System.nanoTime();
                TongueYoloDetector.DetectionResult result =
                        tongueDetector.detectTongueWithRealPosition(
                                mirroredBitmap, bitmapROIFinal, overlayWidth, overlayHeight);
                long t1 = System.nanoTime();
                float inferMs = (t1 - t0) / 1_000_000f;

                Rect viewTongueBox = null;
                if (result.detected && result.boundingBox != null) {
                    float sx = overlayWidth  / (float) mirroredBitmap.getWidth();
                    float sy = overlayHeight / (float) mirroredBitmap.getHeight();
                    Rect b = result.boundingBox;
                    viewTongueBox = new Rect(
                            Math.round(b.left   * sx),
                            Math.round(b.top    * sy),
                            Math.round(b.right  * sx),
                            Math.round(b.bottom * sy)
                    );
                }

                // 每 10 秒打一行 METRICS
                String thermalStr = "N/A";
                try {
                    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                    if (pm != null) {
                        int ts = pm.getCurrentThermalStatus();
                        switch (ts) {
                            case android.os.PowerManager.THERMAL_STATUS_NONE:      thermalStr = "NONE"; break;
                            case android.os.PowerManager.THERMAL_STATUS_LIGHT:     thermalStr = "LIGHT"; break;
                            case android.os.PowerManager.THERMAL_STATUS_MODERATE:  thermalStr = "MODERATE"; break;
                            case android.os.PowerManager.THERMAL_STATUS_SEVERE:    thermalStr = "SEVERE"; break;
                            case android.os.PowerManager.THERMAL_STATUS_CRITICAL:  thermalStr = "CRITICAL"; break;
                            case android.os.PowerManager.THERMAL_STATUS_EMERGENCY: thermalStr = "EMERGENCY"; break;
                            case android.os.PowerManager.THERMAL_STATUS_SHUTDOWN:  thermalStr = "SHUTDOWN"; break;
                            default: thermalStr = String.valueOf(ts);
                        }
                    }
                } catch (Throwable ignore) {}

                long now = System.currentTimeMillis();
                if (firstMetricTime == 0) firstMetricTime = now;
                long elapsed = (now - firstMetricTime) / 1000;
                if (elapsed == 10 || elapsed == 20 || elapsed == 30 || elapsed == 40) {
                    Log.d(TAG, String.format("METRICS@%ds infer=%.1fms bestProb=%.3f thermal=%s",
                            elapsed, inferMs, result.confidence, thermalStr));
                }

                Rect finalViewTongueBox = viewTongueBox;
                final boolean detected = result.detected;
                final float conf = result.confidence;

                mainHandler.post(() -> {
                    overlayView.setYoloDetectionResult(detected, conf, finalViewTongueBox, mouthROIFinal);
                    if (!isTrainingCompleted && (currentState == AppState.CALIBRATING || currentState == AppState.MAINTAINING)) {
                        String stateString = (currentState == AppState.CALIBRATING) ? "CALIBRATING" : "MAINTAINING";
                        dataRecorder.recordLandmarkData(stateString, allPointsFinal, detected);
                    }
                });
            });

        } catch (Exception e) {
            Log.e(TAG, "處理舌頭模式時發生錯誤", e);
            if (!isTrainingCompleted && (currentState == AppState.CALIBRATING || currentState == AppState.MAINTAINING)) {
                String stateString = (currentState == AppState.CALIBRATING) ? "CALIBRATING" : "MAINTAINING";
                dataRecorder.recordLandmarkData(stateString, allPoints, false);
            }
        }
    }

    // 嘴唇模式：MediaPipe 關鍵點
    private void handleLipMode(float[][] allPoints) {
        overlayView.setAllFaceLandmarks(allPoints);

        if (!isTrainingCompleted && (currentState == AppState.CALIBRATING || currentState == AppState.MAINTAINING)) {
            String stateString = (currentState == AppState.CALIBRATING) ? "CALIBRATING" : "MAINTAINING";
            dataRecorder.recordLandmarkData(stateString, allPoints, null);
            Log.d(TAG, "記錄嘴唇資料: " + stateString + ", 關鍵點數量: " + allPoints.length);
        }
    }

    private void handleFacePosition(boolean faceInside) {
        if (isTrainingCompleted) return;

        long currentTime = System.currentTimeMillis();

        switch (currentState) {
            case CALIBRATING:
                if (faceInside) {
                    if (calibrationStartTime == 0) {
                        calibrationStartTime = currentTime;
                        startCalibrationTimer();
                    }
                    overlayView.setStatus(CircleOverlayView.Status.CALIBRATING);
                } else {
                    resetCalibration();
                    overlayView.setStatus(CircleOverlayView.Status.OUT_OF_BOUND);
                    currentState = AppState.OUT_OF_BOUNDS;
                }
                break;

            case MAINTAINING:
                if (faceInside) {
                    if (maintainStartTime == 0) {
                        maintainStartTime = currentTime;
                    }
                    overlayView.setStatus(CircleOverlayView.Status.OK);
                } else {
                    if (maintainStartTime > 0) {
                        maintainTotalTime += (currentTime - maintainStartTime);
                        maintainStartTime = 0;
                    }
                    resetToCalibration();
                }
                break;

            case OUT_OF_BOUNDS:
                if (faceInside) {
                    resetToCalibration();
                } else {
                    overlayView.setStatus(CircleOverlayView.Status.OUT_OF_BOUND);
                }
                break;
        }

        updateStatusDisplay();
        updateTimerDisplay();
    }

    private void startCalibrationTimer() {
        cancelTimers();
        Log.d(TAG, "🟡 開始校正階段計時器");

        calibrationTimer = () -> {
            Log.d(TAG, "🟡 校正完成，切換到維持狀態");
            currentState = AppState.MAINTAINING;
            maintainStartTime = System.currentTimeMillis();
            overlayView.setStatus(CircleOverlayView.Status.OK);
            startMaintainTimer();
            updateStatusDisplay();
            updateTimerDisplay();
        };
        mainHandler.postDelayed(calibrationTimer, CALIBRATION_TIME);
    }

    private void startMaintainTimer() {
        cancelTimers();
        Log.d(TAG, "🟢 開始維持階段計時器");

        maintainTimer = () -> {
            long currentTime = System.currentTimeMillis();
            long currentMaintainTime = maintainTotalTime;
            if (maintainStartTime > 0) {
                currentMaintainTime += (currentTime - maintainStartTime);
            }

            if (currentMaintainTime % 5000 < 100) {
                Log.d(TAG, String.format("⏱️ 維持計時檢查 - 累計時間: %d ms / %d ms (%.1f%%)",
                        currentMaintainTime, MAINTAIN_TIME_TOTAL,
                        (currentMaintainTime * 100.0 / MAINTAIN_TIME_TOTAL)));
            }

            if (currentMaintainTime >= MAINTAIN_TIME_TOTAL) {
                Log.d(TAG, "✅ 維持時間達標！訓練完成");
                completedTraining();
            } else {
                mainHandler.postDelayed(maintainTimer, 100);
            }
        };
        mainHandler.postDelayed(maintainTimer, 100);
    }

    private void startProgressUpdater() {
        progressUpdater = () -> {
            updateProgressBar();
            mainHandler.postDelayed(progressUpdater, PROGRESS_UPDATE_INTERVAL);
        };
        mainHandler.post(progressUpdater);
    }

    private void updateProgressBar() {
        if (isTrainingCompleted) {
            progressBar.setProgress(100);
            return;
        }

        int progress = 0;
        long currentTime = System.currentTimeMillis();

        switch (currentState) {
            case CALIBRATING:
                if (calibrationStartTime > 0) {
                    long elapsed = currentTime - calibrationStartTime;
                    progress = (int) ((elapsed * 100) / CALIBRATION_TIME);
                    progress = Math.min(progress, 100);
                }
                break;

            case MAINTAINING:
                long totalMaintainTime = maintainTotalTime;
                if (maintainStartTime > 0) {
                    totalMaintainTime += (currentTime - maintainStartTime);
                }
                progress = (int) ((totalMaintainTime * 100) / MAINTAIN_TIME_TOTAL);
                progress = Math.min(progress, 100);
                break;

            case OUT_OF_BOUNDS:
                break;
        }

        progressBar.setProgress(progress);
    }

    private void resetCalibration() {
        if (!isTrainingCompleted) {
            calibrationStartTime = 0;
            cancelTimers();
            currentState = AppState.CALIBRATING;
        }
    }

    private void resetToCalibration() {
        if (!isTrainingCompleted) {
            calibrationStartTime = 0;
            maintainStartTime = 0;
            cancelTimers();
            currentState = AppState.CALIBRATING;
        }
    }

    private void cancelTimers() {
        if (calibrationTimer != null) {
            mainHandler.removeCallbacks(calibrationTimer);
            calibrationTimer = null;
        }
        if (maintainTimer != null) {
            mainHandler.removeCallbacks(maintainTimer);
            maintainTimer = null;
        }
    }

    private void completedTraining() {
        Log.d(TAG, "🎉🎉🎉 === 訓練完成！開始儲存資料 === 🎉🎉🎉");
        isTrainingCompleted = true;
        cancelTimers();

        overlayView.setStatus(CircleOverlayView.Status.OK);
        updateStatusDisplay();
        updateTimerDisplay();

        Toast.makeText(this, "🎉 訓練完成！\n正在儲存檔案並進行峰值分析...", Toast.LENGTH_LONG).show();

        dataRecorder.saveToFileWithCallback(new FaceDataRecorder.DataSaveCallback() {
            @Override
            public void onComplete(CSVPeakAnalyzer.AnalysisResult result) {
                Log.d(TAG, "✅ 儲存與分析完成，準備跳轉結果頁面");
                Log.d(TAG, String.format("📊 分析結果 - 總峰值: %d", result.totalPeaks));
                Intent intent = new Intent(FaceCircleCheckerActivity.this, AnalysisResultActivity.class);
                intent.putExtra("training_label", trainingLabel);
                intent.putExtra("actual_count", result.totalPeaks);
                intent.putExtra("target_count", 4);
                intent.putExtra("training_duration", MAINTAIN_TIME_TOTAL / 1000);
                intent.putExtra("csv_file_name", dataRecorder.getFileName());
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ 儲存或分析失敗: " + error);
                Toast.makeText(FaceCircleCheckerActivity.this, "處理失敗: " + error, Toast.LENGTH_LONG).show();
                new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 3000);
            }
        });
    }

    private void updateStatusDisplay() {
        if (statusText == null) return;

        String text;
        if (isTrainingCompleted) {
            text = "✅ 訓練完成！\n正在進行峰值分析...";
        } else {
            switch (currentState) {
                case CALIBRATING:
                    text = "校正中\n請正對鏡頭，並保持鼻尖在圓框內5秒";
                    break;
                case MAINTAINING:
                    text = trainingLabel + "\n請維持30秒";
                    break;
                case OUT_OF_BOUNDS:
                default:
                    text = "偵測到臉部超出區域\n請讓鼻尖回到圓框內，重新校正";
                    break;
            }
        }
        statusText.setText(text);
    }

    private void updateTimerDisplay() {
        if (timerText == null) return;

        if (isTrainingCompleted) {
            timerText.setText("✅ 完成");
            return;
        }

        long currentTime = System.currentTimeMillis();
        String timeText;

        switch (currentState) {
            case CALIBRATING:
                if (calibrationStartTime > 0) {
                    long elapsed = currentTime - calibrationStartTime;
                    long remaining = Math.max(0, CALIBRATION_TIME - elapsed);
                    timeText = String.format("⏱ %d秒", (remaining / 1000) + 1);
                } else {
                    timeText = "⏱ 5秒";
                }
                break;

            case MAINTAINING:
                long totalMaintainTime = maintainTotalTime;
                if (maintainStartTime > 0) {
                    totalMaintainTime += (currentTime - maintainStartTime);
                }
                long remaining = Math.max(0, MAINTAIN_TIME_TOTAL - totalMaintainTime);
                timeText = String.format("⏱ %d秒", remaining / 1000);
                break;

            case OUT_OF_BOUNDS:
            default:
                timeText = "⏱ --";
                break;
        }

        timerText.setText(timeText);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimers();
        if (progressUpdater != null) {
            mainHandler.removeCallbacks(progressUpdater);
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (faceLandmarker != null) {
            faceLandmarker.close();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (tongueDetector != null) {
            tongueDetector.release();
            tongueDetector = null;
            Log.d(TAG, "✅ YOLO 檢測器資源已清理");
        }
        if (dataRecorder != null) {
            dataRecorder.clearData();
        }
    }

    private void testCameraPermission() {
        Log.d(TAG, "開始檢查相機權限");
        int cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        Log.d(TAG, "相機權限狀態: " + cameraPermission);
        Log.d(TAG, "PERMISSION_GRANTED 常數: " + PackageManager.PERMISSION_GRANTED);
        Log.d(TAG, "PERMISSION_DENIED 常數: " + PackageManager.PERMISSION_DENIED);

        PackageManager pm = getPackageManager();
        boolean hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
        boolean hasFrontCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);

        Log.d(TAG, "系統支援相機: " + hasCamera);
        Log.d(TAG, "系統支援前置相機: " + hasFrontCamera);
    }
}
