package com.example.rehabilitationapp.ui.results

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.rehabilitationapp.R
import com.example.rehabilitationapp.data.AppDatabase
import com.example.rehabilitationapp.data.model.TrainingHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.Intent
import com.example.rehabilitationapp.MainActivity
import com.example.rehabilitationapp.data.FirebaseUploader
import com.example.rehabilitationapp.data.SftpUploader
import kotlinx.coroutines.launch

class TrainingResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            訓練結果頁()
        }
    }
}

@Preview
@Composable
fun AndroidPreview_訓練結果頁() {
    MaterialTheme {
        Box(Modifier.size(360.dp, 640.dp)) {
            訓練結果頁()
        }
    }
}

@Composable
fun 訓練結果頁() {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    val boxWidth = (screenWidth * 0.85f).dp
    val boxHeight = 60.dp

    val context = LocalContext.current
    val dao = AppDatabase.getInstance(LocalContext.current).trainingHistoryDao()
    var list by remember { mutableStateOf(emptyList<TrainingHistory>()) }

    // ===== 同步 Dialog 狀態 =====
    var showSyncDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncProgressFb by remember { mutableStateOf(Pair(0, 0)) }
    var syncProgressCsv by remember { mutableStateOf(Pair(0, 0)) }
    var syncResultFb by remember { mutableStateOf(Pair(0, 0)) }
    var syncResultCsv by remember { mutableStateOf(Pair(0, 0)) }
    var fbDone by remember { mutableStateOf(false) }
    var csvDone by remember { mutableStateOf(false) }

    // ===== 批次影片上傳 =====
    var showVideoUploadDialog by remember { mutableStateOf(false) }
    var unuploadedVideos by remember { mutableStateOf(emptyList<TrainingHistory>()) }
    var selectedVideoIds by remember { mutableStateOf(setOf<String>()) }
    var isVideoUploading by remember { mutableStateOf(false) }
    var videoUploadProgress by remember { mutableStateOf("") }
    var agreeUseMobileData by remember { mutableStateOf(false) }

    val scrollToId = (context as? android.app.Activity)?.intent?.getStringExtra("scroll_to_id")
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val targetDate = (context as? android.app.Activity)?.intent?.getLongExtra("target_date", 0L) ?: 0L

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            list = if (targetDate > 0L) {
                dao.getRecordsByDate(targetDate)
            } else {
                dao.getTodayRecords()
            }
        }
    }

    LaunchedEffect(list, scrollToId) {
        if (scrollToId != null && list.isNotEmpty()) {
            val index = list.indexOfFirst { it.trainingID == scrollToId }
            if (index >= 0) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景漸層
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFD17C), Color(0xFFFFFBEA)),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp, top = 30.dp)
        ) {
            // 標題列
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

            Box(
                modifier = Modifier.fillMaxWidth(),
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
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list) { data ->
                    TrainingResultCard(data) {
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            list = dao.getTodayRecords()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 白色底框
            Box(
                modifier = Modifier
                    .width(boxWidth)
                    .height(boxHeight)
                    .background(Color.White, shape = RoundedCornerShape(26.dp))
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp)
            ) {
                Text(text = "", modifier = Modifier.align(Alignment.Center).offset(y = (-8).dp))
            }

            // ===== 按鈕列 =====
            Row(
                Modifier
                    .width(boxWidth)
                    .height(boxHeight)
                    .offset(y = (-30).dp)
                    .align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(6.dp))

                // 重做按鈕
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFFFDA73), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFFEEA752), RoundedCornerShape(8.dp))
                        .clickable {
                            val prefs = context.getSharedPreferences("training_prefs", android.content.Context.MODE_PRIVATE)
                            val lastPlanId = prefs.getInt("last_plan_id", -1)
                            val lastPlanTitle = prefs.getString("last_plan_title", "")

                            if (lastPlanId > 0) {
                                val intent = Intent(context, com.example.rehabilitationapp.ui.plan.TrainingDetailActivity::class.java).apply {
                                    putExtra("plan_id", lastPlanId)
                                    putExtra("plan_title", lastPlanTitle)
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                context.startActivity(intent)
                            } else {
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

                Spacer(modifier = Modifier.width(12.dp))

                // 同步按鈕
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFFFDA73), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFFEEA752), RoundedCornerShape(8.dp))
                        .clickable {
                            syncProgressFb = Pair(0, 0)
                            syncProgressCsv = Pair(0, 0)
                            syncResultFb = Pair(0, 0)
                            syncResultCsv = Pair(0, 0)
                            fbDone = false
                            csvDone = false
                            isSyncing = true
                            showSyncDialog = true

                            FirebaseUploader.uploadTodayUnsynced(context, object : FirebaseUploader.UploadCallback {
                                override fun onProgress(current: Int, total: Int) {
                                    syncProgressFb = Pair(current, total)
                                }
                                override fun onComplete(successCount: Int, failCount: Int) {
                                    syncResultFb = Pair(successCount, failCount)
                                    fbDone = true
                                    if (fbDone && csvDone) isSyncing = false
                                }
                            })

                            com.example.rehabilitationapp.data.SupabaseUploader.retryUnsyncedCsv(context, object : com.example.rehabilitationapp.data.SupabaseUploader.RetryCallback {
                                override fun onProgress(current: Int, total: Int) {
                                    syncProgressCsv = Pair(current, total)
                                }
                                override fun onComplete(successCount: Int, failCount: Int) {
                                    syncResultCsv = Pair(successCount, failCount)
                                    csvDone = true
                                    if (fbDone && csvDone) isSyncing = false
                                }
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_result_share),
                        contentDescription = "同步",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 📤 影片上傳按鈕
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFFFDA73), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFFEEA752), RoundedCornerShape(8.dp))
                        .clickable {
                            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                            if (cm.activeNetwork == null) {
                                android.widget.Toast.makeText(context, "❌ 沒有網路連線", android.widget.Toast.LENGTH_SHORT).show()
                                return@clickable
                            }

                            Thread {
                                val records = dao.getUnsyncedVideoRecords()
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    unuploadedVideos = records
                                    selectedVideoIds = emptySet()
                                    agreeUseMobileData = false
                                    showVideoUploadDialog = true
                                }
                            }.start()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_result_share),
                            contentDescription = "影片上傳",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(text = "🎬", fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 返回首頁按鈕
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(44.dp)
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
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "返回首頁",
                        fontSize = 14.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // ===== 同步 Dialog =====
        if (showSyncDialog) {
            AlertDialog(
                onDismissRequest = { if (!isSyncing) showSyncDialog = false },
                title = {
                    Text(
                        text = if (isSyncing) "同步中..." else "同步完成",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when {
                                    !fbDone -> "⏳"
                                    syncResultFb.second > 0 -> "⚠️"
                                    else -> "✅"
                                },
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Firebase 紀錄", fontWeight = FontWeight.Medium)
                                Text(
                                    text = when {
                                        !fbDone && syncProgressFb.second > 0 -> "${syncProgressFb.first} / ${syncProgressFb.second} 筆"
                                        !fbDone -> "檢查中..."
                                        syncResultFb.first == 0 && syncResultFb.second == 0 -> "無資料需同步"
                                        syncResultFb.second > 0 -> "成功 ${syncResultFb.first} 筆，失敗 ${syncResultFb.second} 筆"
                                        else -> "完成 ${syncResultFb.first} 筆"
                                    },
                                    fontSize = 12.sp,
                                    color = if (fbDone && syncResultFb.second > 0) Color(0xFFFF9800) else Color.Gray
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when {
                                    !csvDone -> "⏳"
                                    syncResultCsv.second > 0 -> "⚠️"
                                    else -> "✅"
                                },
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("CSV 資料", fontWeight = FontWeight.Medium)
                                Text(
                                    text = when {
                                        !csvDone && syncProgressCsv.second > 0 -> "${syncProgressCsv.first} / ${syncProgressCsv.second} 筆"
                                        !csvDone -> "檢查中..."
                                        syncResultCsv.first == 0 && syncResultCsv.second == 0 -> "無資料需同步"
                                        syncResultCsv.second > 0 -> "成功 ${syncResultCsv.first} 筆，失敗 ${syncResultCsv.second} 筆"
                                        else -> "完成 ${syncResultCsv.first} 筆"
                                    },
                                    fontSize = 12.sp,
                                    color = if (csvDone && syncResultCsv.second > 0) Color(0xFFFF9800) else Color.Gray
                                )
                            }
                        }

                        if (isSyncing) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {
                    if (!isSyncing) {
                        TextButton(onClick = { showSyncDialog = false }) {
                            Text("確定")
                        }
                    }
                },
                dismissButton = {}
            )
        }

        // ===== 批次影片上傳 Dialog =====
        if (showVideoUploadDialog) {
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val isWifi = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true

            val totalSize = unuploadedVideos
                .filter { selectedVideoIds.contains(it.trainingID) }
                .sumOf {
                    val file = java.io.File(context.getExternalFilesDir(null), it.videoFileName)
                    if (file.exists()) file.length() else 0L
                }
            val totalSizeMB = String.format("%.1f", totalSize / 1024.0 / 1024.0)

            fun getTrainingName(label: String): String {
                return when (label) {
                    "POUT_LIPS" -> "嘟嘴"
                    "SIP_LIPS" -> "縮嘴"
                    "PUFF_CHEEK" -> "鼓腮"
                    "REDUCE_CHEEK" -> "縮腮"
                    "TONGUE_LEFT" -> "舌頭左"
                    "TONGUE_RIGHT" -> "舌頭右"
                    "TONGUE_UP" -> "舌頭上"
                    "TONGUE_DOWN" -> "舌頭下"
                    "TONGUE_FOWARD" -> "舌頭前"
                    "TONGUE_BACK" -> "舌頭後"
                    "JAW_LEFT" -> "下巴左"
                    "JAW_RIGHT" -> "下巴右"
                    else -> label
                }
            }

            AlertDialog(
                onDismissRequest = { if (!isVideoUploading) showVideoUploadDialog = false },
                title = {
                    Text(
                        text = if (isVideoUploading) "上傳中..." else "📤 選擇要上傳的影片",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        if (isVideoUploading) {
                            Text(videoUploadProgress)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else if (unuploadedVideos.isEmpty()) {
                            Text("沒有待上傳的影片 ✅")
                        } else {
                            if (!isWifi) {
                                Text(
                                    text = "⚠️ 目前使用行動網路",
                                    color = Color(0xFFFF9800),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = "共 ${unuploadedVideos.size} 部待上傳",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    selectedVideoIds = if (selectedVideoIds.size == unuploadedVideos.size.coerceAtMost(5)) {
                                        emptySet()
                                    } else {
                                        unuploadedVideos.take(5).map { it.trainingID }.toSet()
                                    }
                                }
                            ) {
                                Checkbox(
                                    checked = selectedVideoIds.size == unuploadedVideos.size.coerceAtMost(5) && selectedVideoIds.isNotEmpty(),
                                    onCheckedChange = {
                                        selectedVideoIds = if (it) {
                                            unuploadedVideos.take(5).map { it.trainingID }.toSet()
                                        } else {
                                            emptySet()
                                        }
                                    }
                                )
                                Text("全選（最多 5 部）")
                            }

                            Divider(modifier = Modifier.padding(vertical = 8.dp))

                            LazyColumn(modifier = Modifier.height(250.dp)) {
                                items(unuploadedVideos) { video ->
                                    val file = java.io.File(context.getExternalFilesDir(null), video.videoFileName)
                                    val sizeMB = if (file.exists()) String.format("%.1f MB", file.length() / 1024.0 / 1024.0) else "?"
                                    val isSelected = selectedVideoIds.contains(video.trainingID)
                                    val canSelect = isSelected || selectedVideoIds.size < 5

                                    val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                    val timeStr = timeFormat.format(java.util.Date(video.createAt))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = canSelect) {
                                                selectedVideoIds = if (isSelected) {
                                                    selectedVideoIds - video.trainingID
                                                } else if (selectedVideoIds.size < 5) {
                                                    selectedVideoIds + video.trainingID
                                                } else {
                                                    selectedVideoIds
                                                }
                                            }
                                            .padding(vertical = 6.dp)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            enabled = canSelect,
                                            onCheckedChange = {
                                                selectedVideoIds = if (it && selectedVideoIds.size < 5) {
                                                    selectedVideoIds + video.trainingID
                                                } else {
                                                    selectedVideoIds - video.trainingID
                                                }
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${getTrainingName(video.trainingLabel)} ${video.achievedTimes}/${video.targetTimes} · ${video.durationTime}秒",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "$timeStr · $sizeMB",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    Divider()
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "已選 ${selectedVideoIds.size} / 5 部 ($totalSizeMB MB)",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )

                            if (!isWifi && selectedVideoIds.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { agreeUseMobileData = !agreeUseMobileData }
                                ) {
                                    Checkbox(
                                        checked = agreeUseMobileData,
                                        onCheckedChange = { agreeUseMobileData = it }
                                    )
                                    Text(
                                        text = "繼續使用行動網路上傳",
                                        fontSize = 12.sp,
                                        color = Color(0xFFFF9800)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!isVideoUploading && unuploadedVideos.isNotEmpty() && selectedVideoIds.isNotEmpty()) {
                        val canUpload = isWifi || agreeUseMobileData
                        TextButton(
                            onClick = {
                                if (canUpload) {
                                    isVideoUploading = true
                                    val selectedVideos = unuploadedVideos.filter { selectedVideoIds.contains(it.trainingID) }
                                    val files = selectedVideos.mapNotNull { video ->
                                        val file = java.io.File(context.getExternalFilesDir(null), video.videoFileName)
                                        if (file.exists()) file else null
                                    }

                                    // ★ 先排 Worker（保險）
                                    selectedVideos.forEach { video ->
                                        SftpUploader.scheduleVideoUpload(context, video.trainingID, video.videoFileName, 10)
                                    }

                                    // ★ 再即時上傳
                                    SftpUploader.uploadMultipleAsync(
                                        context,
                                        files,
                                        object : SftpUploader.BatchUploadCallback {
                                            override fun onFileStart(index: Int, total: Int, fileName: String) {
                                                videoUploadProgress = "上傳中 [${index + 1}/$total]\n$fileName"
                                            }
                                            override fun onFileProgress(index: Int, total: Int, percent: Int) {
                                                videoUploadProgress = "上傳中 [${index + 1}/$total] $percent%"
                                            }
                                            override fun onFileSuccess(index: Int, total: Int, fileName: String) {
                                                val video = selectedVideos.find { it.videoFileName == fileName }
                                                video?.let {
                                                    Thread {
                                                        dao.markVideoUploaded(it.trainingID)
                                                        // ★ 成功後取消 Worker
                                                        SftpUploader.cancelVideoUpload(context, it.trainingID)
                                                    }.start()
                                                }
                                            }
                                            override fun onFileFailure(index: Int, total: Int, fileName: String, error: String) {}
                                            override fun onAllComplete(successCount: Int, failCount: Int, failedFiles: List<String>) {
                                                isVideoUploading = false
                                                showVideoUploadDialog = false
                                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                    val msg = when {
                                                        failCount == 0 -> "✅ 上傳完成：$successCount 部"
                                                        successCount == 0 -> "⚠️ 上傳失敗，稍後自動重試"
                                                        else -> "上傳 $successCount 部，失敗 $failCount 部（稍後自動重試）"
                                                    }
                                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

                                                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                                        list = if (targetDate > 0L) {
                                                            dao.getRecordsByDate(targetDate)
                                                        } else {
                                                            dao.getTodayRecords()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            },
                            enabled = canUpload
                        ) {
                            Text("上傳 (${selectedVideoIds.size})")
                        }
                    }
                },
                dismissButton = {
                    if (!isVideoUploading) {
                        TextButton(onClick = { showVideoUploadDialog = false }) {
                            Text("取消")
                        }
                    }
                }
            )
        }

        CustomBottomNavigation(
            modifier = Modifier.align(Alignment.BottomCenter),
            selectTab = 1
        )
    }
}

@Composable
fun TrainingResultCard(data: TrainingHistory, onUpdate: () -> Unit = {}) {
    val context = LocalContext.current
    val dao = AppDatabase.getInstance(context).trainingHistoryDao()

    var showVideoDialog by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

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

    // 自評對話框
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

    // 影片上傳對話框
    if (showVideoDialog) {
        AlertDialog(
            onDismissRequest = { if (!isUploading) showVideoDialog = false },
            title = { Text("上傳訓練影片") },
            text = {
                Column {
                    if (data.videoFileName.isNotEmpty()) {
                        Text("檔案：${data.videoFileName}")
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isUploading) {
                            Text("上傳中... $uploadProgress%")
                            LinearProgressIndicator(
                                progress = uploadProgress / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (data.videoUploaded == 1) {
                            Text("✅ 已上傳", color = Color(0xFF4CAF50))
                        } else {
                            Text("尚未上傳")
                        }
                    } else {
                        Text("此筆訓練沒有錄製影片")
                    }
                }
            },
            confirmButton = {
                if (data.videoFileName.isNotEmpty() && data.videoUploaded == 0 && !isUploading) {
                    TextButton(
                        onClick = {
                            isUploading = true
                            val videoFile = java.io.File(context.getExternalFilesDir(null), data.videoFileName)

                            // ★ 先排 Worker
                            SftpUploader.scheduleVideoUpload(context, data.trainingID, data.videoFileName, 10)

                            SftpUploader.uploadVideoAsync(
                                context,
                                videoFile,
                                object : SftpUploader.UploadCallback {
                                    override fun onProgress(percent: Int) {
                                        uploadProgress = percent
                                    }
                                    override fun onSuccess(remoteFilePath: String) {
                                        Thread {
                                            dao.markVideoUploaded(data.trainingID)
                                            // ★ 成功後取消 Worker
                                            SftpUploader.cancelVideoUpload(context, data.trainingID)
                                        }.start()
                                        isUploading = false
                                        showVideoDialog = false
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            android.widget.Toast.makeText(context, "✅ 影片上傳成功", android.widget.Toast.LENGTH_SHORT).show()
                                            onUpdate()
                                        }
                                    }
                                    override fun onFailure(errorMessage: String) {
                                        isUploading = false
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            android.widget.Toast.makeText(context, "⚠️ 上傳失敗，稍後自動重試", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    ) {
                        Text("上傳")
                    }
                }
            },
            dismissButton = {
                if (!isUploading) {
                    TextButton(onClick = { showVideoDialog = false }) {
                        Text("關閉")
                    }
                }
            }
        )
    }

    // 卡片
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

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
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

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

            // 影片上傳狀態圖示
            if (data.videoFileName.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (data.videoUploaded == 1) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (data.videoUploaded == 1) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { showVideoDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (data.videoUploaded == 1) "✓" else "⬆",
                        fontSize = 14.sp,
                        color = if (data.videoUploaded == 1) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold
                    )
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
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(109.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg_nav_circle_bar_nav),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxWidth()
                .height(109.dp)
                .align(Alignment.BottomCenter)
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 50.dp, end = 50.dp, top = 35.dp, bottom = 0.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(text = "首頁", isSelected = selectTab == 0, glyphRes = R.drawable.ic_home_glyph_white) {
                onTabSelected(0)
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("start_tab", "home")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                (context as? Activity)?.finish()
            }

            NavItem(text = "計畫", isSelected = selectTab == 1, glyphRes = R.drawable.ic_plan_glyph_white) {
                onTabSelected(1)
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("start_tab", "plan")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                (context as? Activity)?.finish()
            }

            NavItem(text = "紀錄", isSelected = selectTab == 2, glyphRes = R.drawable.ic_record_glyph_white) {
                onTabSelected(2)
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("start_tab", "record")
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                (context as? Activity)?.finish()
            }

            NavItem(text = "設定", isSelected = selectTab == 3, glyphRes = R.drawable.ic_setting_glyph_white) {
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
    glyphRes: Int,
    onClick: () -> Unit
) {
    val bgRes = if (isSelected) R.drawable.bg_nav_icon_selected else R.drawable.bg_nav_icon_unselected

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
            .offset(y = (-12).dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = bgRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
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