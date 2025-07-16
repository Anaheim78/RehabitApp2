package com.example.rehabilitationapp.data.model;

import androidx.room.Entity;

@Entity(primaryKeys = {"planId", "itemId"})
public class PlanItemCrossRef {
    public long planId;
    public long itemId;

    public PlanItemCrossRef(long planId, long itemId) {
        this.planId = planId;
        this.itemId = itemId;
    }
}
