package com.example.rehabilitationapp.ui.results

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
//import androidx.compose.material3.Text
//import androidx.compose.material3.LocalTextStyle
//import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabilitationapp.R
import com.example.rehabilitationapp.data.AppDatabase
import com.example.rehabilitationapp.data.dao.TrainingHistoryDao
import com.example.rehabilitationapp.data.model.TrainingHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalConfiguration
import android.util.Log
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.example.rehabilitationapp.MainActivity
import com.example.rehabilitationapp.data.FirebaseUploader
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope

class TrainingResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            訓練結果頁()   // 這就是你 Figma 匯出的 Composable
        }
    }
}

@Preview
@Composable
fun AndroidPreview_訓練結果頁() {
    MaterialTheme {  // 必須加這個
        Box(Modifier.size(360.dp, 640.dp)) {
            訓練結果頁()
        }
    }
}


@Composable
fun 訓練結果頁() {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    val boxWidth = (screenWidth * 0.7f).dp  // 螢幕寬度的 70%
    val boxHeight = 60.dp

    val context = LocalContext.current
    val dao = AppDatabase.getInstance(LocalContext.current).trainingHistoryDao()
    var list by remember { mutableStateOf(emptyList<TrainingHistory>()) }

// ★ 這兩行要放這裡（在 LaunchedEffect 之前）
    val scrollToId = (context as? android.app.Activity)?.intent?.getStringExtra("scroll_to_id")
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val targetDate = (context as? android.app.Activity)?.intent?.getLongExtra("target_date", 0L) ?: 0L

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            list = if (targetDate > 0L) {
                dao.getRecordsByDate(targetDate)  // ★ 查指定日期
            } else {
                dao.getTodayRecords()  // 沒傳日期就查今天
            }
        }
    }

// ★ 滾動到指定紀錄
    LaunchedEffect(list, scrollToId) {
        if (scrollToId != null && list.isNotEmpty()) {
            val index = list.indexOfFirst { it.trainingID == scrollToId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 背景圖片
        // 用 Compose 漸變替代 XML shape
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFD17C), // startColor
                            Color(0xFFFFFBEA)  // endColor
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
//                .background(Color.White)
                .padding(start = 8.dp, end = 8.dp, top = 30.dp)
            // verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 首頁
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "返回",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "首頁",
                    fontSize = 18.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { }
                )
            }

//        Spacer(modifier = Modifier.height(0.dp))  // 手動控制間距
            //訓練結果
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
//                .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "訓練結果",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            // 卡片列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
//                    .height(400.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list) { data ->
                    TrainingResultCard(data) {
                        // 更新後重新載入
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            list = dao.getTodayRecords()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                //To Do..
                modifier = Modifier.width(boxWidth).height(boxHeight)
                    .background(
                        Color.White,
                        shape = RoundedCornerShape(26.dp)
                    )
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp)
            ) {
                Text(
//                    text = "系統編輯訊息",
                    text = "",
                    modifier = Modifier.align(Alignment.Center)
                        .offset(y = (-8).dp)
                )
            }

            Row(
                Modifier.width(boxWidth).height(boxHeight)
                    .offset(y = (-30).dp).align(Alignment.CenterHorizontally),
//            horizontalArrangement =  Arrangement.SpaceEvenly ,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(6.dp)) // 左右間隔 16dp
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFFFDA73), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFFEEA752), RoundedCornerShape(8.dp))
                    .clickable {
                        // ★ 讀取上次訓練的 planId 和 planTitle
                        val prefs = context.getSharedPreferences("training_prefs", android.content.Context.MODE_PRIVATE)
                        val lastPlanId = prefs.getInt("last_plan_id", -1)
                        val lastPlanTitle = prefs.getString("last_plan_title", "")

                        if (lastPlanId > 0) {
                            // 有 planId → 跳回 TrainingDetailActivity
                            val intent = Intent(context, com.example.rehabilitationapp.ui.plan.TrainingDetailActivity::class.java).apply {
                                putExtra("plan_id", lastPlanId)
                                putExtra("plan_title", lastPlanTitle)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            context.startActivity(intent)
                        } else {
                            // 沒有 planId → 回計畫列表
                            val intent = Intent(context, MainActivity::class.java).apply {
                                putExtra("start_tab", "plan")
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            context.startActivity(intent)
                        }
                    (context as? Activity)?.finish()
                },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_result_redo),
                        contentDescription = "重做",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp)) // 左右間隔 16dp
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFFFDA73), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFFEEA752), RoundedCornerShape(8.dp))
                        .clickable {
                            var fbDone = false
                            var csvDone = false
                            var fbSuccess = 0
                            var fbFail = 0
                            var csvSuccess = 0
                            var csvFail = 0

                            fun showResultIfBothDone() {
                                if (fbDone && csvDone) {
                                    // ★ 查詢剩餘未同步數量 ★
                                    Thread {
                                        val dao = AppDatabase.getInstance(context).trainingHistoryDao()
                                        val remainingFb = dao.getUnsyncedWithLimit().size
                                        val remainingCsv = dao.getUnsyncedCsvRecords().size
                                        val totalRemaining = remainingFb + remainingCsv

                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            val totalSuccess = fbSuccess + csvSuccess

                                            val msg = when {
                                                totalSuccess == 0 && totalRemaining == 0 -> "沒有需要同步的資料"
                                                totalRemaining == 0 -> "同步完成：$totalSuccess 筆 ✓"
                                                totalSuccess > 0 -> "已同步 $totalSuccess 筆，剩餘 $totalRemaining 筆待同步"
                                                else -> "同步失敗，剩餘 $totalRemaining 筆待同步"
                                            }
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }.start()
                                }
                            }

                            // 1. Firebase 補傳
                            FirebaseUploader.uploadTodayUnsynced(context) { s, f ->
                                Log.d("Upload", "Firebase - 成功: $s, 失敗: $f")
                                fbSuccess = s
                                fbFail = f
                                fbDone = true
                                showResultIfBothDone()
                            }

                            // 2. CSV 補傳
                            com.example.rehabilitationapp.data.SupabaseUploader.retryUnsyncedCsv(context) { s, f ->
                                Log.d("Upload", "CSV - 成功: $s, 失敗: $f")
                                csvSuccess = s
                                csvFail = f
                                csvDone = true
                                showResultIfBothDone()
                            }

                            // 3. 先顯示同步中
                            android.widget.Toast.makeText(context, "正在同步...", android.widget.Toast.LENGTH_SHORT).show()

//                            FirebaseUploader.uploadTodayUnsynced(context) { success, fail ->
//                                Log.d("Upload", "成功: $success, 失敗: $fail")
//
//
//                                // ★ 加 Toast 提示
//                                android.os.Handler(android.os.Looper.getMainLooper()).post {
//                                    if (fail == 0 && success > 0) {
//                                        android.widget.Toast.makeText(context, "上傳成功：$success 筆", android.widget.Toast.LENGTH_SHORT).show()
//                                    } else if (fail == 0 && success == 0) {
//                                        android.widget.Toast.makeText(context, "沒有需要上傳的資料", android.widget.Toast.LENGTH_SHORT).show()
//                                    } else {
//                                        android.widget.Toast.makeText(context, "上傳完成：成功 $success 筆，失敗 $fail 筆", android.widget.Toast.LENGTH_SHORT).show()
//                                    }
//                                }
//                            }
//
//                            // 2. CSV 補傳
//                            com.example.rehabilitationapp.data.SupabaseUploader.retryUnsyncedCsv(context) { csvSuccess, csvFail ->
//                                Log.d("Upload", "CSV - 成功: $csvSuccess, 失敗: $csvFail")
//                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_result_share),
                        contentDescription = "分享",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(51.dp)) //

                Box(
                    modifier = Modifier
                        .weight(1.54f)
                        .height(44.dp) // 保持高度
                        .background(Color(0xFFFFDA73), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFFEEA752), RoundedCornerShape(8.dp))
                    .clickable {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("start_tab", "home")
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
                    (context as? Activity)?.finish()
                },

                    contentAlignment = Alignment.Center // 這行很重要！讓內容在 Box 中居中
                ) {
                    Text(
                        text = "返回首頁",
                        fontSize = 14.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center // 文字本身也要居中
                    )

                }
            }

            Spacer(modifier = Modifier.height(80.dp))


        }
        CustomBottomNavigation(

            modifier = Modifier.align(Alignment.BottomCenter),
            selectTab = 1 // 1 = 計畫頁面

        )
    }
}

/*  沒有自評框版本
@Composable
fun TrainingResultCard(data: TrainingHistory) {
    val context = LocalContext.current
    // =============宣告方法區============
    // 計算達成率
    val percentage = if (data.targetTimes > 0) {
        (data.achievedTimes * 100 / data.targetTimes)
    } else {
        0
    }
    //
    val iconRes = when (data.trainingLabel) {
        "PUFF_CHEEK" -> R.drawable.ic_home_cheekpuff
        "REDUCE_CHEEK" -> R.drawable.ic_home_cheekreduce
        "POUT_LIPS" -> R.drawable.ic_home_lippout
        "SIP_LIPS" -> R.drawable.ic_home_lipsip
        "TONGUE_LEFT" -> R.drawable.ic_home_tongueleft
        "TONGUE_RIGHT" -> R.drawable.ic_home_tongueright
        "TONGUE_FOWARD" -> R.drawable.ic_home_tonguefoward
        "TONGUE_BACK" -> R.drawable.ic_home_tongueback
        "TONGUE_UP" -> R.drawable.ic_home_tongueup
        "TONGUE_DOWN" -> R.drawable.ic_home_tonguedown
        "JAW_LEFT" -> R.drawable.ic_home_jawleft
        "JAW_RIGHT" -> R.drawable.ic_home_jawright
        else -> android.R.drawable.ic_dialog_info
    }

    //卡片區
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clickable {
                val curveData = data.curveJson ?: "[]"
                val intent = Intent(context, CurveChartActivity::class.java)
                intent.putExtra("curveJson", curveData)
                context.startActivity(intent)
            },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左側頭像區域
            // 頭像圖片 (這裡用Icon代替，實際可換成Image)
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription =  data.trainingLabel,
                tint = Color.Unspecified,
                modifier = Modifier.size(60.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 右側資訊區域
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,  // 加這行
            ) {
                // 第一行：達成率 和 百分比+時間
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${data.achievedTimes}/${data.targetTimes}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${percentage}%",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${data.durationTime}秒",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 第二行：標籤說明
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "達成    目標次數",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontSize = 10.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "完成率",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "持續",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
*/

@Composable
fun TrainingResultCard(data: TrainingHistory, onUpdate: () -> Unit = {}) {
    val context = LocalContext.current
    val dao = AppDatabase.getInstance(context).trainingHistoryDao()


    // ★ 自評對話框狀態
    var showDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    // 計算達成率
    val percentage = if (data.targetTimes > 0) {
        (data.achievedTimes * 100 / data.targetTimes)
    } else {
        0
    }

    val iconRes = when (data.trainingLabel) {
        "PUFF_CHEEK" -> R.drawable.ic_home_cheekpuff
        "REDUCE_CHEEK" -> R.drawable.ic_home_cheekreduce
        "POUT_LIPS" -> R.drawable.ic_home_lippout
        "SIP_LIPS" -> R.drawable.ic_home_lipsip
        "TONGUE_LEFT" -> R.drawable.ic_home_tongueleft
        "TONGUE_RIGHT" -> R.drawable.ic_home_tongueright
        "TONGUE_FOWARD" -> R.drawable.ic_home_tonguefoward
        "TONGUE_BACK" -> R.drawable.ic_home_tongueback
        "TONGUE_UP" -> R.drawable.ic_home_tongueup
        "TONGUE_DOWN" -> R.drawable.ic_home_tonguedown
        "JAW_LEFT" -> R.drawable.ic_home_jawleft
        "JAW_RIGHT" -> R.drawable.ic_home_jawright
        else -> android.R.drawable.ic_dialog_info
    }

    // ★ 自評對話框
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("自評完成次數") },
            text = {
                Column {
                    Text("系統辨識：${data.achievedTimes} 次")
                    if (data.selfReportCount >= 0) {
                        Text("目前自評：${data.selfReportCount} 次")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it.filter { c -> c.isDigit() } },
                        label = { Text("輸入次數") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = inputText.toIntOrNull() ?: -1
                        if (count >= 0) {
                            Thread {
                                dao.updateSelfReport(data.trainingID, count)
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    android.widget.Toast.makeText(context, "已更新自評：$count 次", android.widget.Toast.LENGTH_SHORT).show()
                                    onUpdate()
                                }
                            }.start()
                        }
                        showDialog = false
                        inputText = ""
                    }
                ) {
                    Text("確定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false; inputText = "" }) {
                    Text("取消")
                }
            }
        )
    }

    // 卡片區
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clickable { showDialog = true },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = data.trainingLabel,
                tint = Color.Unspecified,
                modifier = Modifier.size(60.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayCount = if (data.selfReportCount >= 0) {
                        "${data.selfReportCount}/${data.targetTimes}"
                    } else {
                        "${data.achievedTimes}/${data.targetTimes}"
                    }
                    Text(
                        text = displayCount,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${percentage}%",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${data.durationTime}秒",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val label = if (data.selfReportCount >= 0) "自評    目標次數" else "達成    目標次數"
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontSize = 10.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "完成率",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "持續",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomBottomNavigation(
    modifier: Modifier = Modifier,
    selectTab: Int = 0,
    onTabSelected: (Int) -> Unit = {}
) {
    Log.d("BottomNav", "CustomBottomNavigation 開始執行, selectedTab = $selectTab")
    //context跳轉用
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(109.dp)
    ) {
        // 你原本的橢圓背景：保留
        Image(
            painter = painterResource(id = R.drawable.bg_nav_circle_bar_nav),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.
            fillMaxWidth()
                .height(109.dp)
                .align(Alignment.BottomCenter)
        )

        // 按鈕列：疊在背景上方、靠底置中
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 50.dp, end = 50.dp, top = 35.dp, bottom = 0.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                text = "首頁",
                isSelected = selectTab == 0,
                glyphRes = R.drawable.ic_home_glyph_white
            ) {
                onTabSelected(0)
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("start_tab", "home")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                (context as? Activity)?.finish()
            }

            NavItem(
                text = "計畫",
                isSelected = selectTab == 1,
                glyphRes = R.drawable.ic_plan_glyph_white   // 先共用，之後再換各自的 glyph
            ) {
                onTabSelected(1)
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("start_tab", "plan")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                (context as? Activity)?.finish()
            }

            NavItem(
                text = "紀錄",
                isSelected = selectTab == 2,
                glyphRes = R.drawable.ic_record_glyph_white
            ) {
                onTabSelected(2)
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("start_tab", "record")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                (context as? Activity)?.finish()
            }

            NavItem(
                text = "設定",
                isSelected = selectTab == 3,
                glyphRes = R.drawable.ic_setting_glyph_white
            ) {
                onTabSelected(3)
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("start_tab", "setting")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                (context as? Activity)?.finish()
            }
        }
    }
}



@Composable
fun NavItem(
    text: String,
    isSelected: Boolean,
    glyphRes: Int,            // 這顆白色 icon 圖（向量或 PNG/JPG）
    onClick: () -> Unit
) {
    val bgRes = if (isSelected) R.drawable.bg_nav_icon_selected
    else R.drawable.bg_nav_icon_unselected

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
            .offset(y=-12.dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            // 背景：選中 / 未選中
            Image(
                painter = painterResource(id = bgRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            // 前景：白色 glyph（你現有的 ic_home_glyph_white）
            Image(
                painter = painterResource(id = glyphRes),
                contentDescription = text,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (isSelected) Color.Black else Color.Gray
        )
    }
}
