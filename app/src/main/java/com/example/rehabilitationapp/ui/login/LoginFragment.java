package com.example.rehabilitationapp.ui.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;


// 登入邏輯：
// 啟動時顯示兩個按鈕：「正式帳號登入」與「快速登入（暱稱）」
// A軌：點選正式帳號 → 顯示帳號密碼欄位 → Firebase 驗證（原流程）
// B軌：點選快速登入 → 跳到 QuickRegisterFragment

public class LoginFragment extends Fragment {

    private static final String TAG = "LOGIN";

    // ##### 2026_0315_CAI 雙軌登入修改 - 切換模式 #####
// "both"  = 顯示雙軌選擇（A+B 都有）
// "quick" = 只能匿名登入（B軌版 APK）
// "formal" = 只能正式帳號登入（A軌版 APK）
    private static final String LOGIN_MODE = "quick";
// ##### 2026_0315_CAI 雙軌登入修改 END #####

    public LoginFragment() {
        super(R.layout.fragment_login);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ===== 選擇模式的按鈕 =====
        Button btnModeFormal = view.findViewById(R.id.btnModeFormal);
        Button btnModeQuick  = view.findViewById(R.id.btnModeQuick);

        // ===== A軌的輸入區塊 =====
        LinearLayout layoutFormalLogin = view.findViewById(R.id.layoutFormalLogin);
        EditText etId = view.findViewById(R.id.etId);
        EditText etPassword = view.findViewById(R.id.etPassword);
        Button btnLogin = view.findViewById(R.id.btnLogin);

        FirebaseFirestore db = FirebaseFirestore.getInstance();




// ##### 2026_0315_CAI 雙軌登入修改 - 登入模式切換 #####
        if ("quick".equals(LOGIN_MODE)) {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchFragment(new QuickRegisterFragment());
            }
            return;
        }
        if ("formal".equals(LOGIN_MODE)) {
            layoutFormalLogin.setVisibility(View.VISIBLE);
            btnModeFormal.setVisibility(View.GONE);
            btnModeQuick.setVisibility(View.GONE);
        }
// ##### 2026_0315_CAI 雙軌登入修改 END #####


        // ===== 點「正式帳號登入」→ 顯示帳密欄位 =====
        btnModeFormal.setOnClickListener(v -> {
            layoutFormalLogin.setVisibility(View.VISIBLE);
            btnModeFormal.setVisibility(View.GONE);
            btnModeQuick.setVisibility(View.GONE);
        });

        // ===== 點「快速登入」→ 跳到 QuickRegisterFragment =====
        btnModeQuick.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchFragment(new QuickRegisterFragment());
            }
        });

        // ===== A軌登入邏輯（原流程不變）=====
        btnLogin.setOnClickListener(v -> {
            String id = etId.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            Log.d(TAG, "使用者輸入 → id=" + id + ", password=" + password);

            if (id.isEmpty() || password.isEmpty()) {
                Toast.makeText(getContext(), "請輸入帳號和密碼", Toast.LENGTH_SHORT).show();
                return;
            }

            db.collection("Users")
                    .whereEqualTo("user_id", id)
                    .whereEqualTo("password", password)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            Toast.makeText(getContext(), "登入失敗，請稍後再試", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (task.getResult().isEmpty()) {
                            Toast.makeText(getContext(), "帳號或密碼錯誤", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);

                        try {
                            long createdAt = doc.getLong("createdAt") != null
                                    ? doc.getLong("createdAt") : System.currentTimeMillis();
                            String createdAtFormatted = doc.getString("createdAtFormatted");

                            String email = doc.getString("email");
                            String name = doc.getString("name");
                            String birthday = doc.getString("birthday");
                            String gender = doc.getString("gender");
                            String uiStyle = doc.getString("ui_style");

                            // STEP 1：先寫入 current_user_id
                            SharedPreferences prefs =
                                    requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                            prefs.edit().putString("current_user_id", id).commit();

                            // STEP 2：取得正確 User DB
                            AppDatabase userDb = AppDatabase.getInstance(requireContext());
                            UserDao userDao = userDb.userDao();

                            // STEP 3：更新/新增 User
                            new Thread(() -> {
                                User existing = userDao.findById(id);

                                if (existing == null) {
                                    User u = new User();
                                    u.userId = id;
                                    u.password = password;
                                    u.createdAt = createdAt;
                                    u.createdAtFormatted = createdAtFormatted;
                                    u.loginStatus = 1;
                                    u.email = email;
                                    u.name = name;
                                    u.birthday = birthday;
                                    u.gender = gender;
                                    u.uiStyle = uiStyle;
                                    u.accountType = "formal";  // ★ A軌

                                    userDao.insert(u);
                                    Log.d(TAG, "新使用者已寫入正確 DB: " + u.userId);

                                } else {
                                    userDao.updateLoginStatus(id, 1);
                                    Log.d(TAG, "更新登入狀態: " + id);
                                }
                            }).start();

                            // STEP 4：切換到 Home
                            AppLogger.logLogin(id, name);
                            requireActivity().runOnUiThread(() -> {
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).switchFragment(new HomeFragment());
                                    ((MainActivity) getActivity()).selectTab(R.id.tab_home);
                                }
                            });

                        } catch (Exception e) {
                            Log.e(TAG, "文件解析錯誤", e);
                        }
                    });
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
