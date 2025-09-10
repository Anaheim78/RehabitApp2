package com.example.rehabilitationapp.ui.results

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


    val dao = AppDatabase.getInstance(LocalContext.current).trainingHistoryDao()
    var list by remember { mutableStateOf(emptyList<TrainingHistory>()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            list = dao.getTodayRecords()
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list) { data ->
                    TrainingResultCard(data)
                }
            }
            Box(
                //To Do..
                modifier = Modifier.width(boxWidth).height(boxHeight)
                    .background(
                        Color.White,
                        shape = RoundedCornerShape(26.dp)
                    )
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "系統編輯訊息",
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
                        .border(2.dp, Color(0xFFEEA752), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_result_redo),
                        contentDescription = "分享",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp)) // 左右間隔 16dp
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFFFDA73), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFFEEA752), RoundedCornerShape(8.dp)),
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
                        .border(2.dp, Color(0xFFEEA752), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center // 這行很重要！讓內容在 Box 中居中
                ) {
                    Text(
                        text = "儲存結果",
                        fontSize = 14.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center // 文字本身也要居中
                    )

                }
            }

            Spacer(modifier = Modifier.height(120.dp))


        }
        CustomBottomNavigation(

            modifier = Modifier.align(Alignment.BottomCenter),
            selectTab = 2 // 2 = 紀錄頁面

        )
    }
}
@Composable
fun TrainingResultCard(data: TrainingHistory) {
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
            .padding(horizontal = 6.dp),
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

@Composable
fun CustomBottomNavigation(
    modifier: Modifier = Modifier,
    selectTab: Int = 0
) {
    Log.d("BottomNav", "CustomBottomNavigation 開始執行, selectedTab = $selectTab")
    Box(
        modifier = modifier.fillMaxWidth().height(109.dp)
    ){
        Image(
            painter = painterResource(id= R.drawable.bg_nav_circle_bar_nav),
            contentDescription  = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 55.dp, end = 30.dp, top = 35.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ){
            NavItem("首頁", R.drawable.nav_home_icon_selector, selectTab == 0) {
                // 跳轉首頁
            }
            NavItem("計畫", R.drawable.nav_plan_icon_selector, selectTab == 1) {
                // 跳轉計畫
            }
            NavItem("紀錄", R.drawable.nav_record_icon_selector, selectTab == 2) {
                // 當前頁面
            }
            NavItem("設定", R.drawable.nav_record_icon_selector, selectTab == 3) {
                // 跳轉設定
            }
        }
    }
}

@Composable
fun NavItem(text:String ,icon:Int , isSelected : Boolean , onclick : ()-> Unit){
    Column(
        horizontalAlignment =  Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onclick() }
            .padding(8.dp)
    ){
        Icon(
            painter = painterResource(id = icon),
            contentDescription = text,
            tint = Color.Unspecified,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (isSelected) Color.Black else Color.Gray
        )
    }
}