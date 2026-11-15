package com.example.presentation.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.example.R
import com.example.domain.model.AnnouncementStatus
import com.example.presentation.screens.home.ActivityRow
import com.example.ui.components.Floating3DObjectBox
import com.example.ui.components.PdfStatementPreviewDialog
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.ThreeDTactileCard
import com.example.ui.theme.AppSemanticColors
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.LocalIsDarkTheme
import com.example.util.AnnouncementHelper
import com.example.util.PdfStatementExporter
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryFilter(val label: String) {
    ALL("All"),
    ANNOUNCED("Announced"),
    SKIPPED("Skipped")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

    var showClearConfirm by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }

    val filteredTransactions = remember(transactions, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase()
        transactions.filter { tx ->
            val matchesFilter = when (selectedFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.ANNOUNCED -> tx.status == AnnouncementStatus.ANNOUNCED.name
                HistoryFilter.SKIPPED -> tx.status != AnnouncementStatus.ANNOUNCED.name
            }
            val matchesQuery = q.isEmpty() ||
                (tx.payerName?.lowercase()?.contains(q) == true) ||
                tx.sourceAppName.lowercase().contains(q) ||
                (tx.refId?.lowercase()?.contains(q) == true) ||
                tx.amountFormatted.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    val groupedTransactions = remember(filteredTransactions) {
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        filteredTransactions.groupBy { format.format(Date(it.timestamp)) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Sleek Uber/Swiggy Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Payment History",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${transactions.size} recorded alerts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AnimatedVisibility(visible = transactions.isNotEmpty()) {
                        Row {
                            IconButton(
                                onClick = {
                                    if (!isExportingPdf) {
                                        isExportingPdf = true
                                        scope.launch {
                                            try {
                                                PdfStatementExporter.generatePdfFileAsync(
                                                    context = context,
                                                    transactions = transactions,
                                                    shopName = appSettings.shopName,
                                                    shopUpiId = appSettings.shopUpiId
                                                ).onSuccess { file ->
                                                    previewPdfFile = file
                                                }.onFailure {
                                                    Toast.makeText(
                                                        context,
                                                        "Failed to generate PDF statement",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } finally {
                                                isExportingPdf = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("export_pdf_history_btn")
                            ) {
                                if (isExportingPdf) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.PictureAsPdf,
                                        contentDescription = "Export PDF Statement",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showClearConfirm = true },
                                modifier = Modifier.testTag("clear_history_btn")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Clear All History",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Search & Filter Controls
            if (transactions.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("history_search_input"),
                    placeholder = { Text("Search name, UPI app, amount or UTR...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(Modifier.height(10.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(HistoryFilter.entries) { filter ->
                        val selected = selectedFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    filter.label,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandIndigo,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
            }

            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ThreeDTactileCard(
                        containerColor = MaterialTheme.colorScheme.surface,
                        depthColor = MaterialTheme.colorScheme.outline
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val isDark = LocalIsDarkTheme.current
                            Floating3DObjectBox(bobDistanceDp = 5f) {
                                Image(
                                    painter = painterResource(
                                        id = if (isDark) R.drawable.img_3d_empty_payments_dark else R.drawable.img_3d_empty_payments
                                    ),
                                    contentDescription = "No matching payments",
                                    modifier = Modifier
                                        .size(134.dp)
                                        .clip(RoundedCornerShape(24.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                text = if (transactions.isEmpty()) "No Payments Recorded Yet" else "No Matching Payments",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (transactions.isEmpty()) {
                                    "Your announced PhonePe, GPay, Paytm, BHIM, and Bank receipts will appear here."
                                } else {
                                    "Try clearing the search query or switching your filter."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedTransactions.forEach { (date, dayItems) ->
                        val dayTotal = dayItems
                            .filter { it.status == AnnouncementStatus.ANNOUNCED.name }
                            .sumOf { it.amount }

                        stickyHeader(key = "header_$date") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    StatusPillBadge(
                                        text = "${dayItems.size} txns • ₹${String.format(Locale.US, "%,.0f", dayTotal)}",
                                        color = AppSemanticColors.emeraldText,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                }
                            }
                        }
                        items(dayItems, key = { it.id }) { tx ->
                            ActivityRow(
                                tx = tx,
                                onReplay = {
                                    scope.launch {
                                        AnnouncementHelper.replayTransaction(context, tx)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Payment History?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove all recorded payment announcements from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearConfirm = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    previewPdfFile?.let { file ->
        PdfStatementPreviewDialog(
            pdfFile = file,
            shopName = appSettings.shopName,
            onDismiss = { previewPdfFile = null }
        )
    }
}
