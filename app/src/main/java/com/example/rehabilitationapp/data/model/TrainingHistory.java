package com.example.rehabilitationapp.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trainingHistory")  // 注意你的拼字是 "Histroy"
public class TrainingHistory {
    @PrimaryKey
    @NonNull
    public String trainingID;

    //public String username;
    public String trainingLabel;
    public long createAt;
    public long finishAt;
    public int targetTimes;
    public int achievedTimes;
    public int durationTime;
    public String curveJson;

    //與FIREBASE紀錄 0=未同步, 1=已同步
    @ColumnInfo(defaultValue = "0")
    public int synced = 0;
    //用bucket紀錄，csv上傳
    @ColumnInfo(defaultValue = "0")
    public int csvUploaded = 0;  // 0=未上傳, 1=已上傳

    @NonNull
    @ColumnInfo(defaultValue = "")
    public String csvFileName = "";

    //記自評次數
    @ColumnInfo(defaultValue = "-1")
    public int selfReportCount = -1;

    //影片上傳紀錄
    @ColumnInfo(defaultValue = "0")
    public int videoUploaded = 0;  // 0=未上傳, 1=已上傳

    @ColumnInfo(defaultValue = "")
    public String videoFileName = "";  // 影片檔名
// ====================================


    //停用
    @ColumnInfo(defaultValue = "0")
    public int saved = 0;

    // 空建構子 (Room 需要)
    public TrainingHistory() {}



    // 建構子1 (保持舊參數量，向舊版本相容)
    public TrainingHistory(String trainingID, String trainingLabel,
                           long createAt, long finishAt, int targetTimes,
                           int achievedTimes, int durationTime, String curveJson) {
        this.trainingID = trainingID;
        this.trainingLabel = trainingLabel;
        this.createAt = createAt;
        this.finishAt = finishAt;
        this.targetTimes = targetTimes;
        this.achievedTimes = achievedTimes;
        this.durationTime = durationTime;
        this.curveJson = curveJson;
        this.synced = 0;
        this.csvUploaded = 0;
        this.csvFileName = "";

    }

    //建構子2，新增獲取csvUploaded、csvFileName
    public TrainingHistory(String trainingID, String trainingLabel,
                           long createAt, long finishAt, int targetTimes,
                           int achievedTimes, int durationTime, String curveJson,
                           String csvFileName) {
        this.trainingID = trainingID;
        this.trainingLabel = trainingLabel;
        this.createAt = createAt;
        this.finishAt = finishAt;
        this.targetTimes = targetTimes;
        this.achievedTimes = achievedTimes;
        this.durationTime = durationTime;
        this.curveJson = curveJson;
        this.synced = 0;
        this.csvUploaded = 0;
        this.csvFileName = csvFileName != null ? csvFileName : "";
    }

    //建構子3，包含新增影片上傳
    public TrainingHistory(String trainingID, String trainingLabel,
                           long createAt, long finishAt, int targetTimes,
                           int achievedTimes, int durationTime, String curveJson,
                           String csvFileName, String videoFileName) {
        this.trainingID = trainingID;
        this.trainingLabel = trainingLabel;
        this.createAt = createAt;
        this.finishAt = finishAt;
        this.targetTimes = targetTimes;
        this.achievedTimes = achievedTimes;
        this.durationTime = durationTime;
        this.curveJson = curveJson;
        this.synced = 0;
        this.csvUploaded = 0;
        this.csvFileName = csvFileName != null ? csvFileName : "";
        this.videoUploaded = 0;
        this.videoFileName = videoFileName != null ? videoFileName : "";
    }
}