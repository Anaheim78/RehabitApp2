package com.example.rehabilitationapp.ui.home;

import androidx.appcompat.app.AlertDialog;
import android.widget.VideoView;
import android.content.SharedPreferences;
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

import com.example.rehabilitationapp.MainActivity;
import com.example.rehabilitationapp.R;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.rehabilitationapp.ui.login.LoginFragment;
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

//syncUserDataToFirebase() : 每次進入首頁時，檢查是否帳密要同步更新到FIREBASE


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
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                UserDao userDao = AppDatabase.getInstance(requireContext()).userDao();
                User user = userDao.findLoggedInOne();

                if (user != null) {
                    requireActivity().runOnUiThread(() -> {
                        String displayName = (user.name != null && !user.name.isEmpty()) ? user.name : user.userId;
                        binding.titleGreeting.setText("Hi, " + displayName + "!");





                    });
                } else {
                    requireActivity().runOnUiThread(() -> binding.titleGreeting.setText("Hi!"));
                }

            } catch (Exception e) {
                Log.e("HomeFragment", "讀取使用者失敗", e);
                requireActivity().runOnUiThread(() -> binding.titleGreeting.setText("Hi!"));
            }
        });

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        //20251123 登出功能
        TextView homeText = root.findViewById(R.id.home_text);



        homeText.setOnClickListener(v -> {

            new android.app.AlertDialog.Builder(requireContext())
                    .setTitle("確認登出")
                    .setMessage("你確定要登出並回到登入頁面嗎？")
                    .setPositiveButton("登出", (dialog, which) -> {

                        // 1. 清除現在登入的 userId
                        SharedPreferences prefs =
                                requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                        prefs.edit().remove("current_user_id").apply();

                        // 2. 關閉目前的資料庫
                        com.example.rehabilitationapp.data.DatabaseProvider.close();

                        // 3. 回到登入頁面
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).switchFragment(new LoginFragment());
                            ((MainActivity) getActivity()).selectTab(R.id.tab_home);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });


        // 初始化界面
        initializeUI();
        return root;
    }

    private void initializeUI() {
        Log.d("Sync_forTest", "HomeFragment initializeUI CALLED");
        binding.titleGreeting.setText("Hi, Allen!");

        //0) 固定SOP : 檢查有沒有修改密碼，同步到FIREBASE
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

        // 顯示影片 + 文字對話框
        showTutorialDialog(selectedTrainingType, item);
    }

//    private void onStartClicked() {
//        if (selectedTrainingType == -1 || items == null || selectedTrainingType >= items.size()) {
//            Toast.makeText(getContext(), "請先選擇一個訓練項目", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        TrainingItem item = items.get(selectedTrainingType);
//
//        // 根據「目前選到的卡片」決定要顯示哪一段說明文字
//        String message = getTrainingDescription(selectedTrainingType, item);
//
//        new AlertDialog.Builder(requireContext())
//                .setTitle(item.title)      // 小框標題：用卡片標題
//                .setMessage(message)       // 說明文字（你在下面函式改）
//                .setPositiveButton("知道了", (dialog, which) -> {
//                    // 按關閉後如果要真的開始訓練，就放這裡
//                    // 例如未來要進 FaceCircleCheckerActivity：
//                    // Intent intent = new Intent(getActivity(), FaceCircleCheckerActivity.class);
//                    // intent.putExtra("training_type", item.analysisType);
//                    // intent.putExtra("training_label", item.title);
//                    // startActivity(intent);
//                })
//                .show();
//    }

    private void showTutorialDialog(int index, TrainingItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tutorial, null);
        builder.setView(dialogView);

        // 取得元件
        VideoView videoView = dialogView.findViewById(R.id.tutorial_video);
        TextView descriptionText = dialogView.findViewById(R.id.tutorial_description);

        // 設定文字說明
        descriptionText.setText(getTrainingDescription(index, item));

        // 設定影片
        int videoResId = getVideoResourceId(index);
        if (videoResId != 0) {
            String videoPath = "android.resource://" + requireContext().getPackageName() + "/" + videoResId;
            videoView.setVideoURI(android.net.Uri.parse(videoPath));
            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                videoView.start();
            });
        }

        // 只有「知道了」按鈕
        AlertDialog dialog = builder
                .setTitle(item.title)
                .setPositiveButton("知道了", (d, which) -> {
                    videoView.stopPlayback();
                })
                .create();

        dialog.setOnDismissListener(d -> videoView.stopPlayback());
        dialog.show();
    }

    private int getVideoResourceId(int index) {
        switch (index) {
            case 0: return R.raw.puffcheek_class;
            case 1: return R.raw.reduce_cheek_class;
            case 2: return R.raw.loutlip_class;
            case 3: return R.raw.siplip_class;
            // 其他的之後再加
            default: return 0;
        }
    }


    // 依照「選到第幾個卡片」回傳對應說明文字
    private String getTrainingDescription(int index, TrainingItem item) {
        switch (index) {
            case 0:
                // 第 1 種動作
                return "1. 請先取下眼鏡等會遮住臉部的物品。\n" +
                        "2. 請按照文字導引，鼓起兩側臉頰或自然放鬆。\n\n" +
//                        "2. 鼓起兩側臉頰並保持至少 1.5~3 秒，每次動作間隔約 1 秒。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色：校正階段，請保持不動。\n" +
                        "．藍色：請做一次鼓起臉頰的動作，提供系統作為參考。\n" +
                        "．再次黃色：再次保持不動讓系統完成校正。\n" +
                        "．綠色：開始正式訓練，依文字導引進行復健動作。";
            case 1:
                // 第 2 種動作
                return "1. 請先取下眼鏡等會遮住臉部的物品。\n" +
//                        "2. 縮起兩側臉頰並保持至少 1.5~3 秒，每次動作間隔約 1 秒。\n\n" +
                        "2. 請按照文字導引，縮起兩側臉頰或自然放鬆。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色：校正階段，請保持不動。\n" +
                        "．藍色：請做一次鼓起臉頰的動作，提供系統作為參考。\n" +
                        "．再次黃色：再次保持不動讓系統完成校正。\n" +
                        "．綠色：開始正式訓練，依文字導引進行復健動作。";

            case 2:
                // 第 3 種動作
                return  "1. 請依照文字導引，嘴唇往前嘟起並保持或是自然放鬆。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色：校正階段，請保持不動。\n" +
                        "．藍色：請做一次鼓起臉頰的動作，提供系統作為參考。\n" +
                        "．再次黃色：再次保持不動讓系統完成校正。\n" +
                        "．綠色：開始正式訓練，依文字導引進行復健動作。";

            case 3:
                // 第 4 種動作
                return  "1. 請依照文字導引，雙脣往內縮並保持或是自然放鬆。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色（11～7 秒）：校正階段，請保持不動。\n" +
                        "．藍色（7～3 秒）：請做一次抿嘴動作，提供系統作為參考。\n" +
                        "．再次變回黃色：約 3 秒，再次保持不動讓系統完成校正。\n" +
                        "．綠色：開始正式訓練，依上面方式進行復健動作。";

            case 4:
                // 第 5 種動作
                return  "1. 動作時請儘可能保持張嘴，確認舌頭檢測框初始位置置中，若疲累也可先閉上再張嘴。\n" +
                        "2. 舌頭往左並保持至少 1.5~3 秒，並回到初始位置，每次動作間隔約 1 秒。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色（11～7 秒）：校正階段，請保持不動。\n" +
                        "．藍色（7～3 秒）：請做一次舌頭往左動作，提供系統作為參考。\n" +
                        "．再次變回黃色：約 3 秒，再次保持不動讓系統完成校正。\n" +
                        "．綠色：開始正式訓練，依上面方式進行復健動作。";


            case 5:
                // 第 6 種動作
                return  "1. 動作時請儘可能保持張嘴，確認舌頭檢測框初始位置置中，若疲累也可先閉上再張嘴。\n" +
                        "2. 舌頭往右並保持至少 1.5~3 秒，並回到初始位置，每次動作間隔約 1 秒。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色（11～7 秒）：校正階段，請保持不動。\n" +
                        "．藍色（7～3 秒）：請做一次舌頭往右動作，提供系統作為參考。\n" +
                        "．再次變回黃色：約 3 秒，再次保持不動讓系統完成校正。\n" +
                        "．綠色：開始正式訓練，依上面方式進行復健動作。";

            case 6:
                // 第 7 種動作
                return  "1. 動作時請儘可能保持張嘴，確認舌頭檢測框初始位置置中，若疲勞也可先閉上再張嘴。\n" +
                        "2. 舌頭往前並保持至少 1.5~3 秒，並回到初始位置，每次動作間隔約 1 秒。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色（11～7 秒）：校正階段，請保持不動。\n" +
                        "．藍色（7～3 秒）：請做一次舌頭往前動作，提供系統作為參考。\n" +
                        "．再次變回黃色：約 3 秒，再次保持不動讓系統完成校正。\n" +
                        "．綠色：開始正式訓練，依上面方式進行復健動作。";

            case 7:
                // 第 8 種動作
                return  "1. 動作時請儘可能保持張嘴，確認舌頭檢測框初始位置置中，若疲累也可先閉上再張嘴。\n" +
                        "2. 舌頭往上並保持至少 1.5~3 秒，並回到初始位置，每次動作間隔約 1 秒。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色（11～7 秒）：校正階段，請保持不動。\n" +
                        "．藍色（7～3 秒）：請做一次舌頭往上動作，提供系統作為參考。\n" +
                        "．再次變回黃色：約 3 秒，再次保持不動讓系統完成校正。\n" +
                        "．綠色：開始正式訓練，依上面方式進行復健動作。";

            case 8:
                // 第 9 種動作
                return  "1. 動作時請儘可能保持張嘴，確認舌頭檢測框初始位置置中，若疲累也可先閉上再張嘴。\n" +
                        "2. 舌頭往下並保持至少 1.5~3 秒，並回到初始位置，每次動作間隔約 1 秒。\n\n" +
                        "【圓框與顏色說明】：\n" +
                        "．請將頭部完全放進圓框內，保持頭部端正、不要晃動。\n" +
                        "．黃色（11～7 秒）：校正階段，請保持不動。\n" +
                        "．藍色（7～3 秒）：請做一次舌頭往下動作，提供系統作為參考。\n" +
                        "．再次變回黃色：約 3 秒，再次保持不動讓系統完成校正。\n" +
                        "．綠色：開始正式訓練，依上面方式進行復健動作。";

            default:
                // 安全預設（理論上不會到這裡）
                return "此訓練的說明尚未設定，請之後補上內容。";
        }
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