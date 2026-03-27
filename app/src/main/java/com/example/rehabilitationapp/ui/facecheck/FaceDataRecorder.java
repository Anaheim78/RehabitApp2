package com.example.rehabilitationapp.ui.facecheck;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.example.rehabilitationapp.ui.analysis.CSVMotioner;
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
    //recordLandmarkData是多載(從FACECHECK呼叫的參數決定)，dataLines會儲存CSVHEDAER(第一行)跟數值(第2~N行)。
    private String fileName;

    // 🔥 新增：記錄開始時間，用於計算相對時間
    private long startTime = 0;

    // MediaPipe 臉部關鍵點索引 BAK 錯誤
//    private static final int[] UPPER_LIP_INDICES = {61, 84, 17, 314, 405, 320, 307, 375, 321, 308, 324, 318};
//    private static final int[] LOWER_LIP_INDICES = {78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415};

    // MediaPipe 臉部關鍵點索引（封閉輪廓，順時針）
// 上唇紅色部分：外緣→內緣→接回起點
    private static final int[] UPPER_LIP_INDICES = {185, 40, 39, 37, 0, 267, 269, 270, 409, 415, 310, 311, 312, 13, 82, 81, 42, 183, 78};
    // 下唇紅色部分：外緣→內緣→接回起點
    private static final int[] LOWER_LIP_INDICES = {61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 308, 324, 318, 402, 317, 14, 87, 178, 88, 95};
    // 嘴唇外緣（嘟嘴Z軸用）
// 嘴唇所有點（外緣 + 內緣）- 用於存 CSV
    private static final int[] LIP_OUTER_IDXS = {61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185};
    private static final int[] LIP_INNER_IDXS = {78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415, 310, 311, 312, 13, 82, 81, 80, 191};

    //CSV Header
//    private static final String CHEEKS_HEADER = "time_seconds,state,LI_X,LI_Y,RI_X,RI_Y";
    // 臉頰 FULL (27+27) 個點的 header，直接展開成 CSV 欄位名稱
    // ORIG 18+18 + FULL 額外 9+9 = 共 27+27
    private static final String CHEEKS_HEADER =
            "time_seconds,state" +
                    // ===== 左臉頰 ORIG 18 點 =====
                    ",point117_x,point117_y,point117_z" +
                    ",point118_x,point118_y,point118_z" +
                    ",point101_x,point101_y,point101_z" +
                    ",point36_x,point36_y,point36_z" +
                    ",point203_x,point203_y,point203_z" +
                    ",point212_x,point212_y,point212_z" +
                    ",point214_x,point214_y,point214_z" +
                    ",point192_x,point192_y,point192_z" +
                    ",point147_x,point147_y,point147_z" +
                    ",point123_x,point123_y,point123_z" +
                    ",point98_x,point98_y,point98_z" +
                    ",point97_x,point97_y,point97_z" +
                    ",point164_x,point164_y,point164_z" +
                    ",point0_x,point0_y,point0_z" +
                    ",point37_x,point37_y,point37_z" +
                    ",point39_x,point39_y,point39_z" +
                    ",point40_x,point40_y,point40_z" +
                    ",point186_x,point186_y,point186_z" +
                    // ===== 左臉頰 FULL 額外 9 點 =====
                    ",point50_x,point50_y,point50_z" +
                    ",point187_x,point187_y,point187_z" +
                    ",point205_x,point205_y,point205_z" +
                    ",point207_x,point207_y,point207_z" +
                    ",point206_x,point206_y,point206_z" +
                    ",point216_x,point216_y,point216_z" +
                    ",point165_x,point165_y,point165_z" +
                    ",point92_x,point92_y,point92_z" +
                    ",point167_x,point167_y,point167_z" +

                    // ===== 右臉頰 ORIG 18 點 =====
                    ",point164_x,point164_y,point164_z" +
                    ",point0_x,point0_y,point0_z" +
                    ",point267_x,point267_y,point267_z" +
                    ",point269_x,point269_y,point269_z" +
                    ",point270_x,point270_y,point270_z" +
                    ",point410_x,point410_y,point410_z" +
                    ",point423_x,point423_y,point423_z" +
                    ",point327_x,point327_y,point327_z" +
                    ",point326_x,point326_y,point326_z" +
                    ",point432_x,point432_y,point432_z" +
                    ",point434_x,point434_y,point434_z" +
                    ",point416_x,point416_y,point416_z" +
                    ",point376_x,point376_y,point376_z" +
                    ",point352_x,point352_y,point352_z" +
                    ",point346_x,point346_y,point346_z" +
                    ",point347_x,point347_y,point347_z" +
                    ",point330_x,point330_y,point330_z" +
                    ",point266_x,point266_y,point266_z" +
                    // ===== 右臉頰 FULL 額外 9 點 =====
                    ",point393_x,point393_y,point393_z" +
                    ",point391_x,point391_y,point391_z" +
                    ",point322_x,point322_y,point322_z" +
                    ",point426_x,point426_y,point426_z" +
                    ",point436_x,point436_y,point436_z" +
                    ",point425_x,point425_y,point425_z" +
                    ",point427_x,point427_y,point427_z" +
                    ",point280_x,point280_y,point280_z" +
                    ",point411_x,point411_y,point411_z" +
                    ",img_w,img_h,nosePeakDirection";


    //嘟嘴指標 : 高除以寬 版本1
    private static final String Lip_Prot_HEADER =  "time_seconds,state,mouth_height,mouth_width,height_width_ratio";
    //嘟嘴指標 : 外緣Z軸 版本2


    // 嘴唇 landmark header（40點 x 3座標 = 120欄）
    private static final String LIP_LANDMARKS_HEADER;
    static {
        StringBuilder sb = new StringBuilder();
        // 外緣 20 點
        for (int idx : new int[]{61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185}) {
            sb.append(",p").append(idx).append("_x,p").append(idx).append("_y,p").append(idx).append("_z");
        }
        // 內緣 20 點
        for (int idx : new int[]{78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415, 310, 311, 312, 13, 82, 81, 80, 191}) {
            sb.append(",p").append(idx).append("_x,p").append(idx).append("_y,p").append(idx).append("_z");
        }
        LIP_LANDMARKS_HEADER = sb.toString();
    }

    // 嘟嘴
    private static final String Lip_Prot_HEADER2 = "time_seconds,state,outer_mouth_z_avg,nosepeak_direction" + LIP_LANDMARKS_HEADER;

    // 抿嘴
    private static final String Lip_Closure_HEADER = "time_seconds,state,upper_lip_area,lower_lip_area,total_lip_area,nosepeak_direction" + LIP_LANDMARKS_HEADER;


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
        void onComplete(CSVMotioner. PyAnalysisResult res);
        void onError(String error);
    }

    /*
     * 數字存儲物件，CSV建檔並給定檔名/標頭，根據每種動作不同。
     */
    public FaceDataRecorder(Context context, String trainingLabel, int trainingType) {
        this.context = context;
        this.trainingLabel = trainingLabel;
        this.trainingType = trainingType;
        this.dataLines = new ArrayList<>();
        // 寫入Log
        Log.d(TAG, "確認參數 trainingLabel=> " + trainingLabel + ", trainingType=>: " + trainingType);
        // 🔥 記錄開始時間
        this.startTime = System.currentTimeMillis();
        // 建立檔案名稱
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
//        String timestamp = sdf.format(new Date());
//        this.fileName = String.format("FaceTraining_%s_%s.csv", trainingLabel, timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String timestamp = sdf.format(new Date());

// 從 SharedPreferences 拿現在登入的 userId
        SharedPreferences prefs =
                context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("current_user_id", "guest");

// 在檔名前面加 userId
        this.fileName = String.format("%s_FaceTraining_%s_%s.csv", userId, trainingLabel, timestamp);

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
            header = Lip_Prot_HEADER2;
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


    //recordLandmarkData方法是一個多載Overload方法，用參數數量決定呼叫方法，區別各動作。
    //calculateXxx，每種動作內處理會叫的方法，計算的CSV各cell指標的內容數值。
    //多載: 1.嘴唇
    public void recordLandmarkData(String state, float[][] landmarks, Boolean JawDetected) {
        try {
            // 🔥 COUNTDOWN 不記錄
            if ("COUNTDOWN".equals(state)) {
                return;
            }
            // 🔥 改用相對時間，從0開始，以秒為單位
            long currentTime = System.currentTimeMillis();
            double relativeTimeSeconds = (currentTime - startTime) / 1000.0;
            // dataLine存入裡面的值會被記錄到CSV，並ADD到共用變數DataLines後方。
            // DataLines : 上方已根據動作初始化Header內容
            String dataLine = "";

            if ("SIP_LIPS".equals(trainingLabel)) {
                // 🔥 改用掃描線方法計算上下嘴唇面積
                float upperLipArea = calculateLipAreaByScanline(landmarks, UPPER_LIP_INDICES);
                float lowerLipArea = calculateLipAreaByScanline(landmarks, LOWER_LIP_INDICES);
                // ✨ 總嘴唇面積 = 上唇 + 下唇
                float totalLipArea = upperLipArea + lowerLipArea;

                // 加入鼻尖方向
                float noseTipZ = landmarks[1][2];
                float z_avg = calculateMouthDepth(landmarks);
                //Z=離鏡頭距離，鼻尖近=T，嘟起嘴巴也跟著變小
                //若=F則反過來鼻尖較遠，嘟起時應該抓大
                String nosePeakDirection = noseTipZ < z_avg ? "T" : "F";


                StringBuilder sb = new StringBuilder();
                sb.append(String.format(Locale.getDefault(), "%.3f,%s,%.6f,%.6f,%.6f,%s",
                        relativeTimeSeconds, state, upperLipArea, lowerLipArea, totalLipArea, nosePeakDirection));

                // 加入 40 個 landmark 座標
                for (int idx : LIP_OUTER_IDXS) {
                    sb.append(String.format(Locale.getDefault(), ",%.6f,%.6f,%.6f",
                            landmarks[idx][0], landmarks[idx][1], landmarks[idx][2]));
                }
                for (int idx : LIP_INNER_IDXS) {
                    sb.append(String.format(Locale.getDefault(), ",%.6f,%.6f,%.6f",
                            landmarks[idx][0], landmarks[idx][1], landmarks[idx][2]));
                }

                dataLine = sb.toString();
                //DEBUG列印輸出
//                Log.d(TAG, String.format("抿嘴數據 [%.3fs] - 上唇面積: %.3f, 下唇面積: %.3f, 比值: %.3f",
//                        relativeTimeSeconds, upperLipArea, lowerLipArea, totalLipArea));

            } else if ("POUT_LIPS".equals(trainingLabel)) {
                //版本 1 : 外緣點計算嘴巴高度和寬度
//                float[] mouthDimensions = calculateMouthDimensionsImproved(landmarks);
//                float height = mouthDimensions[0];
//                float width = mouthDimensions[1];
//                float heightWidthRatio = width > 0 ? height / width : 0;
//
//                dataLine = String.format(Locale.getDefault(), "%.3f,%s,%.3f,%.3f,%.3f",
//                        relativeTimeSeconds, state, height, width, heightWidthRatio);

//                Log.d(TAG, String.format("嘟嘴數據 [%.3fs] - 高度: %.3f, 寬度: %.3f, 比值: %.3f",
//                        relativeTimeSeconds, height, width, heightWidthRatio));

                //版本2 : 改用唇外緣Z軸總平均
                float noseTipZ = landmarks[1][2];
                float z_avg = calculateMouthDepth(landmarks);
                //Z=離鏡頭距離，鼻尖近=T，嘟起嘴巴也跟著變小
                //若=F則反過來鼻尖較遠，嘟起時應該抓大
                String nosePeakDirection = noseTipZ < z_avg ? "T" : "F";

                StringBuilder sb = new StringBuilder();
                sb.append(String.format(Locale.getDefault(), "%.3f,%s,%.6f,%s",
                        relativeTimeSeconds, state, z_avg, nosePeakDirection));

                // 加入 40 個 landmark 座標
                for (int idx : LIP_OUTER_IDXS) {
                    sb.append(String.format(Locale.getDefault(), ",%.6f,%.6f,%.6f",
                            landmarks[idx][0], landmarks[idx][1], landmarks[idx][2]));
                }
                for (int idx : LIP_INNER_IDXS) {
                    sb.append(String.format(Locale.getDefault(), ",%.6f,%.6f,%.6f",
                            landmarks[idx][0], landmarks[idx][1], landmarks[idx][2]));
                }



                dataLine = sb.toString();
                Log.d(TAG,"嘟嘴CSV內文 = "+dataLine);
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

    //多載:臉頰曲率
    public void recordLandmarkData(String state, float[][] landmarks, int img_w, int img_h) {
        try {
            // 🔥 COUNTDOWN 不記錄
            if ("COUNTDOWN".equals(state)) {
                return;
            }
            // 臉頰 FULL index (27+27，與電腦版一致)
            int[] LEFT_CHEEK_IDXS = {117,118,101,36,203,212,214,192,147,123,98,97,164,0,37,39,40,186,
                    50,187,205,207,206,216,165,92,167};
            int[] RIGHT_CHEEK_IDXS = {164,0,267,269,270,410,423,327,326,432,434,416,376,352,346,347,330,266,
                    393,391,322,426,436,425,427,280,411};


            Log.d(TAG, "臉頰曲率參數 img_w=" + img_w + ",img_h=" + img_h);

            long now = System.currentTimeMillis();
            double t = (now - startTime) / 1000.0;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.getDefault(), "%.3f,%s", t, state));

            // 拼接左臉頰 + 計算Z平均
            float leftCheekZSum = 0;
            float rightCheekZSum  = 0;
            for (int idx : LEFT_CHEEK_IDXS) {
                float x = landmarks[idx][0] * img_w;
                float y = landmarks[idx][1] * img_h;
                float z = landmarks[idx][2]; // z 不縮放
                leftCheekZSum += z;
                sb.append(String.format(Locale.getDefault(), ",%.6f,%.6f,%.6f", x, y, z));
            }
            float leftCheekZAvg = leftCheekZSum / LEFT_CHEEK_IDXS.length;

            // 拼接右臉頰
            for (int idx : RIGHT_CHEEK_IDXS) {
                float x = landmarks[idx][0] * img_w;
                float y = landmarks[idx][1] * img_h;
                float z = landmarks[idx][2];
                rightCheekZSum += z;
                sb.append(String.format(Locale.getDefault(), ",%.6f,%.6f,%.6f", x, y, z));
            }
            float rightCheekZAvg = rightCheekZSum / RIGHT_CHEEK_IDXS.length;
            // 計算鼻尖方向 (用左右臉頰Z平均)
            float noseTipZ = landmarks[1][2];
            float cheekZAvg = (leftCheekZAvg + rightCheekZAvg) / 2;
            String nosePeakDirection = noseTipZ < cheekZAvg ? "T" : "F";


            sb.append(String.format(Locale.getDefault(), ",%d,%d,%s", img_w, img_h,nosePeakDirection));

            String line = sb.toString();

            Log.d(TAG, "臉頰曲率_CSV " + line);

            dataLines.add(line);

        } catch (Exception e) {
            Log.e(TAG, "recordLandmarkData error", e);
        }
    }


    //多載:臉頰_光流_舊版
    public void recordLandmarkData(String state, Float liX, Float liY, Float riX, Float riY, Float liRawX, Float liRawY, Float riRawX, Float riRawY) {

        try {
            // 🔥 COUNTDOWN 不記錄
            if ("COUNTDOWN".equals(state)) {
                return;
            }
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
        // 🔥 COUNTDOWN 不記錄
        if ("COUNTDOWN".equals(state)) {
            return;
        }
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

    // 🔥 嘟嘴：用外緣點計算嘴巴高度和寬度_不要刪除 20251002
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

//            Log.d(TAG, String.format("嘴巴尺寸 - 寬度: %.3f (左%.1f → 右%.1f), 高度: %.3f (上%.1f → 下%.1f)",
//                    mouthWidth, leftCornerX, rightCornerX, mouthHeight, highestY, lowestY));

            return new float[]{mouthHeight, mouthWidth};

        } catch (Exception e) {
            Log.e(TAG, "計算嘴巴尺寸時發生錯誤", e);
            return new float[]{0, 0};
        }
    }

    // 🔥 嘟嘴：用嘴唇外緣點計Z值加總平均 20251002
//    private float calculateMouthDepth(float[][] landmarks) {
//        try {
//            // 上唇外緣關鍵點
//            int[] upperOuterIndices = {61, 62, 63, 64, 65, 66, 67, 291, 292, 293, 294, 295, 296, 297};
//            // 下唇外緣關鍵點
//            int[] lowerOuterIndices = {61, 84, 17, 314, 405, 320, 307, 291, 375, 321, 308, 324, 318};
//
//            float sumZ = 0f;
//            int count = 0;
//
//            // 上唇
//            for (int index : upperOuterIndices) {
//                if (index < landmarks.length && landmarks[index].length > 2) {
//                    sumZ += landmarks[index][2];
//                    count++;
//                }
//            }
//
//            // 下唇
//            for (int index : lowerOuterIndices) {
//                if (index < landmarks.length && landmarks[index].length > 2) {
//                    sumZ += landmarks[index][2];
//                    count++;
//                }
//            }
//
//            // 平均值
//            return (count > 0) ? (sumZ / count) : 0f;
//
//        } catch (Exception e) {
//            Log.e(TAG, "計算嘴巴Z平均時發生錯誤", e);
//            return 0f;
//        }
//    }
// 🔥 嘟嘴：用嘴唇外緣點計Z值加總平均 20251002
    private float calculateMouthDepth(float[][] landmarks) {
        try {
            // 嘴唇外緣（官方順序）

            float sumZ = 0f;
            int count = 0;

            for (int index : LIP_OUTER_IDXS ) {
                if (index < landmarks.length && landmarks[index].length > 2) {
                    sumZ += landmarks[index][2];
                    count++;
                }
            }

            return (count > 0) ? (sumZ / count) : 0f;

        } catch (Exception e) {
            Log.e(TAG, "計算嘴巴Z平均時發生錯誤", e);
            return 0f;
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
            // 換地方
            File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);

//            File file = new File(downloadsDir, fileName);

            File file = new File(dir, fileName);
            FileWriter writer = new FileWriter(file);
            for (String line : dataLines) {
                writer.write(line + "\n");
            }
            writer.close();

            // ★ 備份到隱藏公共目錄
            try {
                File publicDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS), "rhabdata");
                if (!publicDir.exists()) publicDir.mkdirs();
                File dst = new File(publicDir, fileName);
                java.nio.file.Files.copy(file.toPath(), dst.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Log.d(TAG, "✅ CSV 備份成功: " + dst.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "⚠️ CSV 備份失敗: " + e.getMessage());
            }
            //writer.close();代表真的寫好
            Log.d(TAG, "✅ 檔案儲存成功: " + file.getAbsolutePath());
            Log.d(TAG, "📊 總共記錄了 " + (dataLines.size() - 1) + " 筆數據");

            // 🔥 檔案儲存完成()後進行峰值分析，並通過 callback 回傳結果
            performPeakAnalysisWithCallback(callback);

            // 使用 Handler 切換到主線程顯示 Toast
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Log.d(TAG, "✅ 檔案儲存成功: " + file.getAbsolutePath())
//                    Toast.makeText(context, "檔案已儲存至下載資料夾", Toast.LENGTH_SHORT).show()

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
                // 棄用 :調用 CSV 峰值分析器
                // 確認是否能不用傳過CSVPeakAnalyzer了，改用PYTHON處理了
                CSVPeakAnalyzer.AnalysisResult result = CSVPeakAnalyzer.analyzePeaksFromFile(context, fileName);

                // 調用PYTHON
                Log.d("CSVMotioner  ==>", "call CSVMotioner ");
                CSVMotioner.PyAnalysisResult res = CSVMotioner.analyzePeaksFromFile(context, fileName);
                Log.d("RECORDER FACE DATA  ==>", "Py  RES ==> " + res);
                Log.d("RECORDER FACE DATA  ==>", "動作數: " + res.actionCount);
                Log.d("RECORDER FACE DATA  ==>", "總動作時間: " + res.totalActionTime);
//                Log.d("RECORDER FACE DATA  ==>", "第一個斷點: " + (res.breakpoints.size() > 0 ? res.breakpoints.get(0) : -1));

//                Log.d("TEST_CSVMOTION_CALL", "✅ TEST_CSVMOTION_CALL fin...");
//                if (res.success) {
//                    Log.d("FACECIRCLE", "動作數: " + res.actionCount);
//                    Log.d("FACECIRCLE", "總動作時間: " + res.totalActionTime);
//                    Log.d("FACECIRCLE", "第一個斷點: " + (res.breakpoints.size() > 0 ? res.breakpoints.get(0) : -1));
//                }
                //如果成功就跳接GO
                //dataRecorder 改用res回呼
//                if (result.success) {
//                    Log.d(TAG, "✅ 峰值分析完成!");
//                    Log.d(TAG, String.format("📊 峰值統計 - 校正: %d, 維持: %d, 總計: %d",
//                            result.calibratingPeaks, result.maintainingPeaks, result.totalPeaks));
//
//                    // 成功後，這裡負責回調FaceCircle的onCompelete，若以棄用則回傳CSV路徑就好
//                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
//                            callback.onComplete(result)
//                    );
                if (res.success) {
                    Log.d(TAG, "✅ python分析完成!");
//                    Log.d(TAG, String.format("📊 峰值統計 - 校正: %d, 維持: %d, 總計: %d",
//                            result.calibratingPeaks, result.maintainingPeaks, result.totalPeaks));
//
                    //成功後，這裡負責回調FaceCircle的onCompelete，若以棄用則回傳CSV路徑就好
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            callback.onComplete(res)
                    );

                } else {
                    Log.e(TAG, "❌ 峰值分析失敗: " + res.debug);

                    // 🔥 失敗回調
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            callback.onError("峰值分析失敗: " +  res.debug)
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


    // 🆕 清空資料，重新開始記錄
    public void clearData() {
        dataLines.clear();
        initializeCSV();  // 重新加入 header
        startTime = System.currentTimeMillis();  // 重設開始時間
        Log.d(TAG, "🔄 資料已清空，重新開始記錄");
    }

    public int getDataCount() {
        return Math.max(0, dataLines.size() - 1); // 扣除標題行
    }

    // 🔥 新增：獲取檔案名稱（供外部使用）
    public String getFileName() {
        return fileName;
    }

    //提供VERCEL分析動作
    public String exportLinesAsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"trainingType\":\"")
                .append(trainingLabel.replace("\"", "\\\"")) // 保險：避免有雙引號
                .append("\",\"lines\":[");


        for (int i = 0; i < dataLines.size(); i++) {
            if (i > 0) sb.append(',');
            // 轉義雙引號
            sb.append('\"')
                    .append(dataLines.get(i).replace("\"","\\\""))
                    .append('\"');
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * 🔹 取得時間軸 (time_seconds)，若找不到欄位則回傳空陣列 （供外部使用）
     */
    public double[] getTimeSecondsArrayForRatio() {
        try {
            if (dataLines.size() <= 1) return new double[0]; // 沒有資料
            String header = dataLines.get(0);
            String[] cols = header.split(",");
            int timeIndex = -1;
            for (int i = 0; i < cols.length; i++) {
                if ("time_seconds".equals(cols[i].trim())) {
                    timeIndex = i;
                    break;
                }
            }
            if (timeIndex == -1) return new double[0];

            List<Double> values = new ArrayList<>();
            for (int i = 1; i < dataLines.size(); i++) {
                String[] parts = dataLines.get(i).split(",");
                if (parts.length > timeIndex) {
                    try {
                        values.add(Double.parseDouble(parts[timeIndex]));
                    } catch (NumberFormatException ignore) {}
                }
            }
            double[] arr = new double[values.size()];
            for (int i = 0; i < values.size(); i++) arr[i] = values.get(i);
            return arr;
        } catch (Exception e) {
            Log.e(TAG, "getTimeSecondsArrayForRatio error", e);
            return new double[0];
        }
    }

    /**
     * 🔹 取得 height_width_ratio (僅 POUT_LIPS 有)，若不存在則回傳空陣列 （供外部使用）
     */
    public double[] getHeightWidthRatioArray() {
        try {
            if (dataLines.size() <= 1) return new double[0];
            String header = dataLines.get(0);
            String[] cols = header.split(",");
            int ratioIndex = -1;
            for (int i = 0; i < cols.length; i++) {
                if ("height_width_ratio".equals(cols[i].trim())) {
                    ratioIndex = i;
                    break;
                }
            }
            if (ratioIndex == -1) return new double[0];

            List<Double> values = new ArrayList<>();
            for (int i = 1; i < dataLines.size(); i++) {
                String[] parts = dataLines.get(i).split(",");
                if (parts.length > ratioIndex) {
                    try {
                        values.add(Double.parseDouble(parts[ratioIndex]));
                    } catch (NumberFormatException ignore) {}
                }
            }
            double[] arr = new double[values.size()];
            for (int i = 0; i < values.size(); i++) arr[i] = values.get(i);
            return arr;
        } catch (Exception e) {
            Log.e(TAG, "getHeightWidthRatioArray error", e);
            return new double[0];
        }
    }

    // ★ 新增：給 closeLip(=抿嘴) 畫圖用的時間軸與總面積
    public double[][] exportLipTimeAndTotal() {
        List<Double> t = new ArrayList<>();
        List<Double> v = new ArrayList<>();
        // 假設你寫 CSV 時有把每一行暫存到 dataLines（和原本寫檔同一套）
        for (String line : dataLines) {
            if (line == null || line.isEmpty()) continue;
            if (line.startsWith("time_seconds")) continue; // 跳過表頭
            String[] p = line.split(",");
            if (p.length < 5) continue; // time, state, upper, lower, total
            try {
                t.add(Double.parseDouble(p[0])); // time_seconds
                v.add(Double.parseDouble(p[4])); // total_lip_area
            } catch (Exception ignore) {}
        }
        double[] times  = t.stream().mapToDouble(Double::doubleValue).toArray();
        double[] totals = v.stream().mapToDouble(Double::doubleValue).toArray();
        return new double[][]{ times, totals };
    }

    // 🆕 重設開始時間（倒數結束後呼叫）
    public void resetStartTime() {
        this.startTime = System.currentTimeMillis();
        Log.d(TAG, "🔄 開始時間已重設: " + startTime);
    }

}