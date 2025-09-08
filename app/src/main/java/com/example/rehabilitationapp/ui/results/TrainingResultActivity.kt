package com.example.rehabilitationapp.ui.results

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val dao = AppDatabase.getInstance(LocalContext.current).trainingHistoryDao()
    var list by remember { mutableStateOf(emptyList<TrainingHistory>()) }
    // 一行搞定資料查詢
    // 修正：加上 withContext(Dispatchers.IO)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            list = dao.getTodayRecords()
        }
    }
    ConstraintLayout(
        modifier = Modifier
            .background(Color(1f, 1f, 1f, 1f))
            .fillMaxSize()
    ) {
        val (
            Rectangle_2, Frame_247, Frame_277, Frame_276, Frame_2, Vector_1,
            Frame_275, Group_191, Frame_1, Frame_206, Frame_283, Group_252, Group_255
        ) = createRefs()

        LazyColumn(
            modifier = Modifier
                //.background(Color(0xFFF0F0F0))
                .constrainAs(Frame_247) {
                start.linkTo(parent.start, 30.dp)
                top.linkTo(parent.top, 143.dp)
                end.linkTo(parent.end, 30.dp)        // 必須加這行
                bottom.linkTo(Frame_206.top, 16.dp)  // 必須加這行
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
            },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list) { // 顯示3張卡片
                    data ->
                TrainingResultCard(data)
            }
        }

        Row(
            Modifier
                .height(65.dp)
                .constrainAs(Frame_2) {
                    start.linkTo(parent.start, 0.dp)
                    top.linkTo(parent.top, 41.dp)
                    width = Dimension.value(412.dp)
                    height = Dimension.value(65.dp)
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(20.dp))
            /* raw vector Arrow 1 should have an export setting */
            Text(
                "首頁",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(373.dp, 41.dp)
                    .fillMaxHeight(),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Left,
                    fontSize = 18.sp
                )
            )
            Spacer(modifier = Modifier.width(20.dp))
        }

        /* raw vector Vector 1 should have an export setting */
        ConstraintLayout(
            modifier = Modifier.constrainAs(Frame_275) {
                start.linkTo(parent.start, 61.dp)
                top.linkTo(parent.top, 790.dp)
                width = Dimension.value(290.dp)
                height = Dimension.value(54.dp)
            }
        ) {
            // 修正：避免重名
            val (Group_234, Group_236, Group_235, Group_233a, Group_233b, Group_218) = createRefs()

            Box {
                Box {
                    Text(
                        "計畫",
                        Modifier.wrapContentHeight(Alignment.CenterVertically),
                        style = LocalTextStyle.current.copy(
                            color = Color(0f, 0f, 0f, 1f),
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp
                        )
                    )
                }
                Box {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .size(32.dp, 31.dp)
                            .background(Color(0.93f, 0.65f, 0.32f, 1f))
                    ) {}
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .size(15.dp, 6.dp)
                            .background(Color(0.93f, 0.65f, 0.32f, 1f))
                    ) {}
                    Box {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(20.dp, 2.0000017.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(2.0.dp, 2.0000002.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                    }
                    Box {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(20.dp, 2.0000017.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(2.0.dp, 2.0000002.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                    }
                    Box {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(20.dp, 2.0000017.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(2.0.dp, 2.0000002.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                    }
                    Box {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(20.dp, 2.0000017.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(2.0.dp, 2.0000002.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                    }
                }
            }

            Box {
                Box {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .size(32.dp, 32.dp)
                            .background(Color(0.93f, 0.65f, 0.32f, 1f))
                    ) {}
                    Box {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(3.0.dp, 4.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(3.0.dp, 4.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(3.0.dp, 4.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(3.0.dp, 4.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(3.0.dp, 4.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(3.0.dp, 4.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(3.0.dp, 4.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(3.0.dp, 4.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(1.dp))
                                .size(3.0.dp, 4.dp)
                                .background(Color(1f, 0.98f, 0.92f, 1f))
                        ) {}
                    }
                }
                Text(
                    "設定",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp
                    )
                )
            }

            Box {
                Box {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .size(32.dp, 32.dp)
                            .background(Color(0.93f, 0.65f, 0.32f, 1f))
                    ) {}
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(1.dp))
                            .size(17.dp, 5.dp)
                            .background(Color(1f, 0.98f, 0.92f, 1f))
                    ) {}
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(1.dp))
                            .size(24.dp, 5.dp)
                            .background(Color(1f, 0.98f, 0.92f, 1f))
                    ) {}
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(1.dp))
                            .size(11.dp, 5.dp)
                            .background(Color(1f, 0.98f, 0.92f, 1f))
                    ) {}
                }
                Text(
                    "紀錄",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp
                    )
                )
            }

            Box {
                Box {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .size(32.dp, 32.dp)
                            .background(Color(0.28f, 0.66f, 0.54f, 1f))
                    ) {}
                }
                Text(
                    "首頁",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp
                    )
                )
            }

            Box {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .size(32.dp, 32.dp)
                        .background(Color(0.28f, 0.66f, 0.54f, 1f))
                ) {}
                Box(
                    Modifier
                        .clip(RoundedCornerShape(1.dp))
                        .size(17.dp, 5.dp)
                        .background(Color(1f, 0.98f, 0.92f, 1f))
                ) {}
                Box(
                    Modifier
                        .clip(RoundedCornerShape(1.dp))
                        .size(24.dp, 5.dp)
                        .background(Color(1f, 0.98f, 0.92f, 1f))
                ) {}
                Box(
                    Modifier
                        .clip(RoundedCornerShape(1.dp))
                        .size(11.dp, 5.dp)
                        .background(Color(1f, 0.98f, 0.92f, 1f))
                ) {}
            }

            Box {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .size(32.dp, 32.dp)
                        .background(Color(0.93f, 0.65f, 0.32f, 1f))
                ) {}
            }
        }

        Box {
            Row(
                Modifier.height(54.dp).background(Color(0f, 0f, 0f, 1f)),
                horizontalArrangement = Arrangement.spacedBy(85.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(93.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .size(17.dp, 17.dp)
                        .background(Color(0.85f, 0.85f, 0.85f, 1f))
                        .size(17.dp, 17.dp)
                ) {}
                Spacer(modifier = Modifier.width(93.dp))
            }
            Row(
                Modifier.height(41.dp).background(Color(1f, 0.98f, 0.92f, 1f)),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(17.dp))
                Row(
                    Modifier.size(380.dp, 19.dp),
                    horizontalArrangement = Arrangement.spacedBy(303.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "1:41",
                        Modifier
                            .wrapContentHeight(Alignment.CenterVertically)
                            .size(32.dp, 19.dp),
                        style = LocalTextStyle.current.copy(
                            color = Color(0f, 0f, 0f, 1f),
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp
                        )
                    )
                    Row(
                        Modifier.size(45.dp, 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .size(5.dp, 11.dp)
                                    .background(Color(0f, 0f, 0f, 1f))
                            ) {}
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .size(5.dp, 14.dp)
                                    .background(Color(0f, 0f, 0f, 1f))
                            ) {}
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .size(5.dp, 7.dp)
                                    .background(Color(0f, 0f, 0f, 1f))
                            ) {}
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .size(15.dp, 14.dp)
                                .background(Color(0f, 0f, 0f, 1f))
                                .size(15.dp, 14.dp)
                        ) {}
                    }
                }
                Spacer(modifier = Modifier.width(17.dp))
            }
        }

        Column(
            Modifier
                .width(412.dp)
                .constrainAs(Frame_1) {
                    start.linkTo(parent.start, 0.dp)
                    top.linkTo(parent.top, 87.dp)
                    width = Dimension.value(412.dp)
                    height = Dimension.value(29.dp)
                },
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                "訓練結果",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(412.dp, 29.dp)
                    .fillMaxWidth(),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp
                )
            )
        }

        ConstraintLayout(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(1f, 1f, 0.98f, 1f))
                .constrainAs(Frame_206) {
                    centerHorizontallyTo(parent)
                    top.linkTo(parent.top, 651.dp)
                    width = Dimension.value(352.dp)
                    height = Dimension.value(54.dp)
                }
        ) {
            val (系統建議訊息) = createRefs()
            Text(
                "系統建議訊息",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .constrainAs(系統建議訊息) {
                        centerHorizontallyTo(parent)
                        centerVerticallyTo(parent)
                        width = Dimension.value(293.dp)
                        height = Dimension.value(36.dp)
                    },
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            )
        }

        Column(
            Modifier
                .width(154.dp)
                .constrainAs(Frame_283) {
                    start.linkTo(parent.start, 189.dp)
                    top.linkTo(parent.top, 690.dp)
                    width = Dimension.value(154.dp)
                    height = Dimension.value(43.dp)
                },
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                "儲存結果",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(142.dp, 21.dp)
                    .fillMaxWidth(),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = Modifier.height(5.dp))
        }

        Box {
            Box {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .size(44.dp, 43.dp)
                        .background(Color(1f, 0.85f, 0.45f, 1f))
                ) {}
            }
            Box {}
        }

        Box {
            Box {
                Box {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .size(44.dp, 43.dp)
                            .background(Color(1f, 0.85f, 0.45f, 1f))
                    ) {}
                }
            }
            Box {
                /* raw vector Vector 5 should have an export setting */
                Box {
                    /* raw vector Arrow 2 should have an export setting */
                }
            }
        }
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
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        Color(0xFFE8F5E8),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 頭像圖片 (這裡用Icon代替，實際可換成Image)
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription =  data.trainingLabel,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(30.dp)
                )
            }

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
                            text = "準確率",
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
