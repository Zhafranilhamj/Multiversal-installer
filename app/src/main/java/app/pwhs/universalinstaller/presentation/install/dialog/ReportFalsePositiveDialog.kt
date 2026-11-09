package app.pwhs.universalinstaller.presentation.install.dialog

import app.pwhs.universalinstaller.R
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.core.domain.model.FalsePositiveRequest
import app.pwhs.core.network.VirusDetecService
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ReportFalsePositiveDialog(
    sha256: String,
    packageName: String,
    appName: String,
    onDismiss: () -> Unit,
    virusDetecService: VirusDetecService = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isReasonValid = reason.trim().length >= 5

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.scan_fp_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.scan_fp_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = appName.ifBlank { packageName },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (sha256.isNotBlank()) {
                            Text(
                                text = "SHA: ${sha256.take(16)}...",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.scan_fp_reason)) },
                    placeholder = { Text(stringResource(R.string.scan_fp_hint)) },
                    isError = reason.isNotBlank() && !isReasonValid,
                    supportingText = {
                        if (reason.isNotBlank() && !isReasonValid) {
                            Text(stringResource(R.string.scan_fp_minimum, reason.trim().length))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isSubmitting,
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.scan_fp_email)) },
                    placeholder = { Text(stringResource(R.string.scan_fp_email_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isReasonValid) return@Button
                    isSubmitting = true
                    errorMessage = null
                    scope.launch {
                        val req = FalsePositiveRequest(
                            sha256 = sha256,
                            packageName = packageName,
                            appName = appName,
                            reason = reason.trim(),
                            contactEmail = email.trim(),
                        )
                        virusDetecService.submitFalsePositive(req, app.pwhs.universalinstaller.domain.scanner.ScanProvider.language(
                            androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration)[0]?.toLanguageTag().orEmpty()))
                            .onSuccess { res ->
                                isSubmitting = false
                                Toast.makeText(
                                    context,
                                    res.message.ifBlank { context.getString(R.string.scan_fp_sent) },
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            }
                            .onFailure { err ->
                                isSubmitting = false
                                errorMessage = err.message ?: context.getString(R.string.scan_fp_failed)
                            }
                    }
                },
                enabled = isReasonValid && !isSubmitting,
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringResource(R.string.scan_fp_send))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
