package com.example.rehabilitationapp.ui.home;

import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import com.example.rehabilitationapp.R;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DatabaseReference;

import com.example.rehabilitationapp.data.dao.UserDao;
import com.example.rehabilitationapp.data.model.User;
import com.example.rehabilitationapp.databinding.FragmentHomeBinding;
import android.view.View.OnClickListener;
import android.widget.Toast;
import android.widget.GridLayout;
import android.content.Intent;
import com.example.rehabilitationapp.ui.facecheck.FaceCircleCheckerActivity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.example.rehabilitationapp.data.AppDatabase;
import com.example.rehabilitationapp.data.dao.TrainingItemDao;
import com.example.rehabilitationapp.data.model.TrainingItem;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import android.util.Log;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
//每次進入首頁時，檢查是否帳密要同步更新到FIREBASE


public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private View selectedCard = null;
    private int selectedTrainingType = -1;
    private List<TrainingItem> items;



    //在onCreateView的視圖初始化_穩定結束後，再進行部分內容渲染
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 在這裡設定標題
        requireActivity().setTitle("首頁");  // 或 "訓練計畫"
        //binding.titleGreeting.setText("Hi, Allen!");

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        // 初始化界面
        initializeUI();
        return root;
    }

    private void initializeUI() {
        Log.d("Sync_forTest", "HomeFragment initializeUI CALLED");
        binding.titleGreeting.setText("Hi, Allen!");

        //0) 同步檢查
        syncUserDataToFirebase();

        // 1) 用 DAO 讀資料（背景執行緒）
        Executors.newSingleThreadExecutor().execute(() -> {
            TrainingItemDao dao = AppDatabase.getInstance(requireContext()).trainingItemDao();

            List<TrainingItem> list = dao.getAllNow(); // 同步查詢

            // 2) 回到主執行緒渲染 UI
            requireActivity().runOnUiThread(() -> {
                items = list;
                buildCards(items);
            });
        });

        // 3) 開始按鈕
        binding.startButton.setOnClickListener(v -> onStartClicked());
    }

    private void onStartClicked() {
        if (selectedTrainingType == -1 || items == null || selectedTrainingType >= items.size()) {
            Toast.makeText(getContext(), "請先選擇一個訓練項目", Toast.LENGTH_SHORT).show();
            return;
        }
        TrainingItem item = items.get(selectedTrainingType);

        Toast.makeText(getContext(), "開始 " + item.title + " 訓練！", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(getActivity(), FaceCircleCheckerActivity.class);
        intent.putExtra("training_type", item.analysisType); // 用 DB 裡的 type
        intent.putExtra("training_label", item.title);
        startActivity(intent);
    }

    private void selectCard(View card, int trainingType) {
        // 清除之前選中的卡片
        if (selectedCard != null) {
            selectedCard.setSelected(false);
            ImageView prevIndicator = selectedCard.findViewById(R.id.selected_indicator);
            if (prevIndicator != null) {
                prevIndicator.setVisibility(View.GONE);
            }
        }

        // 設置新選中的卡片
        selectedCard = card;
        selectedTrainingType = trainingType;
        card.setSelected(true);

        ImageView indicator = card.findViewById(R.id.selected_indicator);
        if (indicator != null) {
            indicator.setVisibility(View.VISIBLE);
        }

        // 啟用開始按鈕
        binding.startButton.setEnabled(true);
    }

    private void buildCards(List<TrainingItem> items) {
        GridLayout trainingContainer = binding.trainingContainer;
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());

        trainingContainer.removeAllViews();

        for (int i = 0; i < items.size(); i++) {
            TrainingItem item = items.get(i);
            View card = layoutInflater.inflate(R.layout.training_card_item, trainingContainer, false);

            ImageView image = card.findViewById(R.id.card_image);
            TextView  label = card.findViewById(R.id.card_label);

            int resId = getResources().getIdentifier(
                    item.imageResName, "drawable", requireContext().getPackageName()
            );
            if (resId != 0) image.setImageResource(resId);
            label.setText(item.title);

            final int index = i;
            card.setOnClickListener(v -> selectCard(v, index));

            // ✅ 父容器為 GridLayout → 用 GridLayout.LayoutParams
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            // 若要 Figma 的水平 18 / 垂直 13，左右各 9、上下各 13
            lp.setMargins(dp(9), dp(13), dp(9), dp(13));
            card.setLayoutParams(lp);

            trainingContainer.addView(card);
        }
    }

    private void syncUserDataToFirebase() {

        if (!isNetworkAvailable()) {
            Log.d("Sync_forTest", "without network");
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                UserDao userDao = AppDatabase.getInstance(requireContext()).userDao();
                User user = userDao.findLoggedInOne();

                if (user == null) {
                    Log.d("Sync_forTest", "User = null, skip sync");
                    return;
                }

                Log.d("Sync_forTest", "LoggedInUser = " + user.userId + ", need_sync = " + user.need_sync);

                if (user.need_sync != 1) return;

                FirebaseFirestore db = FirebaseFirestore.getInstance();
                CollectionReference usersRef = db.collection("Users");

                // Step 1. 用 user_id 查 Firestore 文件
                usersRef.whereEqualTo("user_id", user.userId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            if (!querySnapshot.isEmpty()) {
                                // Step 2. 找到該文件的亂碼 docId
                                String docId = querySnapshot.getDocuments().get(0).getId();
                                Log.d("Sync_forTest", "找到 user_id 對應文件: " + docId);

                                // Step 3. 更新指定欄位（不覆蓋整筆）
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("password", user.password);
                                updates.put("updateTime", System.currentTimeMillis());

                                usersRef.document(docId)
                                        .update(updates)
                                        .addOnSuccessListener(aVoid -> {
                                            Log.d("Sync_forTest", "Firestore 密碼更新成功");

                                            // Step 4. 更新本地 DB 狀態
                                            Executors.newSingleThreadExecutor().execute(() -> {
                                                userDao.updateSyncStatus(user.userId, 0);
                                                Log.d("Sync_forTest", "本地同步成功");
                                            });
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e("Sync_forTest", "Firestore 更新失敗", e);
                                        });

                            } else {
                                Log.w("Sync_forTest", "找不到 user_id = " + user.userId + " 的文件，略過更新");
                            }
                        })
                        .addOnFailureListener(e -> Log.e("Sync_forTest", "查詢失敗", e));

            } catch (Exception e) {
                Log.e("Sync_forTest", "錯誤", e);
            }
        });
    }



    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        selectedCard = null;
    }


    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)
                requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            // 舊版 Android
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
    }

}