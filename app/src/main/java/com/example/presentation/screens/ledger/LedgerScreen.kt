package com.example.presentation.screens.ledger

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(viewModel: LedgerViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cashbook & Khata", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.tertiary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Entry")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            // Balances Dashboard
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BalanceCard("Online", uiState.onlineBalance, MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f))
                    BalanceCard("Drawer", uiState.drawerBalance, MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BalanceCard("Home", uiState.homeBalance, MaterialTheme.colorScheme.tertiaryContainer, Modifier.weight(1f))
                    BalanceCard("Pending", uiState.totalPendingBorrows, MaterialTheme.colorScheme.errorContainer, Modifier.weight(1f))
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Cash Flow") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Customer Khata") })
            }

            if (selectedTab == 0) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.manualTransactions, key = { it.id }) { tx ->
                        val isPositive = tx.amount >= 0
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.note, fontWeight = FontWeight.SemiBold)
                                    Text("${tx.walletType} • ${tx.category}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = "${if (isPositive) "+" else ""}₹${String.format(Locale.US, "%.2f", tx.amount)}",
                                    color = if (isPositive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.customerBorrows, key = { it.id }) { borrow ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(borrow.customerName, fontWeight = FontWeight.Bold)
                                    Text(borrow.purpose, style = MaterialTheme.typography.bodyMedium)
                                    Text("Due: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(borrow.expectedReturnDate))}", style = MaterialTheme.typography.bodySmall)
                                    if (borrow.isCleared) {
                                        Text("Cleared", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("₹${String.format(Locale.US, "%.2f", borrow.amount)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    if (!borrow.isCleared) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    val msg = "Namaste ${borrow.customerName}, you have a pending payment of ₹${borrow.amount} for ${borrow.purpose}. Please pay via UPI at your earliest. Thank you!"
                                                    val uri = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(msg)}")
                                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        val share = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, msg)
                                                        }
                                                        context.startActivity(Intent.createChooser(share, "Send Reminder"))
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Send, contentDescription = "Remind", modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Remind", style = MaterialTheme.typography.labelSmall)
                                            }
                                            Button(
                                                onClick = { viewModel.markBorrowCleared(borrow) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Mark Cleared", modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Clear", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (showAddDialog) {
            AddTransactionDialog(
                onDismiss = { showAddDialog = false },
                onAddManual = { type, amt, wallet, note -> 
                    viewModel.addManualTransaction(type, amt, wallet, note)
                    showAddDialog = false
                },
                onAddBorrow = { name, amt, purpose, date ->
                    viewModel.addCustomerBorrow(name, amt, purpose, date)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun BalanceCard(title: String, amount: Double, bgColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text("₹${String.format(Locale.US, "%.2f", amount)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onAddManual: (type: String, amount: Double, wallet: String, note: String) -> Unit,
    onAddBorrow: (name: String, amount: Double, purpose: String, expectedDate: Long) -> Unit
) {
    var entryType by remember { mutableStateOf("INCOME") } // INCOME, EXPENSE, TRANSFER, BORROW
    var amountStr by remember { mutableStateOf("") }
    var noteStr by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var selectedWallet by remember { mutableStateOf("DRAWER") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = entryType == "INCOME",
                        onClick = { entryType = "INCOME" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) { Text("In") }
                    SegmentedButton(
                        selected = entryType == "EXPENSE",
                        onClick = { entryType = "EXPENSE" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) { Text("Out") }
                    SegmentedButton(
                        selected = entryType == "BORROW",
                        onClick = { entryType = "BORROW" },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) { Text("Khata") }
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                if (entryType == "BORROW") {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Customer Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteStr,
                        onValueChange = { noteStr = it },
                        label = { Text("Purpose") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = noteStr,
                        onValueChange = { noteStr = it },
                        label = { Text("Note / Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Wallet: ")
                        RadioButton(selected = selectedWallet == "DRAWER", onClick = { selectedWallet = "DRAWER" })
                        Text("Drawer")
                        RadioButton(selected = selectedWallet == "HOME", onClick = { selectedWallet = "HOME" })
                        Text("Home")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amountStr.toDoubleOrNull() ?: return@Button
                if (entryType == "BORROW") {
                    if (customerName.isNotBlank()) {
                        onAddBorrow(customerName, amt, noteStr, System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000) // 7 days default
                    }
                } else {
                    onAddManual(entryType, amt, selectedWallet, noteStr.ifBlank { entryType })
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
