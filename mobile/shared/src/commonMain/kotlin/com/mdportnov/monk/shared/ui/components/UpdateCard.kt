package com.mdportnov.monk.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.platform.UpdateState
import com.mdportnov.monk.shared.platform.Updater

/** Shown on Home only while there is something to act on; Settings shows the full status. */
@Composable
fun UpdateCard(updater: Updater, compact: Boolean) {
    val s = strings
    val state by updater.state.collectAsStateWithLifecycle()
    if (compact && (state is UpdateState.Idle || state is UpdateState.Checking || state is UpdateState.UpToDate)) return
    MonkCard {
        when (val st = state) {
            UpdateState.Idle -> {
                Text(s.version(updater.currentVersion), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { updater.check(force = true) }) { Text(s.checkUpdates) }
            }
            UpdateState.Checking -> {
                Text(s.version(updater.currentVersion), style = MaterialTheme.typography.bodyMedium)
                Hint(s.updateChecking)
            }
            UpdateState.UpToDate -> {
                Text(s.version(updater.currentVersion), style = MaterialTheme.typography.bodyMedium)
                Hint(s.updateUpToDate)
                OutlinedButton(onClick = { updater.check(force = true) }) { Text(s.checkUpdates) }
            }
            is UpdateState.Available -> {
                Text(s.updateAvailable(st.version), style = MaterialTheme.typography.titleMedium)
                Hint(s.updateSize(formatMb(st.sizeBytes)))
                if (st.notes.isNotBlank()) {
                    Text(s.releaseNotes, style = MaterialTheme.typography.labelLarge)
                    Text(st.notes.trim().take(600), style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = updater::install) { Text(s.updateInstall) }
            }
            is UpdateState.Downloading -> {
                Text(s.updateAvailable(st.version), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth())
                Hint(s.updateDownloading((st.progress * 100).toInt()))
            }
            is UpdateState.Verifying -> {
                Text(s.updateAvailable(st.version), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Hint(s.updateVerifying)
            }
            is UpdateState.Installing -> {
                Text(s.updateAvailable(st.version), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Hint(s.updateInstalling)
            }
            is UpdateState.NeedsInstallPermission -> {
                Text(s.updateAvailable(st.version), style = MaterialTheme.typography.titleMedium)
                Hint(s.updateNeedsPermission)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = updater::openInstallPermission) { Text(s.updateAllow) }
                    OutlinedButton(onClick = updater::install) { Text(s.updateRetry) }
                }
            }
            is UpdateState.Failed -> {
                Text(s.updateFailed(st.reason), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (st.version != null) Button(onClick = updater::install) { Text(s.updateRetry) }
                    OutlinedButton(onClick = { updater.check(force = true) }) { Text(s.checkUpdates) }
                }
            }
        }
    }
}

private fun formatMb(bytes: Long): String {
    val tenths = (bytes * 10 / (1024 * 1024)).toInt()
    return "${tenths / 10}.${tenths % 10}"
}
