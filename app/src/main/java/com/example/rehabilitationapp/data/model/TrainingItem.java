package com.example.rehabilitationapp.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "training_items")
public class TrainingItem {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String title;

    public String description;

    public String imageResName;  // 對應 drawable 裡的圖片名稱，不含副檔名

    public String analysisType;  // 對應後端演算法用途
}
