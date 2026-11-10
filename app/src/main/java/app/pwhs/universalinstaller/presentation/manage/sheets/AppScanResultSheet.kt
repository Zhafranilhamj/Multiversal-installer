package app.pwhs.universalinstaller.presentation.manage.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.presentation.manage.ScanUiResult
import app.pwhs.universalinstaller.ui.theme.LocalExtendedColors

/**
 * Result of scanning an *installed* app, which has no install dialog to report into.
 *
 * Before this existed the Manage action hashed the APK and opened a browser tab, so the user was
 * told nothing. Every terminal state — clean, flagged, unknown, failed, no key — has copy here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScanResultSheet(
    state: ScanUiResult,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val extendedColors = LocalExtendedColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val result = state.result
    val status = result?.status

    val accent = when (status) {
        VtStatus.CLEAN -> extendedColors.success
        VtStatus.MALICIOUS -> MaterialTheme.colorScheme.error
        VtStatus.SUSPICIOUS -> extendedColors.warning
        VtStatus.ERROR, VtStatus.INVALID_API_KEY -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val body = when {
        state.isScanning -> stringResource(R.string.scan_checking)
        status == VtStatus.CLEAN -> stringResource(R.string.scan_safe) + " — " +
            stringResource(R.string.scan_safe_detail)
        status == VtStatus.MALICIOUS -> stringResource(R.string.scan_malicious) + " — " +
            stringResource(R.string.scan_malicious_detail)
        status == VtStatus.SUSPICIOUS -> stringResource(R.string.scan_suspicious) + " — " +
            stringResource(R.string.scan_suspicious_detail)
        status == VtStatus.NOT_FOUND -> stringResource(R.string.scan_not_found)
        status == VtStatus.NO_API_KEY -> stringResource(R.string.scan_missing_key)
        status == VtStatus.INVALID_API_KEY -> stringResource(R.string.scan_invalid_key)
        status == VtStatus.RATE_LIMITED -> stringResource(R.string.scan_rate_limit)
        status == VtStatus.TOO_LARGE ->
            stringResource(R.string.apk_info_vt_too_large, result?.errorMessage.orEmpty())
        status == VtStatus.ERROR ->
            result?.errorMessage?.takeIf { it.isNotBlank() } ?: stringResource(R.string.scan_error)
        else -> stringResource(R.string.scan_error)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.appName.ifBlank { state.packageName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = result?.provider?.let { context.getString(R.string.scan_provider_result, it) }
                            ?: context.getString(R.string.scan_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (status == VtStatus.CLEAN) MaterialTheme.colorScheme.onSurface else accent,
            )

            if (!state.isScanning && status in setOf(
                    VtStatus.ERROR, VtStatus.NOT_FOUND, VtStatus.RATE_LIMITED, VtStatus.TOO_LARGE,
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.scan_upload_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = { if (state.isScanning) onDismiss() else onRetry() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.isScanning) android.R.string.cancel
                        else if (status in setOf(VtStatus.ERROR, VtStatus.NOT_FOUND, VtStatus.RATE_LIMITED)) R.string.dialog_failed_retry
                        else android.R.string.ok
                    )
                )
            }
        }
    }
}
