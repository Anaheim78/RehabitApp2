package com.example.rehabilitationapp.ui.facecheck;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.rehabilitationapp.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

/**
 * 教學面板（支援 label 別名直接在這裡判斷）
 * 用法：
 *   MotionGuideBottomSheet sheet = MotionGuideBottomSheet.newInstance(analysisType, titleZh);
 *   sheet.setOnStartListener(this::onStartTraining);
 *   sheet.show(getSupportFragmentManager(), "motion_guide");
 *
 * 偏好儲存 key：guide_hide_<canonical>
 */
public class MotionGuideBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_LABEL = "arg_label";
    private static final String ARG_TITLE_ZH = "arg_title_zh";
    private static final String PREFS = "motion_prefs";

    @Nullable private Runnable onStart;
    private String label;       // 例如 "POUT_LIPS" / "poutLip" / "SIP_LIPS" / "closeLip" ...
    private String titleZh;     // 例如 "噘嘴" / "抿嘴唇"（可為 null）

    // ========= 對外 API =========

    public static MotionGuideBottomSheet newInstance(@NonNull String analysisType, @Nullable String titleZh) {
        MotionGuideBottomSheet f = new MotionGuideBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_LABEL, analysisType);
        b.putString(ARG_TITLE_ZH, titleZh);
        f.setArguments(b);
        return f;
    }

    /** 設定按「開始」時要做的事 */
    public void setOnStartListener(@Nullable Runnable r) {
        this.onStart = r;
    }

    /** 是否需要顯示教學（未勾「不再顯示」才顯示） */
    public static boolean shouldShow(@NonNull Context ctx, @Nullable String trainingType) {
        String can = canonical(trainingType);
        String key = "guide_hide_" + can;
        //return !ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false);
        return true;
    }

    // ========= 生命週期 =========

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        label   = (args != null) ? args.getString(ARG_LABEL) : null;
        titleZh = (args != null) ? args.getString(ARG_TITLE_ZH) : null;
        if (TextUtils.isEmpty(titleZh)) {
            // 若沒傳中文名，依 label 給個預設中文
            titleZh = defaultTitleZhFor(canonical(label));
        }
    }

    @Nullable
    @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                       @Nullable ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        // 用程式動態畫面（不需要 XML）
        Context ctx = requireContext();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));

        // ICON
        ImageView iv = new ImageView(ctx);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(64), dp(64));
        ivLp.gravity = Gravity.CENTER_HORIZONTAL;
        iv.setLayoutParams(ivLp);
        iv.setImageResource(pickIconRes(canonical(label)));
        root.addView(iv);

        // 標題
        TextView title = new TextView(ctx);
        title.setText(titleZh);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.topMargin = dp(12);
        root.addView(title, tLp);

        // 說明
        TextView desc = new TextView(ctx);
        desc.setText(guideTextFor(canonical(label)));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        desc.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(12);
        root.addView(desc, dLp);

        // 勾選：不再顯示
        CheckBox cb = new CheckBox(ctx);
        cb.setText("不再顯示");
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbLp.topMargin = dp(12);
        root.addView(cb, cbLp);

        // 開始按鈕
        Button btn = new Button(ctx);
        btn.setText("開始");
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        bLp.topMargin = dp(16);
        root.addView(btn, bLp);

        btn.setOnClickListener(v -> {
            // 存偏好：不再顯示
            if (cb.isChecked()) {
                SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                sp.edit().putBoolean("guide_hide_" + canonical(label), true).apply();
            }
            dismissAllowingStateLoss();
            if (onStart != null) onStart.run();
        });

        return root;
    }

    // ========= 內部工具：別名映射（方法 B 的重點） =========

    /** 把各種別名統一成 canonical 值（僅供偏好 key 與顯示用） */
    private static String canonical(@Nullable String s) {
        if (s == null) return "";
        String x = s.trim().toLowerCase(Locale.ROOT);
        // 嘟嘴（高平台）：支援 POUT_LIPS / poutLip / pout
        if (x.contains("pout_lips") || x.equals("poutlip") || x.contains("pout")) return "poutLip";
        // 抿嘴（低谷）：支援 closeLip / SIP_LIPS / slipLip / close
        if (x.contains("close_lip") || x.equals("closelip") || x.contains("sip_lips") || x.contains("sliplip") || x.contains("close"))
            return "closeLip";
        // 舌頭
        if (x.contains("tongue_left"))   return "TONGUE_LEFT";
        if (x.contains("tongue_right"))  return "TONGUE_RIGHT";
        if (x.contains("tongue_foward")) return "TONGUE_FOWARD";
        if (x.contains("tongue_forward"))return "TONGUE_FOWARD"; // 容錯
        if (x.contains("tongue_back"))   return "TONGUE_BACK";
        if (x.contains("tongue_up"))     return "TONGUE_UP";
        if (x.contains("tongue_down"))   return "TONGUE_DOWN";
        return s;
    }

    private static String defaultTitleZhFor(String can) {
        switch (can) {
            case "poutLip":   return "噘嘴";
            case "closeLip":  return "抿嘴唇";
            case "TONGUE_LEFT":  return "舌頭往左";
            case "TONGUE_RIGHT": return "舌頭往右";
            case "TONGUE_FOWARD":return "舌頭往前";
            case "TONGUE_BACK":  return "舌頭往後";
            case "TONGUE_UP":    return "舌頭往上";
            case "TONGUE_DOWN":  return "舌頭往下";
            default: return "訓練指引";
        }
    }

    private static CharSequence guideTextFor(String can) {
        switch (can) {
            case "poutLip":
                return "・臉部維持在圓框內\n" +
                        "・嘴唇向前嘟起，維持 2–3 秒\n" +
                        "・放鬆回到自然\n" +
                        "・重複指示節奏完成次數";
            case "closeLip":
                return "・・臉部維持在圓框內\n" +
                        "・上下唇貼合緊閉，維持 2–3 秒\n" +
                        "・放鬆回到自然\n" +
                        "・跟著節奏重複";
            case "TONGUE_LEFT":
                return "・臉部維持在圓框內\n" + "・舌頭伸出口腔外，向左側頂，維持 2–3 秒\n・放鬆回到自然，跟著節奏重複";
            case "TONGUE_RIGHT":
                return "・臉部維持在圓框內\n" + "・舌頭伸出口腔外，向右側頂，維持 2–3 秒\n・放鬆回到自然，跟著節奏重複";
            case "TONGUE_FOWARD":
                return "・舌尖向前伸出 1–2 公分，維持 2–3 秒\n・放鬆回到自然，跟著節奏重複";
            case "TONGUE_BACK":
                return "・舌背抬起頂上顎 2–3 秒\n・放鬆回到自然，跟著節奏重複";
            case "TONGUE_UP":
                return "・舌尖抬起碰觸上顎 2–3 秒\n・放鬆回到自然，跟著節奏重複";
            case "TONGUE_DOWN":
                return "・舌尖向下貼近下齒齦 2–3 秒\n・放鬆回到自然，跟著節奏重複";
            default:
                return "・依畫面提示完成動作\n・維持 2–3 秒再放鬆\n・跟著節奏重複";
        }
    }

    @DrawableRes
    private int pickIconRes(String can) {
        // 先用你提供的兩個 icon；其餘使用應用程式圖示替代
        if ("poutLip".equals(can)) {
            int resId = getResIdSafe("ic_home_lippout");
            return resId != 0 ? resId : android.R.drawable.sym_def_app_icon;
        }
        if ("closeLip".equals(can)) {
            int resId = getResIdSafe("ic_home_lipsip");
            return resId != 0 ? resId : android.R.drawable.sym_def_app_icon;
        }
        // 舌頭類可以擴充自訂圖示；沒有就 fallback
        return android.R.drawable.sym_def_app_icon;
    }

    private int getResIdSafe(@NonNull String name) {
        try {
            return requireContext().getResources().getIdentifier(name, "drawable", requireContext().getPackageName());
        } catch (Exception e) {
            return 0;
        }
    }

    // 小工具
    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
