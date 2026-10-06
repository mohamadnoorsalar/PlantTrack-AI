package com.example.ui.telegram

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramPreviewDialog(
    initialPostText: String,
    imagePath: String,
    onDismiss: () -> Unit,
    onSend: (editedText: String) -> Unit
) {
    var textContent by remember(initialPostText) { mutableStateOf(initialPostText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "پیش‌نمایش و ویرایش پست تلگرام",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (imagePath.isNotBlank() && File(imagePath).exists()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(File(imagePath)),
                            contentDescription = "تصویر گیاه",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Text(
                    text = "متن پیام قبل از انتشار در تلگرام:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 340.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(textContent) },
                enabled = textContent.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ارسال به تلگرام")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
