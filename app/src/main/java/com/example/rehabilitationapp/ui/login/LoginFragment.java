package com.example.rehabilitationapp.ui.login;

import android.os.Bundle;
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

public class LoginFragment extends Fragment {

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

            if (id.isEmpty() || password.isEmpty()) {
                Toast.makeText(getContext(), "請輸入帳號和密碼", Toast.LENGTH_SHORT).show();
                return;
            }

            db.collection("Users")
                    .whereEqualTo("user_id", id)
                    .whereEqualTo("password", password)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            DocumentSnapshot doc = task.getResult().getDocuments().get(0);

                            long createdAt = doc.getLong("createdAt") != null
                                    ? doc.getLong("createdAt") : System.currentTimeMillis();
                            String createdAtFormatted = doc.getString("createdAtFormatted");

                            String email = doc.getString("email");
                            String name = doc.getString("name");
                            String birthday = doc.getString("birthday");
                            String gender = doc.getString("gender");
                            String uiStyle = doc.getString("ui_style");

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
                                } else {
                                    userDao.updateLoginStatus(id, 1);
                                }
                            }).start();

                            // ✅ 改成直接呼叫 MainActivity 切換 Fragment
                            requireActivity().runOnUiThread(() -> {
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).switchFragment(new HomeFragment());
                                    ((MainActivity) getActivity()).selectTab(R.id.tab_home);
                                }
                            });

                        } else {
                            Toast.makeText(getContext(), "帳號或密碼錯誤", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}
