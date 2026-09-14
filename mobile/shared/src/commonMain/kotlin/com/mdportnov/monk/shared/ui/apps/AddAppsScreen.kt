package com.mdportnov.monk.shared.ui.apps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.BlockedApp
import com.mdportnov.monk.shared.model.InstalledApp
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.platform.MonkPlatform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppsScreen(store: MonkStore, platform: MonkPlatform, onClose: () -> Unit) {
    val s = strings
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    val already = remember { store.config.value.apps.map { it.packageName }.toSet() }
    var selected by remember { mutableStateOf(already) }

    LaunchedEffect(Unit) { apps = platform.installedApps() }

    val visible = remember(apps, query) {
        val q = query.trim().lowercase()
        apps.orEmpty().filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.contains(q) }
    }
    val added = selected - already

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.addApps) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, s.cancel) } },
                actions = {
                    TextButton(
                        enabled = selected != already,
                        onClick = {
                            val byPkg = apps.orEmpty().associateBy { it.packageName }
                            store.updateConfig { c ->
                                val kept = c.apps.filter { it.packageName in selected }
                                val fresh = added.mapNotNull { pkg -> byPkg[pkg]?.let { BlockedApp(it.packageName, it.label) } }
                                c.copy(apps = kept + fresh)
                            }
                            onClose()
                        },
                    ) { Text(if (added.isEmpty()) s.done else "${s.done} (${added.size})") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(s.search) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, null) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.size(12.dp))
                        Text(s.loadingApps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(visible, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        Surface(
                            onClick = { selected = if (checked) selected - app.packageName else selected + app.packageName },
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(app.packageName, 40.dp)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Checkbox(checked = checked, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
        }
    }
}
