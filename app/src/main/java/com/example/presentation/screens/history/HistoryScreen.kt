package com.example.presentation.screens.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.presentation.screens.home.ActivityRow
import com.example.util.AnnouncementHelper
import com.example.util.CsvExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }

    val groupedTransactions = remember(transactions) {
        transactions.groupBy { 
            val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            format.format(Date(it.timestamp))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction History") },
                actions = {
                    if (transactions.isNotEmpty()) {
                        IconButton(onClick = { CsvExporter.exportAndShare(context, transactions) }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export CSV Statement")
                        }
                    }
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear All")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No transactions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedTransactions.forEach { (date, items) ->
                    stickyHeader {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(vertical = 8.dp)
                        )
                    }
                    items(items, key = { it.id }) { tx ->
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
    
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to delete all transactions?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearConfirm = false
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
