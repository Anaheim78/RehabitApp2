package com.example.rehabilitationapp.ui.facecheck;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PointF;
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

    //相機權限用
    private static final int PERMISSION_REQUEST_CODE = 123;
    //LOG的Tag
    private static final String TAG = "FaceCircleChecker";

    // 計時用的目標常數(多久到點)，所以不會改
    private static final int CALIBRATION_TIME = 5000; // 5秒校正時間
    private static final int MAINTAIN_TIME_TOTAL = 15000; // 總共30秒維持時間
    private static final int PROGRESS_UPDATE_INTERVAL = 50; // 進度條更新間隔 (毫秒)

    //android.camera.core等開源套件裡面的東西
    private PreviewView cameraView;
    private FaceLandmarker faceLandmarker;
    private ProcessCameraProvider cameraProvider;

    // ExecutorService 執行緒管理工具
    private ExecutorService cameraExecutor;

    //UI用變數
    private CircleOverlayView overlayView;
    private TextView statusText;
    private TextView timerText; // 倒數計時顯示
    private ProgressBar progressBar; // 進度條


    // 變數 : 接收 訓練的動作類型
    private String trainingLabel = "訓練"; // 預設值
    private int trainingType = -1;

    // 🔥 資料記錄器，方法會紀錄landmark到dataLines，算動作指標到dataLines，dataLines存csv
    private FaceDataRecorder dataRecorder;

    // 狀態管理
    private enum AppState {
        CALIBRATING,    // 黃色 - 校正中
        MAINTAINING,    // 綠色 - 維持狀態
        OUT_OF_BOUNDS   // 紅色 - 超出範圍
    }

    private AppState currentState = AppState.CALIBRATING;

    //mainHandler.Looper.getMaininLoop()，說是主執行緒才可改畫面，其他分支Thread做好了要回傳給Handler要他改
    private Handler mainHandler;
    //runable 是用來開新執行緒，裡面可以裝lamda，lamda就是把一套可跑程式當變數存起來，丟給mainHandler執行
    private Runnable calibrationTimer;
    private Runnable maintainTimer;
    private Runnable progressUpdater;

    //紀錄時間
    private long calibrationStartTime = 0;
    private long maintainStartTime = 0;
    private long maintainTotalTime = 0; // 累計維持時間
    private boolean isTrainingCompleted = false; // 🔥 新增：標記訓練是否完成

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //綁定layout
        setContentView(R.layout.activity_face_circle_checker);

        //獲取從前一個頁面傳過來的資料
        trainingType = getIntent().getIntExtra("training_type", -1);
        trainingLabel = getIntent().getStringExtra("training_label");
        if (trainingLabel == null) {
            trainingLabel = "訓練"; // 預設值
        }

        Log.d(TAG, "接收到訓練類型: " + trainingType + ", 標籤: " + trainingLabel);

        // 🔥 初始化資料記錄器
        dataRecorder = new FaceDataRecorder(this, trainingLabel, trainingType);
        Log.d(TAG, "資料記錄器初始化完成");
        //把LAYOUT控件 物件化
        cameraView = findViewById(R.id.camera_view);
        overlayView = findViewById(R.id.overlay_view);
        statusText = findViewById(R.id.status_text);
        timerText = findViewById(R.id.timer_text);
        progressBar = findViewById(R.id.progress_bar);
        // 一個新的可反覆利用的子執行緒
        cameraExecutor = Executors.newSingleThreadExecutor();
        // 主執行緒 : 改UI用的
        mainHandler = new Handler(Looper.getMainLooper());

        Log.d("FaceCircleAct","into onCreate");
        //第三方套件的前置作業
        testCameraPermission();
        setupFaceLandmarker();

        // 初始化UI
        initializeUI();

        if (checkCameraPermission()) {
            Log.d("FaceCircleAct"," onCreate : into checkCameraPermission : YES");
            startCamera();
        } else {
            Log.d("FaceCircleAct"," onCreate : into checkCameraPermission : NO");
            requestCameraPermission();
        }
    }

    private void initializeUI() {
        // 設置進度條
        progressBar.setMax(100);
        progressBar.setProgress(0);

        // 初始化狀態
        //更新和狀態有關的"提示文字"
        updateStatusDisplay();
        //更新和狀態有關的"時間"
        updateTimerDisplay();
        //更新進度條，它直接給主執行緒定期跑
        startProgressUpdater();
    }
    /*
    * 初始化Landmark模型，還沒有推論座標
    * */
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
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
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

    // 🔥 修復後的 analyzeImage 方法 - 添加旋轉處理
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (faceLandmarker == null) {
            imageProxy.close();
            return;
        }

        try {
            // 🔥 關鍵：獲取圖像旋轉角度
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            //Log.d(TAG, "圖像旋轉角度: " + rotationDegrees + "度");
            //Log.d(TAG, "ImageProxy尺寸: " + imageProxy.getWidth() + "x" + imageProxy.getHeight());

            Bitmap rawBitmap = imageProxyToBitmap(imageProxy);
            if (rawBitmap != null) {
                //Log.d(TAG, "Raw Bitmap尺寸: " + rawBitmap.getWidth() + "x" + rawBitmap.getHeight());

                // 🔥 步驟1：先旋轉
                Bitmap rotatedBitmap = rotateBitmap(rawBitmap, rotationDegrees);
                //Log.d(TAG, "Rotated Bitmap尺寸: " + rotatedBitmap.getWidth() + "x" + rotatedBitmap.getHeight());

                // 🔥 步驟2：再鏡像翻轉
                Bitmap mirroredBitmap = mirrorBitmap(rotatedBitmap);
                //Log.d(TAG, "Final Bitmap尺寸: " + mirroredBitmap.getWidth() + "x" + mirroredBitmap.getHeight());

                MPImage mpImage = new BitmapImageBuilder(mirroredBitmap).build();
                FaceLandmarkerResult result = faceLandmarker.detect(mpImage);

                // 調試信息
                if (result != null && !result.faceLandmarks().isEmpty()) {
                    Log.d(TAG, "檢測到人臉，關鍵點數量: " + result.faceLandmarks().get(0).size());
                }
                //**判斷臉部位置
                checkFacePosition(result, mirroredBitmap.getWidth(), mirroredBitmap.getHeight());

                // 清理記憶體
                rawBitmap.recycle();
                if (rotatedBitmap != rawBitmap) rotatedBitmap.recycle();
                mirroredBitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "圖像分析錯誤", e);
        } finally {
            imageProxy.close();
        }
    }

    // 🔥 新增：旋轉Bitmap的方法
    private Bitmap rotateBitmap(Bitmap original, int degrees) {
        if (degrees == 0) {
            return original; // 不需要旋轉，直接返回原圖
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), matrix, true);
    }

    // 🔥 新增：鏡像翻轉方法
    private Bitmap mirrorBitmap(Bitmap original) {
        Matrix matrix = new Matrix();
        matrix.preScale(-1.0f, 1.0f); // 水平翻轉
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

    // 🔥 重新設計的 checkFacePosition 方法 - 改回鼻尖檢測
    private void checkFacePosition(FaceLandmarkerResult result, int bitmapWidth, int bitmapHeight) {
        boolean faceDetected = result != null && !result.faceLandmarks().isEmpty();

        if (faceDetected) {
            try {
                runOnUiThread(() -> {
                    int overlayWidth = overlayView.getWidth();
                    int overlayHeight = overlayView.getHeight();

                    //Log.d(TAG, "OverlayView尺寸: " + overlayWidth + "x" + overlayHeight);
                    //Log.d(TAG, "檢測到人臉，關鍵點數量: " + result.faceLandmarks().get(0).size());

                    if (overlayWidth > 0 && overlayHeight > 0) {
                        // 🔥 加上比例補償，修復臉部變窄問題
                        float inputAspect = 480f / 640f; // Bitmap 寬高比
                        float viewAspect = overlayWidth / (float) overlayHeight; // Overlay 寬高比
                        float scaleX = inputAspect / viewAspect;

                        //Log.d(TAG, "輸入圖像比例: " + inputAspect);
                        //Log.d(TAG, "顯示視圖比例: " + viewAspect);
                        //Log.d(TAG, "X軸補償係數: " + scaleX);

                        int landmarkCount = result.faceLandmarks().get(0).size();
                        float[][] allPoints = new float[landmarkCount][2];

                        for (int i = 0; i < landmarkCount; i++) {
                            float x = result.faceLandmarks().get(0).get(i).x();
                            float y = result.faceLandmarks().get(0).get(i).y();

                            // ⭐ 關鍵：中心對齊後補償 X 軸壓縮
                            x = (x - 0.5f) * scaleX + 0.5f;

                            allPoints[i][0] = x * overlayWidth;
                            allPoints[i][1] = y * overlayHeight;
                        }

                        // 設置所有關鍵點到 overlayView 顯示
                        overlayView.setAllFaceLandmarks(allPoints);

                        // 🔥 記錄關鍵點資料 (只在校正和維持狀態時記錄)
                        if (!isTrainingCompleted && (currentState == AppState.CALIBRATING || currentState == AppState.MAINTAINING)) {
                            String stateString = (currentState == AppState.CALIBRATING) ? "CALIBRATING" : "MAINTAINING";
                            dataRecorder.recordLandmarkData(stateString, allPoints);
                            Log.d(TAG, "記錄資料: " + stateString + ", 關鍵點數量: " + allPoints.length);
                        }

                        // 🔥 計算鼻尖坐標（也要應用相同的比例補償）
                        float noseRelativeX = result.faceLandmarks().get(0).get(1).x();
                        float noseRelativeY = result.faceLandmarks().get(0).get(1).y();

                        // 應用X軸比例補償
                        float noseCorrectedX = (noseRelativeX - 0.5f) * scaleX + 0.5f;

                        float noseScreenX = noseCorrectedX * overlayWidth;
                        float noseScreenY = noseRelativeY * overlayHeight;

                        //Log.d(TAG, "鼻尖原始X: " + noseRelativeX + " → 補償後X: " + noseCorrectedX);
                        //Log.d(TAG, "鼻尖屏幕坐標: (" + noseScreenX + ", " + noseScreenY + ")");

                        // 🔥 計算圓圈的中心和半徑
                        float centerX = overlayWidth / 2f;
                        float centerY = overlayHeight / 2f;
                        float radius = Math.min(centerX, centerY) - 80;

                        // 計算鼻尖到圓心的距離
                        float dx = noseScreenX - centerX;
                        float dy = noseScreenY - centerY;
                        float distance = (float) Math.sqrt(dx * dx + dy * dy);

                        //Log.d(TAG, "圓心: (" + centerX + ", " + centerY + ")");
                        //Log.d(TAG, "半徑: " + radius);
                        //Log.d(TAG, "鼻尖到圓心距離: " + distance);

                        // 🔥 判斷鼻尖是否在圓圈內
                        boolean noseInside = distance <= radius;
                        //Log.d(TAG, "鼻尖在圓內: " + noseInside);

                        // 調用處理邏輯
                        handleFacePosition(noseInside);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "檢查臉部位置時發生錯誤", e);
                runOnUiThread(() -> handleFacePosition(false));
            }
        } else {
            // 沒有檢測到人臉
            runOnUiThread(() -> {
                overlayView.clearAllLandmarks();
                handleFacePosition(false);
                Log.d(TAG, "未檢測到人臉");
            });
        }
    }

    private void handleFacePosition(boolean faceInside) {
        // 🔥 如果訓練已完成，不再處理位置變化
        if (isTrainingCompleted) {
            return;
        }

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
                    // 記錄已維持的時間
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
    /*
    * 開始校正的方法
    * 看起來他只有跑通知完成的CODE，沒做別的
    * */
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
        mainHandler.postDelayed(calibrationTimer, CALIBRATION_TIME);//隔CALIBRATION_TIME秒後執行。
    }

    private void startMaintainTimer() {
        cancelTimers();
        Log.d(TAG, "🟢 開始維持階段計時器");

        maintainTimer = () -> {
            // 檢查總維持時間是否達到30秒
            long currentTime = System.currentTimeMillis();
            long currentMaintainTime = maintainTotalTime;
            if (maintainStartTime > 0) {
                currentMaintainTime += (currentTime - maintainStartTime);
            }

            // 🔥 增加除錯訊息
            if (currentMaintainTime % 5000 < 100) { // 每5秒顯示一次
                Log.d(TAG, String.format("⏱️ 維持計時檢查 - 累計時間: %d ms / %d ms (%.1f%%)",
                        currentMaintainTime, MAINTAIN_TIME_TOTAL,
                        (currentMaintainTime * 100.0 / MAINTAIN_TIME_TOTAL)));
            }

            if (currentMaintainTime >= MAINTAIN_TIME_TOTAL) {
                Log.d(TAG, "✅ 維持時間達標！訓練完成");
                completedTraining();
            } else {
                // 繼續檢查
                mainHandler.postDelayed(maintainTimer, 100);
            }
        };
        mainHandler.postDelayed(maintainTimer, 100);
    }
    /*
    * 丟給主執行緒定期跑更新進度條
    * */
    private void startProgressUpdater() {
        progressUpdater = () -> {
            updateProgressBar();
            mainHandler.postDelayed(progressUpdater, PROGRESS_UPDATE_INTERVAL);
        };
        mainHandler.post(progressUpdater);
    }
    /**
     *中間進度條，各狀態顯示更新
     */
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
                // 保持當前進度
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

    // 🔥 修改：使用 callback 的訓練完成方法
    private void completedTraining() {
        Log.d(TAG, "🎉🎉🎉 === 訓練完成！開始儲存資料 === 🎉🎉🎉");

        // 標記訓練完成，停止記錄資料
        isTrainingCompleted = true;

        // 停止所有計時器
        cancelTimers();

        // 更新 UI
        overlayView.setStatus(CircleOverlayView.Status.OK);
        //用來更新會跟狀態變化呼應的【提示字】
        updateStatusDisplay();
        //用來更新跟狀態變化呼應的【時間】
        updateTimerDisplay();

        Toast.makeText(this, "🎉 訓練完成！\n正在儲存檔案並進行峰值分析...", Toast.LENGTH_LONG).show();

        // 🔥 使用 callback 版本的儲存方法
        dataRecorder.saveToFileWithCallback(new FaceDataRecorder.DataSaveCallback() {
            @Override
            public void onComplete(CSVPeakAnalyzer.AnalysisResult result) {
                Log.d(TAG, "✅ 儲存與分析完成，準備跳轉結果頁面");
                Log.d(TAG, String.format("📊 分析結果 - 總峰值: %d", result.totalPeaks));

                // 🔥 跳轉到結果頁面
                Intent intent = new Intent(FaceCircleCheckerActivity.this, AnalysisResultActivity.class);
                intent.putExtra("training_label", trainingLabel);
                intent.putExtra("actual_count", result.totalPeaks);  // 🔥 真正的峰值數量
                intent.putExtra("target_count", 4);  // 目標次數
                intent.putExtra("training_duration", MAINTAIN_TIME_TOTAL / 1000);  // 訓練時間（秒）
                intent.putExtra("csv_file_name", dataRecorder.getFileName());

                startActivity(intent);
                finish();  // 關閉當前頁面
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ 儲存或分析失敗: " + error);
                Toast.makeText(FaceCircleCheckerActivity.this, "處理失敗: " + error, Toast.LENGTH_LONG).show();

                // 🔥 發生錯誤時延遲關閉
                new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 3000);
            }
        });
    }
/*
* 用來更新會跟狀態變化呼應的提示文字
* */
    private void updateStatusDisplay() {
        if (statusText == null) return;

        String text = "";
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
                    text = "偵測到臉部超出區域\n請讓鼻尖回到圓框內，重新校正";
                    break;
            }
        }
        statusText.setText(text);
    }
/*
* 用來更新跟狀態變化呼應的時間顯示
* */
    private void updateTimerDisplay() {
        if (timerText == null) return;

        if (isTrainingCompleted) {
            timerText.setText("✅ 完成");
            return;
        }

        long currentTime = System.currentTimeMillis();
        String timeText = "";

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

        // 🔥 清理資料記錄器
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