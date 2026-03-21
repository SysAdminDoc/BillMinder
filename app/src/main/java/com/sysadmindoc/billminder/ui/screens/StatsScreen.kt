package com.sysadmindoc.billminder.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.data.BillCategory
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import com.sysadmindoc.billminder.viewmodel.ChartData

private val chartColors = listOf(
    CatBlue, CatMauve, CatGreen, CatPeach, CatYellow,
    CatPink, CatTeal, CatSapphire, CatFlamingo, CatLavender,
    CatRosewater, CatRed
)

@Composable
fun StatsScreen(viewModel: BillViewModel) {
    val chartData by viewModel.chartData.collectAsState()
    val summary by viewModel.monthlySummary.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadChartData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CatCrust)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // Lifetime spending
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CatBase)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Lifetime Spending", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)
                Spacer(Modifier.height(4.dp))
                Text(
                    "$${"%,.2f".format(chartData.lifetimeTotal)}",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = CatText
                )
                Text(
                    "${summary.billCount} active bills",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CatSubtext0
                )
            }
        }

        // Yearly projection
        if (chartData.yearlyProjection > 0) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CatSurface0)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Yearly Projection", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)
                        Text(
                            "$${"%,.2f".format(chartData.yearlyProjection)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = CatPeach
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Monthly Avg", style = MaterialTheme.typography.labelMedium, color = CatSubtext0)
                        Text(
                            "$${"%,.2f".format(chartData.yearlyProjection / 12)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CatSubtext1
                        )
                    }
                }
            }
        }

        // Category pie chart
        if (chartData.categoryBreakdown.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CatBase)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Spending by Category", style = MaterialTheme.typography.titleMedium, color = CatText)
                    Spacer(Modifier.height(16.dp))

                    PieChart(chartData.categoryBreakdown, modifier = Modifier.fillMaxWidth().height(200.dp))

                    Spacer(Modifier.height(16.dp))

                    // Legend
                    chartData.categoryBreakdown.forEachIndexed { index, (category, amount) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(chartColors[index % chartColors.size])
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                category.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CatText,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$${"%,.2f".format(amount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = CatSubtext1
                            )
                        }
                    }
                }
            }
        }

        // Monthly trend
        if (chartData.monthlyTrend.any { it.second > 0 }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CatBase)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Monthly Trend", style = MaterialTheme.typography.titleMedium, color = CatText)
                    Spacer(Modifier.height(16.dp))
                    TrendChart(chartData.monthlyTrend, modifier = Modifier.fillMaxWidth().height(180.dp))
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun PieChart(data: List<Pair<BillCategory, Double>>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.second }
    if (total <= 0) return

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = modifier) {
        val diameter = minOf(size.width, size.height) * 0.8f
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)
        var startAngle = -90f

        data.forEachIndexed { index, (_, amount) ->
            val sweep = (amount / total * 360f * animatedProgress.value).toFloat()
            drawArc(
                color = chartColors[index % chartColors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = topLeft,
                size = arcSize
            )
            startAngle += sweep
        }

        // Center hole for donut effect
        val holeSize = diameter * 0.55f
        val holeOffset = Offset((size.width - holeSize) / 2, (size.height - holeSize) / 2)
        drawOval(
            color = Color(0xFF1E1E2E),
            topLeft = holeOffset,
            size = Size(holeSize, holeSize)
        )
    }
}

@Composable
private fun TrendChart(data: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val maxVal = data.maxOfOrNull { it.second } ?: 1.0
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = modifier) {
        val padLeft = 60f
        val padBottom = 40f
        val padTop = 20f
        val chartWidth = size.width - padLeft - 20f
        val chartHeight = size.height - padBottom - padTop
        val stepX = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth

        val textPaint = android.graphics.Paint().apply {
            color = 0xFFA6ADC8.toInt()
            textSize = 28f
            isAntiAlias = true
        }

        // Grid lines
        for (i in 0..3) {
            val y = padTop + chartHeight * (1 - i / 3f)
            drawLine(
                color = Color(0xFF313244),
                start = Offset(padLeft, y),
                end = Offset(size.width - 20f, y),
                strokeWidth = 1f
            )
            val label = "$${"%,.0f".format(maxVal * i / 3)}"
            drawContext.canvas.nativeCanvas.drawText(label, 4f, y + 10f, textPaint)
        }

        // Labels
        data.forEachIndexed { index, (label, _) ->
            val x = padLeft + index * stepX
            drawContext.canvas.nativeCanvas.drawText(
                label, x - 20f, size.height - 4f, textPaint
            )
        }

        if (data.size < 2) return@Canvas

        // Line + fill
        val points = data.mapIndexed { index, (_, value) ->
            val x = padLeft + index * stepX
            val y = padTop + chartHeight * (1 - (value / maxVal).toFloat()) * animatedProgress.value
            Offset(x, y)
        }

        // Gradient fill
        val fillPath = Path().apply {
            moveTo(points.first().x, padTop + chartHeight)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, padTop + chartHeight)
            close()
        }
        drawPath(fillPath, color = CatBlue.copy(alpha = 0.1f))

        // Line
        for (i in 0 until points.size - 1) {
            drawLine(
                color = CatBlue,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }

        // Dots
        points.forEach { pt ->
            drawCircle(color = CatBlue, radius = 6f, center = pt)
            drawCircle(color = CatCrust, radius = 3f, center = pt)
        }
    }
}
