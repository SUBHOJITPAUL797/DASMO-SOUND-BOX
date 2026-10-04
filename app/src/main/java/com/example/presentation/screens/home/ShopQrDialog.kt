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

@Composable
fun ShopQrDialog(
    initialUpiId: String,
    initialShopName: String,
    onSaveUpiDetails: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var upiId by remember { mutableStateOf(initialUpiId) }
    var shopName by remember { mutableStateOf(initialShopName.ifBlank { "Dasmo SoundBox Merchant" }) }
    var customAmount by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(initialUpiId.isBlank()) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(upiId, shopName, customAmount) {
        if (upiId.isNotBlank()) {
            val amt = customAmount.toDoubleOrNull()
            val upiUrl = QrCodeGenerator.generateUpiUrl(upiId, shopName, amt)
            withContext(Dispatchers.Default) {
                qrBitmap = QrCodeGenerator.createQrBitmap(upiUrl, 600)
            }
        } else {
            qrBitmap = null
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

                if (isEditing) {
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("Shop / Business Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("UPI ID (e.g. mobile@upi, shop@okhdfc)") },
                        placeholder = { Text("example@okicici") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (upiId.isNotBlank()) {
                                onSaveUpiDetails(upiId, shopName)
                                isEditing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = upiId.isNotBlank()
                    ) {
                        Text("Generate & Display QR")
                    }
                } else {
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

                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "UPI QR Code",
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .background(Color(0xFFF3F4F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
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
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isEditing = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Edit UPI ID")
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}
