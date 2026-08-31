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
import androidx.compose.material.icons.filled.Add
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
import com.sysadmindoc.billminder.ui.theme.privateAmount
import com.sysadmindoc.billminder.ui.theme.CatText
import com.sysadmindoc.billminder.ui.theme.storedBillColor
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.domain.BillCycles
import com.sysadmindoc.billminder.domain.CycleRangeSnapshot
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: BillViewModel,
    onNavigateBack: () -> Unit,
    onBillTap: (Long) -> Unit,
    onAddBill: () -> Unit
) {
    val bills by viewModel.bills.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val displayCurrency by viewModel.displayCurrency.collectAsState()
    val currencyRevision by viewModel.currencySettingsRevision.collectAsState()
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
    val firstDayOfWeek = (monthCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val selectedDateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    val monthSnapshot = remember(
        bills,
        payments,
        currentMonth,
        currentYear,
        displayCurrency,
        currencyRevision
    ) {
        val monthStart = LocalDate.of(currentYear, currentMonth + 1, 1)
        BillCycles.rangeSnapshot(
            bills = bills,
            payments = payments,
            start = monthStart,
            endInclusive = monthStart.plusMonths(1).minusDays(1),
            today = LocalDate.now(),
            convert = viewModel::convertToDisplay
        )
    }
    val billsByDay = remember(monthSnapshot) {
        buildCalendarOccurrences(monthSnapshot)
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
                    IconButton(onClick = onAddBill) {
                        Icon(Icons.Filled.Add, "Add bill", tint = CatBlue)
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
                    "${privateAmount(monthSnapshot.totalDue, displayCurrency)} scheduled · " +
                        if (monthSnapshot.occurrenceCount == 1) "1 due date" else {
                            "${monthSnapshot.occurrenceCount} due dates"
                        },
                    color = CatSubtext0,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
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
                    Spacer(Modifier.height(8.dp))
                    CalendarLegend()
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                                "${selectedBills.size} bill${if (selectedBills.size == 1) "" else "s"} · ${privateAmount(selectedTotal, displayCurrency)}"
                            },
                            color = CatSubtext0,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(onClick = onAddBill) {
                        Icon(Icons.Filled.Add, "Add bill", tint = CatBlue)
                    }
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

            item {
                CalendarMonthSummary(
                    total = monthSnapshot.totalDue,
                    paid = monthSnapshot.totalPaid,
                    remaining = monthSnapshot.remaining,
                    currency = displayCurrency
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun CalendarDay(
    day: Int,
    statuses: List<CalendarOccurrence>,
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
            .background(if (isSelected) CatBlue.copy(alpha = 0.08f) else Color.Transparent)
            .then(if (isSelected || isToday) Modifier.border(BorderStroke(1.dp, CatBlue), shape) else Modifier)
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
                            status.isPaid -> CatGreen
                            status.isOverdue -> CatRed
                            status.daysUntilDue <= 3 -> com.sysadmindoc.billminder.ui.theme.CatYellow
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
private fun CalendarBillRow(status: CalendarOccurrence, onClick: () -> Unit) {
    val stateLabel = when {
        status.isPaid -> "Paid"
        status.isOverdue -> "${-status.daysUntilDue} days overdue"
        status.daysUntilDue == 0 -> "Due today"
        status.daysUntilDue == 1 -> "Due tomorrow"
        else -> "Upcoming"
    }
    val stateColor = when {
        status.isPaid -> CatGreen
        status.isOverdue -> CatRed
        status.daysUntilDue <= 1 -> com.sysadmindoc.billminder.ui.theme.CatYellow
        else -> CatBlue
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(82.dp).clickable(onClick = onClick),
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
            Text(stateLabel, color = stateColor, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            privateAmount(status.bill.amount, status.bill.currency),
            color = CatText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.ChevronRight, "View bill", tint = CatSubtext0)
    }
}

@Composable
private fun CalendarLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LegendItem(CatGreen, "Paid")
        LegendItem(com.sysadmindoc.billminder.ui.theme.CatYellow, "Due soon")
        LegendItem(CatRed, "Overdue")
        LegendItem(CatBlue, "Upcoming")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = CatSubtext0, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CalendarMonthSummary(total: Double, paid: Double, remaining: Double, currency: String) {
    GroupedSurface(contentPadding = PaddingValues(vertical = 14.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            CalendarMetric("Scheduled", privateAmount(total, currency), CatText, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(46.dp).background(CatDivider))
            CalendarMetric("Paid", privateAmount(paid, currency), CatGreen, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(46.dp).background(CatDivider))
            CalendarMetric("Remaining", privateAmount(remaining, currency), CatBlue, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CalendarMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = CatSubtext0, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(3.dp))
        Text(value, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** One occurrence of a bill on one calendar day, with the state of that day rather than the bill. */
internal data class CalendarOccurrence(
    val bill: Bill,
    val cycleKey: String,
    val isPaid: Boolean,
    val isOverdue: Boolean,
    val daysUntilDue: Int
)

internal fun buildCalendarOccurrences(
    snapshot: CycleRangeSnapshot
): Map<Int, List<CalendarOccurrence>> = snapshot.occurrences
    .groupBy { it.cycle.date.dayOfMonth }
    .mapValues { (_, occurrences) ->
        occurrences.map { resolved ->
            CalendarOccurrence(
                bill = resolved.bill,
                cycleKey = resolved.cycle.cycleKey,
                isPaid = resolved.cycle.isPaid,
                isOverdue = resolved.cycle.isOverdue,
                daysUntilDue = resolved.cycle.daysUntilDue
            )
        }
    }
