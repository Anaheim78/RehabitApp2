// 📂 data/model/TrainingItemPreload.java
package com.example.rehabilitationapp.data.model;

import java.util.ArrayList;
import java.util.List;

public class Preload {

    public static List<TrainingItem> getDefaultItems() {
        List<TrainingItem> list = new ArrayList<>();
        list.add(make("臉頰鼓起", "鼓起雙頰\n保持3秒", "ic_home_cheekpuff", "PUFF_CHEEK"));
        list.add(make("臉頰內縮", "吸縮雙頰\n保持3秒", "ic_home_cheekreduce", "REDUCE_CHEEK"));
        list.add(make("嘟嘴", "嘟起嘴巴\n保持3秒", "ic_home_lippout", "POUT_LIPS"));
        list.add(make("抿嘴唇", "輕抿嘴唇\n保持3秒", "ic_home_lipsip", "SIP_LIPS"));

        list.add(make("舌頭往左側", "伸出舌頭向左\n保持3秒", "ic_home_tongueleft", "TONGUE_LEFT"));
        list.add(make("舌頭往右側", "伸出舌頭向右\n保持3秒", "ic_home_tongueright", "TONGUE_RIGHT"));
        list.add(make("舌頭往前", "伸出舌頭向前\n保持3秒", "ic_home_tonguefoward", "TONGUE_FOWARD"));
        list.add(make("舌頭往後", "捲起舌頭\n保持3秒", "ic_home_tongueback", "TONGUE_BACK"));
        list.add(make("舌頭上抬", "伸出舌頭向上\n保持3秒", "ic_home_tongueup", "TONGUE_UP"));
        list.add(make("舌頭下壓", "伸出舌頭向下\n保持3秒", "ic_home_tonguedown", "TONGUE_DOWN"));

        list.add(make("下顎往左側", "下顎往左\n保持3秒", "ic_home_jawleft", "JAW_LEFT"));
        list.add(make("下顎往右側", "下顎往右\n保持3秒", "ic_home_jawright", "JAW_RIGHT"));
        return list;
    }


    public static List<TrainingPlan> getDefaultPlans() {
        List<TrainingPlan> list = new ArrayList<>();
        list.add(new TrainingPlan("基礎復健訓練", "包含鼓頰、縮頰與嘟嘴", null)); // id 1
        list.add(new TrainingPlan("進階復健訓練", "左右伸舌", null)); // id 2
        return list;
    }

    public static List<PlanItemCrossRef> getDefaultPlanItemLinks() {
        List<PlanItemCrossRef> list = new ArrayList<>();
        list.add(new PlanItemCrossRef(1, 1)); // 計畫1 ← 動作1 (鼓頰)
        list.add(new PlanItemCrossRef(1, 2)); // 計畫1 ← 動作2 (縮頰)
        list.add(new PlanItemCrossRef(1, 3)); // 計畫1 ← 動作3 (嘟嘴)
        list.add(new PlanItemCrossRef(1, 4)); // 計畫1 ← 動作3 (嘟嘴)
        list.add(new PlanItemCrossRef(2, 1)); // 計畫1 ← 動作1 (鼓頰)
        list.add(new PlanItemCrossRef(2, 2)); // 計畫1 ← 動作2 (縮頰)
        list.add(new PlanItemCrossRef(2, 3)); // 計畫1 ← 動作3 (嘟嘴)
        list.add(new PlanItemCrossRef(2, 4)); // 計畫1 ← 動作3 (嘟嘴)
        list.add(new PlanItemCrossRef(2, 5)); // 計畫2 ← 動作5 (左舌)
        list.add(new PlanItemCrossRef(2, 6)); // 計畫2 ← 動作6 (右舌)
        list.add(new PlanItemCrossRef(2, 7)); // 計畫2 ← 動作7 (舌頭前)
        list.add(new PlanItemCrossRef(2, 9)); // 計畫2 ← 動作9 (舌頭上)
        list.add(new PlanItemCrossRef(2, 10)); // 計畫2 ← 動作10 (舌頭下)

        return list;
    }

    private static TrainingItem make(String title, String desc, String image, String type) {
        TrainingItem item = new TrainingItem();
        item.title = title;
        item.description = desc;
        item.imageResName = image;
        item.analysisType = type;
        return item;
    }
}