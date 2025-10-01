package com.example.rehabilitationapp.ui.analysis;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

//AnalysisResult 遵循原本形式
//..TO_DO 202210002 把PyAnalysisResult類內物件崁入好(PY RETURN)。然後最後要轉型到能在FACE CIRCLE那邊把GO方法內傳送的參數對齊
public class CSVMotioner {
        //全域物件直接讓FacdCircle可見
        public static class PyAnalysisResult {
            public String fileName;
            private boolean success;

            public  PyAnalysisResult() {
            this.success = false;
        }
    }
    public static String analyzePeaksFromFile(Context context, String fileName) {

        // 1. 找到檔案路徑
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File csvFile = new File(downloadsDir, fileName);

        //2. 檔名路徑分流
        Python py = Python.getInstance();
        if(fileName.contains("POUT_LIPS")){
            try(PyObject pyResult = py.getModule("count_pout_lips")
                    .callAttr("analyze_csv",fileName) ){

                String output = pyResult.toString();
                Log.d("CSVMOTIONTEST", "🔥 Python 回傳: " + output);

                //To do..把PY回傳內容放入類內物件 AnalysisResult


            }

        }



        //固定測試輸出
        String output;
        try (PyObject pyResult = py.getModule("count_pout_lips")   // 對應 csv_peak_analyzer.py
                .callAttr("echo_test", "123")) {

            // 2. 把 Python 回傳結果轉字串
            output = pyResult.toString();
        }
        Log.d("CSVMOTIONTEST", "🔥 Python 回傳: " + output);

        return output;
    }
}


