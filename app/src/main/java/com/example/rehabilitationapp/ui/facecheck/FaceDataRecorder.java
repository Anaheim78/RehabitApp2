package com.example.rehabilitationapp.ui.facecheck;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.example.rehabilitationapp.ui.analysis.CSVPeakAnalyzer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

//根據特定動作類型
public class FaceDataRecorder {
    private static final String TAG = "FaceDataRecorder";

    private Context context;
    private String trainingLabel;
    private int trainingType;
    private List<String> dataLines;
    private String fileName;

    // 🔥 新增：記錄開始時間，用於計算相對時間
    private long startTime = 0;

    // MediaPipe 臉部關鍵點索引
    private static final int[] UPPER_LIP_INDICES = {61, 84, 17, 314, 405, 320, 307, 375, 321, 308, 324, 318};
    private static final int[] LOWER_LIP_INDICES = {78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415};

    //CSV Header
    private static final String CHEEKS_HEADER = "time_seconds,state,LI_X,LI_Y,RI_X,RI_Y";
    private static final String Lip_Prot_HEADER =  "time_seconds,state,mouth_height,mouth_width,height_width_ratio";
    private static final String Lip_Closure_HEADER = "time_seconds,state,total_lip_area";
    private static final String TONGUE_HEADER =
            "time_seconds,state," +
                    "tongue_detected," +
                    "bbox_left,bbox_top,bbox_right,bbox_bottom," +
                    "eyeL_x,eyeL_y,eyeR_x,eyeR_y," +
                    "browC_x,browC_y,nose_x,nose_y," +
                    "imgW,imgH,frame_id," +
                    "origin_x,origin_y,theta_rad,dio," +
                    "cx_img,cy_img,x_norm,y_norm";
    /**
     * ==== 舌頭補正參數欄位說明 ====
     *
     * imgW, imgH : 原始影像大小（像素）。用於之後把相對座標還原成比例（例如歸一化到 0–1）。
     * frame_id : 影格序號或時間戳。方便對齊時間軸、追蹤特定幀。
     * origin_x, origin_y :  選定的基準點（通常選鼻尖或眉心）。所有座標會轉換成「相對於這個點」的形式，可以消除臉在畫面裡平移造成的影響。
     * theta_rad : 臉部旋轉角度（由兩眼連線計算），用來把臉「拉正」，避免頭部傾斜影響判斷。
     * dio (distance between eyes) *   兩眼之間的距離: 當作縮放基準，統一不同人臉大小或遠近的比例。
     * cx_img, cy_img   舌頭 YOLO 框的中心點（原始像素座標）。
     * x_norm, y_norm
     *   舌頭中心點經過「平移 + 旋轉 + 縮放」補正後的相對座標。      *   在臉部坐標系裡：x_norm → 左右偏移 y_norm → 上下偏移      *   用於判斷舌頭動作方向（UP / DOWN / LEFT / RIGHT）。
     */

    // 🔥 Callback 介面
    public interface DataSaveCallback {
        void onComplete(CSVPeakAnalyzer.AnalysisResult result);
        void onError(String error);
    }

    /*
     * CSV建檔並給定檔名/標頭，根據每種動作不同。
    */
    public FaceDataRecorder(Context context, String trainingLabel, int trainingType) {
        this.context = context;
        this.trainingLabel = trainingLabel;
        this.trainingType = trainingType;
        this.dataLines = new ArrayList<>();

        // 🔥 記錄開始時間
        this.startTime = System.currentTimeMillis();
        // 建立檔案名稱
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        this.fileName = String.format("FaceTraining_%s_%s.csv", trainingLabel, timestamp);
        // 初始化 CSV 標題
        initializeCSV();
        // 寫入Log
        Log.d(TAG, "初始化記錄器 - 檔案: " + fileName + ", 開始時間: " + startTime);
    }

    private void initializeCSV() {
        String header = "";
        if ("SIP_LIPS".equals(trainingLabel)) {
            header = Lip_Closure_HEADER; // 改成總面積
        } else if ("POUT_LIPS".equals(trainingLabel)) {
            header = Lip_Prot_HEADER;
            //header = "time_seconds,state,mouth_height,mouth_width,height_width_ratio";
        } else if ("舌頭".equals(trainingLabel) ||
                "TONGUE_LEFT".equals(trainingLabel) ||
                "TONGUE_RIGHT".equals(trainingLabel) ||
                "TONGUE_FOWARD".equals(trainingLabel) ||
                "TONGUE_BACK".equals(trainingLabel) ||
                "TONGUE_UP".equals(trainingLabel) ||
                "TONGUE_DOWN".equals(trainingLabel)) {
            header = TONGUE_HEADER;
        } else if ("PUFF_CHEEK".equals(trainingLabel)||"REDUCE_CHEEK".equals(trainingLabel)) {
            header = CHEEKS_HEADER;
        } else {
            header = "time_seconds,state,metric_value"; // 預設格式
        }
        dataLines.add(header);
        Log.d(TAG, "CSV 標題: " + header);
    }


    //多載Overload，用參數分流動作。
    //calculateXxx，每種動作內處理會叫用的方法，計算的CSV各cell指標的內容數值。
    //多載:嘴唇
    public void recordLandmarkData(String state, float[][] landmarks, Boolean JawDetected) {
        try {
            // 🔥 改用相對時間，從0開始，以秒為單位
            long currentTime = System.currentTimeMillis();
            double relativeTimeSeconds = (currentTime - startTime) / 1000.0;

            String dataLine = "";

            if ("SIP_LIPS".equals(trainingLabel)) {
                // 🔥 改用掃描線方法計算上下嘴唇面積
                float upperLipArea = calculateLipAreaByScanline(landmarks, UPPER_LIP_INDICES);
                float lowerLipArea = calculateLipAreaByScanline(landmarks, LOWER_LIP_INDICES);
                // ✨ 總嘴唇面積 = 上唇 + 下唇
                float totalLipArea = upperLipArea + lowerLipArea;

                dataLine = String.format(Locale.getDefault(), "%.3f,%s,%.3f,%.3f,%.3f",
                        relativeTimeSeconds, state, upperLipArea, lowerLipArea, totalLipArea);

                Log.d(TAG, String.format("抿嘴數據 [%.3fs] - 上唇面積: %.3f, 下唇面積: %.3f, 比值: %.3f",
                        relativeTimeSeconds, upperLipArea, lowerLipArea, totalLipArea));

            } else if ("POUT_LIPS".equals(trainingLabel)) {
                // 🔥 改用外緣點計算嘴巴高度和寬度
                float[] mouthDimensions = calculateMouthDimensionsImproved(landmarks);
                float height = mouthDimensions[0];
                float width = mouthDimensions[1];
                float heightWidthRatio = width > 0 ? height / width : 0;

                dataLine = String.format(Locale.getDefault(), "%.3f,%s,%.3f,%.3f,%.3f",
                        relativeTimeSeconds, state, height, width, heightWidthRatio);

                Log.d(TAG, String.format("嘟嘴數據 [%.3fs] - 高度: %.3f, 寬度: %.3f, 比值: %.3f",
                        relativeTimeSeconds, height, width, heightWidthRatio));
            }
            else if ("JAW_LEFT".equals(trainingLabel)||"JAW_RIGHT".equals(trainingLabel)) {
                // 🔥 改用三點平均計算下顎水平位移
                float[] jawShift = calculateJawMoving(landmarks);
                float shift = jawShift[0];      // 可正可負
                float absShift = jawShift[1];   // 絕對值大小

                dataLine = String.format(Locale.getDefault(), "%.3f,%s,%.3f,%.3f",
                        relativeTimeSeconds, state, shift, absShift);

                Log.d(TAG, String.format("下顎數據 [%.3fs] - 位移: %.3f, 絕對值: %.3f",
                        relativeTimeSeconds, shift, absShift));
            }

            if (!dataLine.isEmpty()) {
                dataLines.add(dataLine);
            }

        } catch (Exception e) {
            Log.e(TAG, "記錄數據時發生錯誤", e);
        }
    }
    //多載:臉頰
    public void recordLandmarkData(String state, Float liX, Float liY, Float riX, Float riY, Float liRawX, Float liRawY, Float riRawX, Float riRawY) {
        try {
            if (!("PUFF_CHEEK".equals(trainingLabel)||"REDUCE_CHEEK".equals(trainingLabel))) return; // 僅在臉頰模式有效

            long now = System.currentTimeMillis();
            double t = (now - startTime) / 1000.0;

            // 防呆
            float lix = (liX != null) ? liX : 0f;
            float liy = (liY != null) ? liY : 0f;
            float rix = (riX != null) ? riX : 0f;
            float riy = (riY != null) ? riY : 0f;

            float liRx = (liRawX != null) ? liRawX : 0f;
            float liRy = (liRawY != null) ? liRawY : 0f;
            float riRx = (riRawX != null) ? riRawX : 0f;
            float riRy = (riRawY != null) ? riRawY : 0f;

            double relativeTimeSeconds = (now - startTime) / 1000.0;

            // 一行有補償後 + 原始
            String line = String.format(Locale.getDefault(),
                    "%.3f,%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                    t, state, lix, liy, rix, riy, liRx, liRy, riRx, riRy);

            Log.d(TAG, String.format(
                    "臉頰光流 [%.3fs] - LI(%.6f,%.6f), RI(%.6f,%.6f), LI_RAW(%.6f,%.6f), RI_RAW(%.6f,%.6f)",
                    relativeTimeSeconds, lix, liy, rix, riy, liRx, liRy, riRx, riRy));

            dataLines.add(line);

        } catch (Exception e) {
            Log.e(TAG, "記錄臉頰光流時發生錯誤", e);
        }
    }
    //多載:舌頭
    public void recordLandmarkData(
            String state,
            boolean tongueDetected,
            android.graphics.Rect bboxImgOrNull,
            float eyeLx, float eyeLy, float eyeRx, float eyeRy,    // 兩眼外角 (263,33)
            float browCx, float browCy, float noseX, float noseY,  // 眉心(168)、鼻尖(1)
            int imgW, int imgH,
            long frameIdOrTsMillis,
            float originX, float originY,
            float thetaRad,
            float dio,
            float cxImg, float cyImg,
            float xNorm, float yNorm
    ) {
        // 直接委派給舌頭寫檔實作，避免重複字串格式化邏輯
        recordTongueData(
                state,
                tongueDetected,
                bboxImgOrNull,
                eyeLx, eyeLy, eyeRx, eyeRy,
                browCx, browCy, noseX, noseY,
                imgW, imgH,
                frameIdOrTsMillis,
                originX, originY,
                thetaRad,
                dio,
                cxImg, cyImg,
                xNorm, yNorm
        );
    }

    public void recordTongueData(
            String state,
            boolean tongueDetected,
            android.graphics.Rect bboxImgOrNull,
            float eyeLx, float eyeLy, float eyeRx, float eyeRy,
            float browCx, float browCy, float noseX, float noseY,
            int imgW, int imgH,
            long frameIdOrTsMillis,
            float originX, float originY,
            float thetaRad,
            float dio,
            float cxImg, float cyImg,
            float xNorm, float yNorm
    ) {
        try {
            long now = System.currentTimeMillis();
            double t = (now - startTime) / 1000.0;  // 相對時間 (秒)

            // bbox 預設 -1，若有偵測才填
            int L=-1, T=-1, R=-1, B=-1;
            int detected = (tongueDetected && bboxImgOrNull != null) ? 1 : 0;
            if (detected == 1) {
                L = bboxImgOrNull.left;
                T = bboxImgOrNull.top;
                R = bboxImgOrNull.right;
                B = bboxImgOrNull.bottom;
            }

            // 順序必須與 HEADER_TONGUE 完全一致
            String line = String.format(Locale.US,
                    "%.3f,%s,%d," +                 // time_seconds, state, tongue_detected
                            "%d,%d,%d,%d," +                // bbox_left, bbox_top, bbox_right, bbox_bottom
                            "%.3f,%.3f,%.3f,%.3f," +        // eyeL_x, eyeL_y, eyeR_x, eyeR_y
                            "%.3f,%.3f,%.3f,%.3f," +        // browC_x, browC_y, nose_x, nose_y
                            "%d,%d,%d," +                   // imgW, imgH, frame_id
                            "%.3f,%.3f,%.6f,%.3f," +        // origin_x, origin_y, theta_rad, dio
                            "%.3f,%.3f,%.5f,%.5f",          // cx_img, cy_img, x_norm, y_norm
                    t, state, detected,
                    L, T, R, B,
                    eyeLx, eyeLy, eyeRx, eyeRy,
                    browCx, browCy, noseX, noseY,
                    imgW, imgH, frameIdOrTsMillis,
                    originX, originY, thetaRad, dio,
                    cxImg, cyImg, xNorm, yNorm
            );

            dataLines.add(line); // 寫入緩衝區

        } catch (Exception e) {
            Log.e(TAG, "recordTongueData error", e);
        }
    }

    // 方法：用掃描線計算嘴唇面積
    private float calculateLipAreaByScanline(float[][] landmarks, int[] lipIndices) {
        try {
            List<float[]> lipPoints = new ArrayList<>();

            // 收集嘴唇關鍵點
            for (int index : lipIndices) {
                if (index < landmarks.length) {
                    lipPoints.add(new float[]{landmarks[index][0], landmarks[index][1]});
                }
            }

            if (lipPoints.size() < 3) {
                return 0; // 不足以形成多邊形
            }

            // 找出Y軸的範圍
            float minY = Float.MAX_VALUE;
            float maxY = Float.MIN_VALUE;
            for (float[] point : lipPoints) {
                minY = Math.min(minY, point[1]);
                maxY = Math.max(maxY, point[1]);
            }

            // 🔥 掃描線方法：每隔0.5像素掃描一條橫線
            float totalArea = 0;
            float scanStep = 0.5f; // 掃描精度

            for (float y = minY; y <= maxY; y += scanStep) {
                List<Float> intersections = new ArrayList<>();

                // 找出這條水平線與多邊形邊界的交點
                for (int i = 0; i < lipPoints.size(); i++) {
                    int j = (i + 1) % lipPoints.size();
                    float[] p1 = lipPoints.get(i);
                    float[] p2 = lipPoints.get(j);

                    // 檢查線段是否與水平掃描線相交
                    if ((p1[1] <= y && y < p2[1]) || (p2[1] <= y && y < p1[1])) {
                        // 計算交點的X坐標
                        float x = p1[0] + (y - p1[1]) * (p2[0] - p1[0]) / (p2[1] - p1[1]);
                        intersections.add(x);
                    }
                }

                // 排序交點
                Collections.sort(intersections);

                // 計算這條掃描線上的面積（成對的交點之間）
                for (int i = 0; i < intersections.size() - 1; i += 2) {
                    if (i + 1 < intersections.size()) {
                        float lineWidth = Math.abs(intersections.get(i + 1) - intersections.get(i));
                        totalArea += lineWidth * scanStep;
                    }
                }
            }

            Log.d(TAG, String.format("掃描線面積計算完成 - 總面積: %.3f, 掃描範圍: %.1f to %.1f",
                    totalArea, minY, maxY));

            return totalArea;

        } catch (Exception e) {
            Log.e(TAG, "掃描線計算嘴唇面積時發生錯誤", e);
            return 0;
        }
    }

    // 🔥 改良版：用外緣點計算嘴巴高度和寬度
    private float[] calculateMouthDimensionsImproved(float[][] landmarks) {
        try {
            // 🔥 更準確的嘴角點 (61: 左嘴角, 291: 右嘴角)
            float leftCornerX = landmarks[61][0];
            float rightCornerX = landmarks[291][0];
            float mouthWidth = Math.abs(rightCornerX - leftCornerX);

            // 🔥 找出嘴唇外緣的最高和最低點
            // 上唇外緣關鍵點
            int[] upperOuterIndices = {61, 62, 63, 64, 65, 66, 67, 291, 292, 293, 294, 295, 296, 297};
            // 下唇外緣關鍵點
            int[] lowerOuterIndices = {61, 84, 17, 314, 405, 320, 307, 291, 375, 321, 308, 324, 318};

            float highestY = Float.MAX_VALUE; // Y軸越小越高
            float lowestY = Float.MIN_VALUE;  // Y軸越大越低

            // 找上唇最高點
            for (int index : upperOuterIndices) {
                if (index < landmarks.length) {
                    highestY = Math.min(highestY, landmarks[index][1]);
                }
            }

            // 找下唇最低點
            for (int index : lowerOuterIndices) {
                if (index < landmarks.length) {
                    lowestY = Math.max(lowestY, landmarks[index][1]);
                }
            }

            float mouthHeight = Math.abs(lowestY - highestY);

            Log.d(TAG, String.format("嘴巴尺寸 - 寬度: %.3f (左%.1f → 右%.1f), 高度: %.3f (上%.1f → 下%.1f)",
                    mouthWidth, leftCornerX, rightCornerX, mouthHeight, highestY, lowestY));

            return new float[]{mouthHeight, mouthWidth};

        } catch (Exception e) {
            Log.e(TAG, "計算嘴巴尺寸時發生錯誤", e);
            return new float[]{0, 0};
        }
    }

    // 方法 : 計算下顎位移
    // 方法 : 計算下顎位移 (三點平均, 含正規化)
    private float[] calculateJawMoving(float[][] landmarks) {
        try {
            // === 基準點 ===
            float noseX = landmarks[1][0];   // 鼻尖
            float noseY = landmarks[1][1];
            float eyeRx = landmarks[33][0];  // 右眼外側
            float eyeRy = landmarks[33][1];
            float eyeLx = landmarks[263][0]; // 左眼外側
            float eyeLy = landmarks[263][1];

            // === 下巴三點 (取平均, 減少抖動) ===
            float chinX = (landmarks[152][0] + landmarks[377][0] + landmarks[147][0]) / 3f;
            float chinY = (landmarks[152][1] + landmarks[377][1] + landmarks[147][1]) / 3f;

            // === 計算眼睛距離，作為比例尺 ===
            double dio = Math.sqrt(Math.pow(eyeRx - eyeLx, 2) + Math.pow(eyeRy - eyeLy, 2));
            if (dio < 1e-6) {
                Log.w(TAG, "眼睛距離太小，無法正規化");
                return new float[]{0f, 0f};
            }

            // === 計算頭部旋轉角度 (眼睛連線角度) ===
            double theta = Math.atan2(eyeRy - eyeLy, eyeRx - eyeLx);

            // === 下巴相對鼻尖的向量 ===
            double relX = chinX - noseX;
            double relY = chinY - noseY;

            // === 旋轉校正座標系 ===
            double rotX = relX * Math.cos(-theta) - relY * Math.sin(-theta);

            // === 正規化位移 (相對眼距) ===
            float jaw_x_norm = (float)(rotX / dio);
            float jaw_abs = Math.abs(jaw_x_norm);

            Log.d(TAG, String.format("下顎位移(三點平均) → jaw_x_norm=%.4f, jaw_abs=%.4f", jaw_x_norm, jaw_abs));

            return new float[]{jaw_x_norm, jaw_abs};

        } catch (Exception e) {
            Log.e(TAG, "計算下顎位移時發生錯誤", e);
            return new float[]{0f, 0f};
        }
    }



    // 🔥 新增：帶 callback 的儲存方法
    public void saveToFileWithCallback(DataSaveCallback callback) {
        try {
            // 儲存到 Downloads 資料夾，使用者容易找到
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, fileName);

            FileWriter writer = new FileWriter(file);
            for (String line : dataLines) {
                writer.write(line + "\n");
            }
            writer.close();

            Log.d(TAG, "✅ 檔案儲存成功: " + file.getAbsolutePath());
            Log.d(TAG, "📊 總共記錄了 " + (dataLines.size() - 1) + " 筆數據");

            // 🔥 檔案儲存完成後進行峰值分析，並通過 callback 回傳結果
            performPeakAnalysisWithCallback(callback);

            // 使用 Handler 切換到主線程顯示 Toast
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "檔案已儲存至下載資料夾", Toast.LENGTH_SHORT).show()
            );

        } catch (IOException e) {
            Log.e(TAG, "❌ 儲存檔案失敗", e);

            // 🔥 錯誤回調
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    callback.onError("儲存失敗: " + e.getMessage())
            );
        }
    }


    // 🔥 新增：帶 callback 的峰值分析方法
    private void performPeakAnalysisWithCallback(DataSaveCallback callback) {
        Log.d(TAG, "🎯 開始進行峰值分析...");

        // 在背景線程執行峰值分析
        new Thread(() -> {
            try {
                // 調用 CSV 峰值分析器
                CSVPeakAnalyzer.AnalysisResult result = CSVPeakAnalyzer.analyzePeaksFromFile(context, fileName);

                if (result.success) {
                    Log.d(TAG, "✅ 峰值分析完成!");
                    Log.d(TAG, String.format("📊 峰值統計 - 校正: %d, 維持: %d, 總計: %d",
                            result.calibratingPeaks, result.maintainingPeaks, result.totalPeaks));

                    // 🔥 成功回調
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            callback.onComplete(result)
                    );

                } else {
                    Log.e(TAG, "❌ 峰值分析失敗: " + result.errorMessage);

                    // 🔥 失敗回調
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            callback.onError("峰值分析失敗: " + result.errorMessage)
                    );
                }

            } catch (Exception e) {
                Log.e(TAG, "峰值分析過程發生錯誤", e);

                // 🔥 異常回調
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        callback.onError("峰值分析錯誤: " + e.getMessage())
                );
            }

        }).start();
    }


    public void clearData() {
        dataLines.clear();
        initializeCSV();
        Log.d(TAG, "清空數據");
    }

    public int getDataCount() {
        return Math.max(0, dataLines.size() - 1); // 扣除標題行
    }

    // 🔥 新增：獲取檔案名稱（供外部使用）
    public String getFileName() {
        return fileName;
    }
}