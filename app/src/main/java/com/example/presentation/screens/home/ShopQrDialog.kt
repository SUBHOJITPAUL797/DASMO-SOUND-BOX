package com.example.presentation.screens.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ShopQrUiState {
    data class EditProfile(val upiError: String? = null) : ShopQrUiState
    sealed interface QrDisplay : ShopQrUiState {
        data object Generating : QrDisplay
        data class Ready(val bitmap: Bitmap) : QrDisplay
        data object Failed : QrDisplay
    }
}

@Composable
fun ShopQrDialog(
    initialUpiId: String,
    initialShopName: String,
    onSaveUpiDetails: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var upiId by remember(initialUpiId) { mutableStateOf(initialUpiId) }
    var shopName by remember(initialShopName) { mutableStateOf(initialShopName.ifBlank { "Dasmo SoundBox Merchant" }) }
    var customAmount by remember { mutableStateOf("") }
    var uiState by remember {
        mutableStateOf<ShopQrUiState>(
            if (initialUpiId.isBlank()) ShopQrUiState.EditProfile() else ShopQrUiState.QrDisplay.Generating
        )
    }

    LaunchedEffect(uiState is ShopQrUiState.QrDisplay, upiId, shopName, customAmount) {
        if (uiState !is ShopQrUiState.QrDisplay) return@LaunchedEffect
        val trimmed = upiId.trim()
        if (trimmed.isBlank()) {
            uiState = ShopQrUiState.EditProfile()
            return@LaunchedEffect
        }
        uiState = ShopQrUiState.QrDisplay.Generating
        val amt = customAmount.toDoubleOrNull()?.takeIf { it > 0.0 }
        val upiUrl = QrCodeGenerator.generateUpiUrl(trimmed, shopName.trim(), amt)
        val generated = withContext(Dispatchers.Default) {
            runCatching { QrCodeGenerator.createQrBitmap(upiUrl, 600) }.getOrNull()
        }
        uiState = if (generated != null) {
            ShopQrUiState.QrDisplay.Ready(generated)
        } else {
            ShopQrUiState.QrDisplay.Failed
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Countertop UPI QR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                when (val state = uiState) {
                    is ShopQrUiState.EditProfile -> {
                        OutlinedTextField(
                            value = shopName,
                            onValueChange = { shopName = it },
                            label = { Text("Shop / Business Name") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = upiId,
                            onValueChange = {
                                upiId = it
                                if (state.upiError != null) {
                                    uiState = ShopQrUiState.EditProfile(upiError = null)
                                }
                            },
                            label = { Text("UPI ID (e.g. mobile@upi, shop@okhdfc)") },
                            placeholder = { Text("example@okicici") },
                            isError = state.upiError != null,
                            supportingText = state.upiError?.let { err -> { Text(err) } },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val trimmedUpi = upiId.trim()
                                if (!trimmedUpi.contains("@") || trimmedUpi.length < 5) {
                                    uiState = ShopQrUiState.EditProfile("Enter a valid UPI ID (e.g. name@bank)")
                                    return@Button
                                }
                                onSaveUpiDetails(trimmedUpi, shopName.trim())
                                uiState = ShopQrUiState.QrDisplay.Generating
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Generate & Display QR", fontWeight = FontWeight.Bold)
                        }
                    }

                    is ShopQrUiState.QrDisplay -> {
                        // QR Display
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = shopName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Black
                            )
                            Text(
                                text = upiId,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )

                            Spacer(Modifier.height(12.dp))

                            when (state) {
                                is ShopQrUiState.QrDisplay.Ready -> {
                                    Image(
                                        bitmap = state.bitmap.asImageBitmap(),
                                        contentDescription = "UPI QR Code",
                                        modifier = Modifier
                                            .size(240.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                ShopQrUiState.QrDisplay.Generating -> {
                                    Box(
                                        modifier = Modifier
                                            .size(240.dp)
                                            .background(Color(0xFFF3F4F6)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                                ShopQrUiState.QrDisplay.Failed -> {
                                    Box(
                                        modifier = Modifier
                                            .size(240.dp)
                                            .background(Color(0xFFFEF2F2))
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Could not render QR. Check your UPI ID and try again.",
                                            color = Color(0xFFB91C1C),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = if (customAmount.toDoubleOrNull() != null) "₹$customAmount" else "Scan & Pay with Any UPI App",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Optional custom amount input for dynamic QR
                        OutlinedTextField(
                            value = customAmount,
                            onValueChange = { customAmount = it },
                            label = { Text("Custom Amount to Request (Optional ₹)") },
                            placeholder = { Text("Leave empty for any amount") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { uiState = ShopQrUiState.EditProfile() },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Edit UPI ID")
                            }
                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Done", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
