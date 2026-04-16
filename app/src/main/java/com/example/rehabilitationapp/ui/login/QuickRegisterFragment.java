package com.example.rehabilitationapp.ui.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.rehabilitationapp.MainActivity;
import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.AppLogger;
import com.example.rehabilitationapp.data.dao.UserDao;
import com.example.rehabilitationapp.data.model.User;
import com.example.rehabilitationapp.ui.home.HomeFragment;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * B軌：快速登入（暱稱 + 問卷）
 *
 * 流程：
 * 1. 輸入暱稱
 * 2. 填問卷（年齡範圍 / 性別 / 術後天數 / 診斷類型）
 * 3. App 自動產生 userId = "Q_" + 隨機4碼
 * 4. 純本地建帳（不走 Firebase 驗證）
 * 5. 訓練資料照常上傳（SFTP / Supabase / Firebase）
 */
public class QuickRegisterFragment extends Fragment {

    private static final String TAG = "QUICK_REG";

    public QuickRegisterFragment() {
        super(R.layout.fragment_quick_register);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quick_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ===== 綁定 View =====
        EditText etNickname       = view.findViewById(R.id.etNickname);
        Spinner  spinnerAge       = view.findViewById(R.id.spinnerAge);
        RadioGroup rgGender       = view.findViewById(R.id.rgGender);
        EditText etSurgeryDays    = view.findViewById(R.id.etSurgeryDays);
        EditText etDiagnosis      = view.findViewById(R.id.etDiagnosis);
        Button   btnRegister      = view.findViewById(R.id.btnQuickRegister);
        Button   btnBack          = view.findViewById(R.id.btnBackToLogin);

        // ===== 年齡範圍下拉選單 =====
        String[] ageOptions = {
                "請選擇年齡範圍",
                "20歲以下", "20-29歲", "30-39歲", "40-49歲",
                "50-59歲", "60-69歲", "70歲以上"
        };
        ArrayAdapter<String> ageAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                ageOptions
        );
        spinnerAge.setAdapter(ageAdapter);

        // （診斷類型改為自由輸入，不需要初始化 Spinner）

        // ===== 返回登入頁 =====
        btnBack.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchFragment(new LoginFragment());
            }
        });

        btnBack.setVisibility(View.GONE);

        // ===== 確認註冊 =====
        btnRegister.setOnClickListener(v -> {
            // 1. 驗證暱稱
            String nickname = etNickname.getText().toString().trim();
            if (nickname.isEmpty()) {
                Toast.makeText(getContext(), "請輸入暱稱", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. 驗證年齡
            if (spinnerAge.getSelectedItemPosition() == 0) {
                Toast.makeText(getContext(), "請選擇年齡範圍", Toast.LENGTH_SHORT).show();
                return;
            }
            String ageRange = spinnerAge.getSelectedItem().toString();

            // 3. 驗證性別
            int selectedGenderId = rgGender.getCheckedRadioButtonId();
            if (selectedGenderId == -1) {
                Toast.makeText(getContext(), "請選擇性別", Toast.LENGTH_SHORT).show();
                return;
            }
            RadioButton selectedGender = view.findViewById(selectedGenderId);
            String gender = selectedGender.getTag().toString(); // tag="M" or "F"

            // 4. 術後天數（選填）
            String surgeryDaysStr = etSurgeryDays.getText().toString().trim();
            final int surgeryDays;
            if (!surgeryDaysStr.isEmpty()) {
                int parsed = -1;
                try {
                    parsed = Integer.parseInt(surgeryDaysStr);
                } catch (NumberFormatException e) {
                    // 不是數字就忽略
                }
                surgeryDays = parsed;
            } else {
                surgeryDays = -1;
            }

            // 5. 診斷類型（選填）
            String diagnosisType = etDiagnosis.getText().toString().trim();

            // ===== 產生 userId =====
            String shortId = UUID.randomUUID().toString().substring(0, 4);
            String generatedUserId = "Q_" + shortId;

            Log.d(TAG, "B軌註冊 → 暱稱=" + nickname + ", userId=" + generatedUserId);

            // ===== STEP 1：寫入 SharedPreferences =====
            SharedPreferences prefs =
                    requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .putString("current_user_id", generatedUserId)
                    .putString("account_type", "quick")  // ★ 標記為 B軌
                    .commit();

            // ===== STEP 2：取得對應 DB 並建帳 =====
            AppDatabase userDb = AppDatabase.getInstance(requireContext());
            UserDao userDao = userDb.userDao();

            long now = System.currentTimeMillis();
            String nowFormatted = new SimpleDateFormat("yyyy/MM/dd HH:mm",
                    Locale.getDefault()).format(new Date(now));

            new Thread(() -> {
                User u = new User();
                u.userId = generatedUserId;
                u.password = null;             // B軌不需要密碼
                u.createdAt = now;
                u.createdAtFormatted = nowFormatted;
                u.loginStatus = 1;
                u.name = nickname;             // 暱稱存在 name 欄位
                u.gender = gender;
                u.accountType = "quick";       // ★ B軌標記
                u.ageRange = ageRange;
                u.postSurgeryDays = surgeryDays;
                u.diagnosisType = diagnosisType;
                u.uiStyle = gender;            // UI 風格預設跟性別一致

                userDao.insert(u);
                Log.d(TAG, "B軌帳號已建立: " + u.userId + " (" + nickname + ")");

                // ##### 2026_0315_CAI 雙軌登入修改 - B軌問卷資料上傳 Firebase #####
                Map<String, Object> userData = new HashMap<>();
                userData.put("user_id", generatedUserId);
                userData.put("name", nickname);
                userData.put("gender", gender);
                userData.put("account_type", "quick");
                userData.put("age_range", ageRange);
                userData.put("post_surgery_days", surgeryDays);
                userData.put("diagnosis_type", diagnosisType);
                userData.put("createdAt", now);
                userData.put("createdAtFormatted", nowFormatted);

                FirebaseFirestore.getInstance()
                        .collection("Users")
                        .add(userData)
                        .addOnSuccessListener(docRef ->
                                Log.d(TAG, "B軌資料已上傳 Firebase: " + docRef.getId()))
                        .addOnFailureListener(e ->
                                Log.e(TAG, "B軌 Firebase 上傳失敗", e));
                // ##### 2026_0315_CAI 雙軌登入修改 END #####
            }).start();

            // ===== STEP 3：切換到首頁 =====
            AppLogger.logLogin(generatedUserId, nickname);

            if (getActivity() instanceof MainActivity) {
                requireActivity().runOnUiThread(() -> {
                    ((MainActivity) getActivity()).switchFragment(new HomeFragment());
                    ((MainActivity) getActivity()).selectTab(R.id.tab_home);
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideBottomNav();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showBottomNav();
        }
    }
}