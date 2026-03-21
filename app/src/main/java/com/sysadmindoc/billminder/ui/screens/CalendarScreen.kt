package com.sysadmindoc.billminder.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import com.sysadmindoc.billminder.viewmodel.BillWithStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: BillViewModel,
    onNavigateBack: () -> Unit,
    onBillTap: (Long) -> Unit
) {
    val billsWithStatus by viewModel.billsWithStatus.collectAsState()
    var currentMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var currentYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedDay by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }

    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, currentYear)
        set(Calendar.MONTH, currentMonth)
        set(Calendar.DAY_OF_MONTH, 1)
    }

    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0-based

    // Map bills to their due days in this month
    val billsByDay = remember(billsWithStatus, currentMonth) {
        val map = mutableMapOf<Int, MutableList<BillWithStatus>>()
        billsWithStatus.forEach { bws ->
            val dueCal = Calendar.getInstance().apply { timeInMillis = bws.nextDueDate }
            if (dueCal.get(Calendar.MONTH) == currentMonth && dueCal.get(Calendar.YEAR) == currentYear) {
                val day = dueCal.get(Calendar.DAY_OF_MONTH)
                map.getOrPut(day) { mutableListOf() }.add(bws)
            }
            // Also check if monthly bill falls on a day in this month
            if (bws.bill.dueDay in 1..daysInMonth) {
                val day = bws.bill.dueDay
                if (!map.containsKey(day) || map[day]?.none { it.bill.id == bws.bill.id } == true) {
                    map.getOrPut(day) { mutableListOf() }.let { list ->
                        if (list.none { it.bill.id == bws.bill.id }) list.add(bws)
                    }
                }
            }
        }
        map
    }

    val selectedBills = billsByDay[selectedDay] ?: emptyList()

    Scaffold(
        containerColor = CatCrust,
        topBar = {
            TopAppBar(
                title = { Text("Calendar", color = CatText) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CatCrust),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = CatText)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Month nav
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (currentMonth == 0) {
                            currentMonth = 11; currentYear--
                        } else currentMonth--
                    }) {
                        Icon(Icons.Filled.ChevronLeft, "Previous", tint = CatText)
                    }
                    Text(
                        monthFormat.format(cal.time),
                        style = MaterialTheme.typography.titleLarge,
                        color = CatText
                    )
                    IconButton(onClick = {
                        if (currentMonth == 11) {
                            currentMonth = 0; currentYear++
                        } else currentMonth++
                    }) {
                        Icon(Icons.Filled.ChevronRight, "Next", tint = CatText)
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // Day headers
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                        Text(
                            day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = CatSubtext0,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Calendar grid
            val totalCells = firstDayOfWeek + daysInMonth
            val rows = (totalCells + 6) / 7

            items(rows) { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        val day = cellIndex - firstDayOfWeek + 1

                        if (day in 1..daysInMonth) {
                            val hasBills = billsByDay.containsKey(day)
                            val isSelected = day == selectedDay
                            val today = Calendar.getInstance()
                            val isToday = day == today.get(Calendar.DAY_OF_MONTH) &&
                                    currentMonth == today.get(Calendar.MONTH) &&
                                    currentYear == today.get(Calendar.YEAR)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isSelected -> CatBlue.copy(alpha = 0.2f)
                                            isToday -> CatSurface0
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { selectedDay = day },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        day.toString(),
                                        color = when {
                                            isSelected -> CatBlue
                                            isToday -> CatText
                                            else -> CatSubtext1
                                        },
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                    if (hasBills) {
                                        Spacer(Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            val bills = billsByDay[day] ?: emptyList()
                                            bills.take(3).forEach { bws ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                bws.isPaidThisCycle -> CatGreen
                                                                bws.isOverdue -> CatRed
                                                                else -> Color(bws.bill.color)
                                                            }
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Selected day bills
            item { Spacer(Modifier.height(16.dp)) }

            if (selectedBills.isNotEmpty()) {
                item {
                    Text(
                        "Bills on day $selectedDay",
                        style = MaterialTheme.typography.titleMedium,
                        color = CatText
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(selectedBills, key = { it.bill.id }) { bws ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onBillTap(bws.bill.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CatSurface0)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(bws.bill.color))
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                bws.bill.name,
                                color = CatText,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$${"%,.2f".format(bws.bill.amount)}",
                                color = if (bws.isPaidThisCycle) CatGreen else CatText,
                                fontWeight = FontWeight.Bold
                            )
                            if (bws.isPaidThisCycle) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.CheckCircle, null, tint = CatGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No bills on day $selectedDay", color = CatSubtext0)
                    }
                }
            }
        }
    }
}
