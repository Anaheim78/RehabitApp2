package com.example.rehabilitationapp.data.model;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

public class PlanWithItems {
    @Embedded
    public TrainingPlan plan;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(
                    value = PlanItemCrossRef.class,
                    parentColumn = "planId",
                    entityColumn = "itemId"
            )
    )
    public List<TrainingItem> items;
}
