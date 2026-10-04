package com.example.presentation.screens.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.entity.TransactionEntity
import com.example.presentation.screens.history.HistoryViewModel
import com.example.util.CsvExporter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyTotal(
    val dayLabel: String,
    val dateStr: String,
    val amount: Double,
    val count: Int
)

data class AppShare(
    val appName: String,
    val count: Int,
    val amount: Double,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    val announcedTxs = remember(transactions) {
        transactions.filter { it.status == "ANNOUNCED" }
    }

    // 7 Days Trend
    val last7Days = remember(announcedTxs) {
        val calendar = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val keyFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        val list = mutableListOf<DailyTotal>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dayKey = keyFormat.format(cal.time)
            val label = dayFormat.format(cal.time)
            val dayTxs = announcedTxs.filter { keyFormat.format(Date(it.timestamp)) == dayKey }
            val total = dayTxs.sumOf { it.amount }
            list.add(DailyTotal(label, dayKey, total, dayTxs.size))
        }
        list
    }

    val maxDayAmount = remember(last7Days) {
        last7Days.maxOfOrNull { it.amount }?.coerceAtLeast(100.0) ?: 100.0
    }

    // Payment App Share
    val appShares = remember(announcedTxs) {
        val palette = listOf(
            Color(0xFF8B5CF6), // PhonePe Purple
            Color(0xFF3B82F6), // GPay Blue
            Color(0xFF0EA5E9), // Paytm Cyan
            Color(0xFF10B981), // Bank Green
            Color(0xFFF59E0B), // Orange
            Color(0xFFEC4899)  // Pink
        )
        val grouped = announcedTxs.groupBy {
            val lower = it.sourceAppName.lowercase()
            when {
                lower.contains("phonepe") -> "PhonePe"
                lower.contains("gpay") || lower.contains("google") -> "Google Pay"
                lower.contains("paytm") -> "Paytm"
                lower.contains("sms") || lower.contains("bank") -> "Bank SMS"
                lower.contains("bhim") -> "BHIM UPI"
                lower.contains("cred") -> "CRED"
                else -> "Other UPI"
            }
        }
        var colorIdx = 0
        grouped.map { (name, list) ->
            val col = palette[colorIdx % palette.size]
            colorIdx++
            AppShare(name, list.size, list.sumOf { it.amount }, col)
        }.sortedByDescending { it.amount }
    }

    // Total metrics
    val totalRevenue = remember(announcedTxs) { announcedTxs.sumOf { it.amount } }
    val totalCount = remember(announcedTxs) { announcedTxs.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sales & Analytics", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(
                        onClick = {
                            CsvExporter.exportAndShare(context, transactions)
                        }
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV Statement")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overall Stats Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "All-Time Collections",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Button(
                                onClick = { CsvExporter.exportAndShare(context, transactions) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export CSV", fontSize = 12.sp)
                            }
                        }

                        Text(
                            text = "₹${String.format(Locale.US, "%,.2f", totalRevenue)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = "$totalCount total payments recorded on this soundbox",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 7-Day Trend Bar Chart
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "7-Day Sales Trend",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Bar Chart Display
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            last7Days.forEach { day ->
                                val heightFraction by animateFloatAsState(
                                    targetValue = (day.amount / maxDayAmount).toFloat().coerceIn(0.06f, 1.0f),
                                    animationSpec = tween(600),
                                    label = "barHeight"
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    if (day.amount > 0) {
                                        Text(
                                            text = "₹${day.amount.toInt()}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.55f)
                                            .fillMaxHeight(heightFraction)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .background(
                                                if (day.amount == maxDayAmount && day.amount > 0)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            )
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = day.dayLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Payment App Breakdown
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "App & Bank Distribution",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (appShares.isEmpty()) {
                            Text(
                                text = "No payment breakdown available yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            appShares.forEach { share ->
                                val percent = if (totalRevenue > 0) (share.amount / totalRevenue * 100).toInt() else 0
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(share.color)
                                            )
                                            Text(share.appName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text("(${share.count} txns)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(
                                            "₹${String.format(Locale.US, "%,.2f", share.amount)} ($percent%)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (percent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = share.color,
                                        trackColor = MaterialTheme.colorScheme.surface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
