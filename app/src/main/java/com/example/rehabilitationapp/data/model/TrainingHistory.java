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

    @ColumnInfo(defaultValue = "0")
    public int synced = 0;  //與FIREBASE紀錄 0=未同步, 1=已同步

    // ★ 新增這兩行
    @ColumnInfo(defaultValue = "0")
    public int saved = 0;

    @ColumnInfo(defaultValue = "-1")
    public int selfReportCount = -1;

    // 空建構子 (Room 需要)
    public TrainingHistory() {}
    // 建構子
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
    }
}