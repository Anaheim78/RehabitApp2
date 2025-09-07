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
    Box(Modifier.size(360.dp, 640.dp)) {
        訓練結果頁()
    }
}

@Composable
fun 訓練結果頁() {
    ConstraintLayout(
        modifier = Modifier
            .background(Color(1f, 1f, 1f, 1f))
            .fillMaxSize()
    ) {
        val (
            Rectangle_2, Frame_247, Frame_277, Frame_276, Frame_2, Vector_1,
            Frame_275, Group_191, Frame_1, Frame_206, Frame_283, Group_252, Group_255
        ) = createRefs()

        /* raw vector Rectangle 2 should have an export setting */
        Column(
            Modifier
                .width(352.dp)
                .constrainAs(Frame_247) {
                    start.linkTo(parent.start, 30.dp)
                    top.linkTo(parent.top, 143.dp)
                    width = Dimension.value(352.dp)
                    height = Dimension.value(128.dp)
                },
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "8秒",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(26.dp, 24.dp),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            )
            Text(
                "75%",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(32.dp, 24.dp),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            )
            Box {
                Box(Modifier.size(96.dp, 96.dp)) {}
                Text(
                    "舌尖上抬",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                )
            }
            Text(
                "持續",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(20.dp, 15.dp),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Left,
                    fontSize = 10.sp
                )
            )
            Text(
                "完成率",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(30.dp, 15.dp),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Left,
                    fontSize = 10.sp
                )
            )
            Box {
                Text(
                    "03/04",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Left,
                        fontSize = 32.sp
                    )
                )
                Text(
                    "達成",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Left,
                        fontSize = 10.sp
                    )
                )
                Text(
                    "目標次數",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Left,
                        fontSize = 10.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Column(
            Modifier
                .width(352.dp)
                .constrainAs(Frame_277) {
                    start.linkTo(parent.start, 28.dp)
                    top.linkTo(parent.top, 435.dp)
                    width = Dimension.value(352.dp)
                    height = Dimension.value(128.dp)
                },
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "8秒",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(26.dp, 24.dp),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            )
            Text(
                "75%",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(32.dp, 24.dp),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            )
            Box {
                Box(Modifier.size(96.dp, 96.dp)) {}
                Text(
                    "舌尖上抬",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                )
            }
            Text(
                "持續",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(20.dp, 15.dp),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Left,
                    fontSize = 10.sp
                )
            )
            Text(
                "完成率",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(30.dp, 15.dp),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Left,
                    fontSize = 10.sp
                )
            )
            Box {
                Text(
                    "03/04",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Left,
                        fontSize = 32.sp
                    )
                )
                Text(
                    "達成",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Left,
                        fontSize = 10.sp
                    )
                )
                Text(
                    "目標次數",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Left,
                        fontSize = 10.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Column(
            Modifier
                .width(352.dp)
                .constrainAs(Frame_276) {
                    start.linkTo(parent.start, 30.dp)
                    top.linkTo(parent.top, 289.dp)
                    width = Dimension.value(352.dp)
                    height = Dimension.value(128.dp)
                },
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "10秒",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(34.dp, 24.dp),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            )
            Text(
                "50%",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(33.dp, 24.dp),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
                )
            )
            Box {
                Box(Modifier.size(96.dp, 97.dp)) {}
                Text(
                    "舌尖下壓",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                )
            }
            Text(
                "持續",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(20.dp, 15.dp),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Left,
                    fontSize = 10.sp
                )
            )
            Text(
                "完成率",
                Modifier
                    .wrapContentHeight(Alignment.CenterVertically)
                    .size(30.dp, 15.dp),
                style = LocalTextStyle.current.copy(
                    color = Color(0f, 0f, 0f, 1f),
                    textAlign = TextAlign.Left,
                    fontSize = 10.sp
                )
            )
            Box {
                Text(
                    "02/04",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Left,
                        fontSize = 32.sp
                    )
                )
                Text(
                    "達成",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Left,
                        fontSize = 10.sp
                    )
                )
                Text(
                    "目標次數",
                    Modifier.wrapContentHeight(Alignment.CenterVertically),
                    style = LocalTextStyle.current.copy(
                        color = Color(0f, 0f, 0f, 1f),
                        textAlign = TextAlign.Left,
                        fontSize = 10.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
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
