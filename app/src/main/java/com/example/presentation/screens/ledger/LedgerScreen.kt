package com.example.presentation.screens.ledger

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.components.AnimatedRupeeText
import com.example.ui.components.Floating3DObjectBox
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.ThreeDTactileCard
import com.example.ui.theme.AppSemanticColors
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.BrandEmeraldDark
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIsDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LedgerScreen(viewModel: LedgerViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = BrandIndigo,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("ledger_add_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Cashbook or Khata Entry")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. Sleek 3D Business Wallet Header Banner
            val isDark = LocalIsDarkTheme.current
            val bannerGradient = if (isDark) {
                listOf(Color(0xFF0D1117), Color(0xFF151C28))
            } else {
                listOf(Color.White, Color(0xFFECFDF5))
            }
            ThreeDTactileCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                containerColor = if (isDark) Color(0xFF0D1117) else Color.White,
                depthColor = if (isDark) Color(0xFF020305) else Color(0xFFA7F3D0),
                borderColor = BrandEmerald.copy(alpha = if (isDark) 0.40f else 0.35f),
                cornerRadius = 24.dp,
                depthDp = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(bannerGradient))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Floating3DObjectBox(bobDistanceDp = 3.5f, durationMs = 2500) {
                        Image(
                            painter = painterResource(
                                id = if (isDark) R.drawable.img_3d_wallet_khata_dark else R.drawable.img_3d_wallet_khata
                            ),
                            contentDescription = "3D Business Wallet",
                            modifier = Modifier
                                .size(66.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, BrandEmerald.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cashbook & Khata",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Online UPI, Drawer Cash & Customer Credit",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. 4 Tactile 3D Wallet KPI Cards
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Balance3DCard(
                        title = "Online UPI",
                        amount = uiState.onlineBalance,
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = AppSemanticColors.indigoText,
                        depthColor = Color(0xFFC7D2FE),
                        modifier = Modifier.weight(1f)
                    )
                    Balance3DCard(
                        title = "Cash Drawer",
                        amount = uiState.drawerBalance,
                        icon = Icons.Default.Payments,
                        accentColor = AppSemanticColors.emeraldText,
                        depthColor = Color(0xFFA7F3D0),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Balance3DCard(
                        title = "Home Vault",
                        amount = uiState.homeBalance,
                        icon = Icons.Default.Home,
                        accentColor = AppSemanticColors.orangeText,
                        depthColor = Color(0xFFFED7AA),
                        modifier = Modifier.weight(1f)
                    )
                    Balance3DCard(
                        title = "Pending Khata",
                        amount = uiState.totalPendingBorrows,
                        icon = Icons.Default.PersonOutline,
                        accentColor = AppSemanticColors.coralText,
                        depthColor = Color(0xFFFECACA),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 3. Sleek Segmented Pill Tab Switcher (Uber / Swiggy style)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(0 to "Cash Flow", 1 to "Customer Khata").forEach { (idx, label) ->
                        val isSelected = selectedTab == idx
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedTab = idx }
                                .testTag(if (idx == 0) "tab_cash_flow" else "tab_customer_khata"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isSelected) 2.dp else 0.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                if (uiState.manualTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ThreeDTactileCard(
                            containerColor = MaterialTheme.colorScheme.surface,
                            depthColor = MaterialTheme.colorScheme.outline,
                            cornerRadius = 22.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = AppSemanticColors.emeraldText
                                    )
                                }
                                Text(
                                    text = "No Cash Flow Entries Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap + to log drawer cash income, expenses, or home vault transfers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.manualTransactions, key = { it.id }) { tx ->
                            val isPositive = tx.amount >= 0
                            ThreeDTactileCard(
                                containerColor = MaterialTheme.colorScheme.surface,
                                depthColor = MaterialTheme.colorScheme.outline,
                                cornerRadius = 18.dp,
                                depthDp = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            tx.note.ifBlank { tx.category },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "${tx.walletType} • ${tx.category}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${if (isPositive) "+" else "-"}₹${String.format(Locale.US, "%,.2f", kotlin.math.abs(tx.amount))}",
                                        color = if (isPositive) AppSemanticColors.emeraldText else AppSemanticColors.coralText,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = JetBrainsMono,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (uiState.customerBorrows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ThreeDTactileCard(
                            containerColor = MaterialTheme.colorScheme.surface,
                            depthColor = MaterialTheme.colorScheme.outline,
                            cornerRadius = 22.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PersonOutline,
                                        contentDescription = null,
                                        tint = AppSemanticColors.indigoText
                                    )
                                }
                                Text(
                                    text = "No Pending Customer Khata",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap + and choose Khata to track customer credit & send WhatsApp reminders.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.customerBorrows, key = { it.id }) { borrow ->
                            val cleared = borrow.isCleared
                            val isDark = LocalIsDarkTheme.current
                            ThreeDTactileCard(
                                containerColor = when {
                                    cleared && isDark -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    cleared -> Color(0xFFF0FDF4)
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                depthColor = if (cleared) Color(0xFFA7F3D0) else MaterialTheme.colorScheme.outline,
                                borderColor = if (cleared) BrandEmerald.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline,
                                cornerRadius = 20.dp,
                                depthDp = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            borrow.customerName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (borrow.purpose.isNotBlank()) {
                                            Text(
                                                borrow.purpose,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            "Due: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(borrow.expectedReturnDate))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (cleared) {
                                            Spacer(Modifier.height(2.dp))
                                            StatusPillBadge(
                                                text = "Cleared ✓",
                                                color = AppSemanticColors.emeraldText,
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "₹${String.format(Locale.US, "%,.2f", borrow.amount)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = JetBrainsMono,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (cleared) AppSemanticColors.emeraldText else AppSemanticColors.coralText,
                                            textDecoration = if (cleared) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                        if (!cleared) {
                                            Spacer(Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        val msg = "Namaste ${borrow.customerName}, you have a pending payment of ₹${borrow.amount} for ${borrow.purpose}. Please pay via UPI at your earliest. Thank you!"
                                                        val uri = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(msg)}")
                                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                                        try {
                                                            context.startActivity(intent)
                                                        } catch (_: Exception) {
                                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                                type = "text/plain"
                                                                putExtra(Intent.EXTRA_TEXT, msg)
                                                            }
                                                            try {
                                                                context.startActivity(Intent.createChooser(share, "Send Reminder"))
                                                            } catch (_: Exception) {}
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Remind", modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Remind", style = MaterialTheme.typography.labelSmall)
                                                }
                                                Button(
                                                    onClick = { viewModel.markBorrowCleared(borrow) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmeraldDark),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = "Mark Cleared", modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Clear", style = MaterialTheme.typography.labelSmall, color = Color.White)
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
fun Balance3DCard(
    title: String,
    amount: Double,
    icon: ImageVector,
    accentColor: Color,
    depthColor: Color,
    modifier: Modifier = Modifier
) {
    ThreeDTactileCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        depthColor = depthColor,
        borderColor = accentColor.copy(alpha = 0.28f),
        cornerRadius = 18.dp,
        depthDp = 2.5.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            AnimatedRupeeText(
                amount = amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
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
    var entryType by remember { mutableStateOf("INCOME") }
    var amountStr by remember { mutableStateOf("") }
    var noteStr by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var selectedWallet by remember { mutableStateOf("DRAWER") }
    var amountError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Cashbook / Khata Entry", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = entryType == "INCOME",
                        onClick = { entryType = "INCOME"; nameError = null },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) { Text("Cash In") }
                    SegmentedButton(
                        selected = entryType == "EXPENSE",
                        onClick = { entryType = "EXPENSE"; nameError = null },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) { Text("Cash Out") }
                    SegmentedButton(
                        selected = entryType == "BORROW",
                        onClick = { entryType = "BORROW" },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) { Text("Khata") }
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = {
                        amountStr = it
                        amountError = null
                    },
                    label = { Text("Amount (₹)") },
                    isError = amountError != null,
                    supportingText = amountError?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ledger_amount_input")
                )

                if (entryType == "BORROW") {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = {
                            customerName = it
                            nameError = null
                        },
                        label = { Text("Customer Name") },
                        isError = nameError != null,
                        supportingText = nameError?.let { err -> { Text(err) } },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ledger_customer_input")
                    )
                    OutlinedTextField(
                        value = noteStr,
                        onValueChange = { noteStr = it },
                        label = { Text("Items / Purpose") },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = noteStr,
                        onValueChange = { noteStr = it },
                        label = { Text("Note / Description") },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Wallet:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        RadioButton(selected = selectedWallet == "DRAWER", onClick = { selectedWallet = "DRAWER" })
                        Text("Drawer", style = MaterialTheme.typography.bodySmall)
                        RadioButton(selected = selectedWallet == "HOME", onClick = { selectedWallet = "HOME" })
                        Text("Home", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.trim().toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        amountError = "Enter a valid amount greater than ₹0"
                        return@Button
                    }
                    if (entryType == "BORROW") {
                        if (customerName.isBlank()) {
                            nameError = "Customer name is required"
                            return@Button
                        }
                        onAddBorrow(
                            customerName.trim(),
                            amt,
                            noteStr.trim().ifBlank { "Goods on credit" },
                            System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
                        )
                    } else {
                        onAddManual(entryType, amt, selectedWallet, noteStr.trim().ifBlank { entryType })
                    }
                },
                modifier = Modifier.testTag("ledger_save_btn")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
