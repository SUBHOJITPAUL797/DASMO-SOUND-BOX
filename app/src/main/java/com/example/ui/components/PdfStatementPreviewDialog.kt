package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BrandIndigo
import com.example.util.PdfStatementExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-app PDF Statement Viewer rendered natively with Android's PdfRenderer so users
 * can inspect every wide A4 Landscape page, zoom in on columns, save to Downloads,
 * open in an external PDF viewer, or share directly.
 */
private sealed interface PdfRenderState {
    data object Loading : PdfRenderState
    data class Ready(val pages: List<Bitmap>) : PdfRenderState
    data class Error(val message: String) : PdfRenderState
}

@Composable
fun PdfStatementPreviewDialog(
    pdfFile: File,
    shopName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var renderState by remember(pdfFile) { mutableStateOf<PdfRenderState>(PdfRenderState.Loading) }
    var isZoomed by remember { mutableStateOf(false) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            pdfFile.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        } ?: error("Cannot open output stream")
                    }
                }
                saved.onSuccess {
                    Toast.makeText(context, "PDF statement saved", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Could not save PDF file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(pdfFile) {
        renderState = PdfRenderState.Loading
        val bitmapsResult = withContext(Dispatchers.IO) {
            runCatching {
                val pages = mutableListOf<Bitmap>()
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val pageCount = renderer.pageCount
                        for (i in 0 until pageCount) {
                            renderer.openPage(i).use { page ->
                                // 2x crisp scale for wide 842x595 landscape pages
                                val targetW = (page.width * 2).coerceAtLeast(1200)
                                val targetH = (page.height * 2).coerceAtLeast(800)
                                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(AndroidColor.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pages.add(bmp)
                            }
                        }
                    }
                }
                pages
            }
        }
        renderState = bitmapsResult.fold(
            onSuccess = { PdfRenderState.Ready(it) },
            onFailure = { PdfRenderState.Error(it.message ?: "Unable to render PDF pages") }
        )
    }

    DisposableEffect(pdfFile) {
        onDispose {
            (renderState as? PdfRenderState.Ready)?.pages?.forEach { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .testTag("pdf_preview_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PDF Statement Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val displayShop = shopName.trim().ifBlank { "My Shop" }
                        val readyPages = (renderState as? PdfRenderState.Ready)?.pages
                        val pageSummary = if (!readyPages.isNullOrEmpty()) {
                            "$displayShop • ${readyPages.size} page${if (readyPages.size == 1) "" else "s"}"
                        } else {
                            displayShop
                        }
                        Text(
                            text = pageSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { isZoomed = !isZoomed },
                        modifier = Modifier.testTag("pdf_preview_zoom_btn")
                    ) {
                        Icon(
                            imageVector = if (isZoomed) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                            contentDescription = if (isZoomed) "Fit to screen" else "Zoom in",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("pdf_preview_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close preview"
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Body (Rendered PDF Pages)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                ) {
                    val containerWidth = maxWidth
                    val pageRenderWidth = if (isZoomed) containerWidth * 1.75f else (containerWidth - 24.dp).coerceAtLeast(240.dp)

                    when (val state = renderState) {
                        PdfRenderState.Loading -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    "Rendering formatted PDF pages...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is PdfRenderState.Error -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Preview unavailable on this device",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is PdfRenderState.Ready -> {
                            val hScroll = rememberScrollState()
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (isZoomed) Modifier.horizontalScroll(hScroll) else Modifier)
                            ) {
                                LazyColumn(
                                    modifier = if (isZoomed) Modifier.width(pageRenderWidth + 24.dp) else Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    itemsIndexed(state.pages) { idx, bmp ->
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Surface(
                                                modifier = if (isZoomed) {
                                                    Modifier.width(pageRenderWidth)
                                                } else {
                                                    Modifier.fillMaxWidth()
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color.White,
                                                shadowElevation = 4.dp,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                            ) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = "PDF Statement Page ${idx + 1}",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp)),
                                                    contentScale = ContentScale.FillWidth
                                                )
                                            }
                                            Text(
                                                text = "Page ${idx + 1} of ${state.pages.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Footer Action Bar (Save / Open / Share)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { saveDocumentLauncher.launch(pdfFile.name) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pdf_preview_save_btn"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Save", fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    FilledTonalButton(
                        onClick = { PdfStatementExporter.openPdfExternally(context, pdfFile) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pdf_preview_open_btn"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Open", fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    Button(
                        onClick = { PdfStatementExporter.sharePdfFile(context, pdfFile, shopName) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pdf_preview_share_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(5.dp))
                        Text("Share", fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}
