package com.example.ui.observation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.R
import com.example.data.local.entity.Observation
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationDetailScreen(
    observation: Observation,
    plantName: String,
    onBackClick: () -> Unit,
    onRetryAnalysis: (Observation) -> Unit,
    onShareToTelegram: (Observation) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(observation.createdAt))

    val isPendingOrFailed = observation.aiStatus == "PENDING" || observation.aiStatus == "FAILED"
    val visualChangesList = parseJsonList(observation.visualChanges)
    val visibleIssuesList = parseJsonList(observation.visibleIssues)
    val possibleCausesList = parseJsonList(observation.possibleCauses)
    val recommendedList = parseJsonList(observation.recommendedObservations)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("جزئیات پایش گیاه") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    if (isPendingOrFailed) {
                        IconButton(onClick = { onRetryAnalysis(observation) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تلاش مجدد تحلیل"
                            )
                        }
                    }
                    IconButton(onClick = { onShareToTelegram(observation) }) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "ارسال به تلگرام"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Plant image
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    val file = File(observation.imagePath)
                    if (file.exists()) {
                        Image(
                            painter = rememberAsyncImagePainter(file),
                            contentDescription = "تصویر مشاهده",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("تصویر یافت نشد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Header summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!isPendingOrFailed)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = plantName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "وضعیت پایش: ${observation.overallStatus}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // If Failed or 503 error, show error alert & retry button
            if (isPendingOrFailed) {
                item {
                    val friendlyError = formatFriendlyErrorMessage(observation.summary)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "⚠️ تحلیل نیازمند تلاش مجدد",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = friendlyError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onRetryAnalysis(observation) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تلاش مجدد تحلیل (Retry Analysis)")
                            }
                        }
                    }
                }
            }

            // Summary
            if (observation.summary.isNotBlank() && !isPendingOrFailed) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "📝 خلاصه پایش هوش مصنوعی:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(observation.summary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Visual changes
            if (visualChangesList.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🔍 تغییرات ظاهری مشاهده‌شده:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            visualChangesList.forEach { change ->
                                Text("• $change", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Visible issues
            if (visibleIssuesList.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "⚠️ عارضه‌ها یا نشانه‌های نیازمند توجه:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            visibleIssuesList.forEach { issue ->
                                Text("• $issue", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Possible causes
            if (possibleCausesList.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "💡 علل و تفاسیر احتمالی:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            possibleCausesList.forEach { cause ->
                                Text("• $cause", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Recommended observations
            if (recommendedList.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🌱 اقدامات و بررسی‌های پیشنهادی:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            recommendedList.forEach { rec ->
                                Text("• $rec", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // User Note if available
            if (observation.userNote.isNotBlank()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "✍️ یادداشت کاربر:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(observation.userNote, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Action buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("بازگشت")
                    }
                    Button(
                        onClick = { onShareToTelegram(observation) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ارسال به تلگرام")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

private fun formatFriendlyErrorMessage(rawMessage: String): String {
    return when {
        rawMessage.contains("503") -> "خطای موقت سرور هوش مصنوعی (۵۰۳) - ترافیک بالای سرور. لطفاً مجدداً تلاش کنید."
        rawMessage.contains("429") -> "محدودیت تعداد درخواست هوش مصنوعی (۴۲۹) - لطفاً چند لحظه صبر کرده و دوباره تلاش کنید."
        rawMessage.contains("404") -> "سرویس تحلیل هوش مصنوعی یافت نشد."
        rawMessage.contains("timeout", ignoreCase = true) -> "مهلت ارتباط با سرور به پایان رسید. لطفاً اتصال اینترنت را بررسی و مجدداً امتحان کنید."
        rawMessage.isNotBlank() -> rawMessage
        else -> "تحلیل تصویر هنوز انجام نشده یا با خطا مواجه شده است."
    }
}

private fun parseJsonList(jsonString: String): List<String> {
    if (jsonString.isBlank() || jsonString == "[]") return emptyList()
    val list = mutableListOf<String>()
    try {
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val item = array.optString(i, "").trim()
            if (item.isNotEmpty() && item != "null") {
                list.add(item)
            }
        }
    } catch (_: Exception) {
        jsonString.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { list.add(it) }
    }
    return list
}
