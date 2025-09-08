package com.example.rehabilitationapp.data.model;

import androidx.annotation.NonNull;
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

    // 空建構子 (Room 需要)
    public TrainingHistory() {}
    // 建構子
    public TrainingHistory(String trainingID, String trainingLabel,
                           long createAt, long finishAt, int targetTimes,
                           int achievedTimes, int durationTime) {
        this.trainingID = trainingID;
        this.trainingLabel = trainingLabel;
        this.createAt = createAt;
        this.finishAt = finishAt;
        this.targetTimes = targetTimes;
        this.achievedTimes = achievedTimes;
        this.durationTime = durationTime;
    }
}