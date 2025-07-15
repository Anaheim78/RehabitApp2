package com.example.rehabilitationapp.data.model;

import androidx.room.Entity;

@Entity(primaryKeys = {"planId", "itemId"})
public class PlanItemCrossRef {
    public int planId;
    public int itemId;

    public PlanItemCrossRef(int planId, int itemId) {
        this.planId = planId;
        this.itemId = itemId;
    }
}
