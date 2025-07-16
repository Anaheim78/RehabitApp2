/*
package com.example.rehabilitationapp.ui.facecheck;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceCircleCheckerActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String TAG = "FaceCircleChecker";

    private PreviewView cameraView;
    private SurfaceView overlayView;
    private FaceLandmarker faceLandmarker;
    private Paint circlePaint;
    private float centerX, centerY, radius;
    private boolean faceInCircle = false;

    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_circle_checker);

        cameraView = findViewById(R.id.camera_view);
        overlayView = findViewById(R.id.overlay_view);

        cameraExecutor = Executors.newSingleThreadExecutor();

        setupOverlay();
        setupFaceLandmarker();

        // 檢查相機權限
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    private void setupOverlay() {
        circlePaint = new Paint();
        circlePaint.setColor(Color.GREEN);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(8f);
        circlePaint.setAntiAlias(true);

        overlayView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                // 延遲計算圓圈位置，確保 View 已經測量完成
                overlayView.post(() -> {
                    centerX = overlayView.getWidth() / 2f;
                    centerY = overlayView.getHeight() / 2f;
                    radius = Math.min(centerX, centerY) * 0.6f;
                    drawCircle();
                });
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                centerX = width / 2f;
                centerY = height / 2f;
                radius = Math.min(centerX, centerY) * 0.6f;
                drawCircle();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {}
        });
    }

    private void drawCircle() {
        Canvas canvas = overlayView.getHolder().lockCanvas();
        if (canvas != null) {
            canvas.drawColor(Color.TRANSPARENT);
            canvas.drawCircle(centerX, centerY, radius, circlePaint);
            overlayView.getHolder().unlockCanvasAndPost(canvas);
        }
    }

    private void setupFaceLandmarker() {
        try {
            FaceLandmarker.Options options = FaceLandmarker.Options.builder()
                    .setBaseOptions(FaceLandmarker.BaseOptions.builder()
                            .setModelAssetPath("face_landmarker.task")
                            .build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumFaces(1)
                    .build();

            faceLandmarker = FaceLandmarker.createFromOptions(this, options);
            Log.d(TAG, "FaceLandmarker 初始化成功");
        } catch (Exception e) {
            Toast.makeText(this, "無法初始化 FaceLandmarker: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "FaceLandmarker 初始化錯誤", e);
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
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
        // Preview 用例
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(cameraView.getSurfaceProvider());

        // ImageAnalysis 用例（用於人臉檢測）
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        // 選擇前置相機
        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

        try {
            // 解綁所有用例，然後綁定新的用例
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            Log.d(TAG, "相機綁定成功");
        } catch (Exception e) {
            Log.e(TAG, "相機綁定失敗", e);
            Toast.makeText(this, "相機啟動失敗", Toast.LENGTH_SHORT).show();
        }
    }

    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (faceLandmarker == null) {
            imageProxy.close();
            return;
        }

        try {
            // 將 ImageProxy 轉換為 Bitmap，然後轉換為 MPImage
            android.graphics.Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap != null) {
                MPImage mpImage = new BitmapImageBuilder(bitmap).build();
                FaceLandmarkerResult result = faceLandmarker.detect(mpImage);
                checkFacePosition(result);
            }
        } catch (Exception e) {
            Log.e(TAG, "圖像分析錯誤", e);
        } finally {
            imageProxy.close();
        }
    }

    private android.graphics.Bitmap imageProxyToBitmap(@NonNull ImageProxy imageProxy) {
        // 這裡需要實現 ImageProxy 到 Bitmap 的轉換
        // 由於這比較複雜，暫時返回 null
        // 實際實現需要處理不同的圖像格式（YUV_420_888 等）
        return null;
    }

    private void checkFacePosition(FaceLandmarkerResult result) {
        if (result == null || result.faceLandmarks().isEmpty()) {
            return;
        }

        try {
            // 獲取鼻尖位置（landmark index 1）
            PointF nose = new PointF(
                    result.faceLandmarks().get(0).get(1).x(),
                    result.faceLandmarks().get(0).get(1).y()
            );

            // 將相對座標轉換為螢幕座標
            float screenX = nose.x * overlayView.getWidth();
            float screenY = nose.y * overlayView.getHeight();

            // 計算與圓心的距離
            float dx = screenX - centerX;
            float dy = screenY - centerY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            boolean inside = distance <= radius;

            if (inside != faceInCircle) {
                faceInCircle = inside;
                runOnUiThread(() -> {
                    circlePaint.setColor(inside ? Color.GREEN : Color.RED);
                    drawCircle();

                    // 可以在這裡添加其他反饋，如震動或聲音
                    Log.d(TAG, "臉部" + (inside ? "在" : "不在") + "圓圈內");
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "檢查臉部位置時發生錯誤", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (faceLandmarker != null) {
            faceLandmarker.close();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}

 */