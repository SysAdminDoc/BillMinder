package com.sysadmindoc.billminder.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.data.CurrencyFormatter
import com.sysadmindoc.billminder.ui.components.GroupDivider
import com.sysadmindoc.billminder.ui.components.GroupedSurface
import com.sysadmindoc.billminder.ui.components.IconWell
import com.sysadmindoc.billminder.ui.components.getCategoryIcon
import com.sysadmindoc.billminder.ui.theme.CatBlue
import com.sysadmindoc.billminder.ui.theme.CatCrust
import com.sysadmindoc.billminder.ui.theme.CatDivider
import com.sysadmindoc.billminder.ui.theme.CatGreen
import com.sysadmindoc.billminder.ui.theme.CatRed
import com.sysadmindoc.billminder.ui.theme.CatSubtext0
import com.sysadmindoc.billminder.ui.theme.CatSubtext1
import com.sysadmindoc.billminder.ui.theme.CatSurface0
import com.sysadmindoc.billminder.ui.theme.CatText
import com.sysadmindoc.billminder.ui.theme.storedBillColor
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import com.sysadmindoc.billminder.viewmodel.BillWithStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: BillViewModel,
    onNavigateBack: () -> Unit,
    onBillTap: (Long) -> Unit
) {
    val billsWithStatus by viewModel.billsWithStatus.collectAsState()
    val summary by viewModel.monthlySummary.collectAsState()
    val displayCurrency by viewModel.displayCurrency.collectAsState()
    val today = remember { Calendar.getInstance() }
    var currentMonth by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var currentYear by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var selectedDay by remember { mutableIntStateOf(today.get(Calendar.DAY_OF_MONTH)) }

    val monthCalendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, currentYear)
        set(Calendar.MONTH, currentMonth)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = monthCalendar.get(Calendar.DAY_OF_WEEK) - 1
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val selectedDateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    val billsByDay = remember(billsWithStatus, currentMonth, currentYear, daysInMonth) {
        val result = mutableMapOf<Int, MutableList<BillWithStatus>>()
        billsWithStatus.forEach { status ->
            val due = Calendar.getInstance().apply { timeInMillis = status.nextDueDate }
            if (due.get(Calendar.MONTH) == currentMonth && due.get(Calendar.YEAR) == currentYear) {
                result.getOrPut(due.get(Calendar.DAY_OF_MONTH)) { mutableListOf() }.add(status)
            }
            if (status.bill.dueDay in 1..daysInMonth && status.bill.recurrence.name != "ONE_TIME") {
                val list = result.getOrPut(status.bill.dueDay) { mutableListOf() }
                if (list.none { it.bill.id == status.bill.id }) list.add(status)
            }
        }
        result
    }
    val selectedBills = billsByDay[selectedDay].orEmpty()
    val selectedCalendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, currentYear)
        set(Calendar.MONTH, currentMonth)
        set(Calendar.DAY_OF_MONTH, selectedDay.coerceAtMost(daysInMonth))
    }
    val selectedTotal = selectedBills.sumOf {
        viewModel.convertToDisplay(it.bill.amount, it.bill.currency)
    }

    Scaffold(
        containerColor = CatCrust,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text("Calendar", color = CatText, style = MaterialTheme.typography.headlineMedium)
                },
                actions = {
                    TextButton(onClick = {
                        currentMonth = today.get(Calendar.MONTH)
                        currentYear = today.get(Calendar.YEAR)
                        selectedDay = today.get(Calendar.DAY_OF_MONTH)
                    }) {
                        Text("Today", color = CatBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CatCrust),
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (currentMonth == 0) {
                            currentMonth = 11
                            currentYear--
                        } else currentMonth--
                        selectedDay = 1
                    }) {
                        Icon(Icons.Filled.ChevronLeft, "Previous month", tint = CatText)
                    }
                    Text(
                        monthFormat.format(monthCalendar.time),
                        style = MaterialTheme.typography.titleLarge,
                        color = CatText,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = {
                        if (currentMonth == 11) {
                            currentMonth = 0
                            currentYear++
                        } else currentMonth++
                        selectedDay = 1
                    }) {
                        Icon(Icons.Filled.ChevronRight, "Next month", tint = CatText)
                    }
                }
                Text(
                    "${CurrencyFormatter.format(summary.totalDue, summary.currency)} scheduled · ${summary.billCount} bills",
                    color = CatSubtext0,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                            Text(
                                label,
                                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                                textAlign = TextAlign.Center,
                                color = CatSubtext0,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val totalCells = firstDayOfWeek + daysInMonth
                    val rows = (totalCells + 6) / 7
                    repeat(rows) { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            repeat(7) { column ->
                                val cellIndex = row * 7 + column
                                val day = cellIndex - firstDayOfWeek + 1
                                if (day in 1..daysInMonth) {
                                    CalendarDay(
                                        day = day,
                                        statuses = billsByDay[day].orEmpty(),
                                        isSelected = day == selectedDay,
                                        isToday = day == today.get(Calendar.DAY_OF_MONTH) &&
                                            currentMonth == today.get(Calendar.MONTH) &&
                                            currentYear == today.get(Calendar.YEAR),
                                        onClick = { selectedDay = day },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    Text(
                        selectedDateFormat.format(selectedCalendar.time),
                        color = CatText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (selectedBills.isEmpty()) {
                            "No bills scheduled"
                        } else {
                            "${selectedBills.size} bill${if (selectedBills.size == 1) "" else "s"} · ${CurrencyFormatter.format(selectedTotal, displayCurrency)}"
                        },
                        color = CatSubtext0,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (selectedBills.isEmpty()) {
                item {
                    GroupedSurface {
                        Text(
                            "Nothing is due on this day.",
                            color = CatSubtext0,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                item {
                    GroupedSurface(contentPadding = PaddingValues(horizontal = 12.dp)) {
                        selectedBills.forEachIndexed { index, status ->
                            CalendarBillRow(status = status, onClick = { onBillTap(status.bill.id) })
                            if (index < selectedBills.lastIndex) GroupDivider()
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun CalendarDay(
    day: Int,
    statuses: List<BillWithStatus>,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(if (isSelected) CatBlue.copy(alpha = 0.25f) else Color.Transparent)
            .then(if (isToday) Modifier.border(BorderStroke(1.dp, CatBlue), shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.toString(),
                color = when {
                    isSelected || isToday -> CatBlue
                    else -> CatSubtext1
                },
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
            if (statuses.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    statuses.take(3).forEach { status ->
                        val markerColor = when {
                            status.isPaidThisCycle -> CatGreen
                            status.isOverdue -> CatRed
                            else -> storedBillColor(status.bill.color)
                        }
                        Box(Modifier.size(5.dp).clip(CircleShape).background(markerColor))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarBillRow(status: BillWithStatus, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(84.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconWell(
            icon = getCategoryIcon(status.bill.category),
            contentDescription = null,
            tint = storedBillColor(status.bill.color)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                status.bill.name,
                color = CatText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(status.bill.category.label, color = CatSubtext0, style = MaterialTheme.typography.bodySmall)
            if (status.bill.isAutoPay) {
                Text("Auto-pay", color = CatGreen, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            CurrencyFormatter.format(status.bill.amount, status.bill.currency),
            color = CatText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.ChevronRight, "View bill", tint = CatSubtext0)
    }
}
