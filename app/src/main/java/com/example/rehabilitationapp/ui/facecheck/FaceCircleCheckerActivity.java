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
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Dialog;
import android.os.CountDownTimer;
import android.widget.VideoView;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
// 🎥 影片錄製功能
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.AppLogger;
import com.example.rehabilitationapp.data.SupabaseUploader;
import com.example.rehabilitationapp.ui.analysis.CSVMotioner;
import com.example.rehabilitationapp.ui.results.AnalysisResultActivity;
import com.example.rehabilitationapp.ui.analysis.CSVPeakAnalyzer;
import com.example.rehabilitationapp.ui.results.TrainingResultActivity;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.example.rehabilitationapp.data.model.User;
import com.example.rehabilitationapp.data.dao.UserDao;
import com.example.rehabilitationapp.data.dao.TrainingHistoryDao;
import com.example.rehabilitationapp.data.model.TrainingHistory;
import android.content.SharedPreferences;



//光流
import org.json.JSONArray;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;

/*排版
0. Debug
1. 生命週期
2. 相機
3. 動作處理
4. 計時器
5. UI 更新
6. 教學倒數
7. 導引提示
8. 影片錄製
9. 訓練完成
10. 工具方法
 */


//本物件WorkFlow可分為偵側流與顯示(時間讀秒)流
public class FaceCircleCheckerActivity extends AppCompatActivity {
    //【0. Debug】
    private static final String TAG = "FaceCircleChecker";
    private static final String TAG_2 = "FaceChecker_2";
    private static final String TAG_3 = "FaceCheck_video";

    private volatile boolean isStopping = false;
    //生命週期onDestroy時，會改為false
    //讓提交任務前都能守門，例如在 handleCheeksMode()，
    // 正在停止 → 方法不要接受新幀;
    // 沒在停止 → 方法可以接受新幀;
    //移動到下方
    private boolean shouldAcceptNewFrames() { return !isStopping; }
    //===================>

    //【1. 生命週期】
    //<=======【THREAD旗標】===============>
    //<=========【相機權限用】==========
    // 相機權限用


    //【2. 相機】
    private static final int PERMISSION_REQUEST_CODE = 123;
    // android.camera.core 類似請求id
    private PreviewView cameraView;
    // androidx.camera.view 接【相機影像畫面】的【螢幕預覽】物件
    private ProcessCameraProvider cameraProvider;
    // androidx.camera.lifecycle 管理相機的開關、綁定功能
    private ExecutorService cameraExecutor;
    // 拿去餵給接收器，設定imageAnalysis.setAnalyzer(執行緒, 分析方法)
    private Handler mainHandler;
    // 主執行續
    //===================>


    //<=========【8. 影片錄製】==========

    private boolean ENABLE_VIDEO_RECORDING = true;

    private VideoCapture<Recorder> videoCapture;
    //androidx.camera.video : videoCapture接收Recorder的影像
    private Recording currentRecording;
    //    androidx.camera.video  Recording 用來控制 影像播放相關操作
    //
    private String videoFilePath;
    //刪除影片會用到
    //<=================>





    //<=========【執行緒管理】==========


    // 主執行緒 handler 與計時任務


    //===============================>


    //<====【3. 動作處理】
    //<==========共用(包括舌頭)變數(9種動作)=======
    // 【訓練相關物件】=================
    private FaceDataRecorder dataRecorder;
    private FaceLandmarker faceLandmarker;

    private String trainingLabel = "訓練";
    private int trainingType = -1;
    public String trainingLabel_String;
    //訓練資訊變數(Intent接收），並且存入DB
    //====================================>

    //<========【舌頭專用變數】4種 ========
    //推論頻率控制（可自行調整）
    private static final int FACE_MESH_EVERY = 5;
    // 每 N 幀更新一次「嘴巴 ROI」
    private static final int YOLO_EVERY   = 1;
    // 每 N 幀跑一次 YOLO
    private long firstMetricTime = 0;
    // 用來間隔時間打印 LOG

    // 周邊物件
    private TongueYoloDetector tongueDetector;
    private TongueYoloDetectorLR tongueDetectorLR;
    private volatile boolean isYoloProcessing = false;  // 🔥 新增：YOLO 忙碌旗標
    // 若處理中isYoloProcessing會阻擋新資料進入，因此不會30FPS全部處理
    // 會看~15-20 fps 實際處理

    private boolean isYoloEnabled = false;
    // 確認YOLO初始化了沒有
    private ExecutorService yoloExecutor;
    //獨立執行緒，專門跑舌頭檢測
    private Rect lastOverlayRoi = null;
    private Rect lastBitmapRoi  = null;
    // ROI快取給YOLO（Overlay/Bitmap 兩套座標系）
//    上一幀的嘴巴區域（螢幕座標）
//    上一幀的嘴巴區域（影像座標）




    //====================================>
    //=============>


    //<==【已棄用】====
    //臉頰光流
    private CheekFlowEngine cheekEngine;
    //=======>
    //================================>



    //<===========【4. 計時器】==============
    private static final int CALIBRATION_TIME = 11000;         // 校正總時間(毫秒)
    private static final int MAINTAIN_TIME_TOTAL = 24000;     // 維持總時間(毫秒)
    private static final int PROGRESS_UPDATE_INTERVAL = 50;   // 進度條更新間隔


    private static final int DEMO_PHASE_1 = 4000;       // DEMO開始ms
    private static final int DEMO_PHASE_2 = 8000;       // 4~8s：藍框


    // 計時變數
    private long calibrationStartTime = 0;
    //開始校正時間，拿來算經過多久
    private long maintainStartTime = 0;
    //代表訓練是什麼時候開始，用在
    //    開始訓練 → 記錄時間
    //    離開 → 歸零
    //    繼續訓練 → 重新記錄時間
    //    完成 → 存入 DB
    private long maintainTotalTime = 0;
    //紀錄從訓練開始到現在經過多久
    private boolean isTrainingCompleted = false;

    private int frameId = 0;
    // 紀錄當前幀數
    private Runnable calibrationTimer;
    //方法函數，在校症階段開始時埋好經過CALIBRATION_TIME後觸發，改狀態為"maintain"。
    private Runnable maintainTimer;
    //方法函數 : 循環檢查，每一百ms確認是否completedTraining
    private Runnable progressUpdater;
    //方法函數 : 設定間隔100ms呼叫updateProgressBar更新進度條
    //============================>


    //<===========【5. UI 更新】

    //<=========【UX顯示區塊相關】====
    //臉部圓框
    private CircleOverlayView overlayView;
    //圓框控制器

    private TextView statusText;
    //已廢棄statusText，校正時期就隱藏了，後續沒再開回來之後拿掉舊邏輯
    private TextView timerText;
    //進度條圖示
    private ProgressBar progressBar;

    //圓框狀態管理
    private enum AppState { DEMO,CALIBRATING, MAINTAINING, OUT_OF_BOUNDS }
    private AppState currentState = AppState.CALIBRATING;
    private boolean isDemoPhase = false;

    //新增籃框
    // --- 校正示範（藍色）用的旗標，只在 CALIBRATING 內生效 ---
    private final boolean demoEnabled  = true;   // 要不要跑示範（固定 true）
    private boolean demoStarted  = false;  // 啟動DEMO的旗標
    private boolean demoFinished = false;  // 完成DEMO的旗標，沒什麼用
    private long demoStartMs     = 0L;     // 起始時間（ms）


    // 頭動檢測用
    private float baselineEyeDistance = 0;
    private boolean baselineSet = false;
    private static final float EYE_DISTANCE_THRESHOLD = 0.15f;  // 15% 變化閾值
    //======================================================>

    // 頭動偵測（重心位移法）
    private static final int[] HEAD_STABLE_INDICES = {10, 151, 9, 168, 1, 127, 356};
    private static final int HEAD_STABLE_POINTS_COUNT = HEAD_STABLE_INDICES.length;
    private static final int HEAD_MOTION_WINDOW = 5;
    private static final float HEAD_MOTION_THRESHOLD = 0.003f;
    private float[][] prevStablePoints = null;
    private final java.util.ArrayDeque<Float> headMotionHistory = new java.util.ArrayDeque<>();
    private boolean lastHeadStable = true;
    private int headStableCooldown = 0;



    //===========【UX顯示區塊相關】===============>

    //【6. 教學倒數】
    // 🆕【教學與倒數相關】
    private boolean tutorialShown = false;      // 是否已顯示過教學
    private boolean countdownFinished = false;  // 倒數是否完成
    //守門員，雖然初始化State是校正，但倒數沒完成前，handleFacePosition什麼都不做


    // =======【動作導引文字提示】==【7. 導引提示】========================
    private TextView cueText;
    // 新增：導引專用 TextView，statusText


    // 導引疊字與循環控制（不影響你原本 Handler/Timer）
    private android.os.Handler cueHandler;
    private java.lang.Runnable cueRunnable;
    private boolean cueRunning = false;
    private int cueStep = 0; // 循環計數器，根據除2餘數，顯示偶數=動作，奇數=放鬆
    public int CUE_SEGMENT_SEC = 4; // 可調：文字提示顯示幾秒（預設 4 秒）
    // ===============================================

    //================以下為方法=====================

    //===========01【生命週期】========================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppLogger.logTrainingStart(trainingLabel);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_circle_checker);

        // 🆕 讀取錄影開關設定
        SharedPreferences appSettings = getSharedPreferences("app_settings", MODE_PRIVATE);
        ENABLE_VIDEO_RECORDING = appSettings.getBoolean("video_recording_enabled", true);
        Log.d(TAG, "錄影功能: " + (ENABLE_VIDEO_RECORDING ? "開啟" : "關閉"));

        // OpenCV 初始化
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "❌ OpenCVLoader.initDebug() 失敗");
        } else {
            Log.d(TAG, "✅ OpenCV 初始化成功");
        }

        // 讀取前一頁選擇的運動類型
        trainingType  = getIntent().getIntExtra("training_type", -1);
        trainingLabel = getIntent().getStringExtra("training_type");
        // 替trainingLabel備份，不可編輯
        trainingLabel_String = getIntent().getStringExtra("training_type");

        if (trainingLabel == null) trainingLabel = "訓練";
        Log.d(TAG, "接收到訓練類型: " + trainingType + ", 標籤: " + trainingLabel);
        // 舌頭模式時 : 額外初始化Yolo偵測器
        if ("舌頭".equals(trainingLabel) ||
                "TONGUE_LEFT".equals(trainingLabel) ||
                "TONGUE_RIGHT".equals(trainingLabel) ||
                "TONGUE_FOWARD".equals(trainingLabel) ||
                "TONGUE_BACK".equals(trainingLabel) ||
                "TONGUE_UP".equals(trainingLabel) ||
                "TONGUE_DOWN".equals(trainingLabel)) {
            initializeTongueDetector();
            Log.d("Confirm what trainingLabel is: ", trainingLabel);
            Log.d(TAG, "✅ 舌頭模式：使用 MediaPipe 關鍵點顯示與啟用 YOLO 檢測 + YOLO 顯示");
        } else {
            Log.d(TAG, "✅ 非舌頭模式：使用 MediaPipe 關鍵點顯示");
        }

        // 初始化資料記錄器
        dataRecorder = new FaceDataRecorder(this, trainingLabel, trainingType);
        Log.d(TAG, "資料記錄器初始化完成");

        // 綁定UI控件
        cameraView  = findViewById(R.id.camera_view);
        overlayView = findViewById(R.id.overlay_view);
        statusText  = findViewById(R.id.status_text);
        timerText   = findViewById(R.id.timer_text);
        progressBar = findViewById(R.id.progress_bar);
        cueText     = findViewById(R.id.cue_text);


        // 初始化追蹤示意模式 : 舌頭顯示BBox，其他顯示Landmark
        if ("舌頭".equals(trainingLabel) ||
                "TONGUE_LEFT".equals(trainingLabel) ||
                "TONGUE_RIGHT".equals(trainingLabel) ||
                "TONGUE_FOWARD".equals(trainingLabel) ||
                "TONGUE_BACK".equals(trainingLabel) ||
                "TONGUE_UP".equals(trainingLabel) ||
                "TONGUE_DOWN".equals(trainingLabel)) {
            overlayView.setDisplayMode(CircleOverlayView.DisplayMode.YOLO_DETECTION);
        } else {
            overlayView.setDisplayMode(CircleOverlayView.DisplayMode.LANDMARKS);
        }

        // 執行緒與 Handler
        cameraExecutor = Executors.newSingleThreadExecutor();
        yoloExecutor   = Executors.newSingleThreadExecutor();
        mainHandler    = new Handler(Looper.getMainLooper());

        // /偵測/UI初始化
        testCameraPermission();
        setupFaceLandmarker();
        initializeUI();

        // 相機權限處理：有就打開，沒有就請求。
        if (checkCameraPermission()) {
            startCamera();
            // 🆕 顯示教學彈窗（相機啟動後）
            showTutorialDialog();
        } else {
            requestCameraPermission();
        }
        // 進來就顯示教學（BottomSheet） 註解掉，說明不在此_20250905
        //new Handler(Looper.getMainLooper()).post(this::maybeShowGuideAndStart);
    }

    @Override
    protected void onDestroy() {

        // 🎥 如果還在錄影且訓練未完成，刪除影片
        if (currentRecording != null) {
            currentRecording.stop();
            currentRecording = null;

            // 刪除未完成的影片
            if (videoFilePath != null && !isTrainingCompleted) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    File file = new File(videoFilePath);
                    if (file.exists() && file.delete()) {
                        Log.d(TAG, "🗑️ 已刪除未完成的影片");
                    }
                }, 500);
            }
        }

        stopSimpleCue();
        super.onDestroy();
        // 1) 停入口：之後不要再提交任何新任務
        isStopping = true;

        // 2) 先把 UI/Timer callback 停掉，避免又排新任務
        cancelTimers();
        if (progressUpdater != null) {
            mainHandler.removeCallbacks(progressUpdater);
            progressUpdater = null;
        }

        // 3) 先停相機資料源，避免還有新影格湧入（很關鍵）
        try {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }
        } catch (Exception ignore) { }

        // 4) 停掉背景執行緒並「等它停乾淨」
        awaitShutdown(cameraExecutor);
        awaitShutdown(yoloExecutor);

        // 5) 執行緒都停了，現在才安全釋放各引擎/偵測器
        if (cheekEngine != null) {
            try {
                cheekEngine.release();
            } catch (Throwable ignore) { }
            cheekEngine = null;
        }

        if (tongueDetector != null) {
            try {
                tongueDetector.release();
            } catch (Throwable ignore) { }
            tongueDetector = null;
            Log.d(TAG, "✅ YOLO 檢測器資源已清理");
        }

        if (tongueDetectorLR != null) {
            try {
                tongueDetectorLR.release();
            } catch (Throwable ignore) { }
            tongueDetectorLR = null;
            Log.d(TAG, "✅ YOLO 檢測器資源已清理");
        }

        if (faceLandmarker != null) {
            try {
                faceLandmarker.close();
            } catch (Throwable ignore) { }
            faceLandmarker = null;
        }

        // 6) 千萬不要在 onDestroy 清 CSV，否則結果頁會拿到空資料
        // if (dataRecorder != null) { dataRecorder.clearData(); }  // ← 移除這行
    }
    //===========01【生命週期】========================



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

        try {
            tongueDetectorLR = new TongueYoloDetectorLR(this);

            isYoloEnabled = tongueDetectorLR.isInitialized();
            if (!isYoloEnabled) {
                Log.e(TAG, "❌ LR舌頭檢測器初始化失敗");
                Toast.makeText(this, "LR舌頭檢測器初始化失敗，將使用一般模式", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ LR舌頭檢測器初始化錯誤: " + e.getMessage());
            isYoloEnabled = false;
            Toast.makeText(this, "LR舌頭檢測器載入失敗：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeUI() {
        progressBar.setMax(100);
        progressBar.setProgress(0);
        updateStatusDisplay();
        //沒再用的文字導引更新，校正就隱藏了
        updateTimerDisplay();
        //時間倒數
        startProgressUpdater();
        //進圖條更新
    }
    //  自我檢查相機權限
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

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }
    //請求User給相機權限
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        Log.d("FaceCircleCheckerActivity","in to requestCameraPermission");
    }


    //專案內沒有用到
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
                // 🆕 顯示教學彈窗
                showTutorialDialog();
            } else {
                Toast.makeText(this, "需要相機權限才能使用此功能", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    //直接開啟相機(需要先請求好權限)
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
        Preview preview = new Preview.Builder().build(); //【相機影像畫面】提供
        preview.setSurfaceProvider(cameraView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();


        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
        //ImageAnalysis來拿到ImageProxy
        //setAnalyzer = 設定分析器。
        //javaimageAnalysis.setAnalyzer(執行緒, 分析方法);
        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

        // 🎥 創建 VideoCapture
        if (ENABLE_VIDEO_RECORDING) {
            try {
                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);
                Log.d(TAG, "✅ VideoCapture 初始化成功");
            } catch (Exception e) {
                Log.e(TAG, "❌ VideoCapture 初始化失敗", e);
            }
        }

        try {
            cameraProvider.unbindAll();

            // 🎥 綁定相機用例（包含錄影）
            if (ENABLE_VIDEO_RECORDING && videoCapture != null) {
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, videoCapture);
            } else {
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            }

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
            long t0, t1, t2, t3, t4, t5;
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

            t0 = System.nanoTime();
            Bitmap rawBitmap = imageProxyToBitmap(imageProxy);
            t1 = System.nanoTime();

            if (rawBitmap != null) {
                Bitmap rotatedBitmap = rotateBitmap(rawBitmap, rotationDegrees);
                t2 = System.nanoTime();

                Bitmap mirroredBitmap = mirrorBitmap(rotatedBitmap);
                t3 = System.nanoTime();

                MPImage mpImage = new BitmapImageBuilder(mirroredBitmap).build();
                // 🔥 縮小圖片加速 MediaPipe 嘗試改小張240
//                Bitmap smallBitmap = Bitmap.createScaledBitmap(mirroredBitmap, 240, 320, true);
//                MPImage mpImage = new BitmapImageBuilder(smallBitmap).build();

                FaceLandmarkerResult result = faceLandmarker.detect(mpImage);
                t4 = System.nanoTime();

                if (result != null && !result.faceLandmarks().isEmpty()) {
                    Log.d(TAG, "檢測到人臉，關鍵點數量: " + result.faceLandmarks().get(0).size());
                }
                //checkFacePosition進入後會根據動作分流
                checkFacePosition(result, mirroredBitmap.getWidth(), mirroredBitmap.getHeight(), mirroredBitmap);
                t5 = System.nanoTime();

                // ===== 印出各階段耗時 =====
                Log.d("PERF_TIMING", String.format(
                        "Frame#%d | toBitmap=%.1fms | rotate=%.1fms | mirror=%.1fms | MediaPipe=%.1fms | checkPos=%.1fms | TOTAL=%.1fms",
                        frameId,
                        (t1 - t0) / 1_000_000f,
                        (t2 - t1) / 1_000_000f,
                        (t3 - t2) / 1_000_000f,
                        (t4 - t3) / 1_000_000f,
                        (t5 - t4) / 1_000_000f,
                        (t5 - t0) / 1_000_000f
                ));

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


    /**
     * 將 allPoints 轉成「Bitmap 像素座標」。
     * - 若看起來是 normalized(0~1) → 乘上 imgW/imgH。
     * - 否則視為 Overlay 座標 → 依等比縮放+置中模型反推回 Bitmap 像素。
     */
    private float[][] toBitmapPixels(float[][] ptsIn,
                                     int imgW, int imgH,
                                     int overlayW, int overlayH) {
        float[][] out = new float[ptsIn.length][2];

        // 粗檢：抽樣前20點，有 ≥70% 落在[0,1] 就當 normalized
        int sample = Math.min(20, ptsIn.length), count01 = 0;
        for (int i = 0; i < sample; i++) {
            float x = ptsIn[i][0], y = ptsIn[i][1];
            if (x >= 0f && x <= 1f && y >= 0f && y <= 1f) count01++;
        }
        boolean looksNormalized = (count01 >= sample * 0.7f);

        if (looksNormalized) {
            for (int i = 0; i < ptsIn.length; i++) {
                out[i][0] = ptsIn[i][0] * imgW;
                out[i][1] = ptsIn[i][1] * imgH;
            }
            return out;
        }

        // Overlay→Bitmap 反解（view = img*scale + offset）
        float scale = Math.min((float) overlayW / imgW, (float) overlayH / imgH);
        float offX = (overlayW - imgW * scale) / 2f;
        float offY = (overlayH - imgH * scale) / 2f;

        for (int i = 0; i < ptsIn.length; i++) {
            float vx = ptsIn[i][0], vy = ptsIn[i][1];
            out[i][0] = (vx - offX) / scale;
            out[i][1] = (vy - offY) / scale;
        }
        return out;
    }

    //===========【動作方法區域】=========
    // 加入 YOLO 整合
    private void checkFacePosition(FaceLandmarkerResult result, int bitmapWidth, int bitmapHeight, Bitmap mirroredBitmap) {
        // 臉部位置判斷
        boolean faceDetected = result != null && !result.faceLandmarks().isEmpty();

        if (faceDetected) {
            try {
                runOnUiThread(() -> {
//                    Log.d(TAG_2, "進入主流程_checkFacePosition");
                    int overlayWidth = overlayView.getWidth();
                    int overlayHeight = overlayView.getHeight();
                    //  前置鏡頭顯示影像不是真的，是處理過的，MEDIAPPIPE處理的陣列是未處理得"相機陣列"，所以這邊進行模仿處理再顯示
                    if (overlayWidth > 0 && overlayHeight > 0) {
                        float inputAspect = 480f / 640f; // Bitmap 寬高比(?bitmap從哪來，前置鏡頭原始像?)
                        float viewAspect = overlayWidth / (float) overlayHeight; // Overlay 寬高比(給人看得處理後畫面?)
                        float scaleX = inputAspect / viewAspect;

                        int landmarkCount = result.faceLandmarks().get(0).size();
                        float[][] landmarks01 = new float[landmarkCount][3]; // 0~1;//正規化原始圖像
                        float[][] allPoints = new float[landmarkCount][3]; // 0~1;//變形比例，顯示用圖像

                        for (int i = 0; i < landmarkCount; i++) {
                            float x = result.faceLandmarks().get(0).get(i).x();
                            float y = result.faceLandmarks().get(0).get(i).y();
                            float z = result.faceLandmarks().get(0).get(i).z();

                            // 存一份原始 0~1（給 CheekFlowEngine 用）
                            landmarks01[i][0] = x;
                            landmarks01[i][1] = y;
                            landmarks01[i][2] = z;
                            // 這份是給 overlay 畫面：做 X 比例補償後轉像素
                            x = (x - 0.5f) * scaleX + 0.5f; //???
                            allPoints[i][0] = x * overlayWidth;
                            allPoints[i][1] = y * overlayHeight;
                            allPoints[i][2] = z ;
//                            Log.d(TAG_2, "進入主流程_checkFacePosition_寫完兩個allPoints");
                        }


// === 頭動偵測（只在校正階段阻擋）===


                        //****動作分流給Handler方法，底下handleFacePosition處理時間顯示流
                        if (("TONGUE_FOWARD".equals(trainingLabel) ||
                                "TONGUE_BACK".equals(trainingLabel) ||
                                "TONGUE_UP".equals(trainingLabel) ||
                                "TONGUE_DOWN".equals(trainingLabel)) && isYoloEnabled) {

//                            Log.d(TAG_2, "動作分流_舌頭");

                            // 幀樹過濾器: 每 FACE_MESH_EVERY 幀更新一次 ROI（Overlay→Bitmap），needFaceMesh=需不需要更新
                            // 更換機型可以調整看看
                            boolean needFaceMesh = (lastOverlayRoi == null) || (frameId % FACE_MESH_EVERY == 0);
                            if (needFaceMesh) {
                                Rect overlayRoi = TongueYoloDetector.calculateMouthROI(allPoints, overlayWidth, overlayHeight);
                                lastOverlayRoi = overlayRoi;
                                //mirroredBitmap =>圖已轉正+左右顛倒後
                                // b除以sx=o，求sx就是要算縮放倍率
                                float sx = (float) mirroredBitmap.getWidth() / overlayWidth;
                                float sy = (float) mirroredBitmap.getHeight() / overlayHeight;
                                lastBitmapRoi = new Rect(
                                        Math.round(overlayRoi.left * sx),
                                        Math.round(overlayRoi.top * sy),
                                        Math.round(overlayRoi.right * sx),
                                        Math.round(overlayRoi.bottom * sy)
                                );
                            }

                            // 把快取 ROI 傳給 YOLO（不一定每幀更新 ROI）
//                            handleTongueMode(allPoints, mirroredBitmap, bitmapWidth, bitmapHeight,
//                                    lastOverlayRoi, lastBitmapRoi);
                            //20025 11 13 偷改看看新模型
                            handleTongueMode(allPoints, mirroredBitmap, bitmapWidth, bitmapHeight,
                                    lastOverlayRoi, lastBitmapRoi);

                        } else if("TONGUE_LEFT".equals(trainingLabel) || "TONGUE_RIGHT".equals(trainingLabel) ){
//                            Log.d(TAG_2, "動作分流_舌頭");
                            // 幀樹過濾器: 每 FACE_MESH_EVERY 幀更新一次 ROI（Overlay→Bitmap），needFaceMesh=需不需要更新
                            // 更換機型可以調整看看
                            boolean needFaceMesh = (lastOverlayRoi == null) || (frameId % FACE_MESH_EVERY == 0);
                            if (needFaceMesh) {
                                Rect overlayRoi = TongueYoloDetectorLR.calculateMouthROI(allPoints, overlayWidth, overlayHeight);
                                lastOverlayRoi = overlayRoi;
                                //mirroredBitmap =>圖已轉正+左右顛倒後
                                // b除以sx=o，求sx就是要算縮放倍率
                                Log.d("confirmLR", "into LR checkPos");
                                float sx = (float) mirroredBitmap.getWidth() / overlayWidth;
                                float sy = (float) mirroredBitmap.getHeight() / overlayHeight;
                                lastBitmapRoi = new Rect(
                                        Math.round(overlayRoi.left * sx),
                                        Math.round(overlayRoi.top * sy),
                                        Math.round(overlayRoi.right * sx),
                                        Math.round(overlayRoi.bottom * sy)
                                );
                            }
                            // 把快取 ROI 傳給 YOLO（不一定每幀更新 ROI）
                            handleTongueModeLR(allPoints, mirroredBitmap, bitmapWidth, bitmapHeight,
                                    lastOverlayRoi, lastBitmapRoi);

                        } else if ("鼓頰".equals(trainingLabel) || "PUFF_CHEEK".equals(trainingLabel) || "REDUCE_CHEEK".equals(trainingLabel)) {
                            //Log.d(TAG_2, "動作分流_臉頰");
                            // ★★★ 臉頰模式
                            handleCheeksMode(landmarks01, mirroredBitmap,bitmapWidth,bitmapHeight);
                        } else if ("下顎".equals(trainingLabel) || "JAW_LEFT".equals(trainingLabel) || "JAW_RIGHT".equals(trainingLabel)) {
                            // ★★★ 下顎模式
                            //Log.d(TAG_2, "動作分流_下顎");
                            handleJawMode(allPoints);
                        } else {
                            //Log.d(TAG_2, "動作分流_嘴唇");
                            // 嘴唇模式
                            handleLipMode(allPoints);
                        }
                        // *************************前端邏輯
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
                        // 重要，要確認條件
                        boolean noseInside = (dx * dx + dy * dy) <= (radius * radius);

//                        handleFacePosition(noseInside);
                        /*
                        // 🆕 頭動檢測（眼距變化）
                        float currentEyeDistance = calculateEyeDistance(allPoints);

                        if (currentState == AppState.CALIBRATING && !baselineSet && calibrationStartTime > 0) {
                            baselineEyeDistance = currentEyeDistance;
                            baselineSet = true;
                            Log.d(TAG, "📏 基準眼距設定: " + baselineEyeDistance);
                        }

                        boolean headStable = true;

// 只在校正階段檢測頭動
                        if (baselineSet && currentState == AppState.CALIBRATING) {
                            float changeRatio = Math.abs(currentEyeDistance - baselineEyeDistance) / baselineEyeDistance;
                            headStable = changeRatio < EYE_DISTANCE_THRESHOLD;

                            if (!headStable && cueText != null) {
                                cueText.setText("請保持頭部不動");
                                Log.d(TAG, "⚠️ 頭動檢測: 眼距變化 " + (changeRatio * 100) + "%");
                            }
                        }

                        if (!noseInside && cueText != null) {
                            cueText.setText("請回到圓框內");
                        }

                        boolean faceOK = (currentState == AppState.CALIBRATING)
                                ? (noseInside && headStable)
                                : noseInside;

                        handleFacePosition(faceOK);*/
                        //

                        // === 頭動偵測（校正中 + 倒數完成 + 人在圓框內 才擋）===
                        boolean headStable = isHeadStable(landmarks01);
                        if (headStableCooldown > 0) {
                            headStableCooldown--;
                            headStable = false;
                        }
                        if (!headStable && countdownFinished && currentState == AppState.CALIBRATING && noseInside) {
                            if (cueText != null) {
                                cueText.setVisibility(View.VISIBLE);
                                cueText.setText("⚠️ 請保持頭部不動");
                            }
                            overlayView.setStatus(CircleOverlayView.Status.OUT_OF_BOUND);
                            resetCalibration();
                            return;
                        }

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
     * 模式處理只負責到紀錄，稍後由狀態幾呼叫完成進行後續邏輯
     * 改TongueYoloDetector.DetectionResult result 的物件1或2
     */
    private void handleTongueMode(float[][] allPoints, Bitmap mirroredBitmap, int bitmapWidth, int bitmapHeight,
                                  Rect overlayRoi,   // ← 使用快取 Overlay ROI
                                  Rect bitmapRoi) {  // ← 使用快取 Bitmap ROI
        try {
            if (!shouldAcceptNewFrames()) return;
            // ★ 每 YOLO_EVERY 幀跑一次 YOLO
            if ((frameId % YOLO_EVERY) != 0) return;
            if (overlayRoi == null || bitmapRoi == null) return;

            // 🔥 新增：如果 YOLO 還在忙，跳過這幀（取代原本的 YOLO_EVERY 檢查）
            if (isYoloProcessing) return;
            isYoloProcessing = true;

            int overlayWidth = overlayView.getWidth();
            int overlayHeight = overlayView.getHeight();

            final Rect mouthROIFinal = new Rect(overlayRoi);
            final float[][] allPointsFinal = allPoints;
            final Rect bitmapROIFinal = new Rect(bitmapRoi);

            yoloExecutor.execute(() -> {
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
                // 好像跟電池有關
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
                //準備帶入主執行緒
                final boolean detected = result.detected;
                final float conf = result.confidence;
                final Rect bboxImgFinal = (result.detected && result.boundingBox != null)
                        ? new Rect(result.boundingBox) : null;
                mainHandler.post(() -> {
                    overlayView.setYoloDetectionResult(detected, conf, finalViewTongueBox, mouthROIFinal);

                    // 設定參考線 (用 View 座標)，單純為了把座標丟給overlayView繪製
                    float eyeRxView = allPointsFinal[33][0], eyeRyView = allPointsFinal[33][1];
                    float eyeLxView = allPointsFinal[263][0], eyeLyView = allPointsFinal[263][1];
                    float browCxView = allPointsFinal[168][0], browCyView = allPointsFinal[168][1];
                    float noseXView = allPointsFinal[1][0], noseYView = allPointsFinal[1][1];
                    overlayView.setReferenceLines(eyeLxView, eyeLyView, eyeRxView, eyeRyView, noseXView, noseYView, browCxView, browCyView);

                    if (!isTrainingCompleted && (currentState == AppState.CALIBRATING || currentState == AppState.MAINTAINING)) {
                        String stateString = csvState();
                        // 1) 影像尺寸（Bitmap 像素）
                        final int imgW = mirroredBitmap.getWidth();
                        final int imgH = mirroredBitmap.getHeight();

                        // 2) YOLO bbox（Bitmap 像素；無偵測→null）
                        final android.graphics.Rect bboxImg = bboxImgFinal;

                        // 3) 將 allPointsFinal 統一到「Bitmap 像素」
                        float[][] ptsPx = toBitmapPixels(allPointsFinal, imgW, imgH, overlayView.getWidth(), overlayView.getHeight());

                        // 4) 取需要的臉部點（MediaPipe FaceMesh index）
                        final int EYE_R = 33, EYE_L = 263, BROW_C = 168, NOSE_T = 1;
                        float eyeRx = ptsPx[EYE_R][0], eyeRy = ptsPx[EYE_R][1];
                        float eyeLx = ptsPx[EYE_L][0], eyeLy = ptsPx[EYE_L][1];
                        float browCx = ptsPx[BROW_C][0],  browCy = ptsPx[BROW_C][1];
                        float noseX  = ptsPx[NOSE_T][0],  noseY  = ptsPx[NOSE_T][1];

                        // 5) 補正參數：原點（你要的基準點），旋轉角（兩眼線）、縮放（兩眼距）
                        float originX = noseX, originY = noseY;   // 你要用鼻尖當原點
                        float vxEye = eyeRx - eyeLx, vyEye = eyeRy - eyeLy;
                        float dio   = (float) Math.hypot(vxEye, vyEye);       // 兩眼距
                        float theta = (float) Math.atan2(vyEye, vxEye);       // 兩眼線相對水平角（弧度）

                        // 6) 舌頭中心（Bitmap 像素）與補正後座標
                        float cxImg = Float.NaN, cyImg = Float.NaN, xNorm = Float.NaN, yNorm = Float.NaN;
                        if (bboxImg != null) {
                            cxImg = (bboxImg.left + bboxImg.right) * 0.5f;
                            cyImg = (bboxImg.top  + bboxImg.bottom) * 0.5f;

                            // 平移到原點
                            float vx = cxImg - originX;
                            float vy = cyImg - originY;

                            // 旋轉 -theta（讓兩眼線水平）
                            float cosT = (float) Math.cos(theta), sinT = (float) Math.sin(theta);
                            float xr =  vx * cosT + vy * sinT;
                            float yr = -vx * sinT + vy * cosT;

                            // 縮放正規化（除以兩眼距）
                            if (dio > 1e-3f) {
                                xNorm = xr / dio;
                                yNorm = yr / dio;
                            }
                        }

                        // 7) 寫入：呼叫「舌頭專用多載」
                        dataRecorder.recordLandmarkData(
                                stateString,
                                detected,
                                bboxImg,
                                eyeLx, eyeLy, eyeRx, eyeRy,
                                browCx, browCy, noseX, noseY,
                                imgW, imgH,
                                System.currentTimeMillis(),
                                originX, originY,
                                theta,
                                dio,
                                cxImg, cyImg,
                                xNorm, yNorm
                        );
                    }

                    isYoloProcessing = false;  // 🔥 新增：發生錯誤也要解除
                });
            });

        } catch (Exception e) {
            Log.e(TAG, "處理舌頭模式時發生錯誤", e);
            isYoloProcessing = false;  // 🔥 新增：發生錯誤也要解除
        }
    }


    private void handleTongueModeLR(float[][] allPoints, Bitmap mirroredBitmap, int bitmapWidth, int bitmapHeight,
                                    Rect overlayRoi,   // ← 使用快取 Overlay ROI
                                    Rect bitmapRoi) {  // ← 使用快取 Bitmap ROI
        try {
            if (!shouldAcceptNewFrames()) return;
            // ★ 每 YOLO_EVERY 幀跑一次 YOLO
            if ((frameId % YOLO_EVERY) != 0) return;
            if (overlayRoi == null || bitmapRoi == null) return;

            // 🔥 新增：如果 YOLO 還在忙，跳過這幀
            if (isYoloProcessing) return;
            isYoloProcessing = true;

            int overlayWidth = overlayView.getWidth();
            int overlayHeight = overlayView.getHeight();

            final Rect mouthROIFinal = new Rect(overlayRoi);
            final float[][] allPointsFinal = allPoints;
            final Rect bitmapROIFinal = new Rect(bitmapRoi);

            yoloExecutor.execute(() -> {
                long t0 = System.nanoTime();
                TongueYoloDetectorLR.DetectionResult result =
                        tongueDetectorLR.detectTongueWithRealPosition(
                                mirroredBitmap, bitmapROIFinal, overlayWidth, overlayHeight);
                Log.d("confirmLR", "into LR handle");
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
                // 好像跟電池有關
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
                //準備帶入主執行緒
                final boolean detected = result.detected;
                final float conf = result.confidence;
                final Rect bboxImgFinal = (result.detected && result.boundingBox != null)
                        ? new Rect(result.boundingBox) : null;
                mainHandler.post(() -> {
                    overlayView.setYoloDetectionResult(detected, conf, finalViewTongueBox, mouthROIFinal);

                    // 設定參考線 (用 View 座標)，單純為了把座標丟給overlayView繪製
                    float eyeRxView = allPointsFinal[33][0], eyeRyView = allPointsFinal[33][1];
                    float eyeLxView = allPointsFinal[263][0], eyeLyView = allPointsFinal[263][1];
                    float browCxView = allPointsFinal[168][0], browCyView = allPointsFinal[168][1];
                    float noseXView = allPointsFinal[1][0], noseYView = allPointsFinal[1][1];
                    overlayView.setReferenceLines(eyeLxView, eyeLyView, eyeRxView, eyeRyView, noseXView, noseYView, browCxView, browCyView);

                    if (!isTrainingCompleted && (currentState == AppState.CALIBRATING || currentState == AppState.MAINTAINING)) {
                        String stateString = csvState();
                        // 1) 影像尺寸（Bitmap 像素）
                        final int imgW = mirroredBitmap.getWidth();
                        final int imgH = mirroredBitmap.getHeight();

                        // 2) YOLO bbox（Bitmap 像素；無偵測→null）
                        final android.graphics.Rect bboxImg = bboxImgFinal;

                        // 3) 將 allPointsFinal 統一到「Bitmap 像素」
                        float[][] ptsPx = toBitmapPixels(allPointsFinal, imgW, imgH, overlayView.getWidth(), overlayView.getHeight());

                        // 4) 取需要的臉部點（MediaPipe FaceMesh index）
                        final int EYE_R = 33, EYE_L = 263, BROW_C = 168, NOSE_T = 1;
                        float eyeRx = ptsPx[EYE_R][0], eyeRy = ptsPx[EYE_R][1];
                        float eyeLx = ptsPx[EYE_L][0], eyeLy = ptsPx[EYE_L][1];
                        float browCx = ptsPx[BROW_C][0],  browCy = ptsPx[BROW_C][1];
                        float noseX  = ptsPx[NOSE_T][0],  noseY  = ptsPx[NOSE_T][1];

                        // 5) 補正參數：原點（你要的基準點），旋轉角（兩眼線）、縮放（兩眼距）
                        float originX = noseX, originY = noseY;   // 你要用鼻尖當原點
                        float vxEye = eyeRx - eyeLx, vyEye = eyeRy - eyeLy;
                        float dio   = (float) Math.hypot(vxEye, vyEye);       // 兩眼距
                        float theta = (float) Math.atan2(vyEye, vxEye);       // 兩眼線相對水平角（弧度）

                        // 6) 舌頭中心（Bitmap 像素）與補正後座標
                        float cxImg = Float.NaN, cyImg = Float.NaN, xNorm = Float.NaN, yNorm = Float.NaN;
                        if (bboxImg != null) {
                            cxImg = (bboxImg.left + bboxImg.right) * 0.5f;
                            cyImg = (bboxImg.top  + bboxImg.bottom) * 0.5f;

                            // 平移到原點
                            float vx = cxImg - originX;
                            float vy = cyImg - originY;

                            // 旋轉 -theta（讓兩眼線水平）
                            float cosT = (float) Math.cos(theta), sinT = (float) Math.sin(theta);
                            float xr =  vx * cosT + vy * sinT;
                            float yr = -vx * sinT + vy * cosT;

                            // 縮放正規化（除以兩眼距）
                            if (dio > 1e-3f) {
                                xNorm = xr / dio;
                                yNorm = yr / dio;
                            }
                        }

                        // 7) 寫入：呼叫「舌頭專用多載」
                        dataRecorder.recordLandmarkData(
                                stateString,
                                detected,
                                bboxImg,
                                eyeLx, eyeLy, eyeRx, eyeRy,
                                browCx, browCy, noseX, noseY,
                                imgW, imgH,
                                System.currentTimeMillis(),
                                originX, originY,
                                theta,
                                dio,
                                cxImg, cyImg,
                                xNorm, yNorm
                        );
                    }
                    isYoloProcessing = false;
                });
            });

        } catch (Exception e) {
            Log.e(TAG, "處理舌頭模式時發生錯誤", e);
            isYoloProcessing = false;
        }
    }

    // 嘴唇模式：MediaPipe 關鍵點
    private void handleLipMode(float[][] allPoints) {
        if (!shouldAcceptNewFrames()) return;
        //畫面顯示臉部點
//        overlayView.setAllFaceLandmarks(allPoints);
        //校正中跟動作中狀態=>紀錄
        if (!isTrainingCompleted && (currentState == AppState.CALIBRATING || currentState == AppState.MAINTAINING)) {
            String stateString = csvState();
            dataRecorder.recordLandmarkData(stateString, allPoints, null);
            //Log.d(TAG, "記錄嘴唇資料: " + stateString + ", 關鍵點數量: " + allPoints.length);

            //Log.d(TAG_2, "記錄嘴唇資料: " + stateString + ", 關鍵點數量: " + allPoints.length);
        }
    }

    // 下顎模式：MediaPipe 關鍵點
    private void handleJawMode(float[][] allPoints) {
        if (!shouldAcceptNewFrames()) return;
        overlayView.setAllFaceLandmarks(allPoints);

        if (!isTrainingCompleted && (currentState == AppState.CALIBRATING || currentState == AppState.MAINTAINING)) {
            String stateString = csvState();
            dataRecorder.recordLandmarkData(stateString, allPoints, true);
            Log.d(TAG, "記下顎資料: " + stateString + ", 關鍵點數量: " + allPoints.length);
        }
    }

    //臉頰模式
    private void handleCheeksMode(float[][] landmarks01, Bitmap mirroredBitmap, int img_w,int img_h) {
        if (!shouldAcceptNewFrames()) return;
        try {
//            ensureCheekEngine();
            long ts = System.currentTimeMillis();

            cameraExecutor.execute(() -> {
                //CheekFlowEngine.FlowResult r = cheekEngine.process(mirroredBitmap, landmarks01, ts);

                if (!isTrainingCompleted &&
                        (currentState == AppState.CALIBRATING || currentState == AppState.MAINTAINING)){
//                        r.computedThisFrame) {
                    //補償
//                    org.opencv.core.Point li = r.vectors.get(CheekFlowEngine.Region.LEFT_INNER);
//                    org.opencv.core.Point ri = r.vectors.get(CheekFlowEngine.Region.RIGHT_INNER);
//                    //原始
//                    org.opencv.core.Point liRaw = r.rawVectors.get(CheekFlowEngine.Region.LEFT_INNER);
//                    org.opencv.core.Point riRaw = r.rawVectors.get(CheekFlowEngine.Region.RIGHT_INNER);
//                    // 取得狀態字串（跟你嘴唇/舌頭一致）
                    String stateString = csvState();

                    // 改用曲率
                    Log.e("FCA_Cheek_Curve", "imgW&H=="+img_w +","+img_h);
                    dataRecorder.recordLandmarkData(stateString, landmarks01,  img_w,  img_h);


                }
            });

        } catch (Exception e) {
            Log.e(TAG, "handleCheeksMode error", e);
        }
    }

    //處理時間顯示
    private void handleFacePosition(boolean faceInside) {
        if (isTrainingCompleted) return;

        // 🆕 如果倒數還沒完成，不進行校正流程
        if (!countdownFinished) {
            overlayView.setStatus(CircleOverlayView.Status.NO_FACE);
            return;
        }

        long currentTime = System.currentTimeMillis();

        switch (currentState) {
            case CALIBRATING:
                if (faceInside) {
                    if (calibrationStartTime == 0) {
                        calibrationStartTime = currentTime;
                        startCalibrationTimer();
                        demoStarted = false;
                        demoFinished = false;
                    }

                    CircleOverlayView.Status uiStatus = CircleOverlayView.Status.CALIBRATING;

                    if (demoEnabled && !demoFinished) {
                        if (!demoStarted) {
                            demoStarted = true;
                            demoStartMs = currentTime;
                        }
                        long d = currentTime - demoStartMs;

                        // 統一設定 cueText 大小
                        if (cueText != null) {
                            cueText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32);
                        }

                        //【DEMOTIME】時間直接寫死在這裡
                        if (d < DEMO_PHASE_1) {
                            // 0~4s：黃框 - 放鬆校正
                            if (statusText != null) statusText.setVisibility(android.view.View.GONE);  // 隱藏

                            uiStatus = CircleOverlayView.Status.CALIBRATING;
                            if (statusText != null) statusText.setText("校正中");
                            if (cueText != null) cueText.setText("放鬆，保持不動");
                            isDemoPhase = false;

                        } else if (d < DEMO_PHASE_2) {
                            // 4~8s：藍框 - 示範動作
                            uiStatus = CircleOverlayView.Status.DEMO;
                            String zh = motionLabelZh(trainingLabel);
                            if (statusText != null) statusText.setText("校正中");
                            if (cueText != null) cueText.setText("請" + zh + "");
                            isDemoPhase = true;

                        } else if (d < CALIBRATION_TIME) {
                            // 8~11s：黃框 - 準備開始
                            uiStatus = CircleOverlayView.Status.CALIBRATING;
                            if (statusText != null) statusText.setText("校正中");
                            if (cueText != null) cueText.setText("放鬆，保持不動");
                            isDemoPhase = false;

                        } else {
                            uiStatus = CircleOverlayView.Status.CALIBRATING;
                            demoFinished = true;
                        }
                    }

                    overlayView.setStatus(uiStatus);

                } else {
                    // 離開圓框
                    resetCalibration();
                    overlayView.setStatus(CircleOverlayView.Status.OUT_OF_BOUND);
                    currentState = AppState.OUT_OF_BOUNDS;
                    demoStarted = false;
                    demoFinished = false;
                    if (statusText != null) statusText.setText("超出邊界");
                    if (cueText != null) cueText.setText("請回到圓框內");
                }
                break;

            case MAINTAINING:
                if (faceInside) {
                    if (maintainStartTime == 0) {
                        maintainStartTime = currentTime;

                    }
                    overlayView.setStatus(CircleOverlayView.Status.OK);
                } else {
//                    if (maintainStartTime > 0) {
//                        maintainTotalTime += (currentTime - maintainStartTime);
//                        maintainStartTime = 0;
//                    }
                    //maintainStartTime = 0;
                    // 改離開就全部重來
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




    // 光流相關
    private void ensureCheekEngine() {
        if (cheekEngine == null) {
            CheekFlowEngine.Params pp = new CheekFlowEngine.Params();
            pp.targetWidth = 360;              // 0 = 不降採樣；建議先 360
            pp.flowEvery = 2;                  // 每 2 幀算一次
            pp.landmarksAreNormalized01 = true;
            pp.enableRigidCompensation = true; // 方案A：補償後寫入同欄位
            pp.smoothAlpha = 0.25f;            // 0.2~0.4 建議
            cheekEngine = new CheekFlowEngine(pp);
        }
    }

    //確認時間顯示文字
    // handleFacePosition 偵測到
    private void startCalibrationTimer() {
        cancelTimers();
        Log.d(TAG_2, "🟡 開始校正階段計時器");
        Log.d(TAG_3, "🎥 校正開始，啟動錄影");
        // 🎥 在這裡開始錄影！
        if (ENABLE_VIDEO_RECORDING && videoCapture != null && currentRecording == null) {
            Log.d(TAG_3, "🎥 校正開始，啟動錄影");
            startVideoRecording();
        }

        calibrationTimer = () -> {
            Log.d(TAG, "🟡 校正完成，切換到維持狀態");
            currentState = AppState.MAINTAINING;
            maintainStartTime = System.currentTimeMillis();
            overlayView.setStatus(CircleOverlayView.Status.OK);

            // ★★★ 啟動 2 秒導引（依 trainingLabel 換字）
            startSimpleCue();
            startMaintainTimer();
            updateStatusDisplay();
            updateTimerDisplay();
        };
        //這一段是延後觸發calibrationTimer，他在校正開始時就埋好。
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
                Log.d(TAG_2, "✅ 維持時間達標！訓練完成");
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

            //校正基準線
            baselineSet = false;
            baselineEyeDistance = 0;

            resetHeadMotion();

            // 🆕 清空 CSV 資料
            if (dataRecorder != null) {
                dataRecorder.clearData();
            }

            // 🆕 重置 demo 狀態
            demoStarted = false;
            demoFinished = false;
            demoStartMs = 0;

            // 🆕 停止並刪除未完成的影片
            if (currentRecording != null) {
                currentRecording.stop();
                currentRecording = null;
                if (videoFilePath != null) {
                    File file = new File(videoFilePath);
                    if (file.exists() && file.delete()) {
                        Log.d(TAG, "🗑️ 已刪除作廢的影片（校正中離開）");
                    }
                    videoFilePath = null;
                }
            }
        }
    }

    private void resetToCalibration() {
        if (!isTrainingCompleted) {
            calibrationStartTime = 0;
            maintainStartTime = 0;
            maintainTotalTime = 0; //清空累計時間
            cancelTimers();
            currentState = AppState.CALIBRATING;
        }

        //校正頭動重置
        baselineSet = false;
        baselineEyeDistance = 0;

        resetHeadMotion();

        if (dataRecorder != null) {
            dataRecorder.clearData();
        }

        demoStarted = false;
        demoFinished = false;
        demoStartMs = 0;

        cueStep = 0;
        stopSimpleCue();

        if (currentRecording != null) {
            currentRecording.stop();
            currentRecording = null;
            if (videoFilePath != null) {
                File file = new File(videoFilePath);
                if (file.exists() && file.delete()) {
                    Log.d(TAG, "🗑️ 已刪除作廢的影片（訓練中離開）");
                }
                videoFilePath = null;
            }
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
        stopSimpleCue();
        Log.d(TAG, " === 訓練完成！開始儲存資料 ");
        Log.d(TAG_2, " ==已進入compelete ");
        isTrainingCompleted = true;
        // 🎥 停止錄影（正常完成，不刪除）
        if (currentRecording != null) {
            currentRecording.stop();
            currentRecording = null;
            Log.d(TAG, "✅ 錄影完成，影片已保存");
        }
        cancelTimers();

        overlayView.setStatus(CircleOverlayView.Status.OK);
        updateStatusDisplay();
        updateTimerDisplay();

        Toast.makeText(this, " 訓練完成！\n正在儲存檔案並進行分析...", Toast.LENGTH_LONG).show();

        //這邊會先呼叫dataRecorder.saveToFileWithCallbac，做運算完成後會從dataRecorder那邊呼叫下面方法onComplete
        //底下new FaceDataRecorder.DataSaveCallback()，好像是一個callBack物件在saveToFileWithCallback方法當參數
        dataRecorder.saveToFileWithCallback(new FaceDataRecorder.DataSaveCallback() {


            @Override
            public void onComplete(CSVMotioner.PyAnalysisResult res) {
                //20251002 : 現在要從遠端回傳改回佣PYHON本地值
                Log.d(TAG, "✅ 測試傳數值到Vercel_");
                //變數宣告
                final String payload = dataRecorder.exportLinesAsJson();
                final String csv = dataRecorder.getFileName();
                final String label0 = trainingLabel;
                final int target = 0;
                final int duration0 = MAINTAIN_TIME_TOTAL / 1000;

                Log.d("API_SEND", "✅ 上傳CSV內容::"+payload);
                Log.d("SEND_TO_PYTHON，看payload變數就知道內文", "✅ 上傳CSV內容::"+payload);
                Log.d("PYTHON RETURN REESULT", "✅ 回傳內容::"+payload);

                // 改呼叫Python去讀CSV檔案
                String label = label0;
                String ResMotionType = "";
                String curveJson = "";
                String TAB1 = "viewProblem";

                int actual   = 0;
                int duration = duration0;

                actual   = res.actionCount;
                duration = (int) res.totalActionTime;


                new Thread(() -> {
                    // 改呼叫Python去讀CSV檔案
                    String flabel = label0;
                    String fResMotionType = "";
                    String fcurveJson = "";
                    String fTAB1 = "viewProblem";

                    int factual   = 0;
                    int fduration = duration0;

                    factual   = res.actionCount;
                    fduration = (int) res.totalActionTime;

                    //存檔與跳頁
                    insertTrainingRecord(trainingLabel_String, factual, 3, fduration, csv,null);
                    AppLogger.logTrainingComplete(trainingLabel);

                    //TODO..改道上傳完再跑?
                    runOnUiThread(() -> go(trainingLabel_String, 0, target, 0, csv, "test"));
                }).start();
            }


            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ 儲存或分析失敗: " + error);
                Toast.makeText(FaceCircleCheckerActivity.this, "處理失敗: " + error, Toast.LENGTH_LONG).show();
                new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 3000);
            }
        });
    }

    //區域_提醒放鬆與動作導引
    private String getReadableLabel() {
        if (trainingLabel == null) return "訓練";
        switch (trainingLabel) {
            case "TONGUE_LEFT":   return "舌頭左移";
            case "TONGUE_RIGHT":  return "舌頭右移";
            case "TONGUE_FOWARD": // 你原程式拼的是 FOWARD
            case "TONGUE_FORWARD":return "舌頭前伸";
            case "TONGUE_BACK":   return "舌頭後縮";
            case "TONGUE_UP":     return "舌頭上抬";
            case "TONGUE_DOWN":   return "舌頭下壓";
            case "PUFF_CHEEK":    return "鼓起臉頰";
            case "REDUCE_CHEEK":  return "放鬆臉頰";
            case "JAW_LEFT":      return "下顎左移";
            case "JAW_RIGHT":     return "下顎右移";
            default:              return trainingLabel; // 保底：直接顯示原字
        }
    }
    // 開始 2 秒導引：動作 → 放鬆 → 動作 → 放鬆（循環）
    private void startSimpleCue() {
        stopSimpleCue();      // 保險清理
        cueRunning = true;
        cueStep = 0;
        postNextCue(0);       // 立刻進第一段
    }

    // 停止導引：只移除我們自己的 runnable，不影響其他計時器
    private void stopSimpleCue() {
        cueRunning = false;
        if (cueRunnable != null && mainHandler != null) {
            mainHandler.removeCallbacks(cueRunnable);
            cueRunnable = null;
        }
    }

    // 安排下一步（用 if/else 寫死 2 秒），並根據 trainingLabel 換字
    //20251127 先取消文字
    private void postNextCue(long delayMs) {
        if (mainHandler == null) return;
        final int segMs = Math.max(1, CUE_SEGMENT_SEC) * 1000;//只是CUE_SEGMENT_SEC轉ms，一樣意思

        cueRunnable = () -> {
            // ★ 加入這個檢查：訓練完成就不要再更新
            if (!cueRunning || cueText == null || isTrainingCompleted) return;

            String zh = motionLabelZh(trainingLabel);
            cueText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32);

            if (cueStep % 2 == 0) {
                cueText.setText("保持 " + zh);
            } else {
                cueText.setText("放鬆");
            }

            cueStep++;

            // ★ 也在這裡檢查一次
            if (!isTrainingCompleted) {
                mainHandler.postDelayed(() -> postNextCue(0), segMs);
            }
        };
        mainHandler.postDelayed(cueRunnable, delayMs);
    }



//    private void onStartTraining() {
//        if (trainingStarted) return;
//        trainingStarted = true;
//        Log.d(TAG_3, "✅ 開始錄影流程");
//
//        // 開始錄影
//        if (ENABLE_VIDEO_RECORDING && videoCapture != null) {
//            startVideoRecording();
//        }
//        Log.d(TAG_2, "✅ 開始訓練流程");
//
//        // 狀態提示
//        if (statusText != null) statusText.setText("訓練中...");
//
//        // 1) 開始口令循環（動作提示）
//        cueRunning = true;
//        cueStep = 0;
//        postNextCue(0);
//
//
//    }



    //置頂狀態說明文字
    private void updateStatusDisplay() {
        if (statusText == null) return;

        statusText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);

        if (isTrainingCompleted) {
            statusText.setText("完成");
            if (cueText != null) {
                cueText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32);
                cueText.setText("✅ 訓練完成！");
            }
        } else if (currentState == AppState.MAINTAINING) {
            statusText.setText("訓練中");
        } else if (currentState == AppState.OUT_OF_BOUNDS) {
            statusText.setText("超出邊界");
        }
        // CALIBRATING 在 handleFacePosition() 處理
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

    // 幫手：關閉 ExecutorService（可重用）
    private void awaitShutdown(java.util.concurrent.ExecutorService exec) {
        if (exec == null) return;
        try {
            exec.shutdownNow(); // 立刻中斷尚未開始的與可中斷的任務
            exec.awaitTermination(1500, java.util.concurrent.TimeUnit.MILLISECONDS); // 等一下收尾
        } catch (InterruptedException ignored) {
        }
    }

    // 2) 把代號變中文顯示文字
    private String motionLabelZh(String label) {
        if (label == null) return "動作";

        switch (label) {
            // 嘴唇
            case "POUT_LIPS":
            case "poutLip":
                return "嘟嘴";
            case "SIP_LIPS":
            case "closeLip":
                return "抿嘴";

            // 舌頭
            case "TONGUE_LEFT":     return "舌頭往左";
            case "TONGUE_RIGHT":    return "舌頭往右";
            case "TONGUE_FOWARD":
            case "TONGUE_FORWARD":  return "舌頭前伸";
            case "TONGUE_BACK":     return "舌頭後縮";
            case "TONGUE_UP":       return "舌頭上抬";
            case "TONGUE_DOWN":     return "舌頭下壓";

            // 臉頰
            case "PUFF_CHEEK":      return "鼓臉頰";
            case "REDUCE_CHEEK":    return "縮臉頰";



            default:                return "動作";
        }
    }
    // ★ 新增：把各種寫法歸一成 poutLip / closeLip
    private String canonicalMotion(String s) {
        if (s == null) return "";
        String x = s.trim().toLowerCase(java.util.Locale.ROOT);
        if (x.contains("pout"))  return "poutLip";
        if (x.contains("close") || x.contains("sip") || x.contains("slip") || x.contains("抿"))
            return "closeLip";
        return s;
    }

    // 新增訓練結果到DB
    private void insertTrainingRecord(String label, int achieved, int target, int duration, String csv,String curveJson) {
        long currentTime = System.currentTimeMillis();

        User loggedInUser = AppDatabase.getInstance(this).userDao().findLoggedInOne();
        String username = (loggedInUser != null)?loggedInUser.userId : "guest";
        long createAt = maintainStartTime;
        long finishAt = maintainStartTime + maintainTotalTime;

        long targetTimes = MAINTAIN_TIME_TOTAL/1000/CUE_SEGMENT_SEC/2;
        targetTimes = 3;

        int achievedTimes = achieved;
        long durationTime = duration;
        String analysisType = label;

        //先由毫秒轉成"yyyy-MM-dd HH:mm:ss"
        Date date = new Date(createAt);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String readableTime = sdf.format(date);

        String trainingID = username+"_"+label+"_"+readableTime;
        Log.e("寫入運動紀錄中看參數", "==========================================");
        Log.e("寫入運動紀錄中看參數", "username: " + username);
        Log.e("寫入運動紀錄中看參數", "createAt: " + createAt + " (" + sdf.format(new Date(createAt)) + ")");
        Log.e("寫入運動紀錄中看參數", "finishAt: " + finishAt + " (" + sdf.format(new Date(finishAt)) + ")");
        Log.e("寫入運動紀錄中看參數", "targetTimes: " + targetTimes);
        Log.e("寫入運動紀錄中看參數", "achievedTimes: " + achievedTimes);
        Log.e("寫入運動紀錄中看參數", "durationTime: " + durationTime);
        Log.e("寫入運動紀錄中看參數", "analysisType: " + analysisType);
        Log.e("寫入運動紀錄中看參數", "trainingID: " + trainingID);
        Log.e("寫入運動紀錄中看參數", "readableTime: " + readableTime);
        Log.e("寫入運動紀錄中看參數", "curveJson: " + curveJson);
        Log.e("寫入運動紀錄中看參數", "==========================================");

        //保持舊版參數兼容
//        TrainingHistory history = new TrainingHistory(
//                trainingID,
//                label,
//                maintainStartTime,  // createAt
//                currentTime,        // finishAt
//                target,
//                achieved,
//                duration,
//                curveJson,
//                csv
//        );

//to do 增加建構子
        TrainingHistory history = new TrainingHistory(
                trainingID,
                label,
                maintainStartTime,
                currentTime,
                target,
                achieved,
                duration,
                curveJson,
                csv,           // csvFileName（第9個）
                videoFilePath != null ? new File(videoFilePath).getName() : ""  // videoFileName
        );

        new Thread(() -> {

            // 加 try-catch
            try {
                AppDatabase.getInstance(this).trainingHistoryDao().insert(history);
                Log.d(TAG, "✅ 訓練記錄已寫入資料庫");
            } catch (Exception e) {
                Log.e(TAG, "❌ 訓練記錄寫入失敗: " + e.getMessage());
                e.printStackTrace();
                return;  // 寫入失敗就不上傳
            }


            String TAG_TEST_3 = "NoDatTest";
            try {
                Log.d(TAG_TEST_3, "⏳ 模擬延遲開始...");
                Thread.sleep(30000);
                Log.d(TAG_TEST_3, "⏳ 模擬延遲結束，準備上傳");
            } catch (InterruptedException e) {
                Log.e(TAG_TEST_3, "❌ Thread 被中斷了！上傳不會執行");
                return;
            }


            // 改用新方法
            SupabaseUploader.uploadCsvWithMark(this, csv, trainingID, new SupabaseUploader.UploadCallbackWithId() {
                @Override
                public void onSuccess(String publicUrl, String trainingID) {
                    Log.d(TAG, "✅ CSV 上傳成功: " + publicUrl);
                    Log.d(TAG_TEST_3, "✅ CSV 上傳成功: " + publicUrl);
                }

                @Override
                public void onFailure(String error, String trainingID) {
                    Log.e(TAG, "❌ CSV 上傳失敗: " + error);
                    Log.e(TAG_TEST_3, "❌ CSV 上傳失敗: " + error);
                }
            });


            com.example.rehabilitationapp.data.FirebaseUploader.uploadTodayUnsynced(this, (success, fail) -> {
                //ToDO.. 改為上傳到FIREBASE LOG，證實有到過本地資料庫，查找未更新紀錄，這邊會接到onComplete的回傳。
                //FaceCircleCheckActivity : uploadTodayUnsynced.onComplete(ReciveCallBack) 成功 " + success + " 筆，失敗 " + fail + " 筆
                Log.d(TAG, "自動上傳結果：成功 " + success + " 筆，失敗 " + fail + " 筆");
                Log.d(TAG_TEST_3, "自動上傳結果：成功 " + success + " 筆，失敗 " + fail + " 筆");
            });

        }).start();
    }


    /**
     * 把 JSON 傳到 Vercel API
     * @param json 你要送出的 JSON 字串
     */
    private static final String API_URL = "https://wavecut-production.up.railway.app/"; // Railway 根路徑



    // 2) 簡單的跳頁方法（共 10 行）
    // ★ 用正規化後的名稱決定要塞哪組陣列到 Intent
    //To do ... DEBUG CSV要去弄後端看DEBIG分析跟濾波怎麼做，般去railway API
    private void go(String label, int actual, int target, int durationSec, String csv, String apiJson) {
        String canon = canonicalMotion(label);
        Log.e("GO METHOD", "在GO方法跳轉頁面中..");

        //原本的先改FIGMA的看看
        //Intent it = new Intent(FaceCircleCheckerActivity.this, AnalysisResultActivity.class);
        Intent it = new Intent(FaceCircleCheckerActivity.this, TrainingResultActivity.class);
        it.putExtra("training_label", canon);
        it.putExtra("actual_count", actual);
        it.putExtra("target_count", 5);
        it.putExtra("training_duration", durationSec);
        it.putExtra("csv_file_name", csv);
        if (apiJson != null && !apiJson.isEmpty()) it.putExtra("api_response_json", apiJson);

        if ("poutLip".equals(canon)) {
            double[] times  = dataRecorder.getTimeSecondsArrayForRatio();
            double[] ratios = dataRecorder.getHeightWidthRatioArray();
            it.putExtra("ratio_times", times);
            it.putExtra("ratio_values", ratios);
            android.util.Log.d("GO", "poutLip ratio_times=" + java.util.Arrays.toString(times));
            android.util.Log.d("GO", "poutLip ratio_values=" + java.util.Arrays.toString(ratios));

        } else if ("closeLip".equals(canon)) {
            double[][] tv = dataRecorder.exportLipTimeAndTotal();
            it.putExtra("lip_times",  tv[0]);
            it.putExtra("lip_totals", tv[1]);
            android.util.Log.d("GO", "closeLip lip_times=" + java.util.Arrays.toString(tv[0]));
            android.util.Log.d("GO", "closeLip lip_totals=" + java.util.Arrays.toString(tv[1]));
        }

        startActivity(it);
        finish();
    }

    /**
     * 🎥 開始錄影
     */
    private void startVideoRecording() {
        if (videoCapture == null) {
            Log.e(TAG_3, "❌ VideoCapture 未初始化");
            return;
        }

        try {
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
//            String timestamp = sdf.format(new Date());
//            String fileName = "Training_" + trainingLabel + "_" + timestamp + ".mp4";
//
//            File videoFile = new File(getExternalFilesDir(null), fileName);
//            videoFilePath = videoFile.getAbsolutePath();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String timestamp = sdf.format(new Date());

// 從 SharedPreferences 拿 userId
            SharedPreferences prefs =
                    getSharedPreferences("user_prefs", MODE_PRIVATE);
            String userId = prefs.getString("current_user_id", "guest");

// 在檔名前面加 userId
            String fileName = userId + "_Training_" + trainingLabel + "_" + timestamp + ".mp4";

            File videoFile = new File(getExternalFilesDir(null), fileName);
            videoFilePath = videoFile.getAbsolutePath();

            FileOutputOptions outputOptions = new FileOutputOptions.Builder(videoFile).build();

            currentRecording = videoCapture.getOutput()
                    .prepareRecording(this, outputOptions)
                    .start(ContextCompat.getMainExecutor(this), videoRecordEvent -> {
                        if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                            VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) videoRecordEvent;
                            if (!finalizeEvent.hasError()) {
                                Log.d(TAG_3, "✅ 影片已保存: " + videoFilePath);
                                // ★ 備份到隱藏公共目錄
                                try {
                                    File publicDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                                            android.os.Environment.DIRECTORY_DOWNLOADS), "rhabdata");
                                    if (!publicDir.exists()) publicDir.mkdirs();
                                    File src = new File(videoFilePath);
                                    File dst = new File(publicDir, src.getName());
                                    java.nio.file.Files.copy(src.toPath(), dst.toPath(),
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                    Log.d(TAG_3, "✅ 影片備份成功: " + dst.getAbsolutePath());
                                } catch (Exception e) {
                                    Log.e(TAG_3, "⚠️ 影片備份失敗: " + e.getMessage());
                                }

                            } else {
                                Log.e(TAG_3, "❌ 影片錄製失敗: " + finalizeEvent.getError());
                            }
                        }
                    });

            Log.d(TAG_3, "🎥 開始錄影: " + fileName);
        } catch (Exception e) {
            Log.e(TAG_3, "❌ 開始錄影失敗", e);
        }
    }

    /**
     * 🎥 停止錄影
     */
    private void stopVideoRecording() {
        if (currentRecording != null) {
            currentRecording.stop();
            currentRecording = null;
            Log.d(TAG_3, "🎥 停止錄影");
        }
    }

    private String csvState() {
        if (!countdownFinished) return "COUNTDOWN";//避免記到倒數
        CircleOverlayView.Status ui = (overlayView != null) ? overlayView.getStatus() : null;
        if (ui == CircleOverlayView.Status.DEMO) return "DEMO";
        if (currentState == AppState.CALIBRATING) return "CALIBRATING";
        if (currentState == AppState.MAINTAINING) return "MAINTAINING";
        if (ui == CircleOverlayView.Status.OUT_OF_BOUND) return "OUT_OF_BOUND";
        return "UNKNOWN";
    }

    // ==================== 🆕 教學彈窗與倒數功能 ====================

    /**
     * 🎬 顯示教學彈窗（影片 + 文字說明）
     * 沿用原本的 dialog_tutorial.xml 風格
     */
    private void showTutorialDialog() {
        if (tutorialShown) return;
        tutorialShown = true;

        // 暫停校正流程（不讓 calibration 開始計時）
        countdownFinished = false;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tutorial, null);
        builder.setView(dialogView);

        // 取得元件
        VideoView videoView = dialogView.findViewById(R.id.tutorial_video);
        TextView descriptionText = dialogView.findViewById(R.id.tutorial_description);

        // 設定文字說明
        descriptionText.setText(getTutorialDescription());

        // 設定影片
        int videoResId = getTutorialVideoResId();
        if (videoResId != 0) {
            String videoPath = "android.resource://" + getPackageName() + "/" + videoResId;
            videoView.setVideoURI(android.net.Uri.parse(videoPath));
            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                videoView.start();
            });
        }

        // 建立對話框
        AlertDialog dialog = builder
                .setTitle(trainingLabel != null ? trainingLabel : "訓練說明")
                .setCancelable(false)  // 不能按返回關閉
                .setPositiveButton("知道了", (d, which) -> {
                    videoView.stopPlayback();
                    // 按下「知道了」後開始倒數
                    showCountdown();
                })
                .create();

        dialog.setOnDismissListener(d -> videoView.stopPlayback());
        dialog.show();
    }

    /**
     * 🔢 顯示 3-2-1 倒數（用原本的 timerText 和 cueText）
     */
    private void showCountdown() {
        // 確保 cueText 和 timerText 可見
        if (cueText != null) {
            cueText.setVisibility(View.VISIBLE);
            cueText.setText("請將臉部對準框框");
        }
        if (timerText != null) {
            timerText.setVisibility(View.VISIBLE);
            timerText.setText("3");
            timerText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 48);
        }

        // 3 秒倒數
        new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000) + 1;

                if (timerText != null) {
                    timerText.setText(String.valueOf(secondsLeft));
                }

                // 根據秒數更新提示
                if (cueText != null) {
                    switch (secondsLeft) {
                        case 3:
                            cueText.setText("準備開始，請把臉對準圓框");
                            break;
                        case 2:
                            cueText.setText("準備開始，請把臉對準圓框");
                            break;
                        case 1:
                            cueText.setText("準備開始，請把臉對準圓框");
                            break;
                    }
                }
            }

            @Override
            public void onFinish() {
                // 恢復 timerText 原本的樣式
                if (timerText != null) {
                    timerText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24);
                }

                // 清空 cueText（之後校正流程會自己設定）
                if (cueText != null) {
                    cueText.setText("");
                }

                // 🆕 重設 CSV 的開始時間
                if (dataRecorder != null) {
                    dataRecorder.resetStartTime();
                }

                // 🆕 倒數結束，正式開始校正流程
                countdownFinished = true;
                Log.d(TAG, "✅ 倒數結束，開始校正流程");
            }
        }.start();
    }


    // ==================== 🔧 修正後的方法 ====================
// 把這兩個方法替換到 FaceCircleCheckerActivity.java 裡面

    /**
     * 🎬 根據 trainingLabel 取得教學影片資源 ID
     */
    private int getTutorialVideoResId() {
        if (trainingLabel == null) return 0;

        switch (trainingLabel) {
            case "PUFF_CHEEK":
                return R.raw.puffcheek_class;
            case "REDUCE_CHEEK":
                return R.raw.reduce_cheek_class;
            case "POUT_LIPS":
            case "LOUT_LIP":
            case "poutLip":
                return R.raw.loutlip_class;
            case "SIP_LIPS":
            case "SIP_LIP":
            case "closeLip":
                return R.raw.siplip_class;
            case "TONGUE_LEFT":
                return R.raw.tongue_left_class;
            case "TONGUE_RIGHT":
                return R.raw.tongue_right_class;
            case "TONGUE_FOWARD":
            case "TONGUE_FORWARD":
                return R.raw.tongue_foward_class;
            case "TONGUE_UP":
                return R.raw.tongue_up_class;
            case "TONGUE_DOWN":
                return R.raw.tongue_down_class;
            default:
                return 0;
        }
    }

    /**
     * 📝 根據 trainingLabel 取得教學說明文字
     */
    private String getTutorialDescription() {
        if (trainingLabel == null) {
            return "請依照指示進行訓練。";
        }

        switch (trainingLabel) {
            // 臉頰 - 鼓頰
            case "PUFF_CHEEK":
                return "1. 請先取下眼鏡等會遮住臉部的物品。\n" +
                        "2. 請按照文字導引，鼓起兩側臉頰或自然放鬆。\n\n" +
//                        "2. 鼓起兩側臉頰並保持至少 1.5~3 秒，每次動作間隔約 1 秒。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、表情放鬆。\n" +
                        "．黃色(第一次)：請放鬆表情，保持頭部不動。\n" +
                        "．藍色：做鼓臉頰動作並維持，提供系統作為參考。\n" +
                        "．黃色(第二次)：放鬆表情，保持頭部不動。\n" +
                        "．綠色：開始訓練，請按文字導引進行復健動作。";

            // 臉頰 - 縮頰
            case "REDUCE_CHEEK":
                return "1. 請先取下眼鏡等會遮住臉部的物品。\n" +
//                        "2. 縮起兩側臉頰並保持至少 1.5~3 秒，每次動作間隔約 1 秒。\n\n" +
                        "2. 請按照文字導引，縮起兩側臉頰或自然放鬆。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、表情放鬆。\n" +
                        "．黃色(第一次)：校正階段，請保持不動。\n" +
                        "．藍色：請做縮臉頰的動作並維持，供系統參考。\n" +
                        "．黃色(第二次)：回到放鬆，保持頭部不動。\n" +
                        "．綠色：開始正式訓練，請依文字導引進行復健動作。";

            // 嘴唇 - 嘟嘴（支援多種寫法）
            case "POUT_LIPS":
            case "LOUT_LIP":
            case "poutLip":
                return  "1. 請依照文字導引，嘴唇往前嘟起並保持或自然放鬆。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不晃動。\n" +
                        "．黃色(第一次)：校正階段，請保持不動。\n" +
                        "．藍色：做嘟嘴唇的動作，供系統參考。\n" +
                        "．黃色(第二次)：回到放鬆，並保持頭部不動。\n" +
                        "．綠色：開始正式訓練，請依文字導引進行復健動作。";

            // 嘴唇 - 抿嘴（支援多種寫法）
            case "SIP_LIPS":
            case "SIP_LIP":
            case "closeLip":
                return  "1. 請依照文字導引，保持雙脣內縮或自然放鬆。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色（第一次）：校正階段，請保持不動。\n" +
                        "．藍色：請做抿嘴動作並保持，供系統參考。\n" +
                        "．黃色(第二次)：回到放鬆，並保持頭部不動。\n" +
                        "．綠色：開始正式訓練，依文字導引進行復健動作。";

            // 舌頭 - 往左
            case "TONGUE_LEFT":
                return  "1. 動作時請張嘴向左伸出舌頭，可利於系統檢測。\n" +
                        "" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色（第一次）：校正階段，請保持不動。\n" +
                        "．藍色：請張嘴將舌頭往左伸並保持，提供系統作為參考。\n" +
                        "．黃色(第二次)：回到放鬆，並保持不動。\n" +
                        "．綠色：開始正式訓練，依文字導引進行復健動作。";
            // 舌頭 - 往右
            case "TONGUE_RIGHT":
                return  "1. 動作時請張嘴，向右伸出舌頭，可利於系統檢測。\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色（第一次）：校正階段，請保持不動。\n" +
                        "．藍色：請張嘴將舌頭向右伸並保持，提供系統作為參考。\n" +
                        "．黃色(第二次)：回到放鬆，並保持不動。\n" +
                        "．綠色：開始正式訓練，依文字導引進行復健動作。";

            // 舌頭 - 往前
            case "TONGUE_FOWARD":
            case "TONGUE_FORWARD":
                return  "1. 動作時請張嘴向前伸出舌頭。\n" +
                        "2. 面部稍微朝下，再往前方伸舌，可利於系統檢測。\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色（第一次）：校正階段，請保持不動。\n" +
                        "．藍色：請張嘴將舌頭往前伸並保持，提供系統作為參考。\n" +
                        "．黃色(第二次)：回到放鬆，並保持不動。\n" +
                        "．綠色：開始正式訓練，依文字導引進行復健動作。";

            // 舌頭 - 往後 --已棄用
            case "TONGUE_BACK":
                return "1. 動作時請儘可能保持張嘴，確認舌頭檢測框初始位置置中。\n" +
                        "2. 舌頭往後並保持至少 1.5~3 秒，並回到初始位置。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色：校正階段，請保持不動。\n" +
                        "．藍色：請做一次舌頭往後動作，提供系統作為參考。\n" +
                        "．綠色：開始正式訓練。";

            // 舌頭 - 往上
            case "TONGUE_UP":
                // 第 8 種動作
                return  "1. 動作時請張嘴往上伸舌頭。\n" +
                        "2. 伸至約上唇繫帶位置，可利於系統檢測。\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色（第一次）：校正階段，請保持不動。\n" +
                        "．藍色：請張嘴將舌頭往上伸並保持，提供系統作為參考。\n" +
                        "．黃色(第二次)：回到放鬆，並保持不動。\n" +
                        "．綠色：開始正式訓練，依文字導引進行復健動作。";

            // 舌頭 - 往下
            case "TONGUE_DOWN":
                return  "1. 動作時請張嘴往下伸出舌頭。\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色（第一次）：校正階段，請保持不動。\n" +
                        "．藍色：請張嘴將舌頭往下伸並保持，提供系統作為參考。\n" +
                        "．黃色(第二次)：回到放鬆，並保持不動。\n" +
                        "．綠色：開始正式訓練，依文字導引進行復健動作。";

            default:
                return "請依照指示進行訓練。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．黃色：校正中\n" +
                        "．藍色：示範動作\n" +
                        "．綠色：正式訓練";
        }
    }

    private float calculateEyeDistance(float[][] landmarks) {
        float eyeLx = landmarks[263][0];
        float eyeLy = landmarks[263][1];
        float eyeRx = landmarks[33][0];
        float eyeRy = landmarks[33][1];

        return (float) Math.sqrt((eyeRx - eyeLx) * (eyeRx - eyeLx)
                + (eyeRy - eyeLy) * (eyeRy - eyeLy));
    }


    private boolean isHeadStable(float[][] landmarks01) {
        float[][] cur = new float[HEAD_STABLE_POINTS_COUNT][2];
        for (int i = 0; i < HEAD_STABLE_POINTS_COUNT; i++) {
            int idx = HEAD_STABLE_INDICES[i];
            if (idx >= landmarks01.length) return true;
            cur[i][0] = landmarks01[idx][0];
            cur[i][1] = landmarks01[idx][1];
        }
        if (prevStablePoints == null) {
            prevStablePoints = cur;
            lastHeadStable = true;
            return true;
        }
        float cx = 0, cy = 0, px = 0, py = 0;
        for (int i = 0; i < HEAD_STABLE_POINTS_COUNT; i++) {
            cx += cur[i][0]; cy += cur[i][1];
            px += prevStablePoints[i][0]; py += prevStablePoints[i][1];
        }
        cx /= HEAD_STABLE_POINTS_COUNT; cy /= HEAD_STABLE_POINTS_COUNT;
        px /= HEAD_STABLE_POINTS_COUNT; py /= HEAD_STABLE_POINTS_COUNT;
        float movement = (float) Math.sqrt((cx - px) * (cx - px) + (cy - py) * (cy - py));
        prevStablePoints = cur;
        headMotionHistory.addLast(movement);
        while (headMotionHistory.size() > HEAD_MOTION_WINDOW) headMotionHistory.removeFirst();
        float avg = 0;
        for (float v : headMotionHistory) avg += v;
        avg /= headMotionHistory.size();
        lastHeadStable = avg < HEAD_MOTION_THRESHOLD;
        if (!lastHeadStable) {
            headStableCooldown = 30;
        }
        return lastHeadStable;
    }

    private void resetHeadMotion() {
        // 不清 prevStablePoints，避免下一幀無法比較導致閃爍
        headMotionHistory.clear();
        lastHeadStable = true;
        headStableCooldown = 0;
    }



}
