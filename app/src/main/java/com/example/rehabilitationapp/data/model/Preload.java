// 📂 data/model/TrainingItemPreload.java
package com.example.rehabilitationapp.data.model;

import java.util.ArrayList;
import java.util.List;

public class Preload {
    public static List<TrainingItem> getDefaults() {
        List<TrainingItem> list = new ArrayList<>();

        list.add(make("鼓頰", "鼓起雙頰\n保持3秒", "cheeks", "PUFF_CHEEK"));
        list.add(make("縮頰", "吸縮雙頰\n保持3秒", "cheeks_reduction", "REDUCE_CHEEK"));
        list.add(make("嘟嘴", "嘟起嘴巴\n保持3秒", "pout_lips", "POUT_LIPS"));
        list.add(make("抿嘴", "輕抿嘴唇\n保持3秒", "sip_lips", "SIP_LIPS"));
        list.add(make("向左伸舌", "舌頭向左\n保持3秒", "tongueright", "TONGUE_LEFT"));
        list.add(make("向右伸舌", "舌頭向右\n保持3秒", "tongueleft", "TONGUE_RIGHT"));

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
