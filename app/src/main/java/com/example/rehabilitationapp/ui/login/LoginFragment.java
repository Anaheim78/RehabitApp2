package com.example.rehabilitationapp.ui.login;

import android.os.Bundle;
import android.util.Log;   // ✅ 要 import
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.rehabilitationapp.MainActivity;
import com.example.rehabilitationapp.R;
import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.dao.UserDao;
import com.example.rehabilitationapp.data.model.User;
import com.example.rehabilitationapp.ui.home.HomeFragment;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;


//登入邏輯，首次登入後從FireBase搜尋是否存在

//若成功 : 檢查本地DB有無該帳號資料;
    // 若無則新建該帳號
    // 已存在: 更新登入時間
//所以後續使用者在APP設定頁面，修改密碼時，應同步更新本地與FireBase。防呆 : 若本地ROOM的密碼與FIREBASE不同步時，應修改FIREBASE或本地?
//Q : 假設本地再沒網路得情況更新，是否要同步FIREBASE，或者發現兩者更新沒同步是錯誤，應該以原FIREBASE為主?

//A:
// A.大原則
//	1.本地允許修改
//	2.但始終以 Firebase 為最終真相
//	3.網路恢復後，以 Firebase 為準
//
//B.實際場景
//	1.離線修改密碼
//	2.本地先存儲新密碼
//	3.標記為 PENDING_SYNC
//	4.網路恢復後同步
//	5.立即同步到 Firebase，並更新本地同步狀態
//
//
//C. 後台同步服務
//	1.定期檢查 PENDING_SYNC 狀態
//	2.重試同步到 Firebase
//	3.如果多次同步失敗，通知用戶

public class LoginFragment extends Fragment {

    private static final String TAG = "LOGIN"; // ✅ Log 標籤

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

        EditText etId = view.findViewById(R.id.etId);
        EditText etPassword = view.findViewById(R.id.etPassword);
        Button btnLogin = view.findViewById(R.id.btnLogin);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        AppDatabase localDb = AppDatabase.getInstance(requireContext());
        UserDao userDao = localDb.userDao();

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
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Firestore 查詢成功，筆數: " + task.getResult().size());

                            for (DocumentSnapshot doc : task.getResult()) {
                                Log.d(TAG, "文件內容: " + doc.getData());
                            }

                            if (!task.getResult().isEmpty()) {
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

                                    Log.d(TAG, "解析後 → createdAt=" + createdAt
                                            + ", formatted=" + createdAtFormatted
                                            + ", email=" + email
                                            + ", name=" + name
                                            + ", birthday=" + birthday
                                            + ", gender=" + gender
                                            + ", uiStyle=" + uiStyle);

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
                                            userDao.insert(u);
                                            Log.d(TAG, "新使用者已寫入本地 DB: " + u.userId);
                                        } else {
                                            userDao.updateLoginStatus(id, 1);
                                            Log.d(TAG, "更新本地 DB 登入狀態: " + id);
                                        }
                                    }).start();

                                    requireActivity().runOnUiThread(() -> {
                                        if (getActivity() instanceof MainActivity) {
                                            ((MainActivity) getActivity()).switchFragment(new HomeFragment());
                                            ((MainActivity) getActivity()).selectTab(R.id.tab_home);
                                        }
                                    });

                                } catch (Exception e) {
                                    Log.e(TAG, "文件解析錯誤", e);
                                }
                            } else {
                                Log.w(TAG, "查無符合帳密 → Firestore 無文件");
                                Toast.makeText(getContext(), "帳號或密碼錯誤", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.e(TAG, "Firestore 查詢失敗", task.getException());
                            Toast.makeText(getContext(), "登入失敗，請稍後再試", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}
