package com.example.rehabilitationapp.ui.results

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import kotlin.math.abs

class CurveChartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val curveJson = intent.getStringExtra("curveJson") ?: "[]"
        Log.d("CurveChart", "Received JSON: $curveJson")

        setContent {
            MaterialTheme {
                SimpleCurveChart(curveJson)
            }
        }
    }
}

@Composable
fun SimpleCurveChart(curveJson: String) {
    val points = remember(curveJson) {
        parseJsonToPoints(curveJson)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "曲線圖",
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (points.isEmpty()) {
            Text(
                text = "沒有可用的數據",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                drawCurve(points)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "數據點: ${points.size}",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun parseJsonToPoints(curveJson: String): List<Pair<Float, Float>> {
    return try {
        if (curveJson.isEmpty() || curveJson == "[]") {
            Log.w("CurveChart", "Empty or null curve data")
            return emptyList()
        }

        val arr = JSONArray(curveJson)
        val pointsList = mutableListOf<Pair<Float, Float>>()

        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                val t = obj.optDouble("t", 0.0).toFloat()
                val vStr = obj.optString("v", "0")

                val v = try {
                    vStr.toFloat()
                } catch (e: NumberFormatException) {
                    0.0f
                }

                if (t.isFinite() && v.isFinite()) {
                    pointsList.add(t to v)
                }
            } catch (e: Exception) {
                Log.w("CurveChart", "Skip invalid point at index $i: ${e.message}")
            }
        }

        Log.d("CurveChart", "Parsed ${pointsList.size} valid points")
        pointsList

    } catch (e: Exception) {
        Log.e("CurveChart", "JSON parsing error: ${e.message}")
        emptyList()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCurve(points: List<Pair<Float, Float>>) {
    if (points.size < 2) return

    // 原始範圍
    val minX = points.minOf { it.first }
    val maxX = points.maxOf { it.first }
    val minY = points.minOf { it.second }
    val maxY = points.maxOf { it.second }

    // 歸一化 Y 到 [-1, 1]
    val maxAbsY = maxOf(abs(minY), abs(maxY))
    val normalizedPoints = points.map { (x, y) ->
        val normY = if (maxAbsY < 1e-9) 0f else (y / maxAbsY)
        x to normY
    }

    val rangeX = if (abs(maxX - minX) < 0.001f) 1f else maxX - minX
    val margin = 50f
    val drawWidth = size.width - 2 * margin
    val drawHeight = size.height - 2 * margin

    val path = Path()
    normalizedPoints.forEachIndexed { index, (x, y) ->
        val screenX = margin + ((x - minX) / rangeX) * drawWidth
        val screenY = margin + drawHeight - ((y + 1) / 2f) * drawHeight

        if (index == 0) {
            path.moveTo(screenX, screenY)
        } else {
            path.lineTo(screenX, screenY)
        }
    }

    // 畫曲線
    drawPath(
        path = path,
        color = Color.Blue,
        style = Stroke(width = 2.dp.toPx())
    )

    // 畫座標軸
    // X 軸
    drawLine(
        color = Color.Gray,
        start = androidx.compose.ui.geometry.Offset(margin, size.height - margin),
        end = androidx.compose.ui.geometry.Offset(size.width - margin, size.height - margin),
        strokeWidth = 1.dp.toPx()
    )
    // Y 軸
    drawLine(
        color = Color.Gray,
        start = androidx.compose.ui.geometry.Offset(margin, margin),
        end = androidx.compose.ui.geometry.Offset(margin, size.height - margin),
        strokeWidth = 1.dp.toPx()
    )

    // ★ 畫 Y=0 的虛線 (放在這裡剛好)
    if (minY <= 0 && maxY >= 0) {
        val zeroY = margin + drawHeight - ((0 - minY) / (maxY - minY)) * drawHeight
        drawLine(
            color = Color.Red,
            start = androidx.compose.ui.geometry.Offset(margin, zeroY),
            end = androidx.compose.ui.geometry.Offset(size.width - margin, zeroY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
    }

    // X 軸刻度 (顯示秒數)
    // X 軸刻度 (固定 1 秒)
    val stepSize = 1f
    val totalSteps = (rangeX / stepSize).toInt()
    for (i in 0..totalSteps) {
        val t = minX + i * stepSize
        val xPos = margin + ((t - minX) / rangeX) * drawWidth
        drawLine(
            color = Color.DarkGray,
            start = androidx.compose.ui.geometry.Offset(xPos, size.height - margin),
            end = androidx.compose.ui.geometry.Offset(xPos, size.height - margin + 8f),
            strokeWidth = 1.dp.toPx()
        )
        drawContext.canvas.nativeCanvas.drawText(
            String.format("%.0f", t),
            xPos,
            size.height - 10f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 24f
                textAlign = android.graphics.Paint.Align.CENTER
            }
        )
    }

    // Y 軸刻度 -1, 0, 1
    for (i in -1..1) {
        val yPos = margin + drawHeight - ((i + 1) / 2f) * drawHeight
        drawLine(
            color = Color.DarkGray,
            start = androidx.compose.ui.geometry.Offset(margin - 8f, yPos),
            end = androidx.compose.ui.geometry.Offset(margin, yPos),
            strokeWidth = 1.dp.toPx()
        )
        drawContext.canvas.nativeCanvas.drawText(
            i.toString(),
            margin - 25f,
            yPos + 8f,
            android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 24f
                textAlign = android.graphics.Paint.Align.CENTER
            }
        )
    }

    //插在這裡嗎

}
