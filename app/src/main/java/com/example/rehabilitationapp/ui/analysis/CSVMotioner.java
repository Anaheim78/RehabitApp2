package com.example.rehabilitationapp.ui.analysis;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CSVMotioner {

    // 全域物件，讓 FacdCircle 那邊也能直接拿
    public static class PyAnalysisResult {
        public String fileName;
        public boolean success;
        public int actionCount;
        public double totalActionTime;
        public List<Double> breakpoints;
        public List<Segment> segments;
        public DebugInfo debug;

        public static class Segment {
            public int index;
            public double startTime;
            public double endTime;
            public double duration;
        }

        public static class DebugInfo {
            public double fsHz;
            public double cutoff;
            public int order;
            public int zcAll;
            public int zcUp;
            public int zcDown;
            public double deadband;
            public int minInterval;
        }
    }

    // ===== 主流程 =====
    public static PyAnalysisResult analyzePeaksFromFile(Context context, String fileName) {

        // 1. 找到檔案路徑
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File csvFile = new File(downloadsDir, fileName);

        Python py = Python.getInstance();
        PyAnalysisResult result = new PyAnalysisResult();
        result.fileName = fileName;
        result.success = false;

        //1.POUT_LIPS
        if (fileName.contains("POUT_LIPS")) {
            try (PyObject pyResult = py.getModule("count_pout_lips")
                    .callAttr("analyze_csv", csvFile.getAbsolutePath())) {

                Log.d("CSVMOTIONTEST", "🔥 Python 回傳: " + pyResult.toString());

                // Python dict → Java Map
                Map<PyObject, PyObject> rawMap = pyResult.asMap();
                Map<String, PyObject> pyMap = new HashMap<>();
                for (Map.Entry<PyObject, PyObject> entry : rawMap.entrySet()) {
                    pyMap.put(entry.getKey().toString(), entry.getValue());
                }


                String status = pyMap.get("status").toString();
                result.success = status.equals("OK");

                if (result.success) {
                    // 數值欄位
                    result.actionCount = pyMap.get("action_count").toInt();
                    result.totalActionTime = pyMap.get("total_action_time").toDouble();

                    // breakpoints
                    result.breakpoints = new ArrayList<>();
                    for (PyObject bp : pyMap.get("breakpoints").asList()) {
                        result.breakpoints.add(bp.toDouble());
                    }

                    // segments
                    result.segments = new ArrayList<>();
                    for (PyObject segObj : pyMap.get("segments").asList()) {
                        Map<PyObject, PyObject> rawSegMap = segObj.asMap();
                        Map<String, PyObject> segMap = new HashMap<>();
                        for (Map.Entry<PyObject, PyObject> entry : rawSegMap.entrySet()) {
                            segMap.put(entry.getKey().toString(), entry.getValue());
                        }


                        PyAnalysisResult.Segment seg = new PyAnalysisResult.Segment();
                        seg.index = segMap.get("index").toInt();
                        seg.startTime = segMap.get("start_time").toDouble();
                        seg.endTime = segMap.get("end_time").toDouble();
                        seg.duration = segMap.get("duration").toDouble();
                        result.segments.add(seg);
                    }

                    // debug
                    Map<PyObject, PyObject> dbgMap = pyMap.get("debug").asMap();
                    Map<String, PyObject> segMap = new HashMap<>();
                    for (Map.Entry<PyObject, PyObject> entry : dbgMap.entrySet()) {
                        segMap.put(entry.getKey().toString(), entry.getValue());
                    }

                    PyAnalysisResult.DebugInfo dbg = new PyAnalysisResult.DebugInfo();
                    dbg.fsHz = dbgMap.get("fs_hz").toDouble();
                    dbg.cutoff = dbgMap.get("cutoff").toDouble();
                    dbg.order = dbgMap.get("order").toInt();
                    dbg.zcAll = dbgMap.get("zc_all").toInt();
                    dbg.zcUp = dbgMap.get("zc_up").toInt();
                    dbg.zcDown = dbgMap.get("zc_down").toInt();
                    dbg.deadband = dbgMap.get("deadband").toDouble();
                    dbg.minInterval = dbgMap.get("min_interval").toInt();
                    result.debug = dbg;
                }

            } catch (Exception e) {
                Log.e("CSVMOTIONTEST", "🔥 解析錯誤", e);
                result.success = false;
            }
        }


        if (fileName.contains("SIP_LIPS")){
            try (PyObject pyResult = py.getModule("count_pout_lips")
                    .callAttr("analyze_csv", csvFile.getAbsolutePath())) {

                Log.d("CSVMOTIONTEST", "🔥 Python 回傳: " + pyResult.toString());

                // Python dict → Java Map
                Map<PyObject, PyObject> rawMap = pyResult.asMap();
                Map<String, PyObject> pyMap = new HashMap<>();
                for (Map.Entry<PyObject, PyObject> entry : rawMap.entrySet()) {
                    pyMap.put(entry.getKey().toString(), entry.getValue());
                }


                String status = pyMap.get("status").toString();
                result.success = status.equals("OK");

                if (result.success) {
                    // 數值欄位
                    result.actionCount = pyMap.get("action_count").toInt();
                    result.totalActionTime = pyMap.get("total_action_time").toDouble();

                    // breakpoints
                    result.breakpoints = new ArrayList<>();
                    for (PyObject bp : pyMap.get("breakpoints").asList()) {
                        result.breakpoints.add(bp.toDouble());
                    }

                    // segments
                    result.segments = new ArrayList<>();
                    for (PyObject segObj : pyMap.get("segments").asList()) {
                        Map<PyObject, PyObject> rawSegMap = segObj.asMap();
                        Map<String, PyObject> segMap = new HashMap<>();
                        for (Map.Entry<PyObject, PyObject> entry : rawSegMap.entrySet()) {
                            segMap.put(entry.getKey().toString(), entry.getValue());
                        }


                        PyAnalysisResult.Segment seg = new PyAnalysisResult.Segment();
                        seg.index = segMap.get("index").toInt();
                        seg.startTime = segMap.get("start_time").toDouble();
                        seg.endTime = segMap.get("end_time").toDouble();
                        seg.duration = segMap.get("duration").toDouble();
                        result.segments.add(seg);
                    }

                    // debug
                    Map<PyObject, PyObject> dbgMap = pyMap.get("debug").asMap();
                    Map<String, PyObject> segMap = new HashMap<>();
                    for (Map.Entry<PyObject, PyObject> entry : dbgMap.entrySet()) {
                        segMap.put(entry.getKey().toString(), entry.getValue());
                    }

                    PyAnalysisResult.DebugInfo dbg = new PyAnalysisResult.DebugInfo();
                    dbg.fsHz = dbgMap.get("fs_hz").toDouble();
                    dbg.cutoff = dbgMap.get("cutoff").toDouble();
                    dbg.order = dbgMap.get("order").toInt();
                    dbg.zcAll = dbgMap.get("zc_all").toInt();
                    dbg.zcUp = dbgMap.get("zc_up").toInt();
                    dbg.zcDown = dbgMap.get("zc_down").toInt();
                    dbg.deadband = dbgMap.get("deadband").toDouble();
                    dbg.minInterval = dbgMap.get("min_interval").toInt();
                    result.debug = dbg;
                }

            } catch (Exception e) {
                Log.e("CSVMOTIONTEST", "🔥 解析錯誤", e);
                result.success = false;
            }
        }

        return result;
    }
}
