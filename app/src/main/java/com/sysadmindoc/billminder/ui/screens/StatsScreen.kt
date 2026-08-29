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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.data.BillCategory
import com.sysadmindoc.billminder.data.BudgetMath
import com.sysadmindoc.billminder.data.BudgetPrefs
import com.sysadmindoc.billminder.data.CurrencyFormatter
import com.sysadmindoc.billminder.data.Recurrence
import com.sysadmindoc.billminder.ui.components.GroupDivider
import com.sysadmindoc.billminder.ui.components.GroupedSurface
import com.sysadmindoc.billminder.ui.components.SectionHeading
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import com.sysadmindoc.billminder.viewmodel.BillWithStatus
import com.sysadmindoc.billminder.viewmodel.ChartData
import com.sysadmindoc.billminder.viewmodel.MonthlyCashFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val chartColors = listOf(
    CatBlue, CatMauve, CatGreen, CatPeach, CatYellow,
    CatPink, CatTeal, CatSapphire, CatFlamingo, CatLavender,
    CatRosewater, CatRed
)

@Composable
fun StatsScreen(viewModel: BillViewModel) {
    val chartData by viewModel.chartData.collectAsState()
    val summary by viewModel.monthlySummary.collectAsState()
    val context = LocalContext.current

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

        Column {
            Text(
                "Insights",
                color = CatText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()),
                color = CatSubtext0,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        GroupedSurface(contentPadding = PaddingValues(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.15f)) {
                    Text("Lifetime spending", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        CurrencyFormatter.format(chartData.lifetimeTotal, chartData.currency),
                        fontSize = 31.sp,
                        letterSpacing = (-0.7).sp,
                        fontWeight = FontWeight.Bold,
                        color = CatText
                    )
                    Text(
                        "${summary.billCount} active bills",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CatSubtext0
                    )
                }
                Box(Modifier.width(1.dp).height(86.dp).background(CatDivider))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(0.85f)) {
                    Text("Annual projection", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        CurrencyFormatter.format(chartData.yearlyProjection, chartData.currency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CatBlue
                    )
                    Text("per year", style = MaterialTheme.typography.bodyMedium, color = CatSubtext0)
                }
            }
        }

        // Forecast panel
        if (chartData.forecast.next90Bills > 0) {
            Column {
                SectionHeading("Forecast", color = CatText)
                Spacer(Modifier.height(8.dp))
                GroupedSurface(contentPadding = PaddingValues(16.dp)) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ForecastColumn("30 days", chartData.forecast.next30Days, chartData.forecast.next30Bills, CatGreen, chartData.currency)
                        ForecastColumn("60 days", chartData.forecast.next60Days, chartData.forecast.next60Bills, CatYellow, chartData.currency)
                        ForecastColumn("90 days", chartData.forecast.next90Days, chartData.forecast.next90Bills, CatPeach, chartData.currency)
                    }
                }
            }
        }

        // Category pie chart
        if (chartData.categoryBreakdown.isNotEmpty()) {
            Column {
                SectionHeading("Spending by category", color = CatText)
                Spacer(Modifier.height(8.dp))
                GroupedSurface(contentPadding = PaddingValues(14.dp)) {
                    val total = chartData.categoryBreakdown.sumOf { it.second }.coerceAtLeast(1.0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PieChart(
                            chartData.categoryBreakdown,
                            modifier = Modifier.weight(0.9f).height(150.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1.1f)) {
                            chartData.categoryBreakdown.take(4).forEachIndexed { index, (category, amount) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(chartColors[index % chartColors.size])
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        category.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CatText,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Text(
                                        "${(amount / total * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = CatSubtext1
                                    )
                                }
                                if (index < minOf(3, chartData.categoryBreakdown.lastIndex)) GroupDivider()
                            }
                        }
                    }
                }
            }
        }

        CategoryBudgetsPanel(viewModel, chartData, context)

        // Monthly trend
        if (chartData.monthlyTrend.any { it.second > 0 }) {
            Column {
                SectionHeading("6-month trend", color = CatText)
                Spacer(Modifier.height(8.dp))
                GroupedSurface(contentPadding = PaddingValues(14.dp)) {
                    TrendChart(chartData.monthlyTrend, chartData.currency, modifier = Modifier.fillMaxWidth().height(150.dp))
                }
            }
        }

        // Twelve-month paid versus outstanding plan
        if (chartData.cashFlow.any { it.paid > 0 || it.outstanding > 0 }) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CatSurfaceRaised)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("12-Month Plan", style = MaterialTheme.typography.titleMedium, color = CatText)
                    Text(
                        "Paid is above zero; projected unpaid due dates are below.",
                        style = MaterialTheme.typography.labelMedium,
                        color = CatSubtext0
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChartLegend(CatGreen, "Paid")
                        ChartLegend(CatPeach, "Outstanding")
                    }
                    Spacer(Modifier.height(8.dp))
                    CashFlowChart(
                        data = chartData.cashFlow,
                        currency = chartData.currency,
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                }
            }
        }

        // What-if panel
        WhatIfPanel(viewModel = viewModel)

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = CatSubtext1)
    }
}

@Composable
private fun CategoryBudgetsPanel(
    viewModel: BillViewModel,
    chartData: ChartData,
    context: android.content.Context
) {
    val budgets = remember { BudgetPrefs.getAll(context) }
    if (budgets.isEmpty()) return
    val spentByCategory = chartData.categoryBreakdown.toMap()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CatSurfaceRaised)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Category Budgets", style = MaterialTheme.typography.titleMedium, color = CatText)
            Text(
                "Current-month spending against your caps",
                style = MaterialTheme.typography.labelMedium,
                color = CatSubtext0
            )
            Spacer(Modifier.height(14.dp))
            budgets.toSortedMap(compareBy { it.ordinal }).forEach { (category, budget) ->
                val limit = viewModel.convertToDisplay(budget.amount, budget.currency)
                val spent = spentByCategory[category] ?: 0.0
                val progress = BudgetMath.progress(spent, limit) ?: return@forEach
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BudgetRing(progress.ratio, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(category.label, color = CatText, fontWeight = FontWeight.Medium)
                        Text(
                            "${CurrencyFormatter.format(progress.spent, chartData.currency)} of ${CurrencyFormatter.format(progress.limit, chartData.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (progress.spent > progress.limit) CatRed else CatSubtext0
                        )
                    }
                    Text(
                        if (progress.spent > progress.limit) "Over" else "${CurrencyFormatter.format(progress.remaining, chartData.currency)} left",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (progress.spent > progress.limit) CatRed else CatGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetRing(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = CatSurface1,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 7f)
            )
            drawArc(
                color = if (progress >= 1f) CatRed else CatMauve,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 7f)
            )
        }
        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = CatText)
    }
}

@Composable
private fun WhatIfPanel(viewModel: BillViewModel) {
    val billsWithStatus by viewModel.billsWithStatus.collectAsState()
    val displayCurrency by viewModel.displayCurrency.collectAsState()
    val recurringBills = billsWithStatus.filter { !it.isPaidThisCycle && it.bill.recurrence != Recurrence.ONE_TIME }
    var expanded by remember { mutableStateOf(false) }
    val droppedBills = remember { mutableStateListOf<Long>() }

    if (recurringBills.isEmpty()) return

    val annualSavings = recurringBills
        .filter { it.bill.id in droppedBills }
        .sumOf { bws ->
            val multiplier = when (bws.bill.recurrence) {
                Recurrence.WEEKLY -> 52
                Recurrence.BIWEEKLY -> 26
                Recurrence.MONTHLY -> 12
                Recurrence.QUARTERLY -> 4
                Recurrence.YEARLY -> 1
                Recurrence.ONE_TIME -> 0
            }
            viewModel.convertToDisplay(bws.bill.amount, bws.bill.currency) * multiplier
        }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CatSurfaceRaised)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("What If?", style = MaterialTheme.typography.titleMedium, color = CatText)
                    Text(
                        "Toggle bills to see annual savings",
                        style = MaterialTheme.typography.labelMedium,
                        color = CatSubtext0
                    )
                }
                if (annualSavings > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CatGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "Save ${CurrencyFormatter.format(annualSavings, displayCurrency)}/yr",
                            color = CatGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            recurringBills.take(if (expanded) recurringBills.size else 5).forEach { bws ->
                val isDropped = bws.bill.id in droppedBills
                val multiplier = when (bws.bill.recurrence) {
                    Recurrence.WEEKLY -> 52
                    Recurrence.BIWEEKLY -> 26
                    Recurrence.MONTHLY -> 12
                    Recurrence.QUARTERLY -> 4
                    Recurrence.YEARLY -> 1
                    Recurrence.ONE_TIME -> 0
                }
                val displayAmount = viewModel.convertToDisplay(bws.bill.amount, bws.bill.currency)
                val yearlyAmount = displayAmount * multiplier

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isDropped,
                        onCheckedChange = {
                            if (isDropped) droppedBills.remove(bws.bill.id)
                            else droppedBills.add(bws.bill.id)
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = CatGreen,
                            uncheckedColor = CatOverlay0,
                            checkmarkColor = CatCrust
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        bws.bill.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDropped) CatSubtext0 else CatText,
                        modifier = Modifier.weight(1f)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${CurrencyFormatter.format(displayAmount, displayCurrency)}/${bws.bill.recurrence.label.take(3).lowercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = CatSubtext0
                        )
                        if (isDropped) {
                            Text(
                                "-${CurrencyFormatter.format(yearlyAmount, displayCurrency)}/yr",
                                style = MaterialTheme.typography.labelSmall,
                                color = CatGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (recurringBills.size > 5) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (expanded) "Show less" else "Show all ${recurringBills.size} bills",
                        color = CatBlue
                    )
                }
            }
        }
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
private fun TrendChart(data: List<Pair<String, Double>>, currency: String, modifier: Modifier = Modifier) {
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
            val label = CurrencyFormatter.format(maxVal * i / 3, currency)
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

@Composable
private fun CashFlowChart(
    data: List<MonthlyCashFlow>,
    currency: String,
    modifier: Modifier = Modifier
) {
    val maxValue = data.maxOfOrNull { maxOf(it.paid, it.outstanding) }?.coerceAtLeast(1.0) ?: 1.0
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = modifier) {
        val padLeft = 52f
        val padRight = 8f
        val padTop = 20f
        val padBottom = 34f
        val chartWidth = size.width - padLeft - padRight
        val chartHeight = size.height - padTop - padBottom
        val centerY = padTop + chartHeight / 2f
        val halfHeight = chartHeight / 2f - 8f
        val stepX = chartWidth / data.size
        val barWidth = (stepX * 0.62f).coerceAtMost(28f)
        val textPaint = android.graphics.Paint().apply {
            color = 0xFFA6ADC8.toInt()
            textSize = 22f
            isAntiAlias = true
        }

        listOf(0.5f, 1f).forEach { fraction ->
            val above = centerY - halfHeight * fraction
            val below = centerY + halfHeight * fraction
            drawLine(Color(0xFF313244), Offset(padLeft, above), Offset(size.width - padRight, above), 1f)
            drawLine(Color(0xFF313244), Offset(padLeft, below), Offset(size.width - padRight, below), 1f)
        }
        drawLine(CatOverlay0, Offset(padLeft, centerY), Offset(size.width - padRight, centerY), 2f)
        drawContext.canvas.nativeCanvas.drawText(
            CurrencyFormatter.format(maxValue, currency),
            2f,
            padTop + 8f,
            textPaint
        )
        drawContext.canvas.nativeCanvas.drawText(
            CurrencyFormatter.format(maxValue, currency),
            2f,
            size.height - padBottom + 8f,
            textPaint
        )

        data.forEachIndexed { index, month ->
            val x = padLeft + stepX * index + (stepX - barWidth) / 2f
            val positiveHeight = (month.paid / maxValue).toFloat() * halfHeight * animatedProgress.value
            val negativeHeight = (month.outstanding / maxValue).toFloat() * halfHeight * animatedProgress.value
            if (positiveHeight > 0) {
                drawRect(
                    color = CatGreen,
                    topLeft = Offset(x, centerY - positiveHeight),
                    size = Size(barWidth, positiveHeight)
                )
            }
            if (negativeHeight > 0) {
                drawRect(
                    color = CatPeach,
                    topLeft = Offset(x, centerY),
                    size = Size(barWidth, negativeHeight)
                )
            }
            val label = month.label.substringBefore(' ')
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x + barWidth / 2f - textPaint.measureText(label) / 2f,
                size.height - 4f,
                textPaint
            )
        }
    }
}

@Composable
private fun ForecastColumn(label: String, amount: Double, count: Int, color: Color, currency: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = CatSubtext0
        )
        Spacer(Modifier.height(4.dp))
        Text(
            CurrencyFormatter.format(amount, currency),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
        Text(
            "$count bills",
            style = MaterialTheme.typography.labelSmall,
            color = CatOverlay0
        )
    }
}
