package com.brightfetch.app.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brightfetch.app.model.MediaCandidate
import com.brightfetch.app.model.MediaDownloadOption
import com.brightfetch.app.ui.theme.Ink
import com.brightfetch.app.ui.theme.Mint
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaFormatSheet(
    candidate: MediaCandidate,
    isLoading: Boolean,
    options: List<MediaDownloadOption>,
    errorMessage: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onDownload: (MediaDownloadOption) -> Unit,
    onCopyLink: (String) -> Unit,
) {
    var selectedOptionId by remember(candidate.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(options) {
        if (options.none { it.id == selectedOptionId }) {
            selectedOptionId = options.firstOrNull(MediaDownloadOption::isAvailable)?.id
                ?: options.firstOrNull()?.id
        }
    }

    val selectedOption = options.firstOrNull { it.id == selectedOptionId }
        ?: options.firstOrNull(MediaDownloadOption::isAvailable)
        ?: options.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                text = "Choose download format",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            CandidateSummary(candidate)
            Spacer(Modifier.height(18.dp))

            when {
                isLoading -> LoadingContent()
                errorMessage != null -> ErrorContent(errorMessage, onRetry)
                options.isEmpty() -> EmptyContent(onRetry)
                else -> {
                    Text(
                        text = "Available quality",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        options.forEach { option ->
                            FormatOptionCard(
                                option = option,
                                selected = option.id == selectedOption?.id,
                                onClick = { selectedOptionId = option.id },
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    selectedOption?.let { option ->
                        SelectedOptionDetails(
                            option = option,
                            onCopyLink = onCopyLink,
                            onDownload = onDownload,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateSummary(candidate: MediaCandidate) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(Mint, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Ink,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = candidate.title.ifBlank { candidate.suggestedFileName },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = candidateSummaryLine(candidate),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Checking available formats…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Could not inspect this video",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Try again")
            }
        }
    }
}

@Composable
private fun EmptyContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No downloadable formats were found.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Check again")
        }
    }
}

@Composable
private fun FormatOptionCard(
    option: MediaDownloadOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        option.isAvailable -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        option.isAvailable -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
    }

    Card(
        modifier = Modifier
            .width(156.dp)
            .heightIn(min = 142.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = optionQualityLabel(option),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                ExtensionBadge(option.outputExtension)
            }
            Text(
                text = optionSizeLabel(option),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            optionResolution(option)?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            formatBitrate(option)?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (!option.isAvailable) {
                Text(
                    text = "Unavailable",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                option.unavailableReason?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 11.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedOptionDetails(
    option: MediaDownloadOption,
    onCopyLink: (String) -> Unit,
    onDownload: (MediaDownloadOption) -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionBadge(option.outputExtension)
        Text(
            text = optionQualityLabel(option),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = optionSizeLabel(option),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
    Spacer(Modifier.height(10.dp))

    val detailItems = listOfNotNull(
        optionResolution(option),
        formatBitrate(option),
        formatDuration(option.durationSeconds),
        option.segmentCount?.takeIf { it > 0 }?.let {
            "$it video ${if (it == 1) "segment" else "segments"}"
        },
        option.codecs?.takeIf(String::isNotBlank)?.let { "Codec: $it" },
    )
    if (detailItems.isNotEmpty()) {
        Text(
            text = detailItems.joinToString("  •  "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
    }

    Text(
        text = "Download link",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 104.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = option.downloadUrl,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                    )
                }
            }
            TextButton(
                onClick = { onCopyLink(option.downloadUrl) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("Copy link")
            }
        }
    }

    option.unavailableReason?.let { reason ->
        Spacer(Modifier.height(10.dp))
        Text(
            text = reason,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }

    Spacer(Modifier.height(14.dp))
    Button(
        onClick = { onDownload(option) },
        enabled = option.isAvailable,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Icon(Icons.Default.Download, contentDescription = null)
        Spacer(Modifier.width(9.dp))
        Text(
            text = "Download Video",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ExtensionBadge(extension: String) {
    Text(
        text = extension.trimStart('.').ifBlank { "VIDEO" }.uppercase(Locale.ROOT),
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .background(Color(0xFFF28B38), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

private fun candidateSummaryLine(candidate: MediaCandidate): String {
    val host = runCatching { Uri.parse(candidate.pageUrl ?: candidate.url).host }
        .getOrNull()
        ?.removePrefix("www.")
    val source = if (candidate.isHls) "HLS stream" else "Direct video"
    return listOfNotNull(host, source).joinToString("  •  ").ifBlank { source }
}

private fun optionResolution(option: MediaDownloadOption): String? {
    val width = option.width?.takeIf { it > 0 }
    val height = option.height?.takeIf { it > 0 }
    return when {
        width != null && height != null -> "${width}×$height"
        height != null -> "${height}p"
        else -> null
    }
}

private fun optionQualityLabel(option: MediaDownloadOption): String =
    option.label.substringBefore(" • ").ifBlank { option.label }

private fun optionSizeLabel(option: MediaDownloadOption): String {
    val size = option.estimatedSizeBytes?.takeIf { it > 0L } ?: return "Size unknown"
    val prefix = if (option.sizeIsExact) "" else "≈ "
    return prefix + formatBytes(size)
}

private fun formatBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes < 1_024L) return "$safeBytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = safeBytes.toDouble()
    var unitIndex = -1
    while (value >= 1_024.0 && unitIndex < units.lastIndex) {
        value /= 1_024.0
        unitIndex++
    }
    val precision = if (value >= 100.0) 0 else if (value >= 10.0) 1 else 2
    return String.format(Locale.US, "%.${precision}f %s", value, units[unitIndex])
}

private fun formatBitrate(option: MediaDownloadOption): String? {
    val bitrate = (option.averageBandwidthBitsPerSecond ?: option.bandwidthBitsPerSecond)
        ?.takeIf { it > 0L }
        ?: return null
    return if (bitrate >= 1_000_000L) {
        String.format(Locale.US, "%.2f Mbps", bitrate / 1_000_000.0)
    } else {
        String.format(Locale.US, "%.0f Kbps", bitrate / 1_000.0)
    }
}

private fun formatDuration(seconds: Double?): String? {
    val totalSeconds = seconds
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.roundToLong()
        ?: return null
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val remainingSeconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, remainingSeconds)
    }
}
