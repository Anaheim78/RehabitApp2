package com.example.rehabilitationapp.data.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;

@Entity(
        primaryKeys = {"planId", "itemId"},
        foreignKeys = {
                @ForeignKey(
                        entity = TrainingPlan.class,
                        parentColumns = "id",
                        childColumns = "planId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = TrainingItem.class,
                        parentColumns = "id",
                        childColumns = "itemId",
                        onDelete = ForeignKey.CASCADE
                )
        }
)
public class PlanItemCrossRef {
    public long planId;
    public long itemId;

    public PlanItemCrossRef(long planId, long itemId) {
        this.planId = planId;
        this.itemId = itemId;
    }
}
