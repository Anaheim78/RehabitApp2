package com.example.rehabilitationapp.data.model;

//pojo只是拿來裝歷史紀錄底下的圖卡
// app/.../db/TrainingHistoryWithTitle.java
public class TrainingHistoryWithTitle {
    public long createAt;   // 開始時間戳
    public String title;    // 來自 training_items.title（若對不到就用 trainingLabel）
    public String trainingLabel; // 原始 Label，當 JOIN 沒結果時用
    public int achievedTimes;
}

