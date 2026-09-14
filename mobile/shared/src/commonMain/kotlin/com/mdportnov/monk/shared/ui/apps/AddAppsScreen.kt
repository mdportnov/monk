package com.mdportnov.monk.shared.ui.apps

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import com.mdportnov.monk.shared.ui.Motion
import com.mdportnov.monk.shared.ui.Motion.itemMotion
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mdportnov.monk.shared.data.MonkStore
import com.mdportnov.monk.shared.data.nowMillis
import com.mdportnov.monk.shared.i18n.strings
import com.mdportnov.monk.shared.model.InstalledApp
import com.mdportnov.monk.shared.model.SuggestedApps
import com.mdportnov.monk.shared.ui.TopBarState
import androidx.compose.runtime.SideEffect
import dev.chrisbanes.haze.HazeState
import com.mdportnov.monk.shared.ui.components.glassTopBarInset
import dev.chrisbanes.haze.hazeSource
import com.mdportnov.monk.shared.platform.AppIcon
import com.mdportnov.monk.shared.platform.MonkPlatform

@Composable
fun AddAppsScreen(store: MonkStore, platform: MonkPlatform, onClose: () -> Unit, topBar: TopBarState, hazeState: HazeState) {
    val s = strings
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val already = remember { store.config.value.apps.map { it.packageName }.toSet() }
    // Strict mode keeps every app already on the list: the picker must say so instead of a silent no-op on Done.
    val strict = remember { store.config.value.isStrict(nowMillis()) }
    var selected by rememberSaveable(saver = listSaver<MutableState<Set<String>>, String>({ it.value.toList() }, { mutableStateOf(it.toSet()) })) { mutableStateOf(already) }

    LaunchedEffect(Unit) { apps = platform.installedApps() }

    val suggested = remember(apps) { SuggestedApps.pick(apps.orEmpty()) }
    val visible = remember(apps, query, suggested) {
        val q = query.trim().lowercase()
        val picks = suggested.map { it.packageName }.toSet()
        apps.orEmpty().filter { (q.isEmpty() && it.packageName !in picks) || (q.isNotEmpty() && (it.label.lowercase().contains(q) || it.packageName.contains(q))) }
    }
    val archived = remember { store.config.value.archivedApps.map { it.packageName }.toSet() }
    val added = selected - already

    val doneEnabled = selected != already
    val commit = {
        val byPkg = apps.orEmpty().associateBy { it.packageName }
        store.applyPicker(remove = already - selected, add = added.mapNotNull { pkg -> byPkg[pkg]?.let { it.packageName to it.label } })
        onClose()
    }
    val titleText = if (added.isEmpty()) s.done else "${s.done} (${added.size})"
    SideEffect {
        topBar.set(
            title = s.addApps,
            visible = true,
            level = 10,
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, s.back) } },
            actions = { TextButton(enabled = doneEnabled, onClick = commit) { Text(titleText) } },
        )
    }
    Box(Modifier.fillMaxSize()) {
        val padding = PaddingValues(top = glassTopBarInset())
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            val keyboard = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focusManager.clearFocus() }),
                placeholder = { Text(s.search, maxLines = 1) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; keyboard?.hide(); focusManager.clearFocus() }) { Icon(Icons.Outlined.Close, s.clearSearch) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AnimatedContent(targetState = apps == null, transitionSpec = { Motion.fadeThrough() }, label = "apps") { loading ->
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.size(12.dp))
                        Text(s.loadingApps, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (strict && already.isNotEmpty()) {
                        item { Text(s.pickerStrictHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                    }
                    if (query.isBlank() && suggested.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(s.suggested, style = MaterialTheme.typography.titleSmall)
                                        Text(s.suggestedHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { selected = selected + suggested.map { it.packageName } }) { Text(s.selectSuggested) }
                                }
                            }
                        }
                        items(suggested, key = { "s:" + it.packageName }) { app ->
                            AppPickRow(app, app.packageName in selected, app.packageName in archived, enabled = !(strict && app.packageName in already)) { on -> selected = if (on) selected + app.packageName else selected - app.packageName }
                        }
                        item {
                            Text(
                                s.allApps,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(visible, key = { it.packageName }) { app ->
                        Box(itemMotion()) {
                            AppPickRow(app, app.packageName in selected, app.packageName in archived, enabled = !(strict && app.packageName in already)) { on -> selected = if (on) selected + app.packageName else selected - app.packageName }
                        }
                    }
                }
            }
            }
        }
        }
        }
    }
}

@Composable
private fun AppPickRow(app: InstalledApp, checked: Boolean, kept: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val s = strings
    Surface(
        onClick = { onChange(!checked) },
        enabled = enabled,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, 40.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (kept) s.settingsKept else app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (kept) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}
