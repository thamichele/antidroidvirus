package dev.antidroidvirus

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.text.Collator
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        setContent {
            val context = LocalContext.current
            val dark = isSystemInDarkTheme()
            val colors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize()) { MainScreen() }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            tick++
        }
    }
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.safeDrawingPadding().padding(horizontal = 16.dp)) {
        Text("AntiDroidVirus", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 12.dp))
        StatusCard(tick)
        TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 12.dp)) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Registro") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Whitelist") })
        }
        when (tab) {
            0 -> EventLog(tick)
            else -> WhitelistScreen()
        }
    }
}

@Composable
private fun StatusCard(tick: Int) {
    val context = LocalContext.current
    val granted = remember(tick) { AppOpsReader.hasPermission(context) }
    val running = remember(tick) { MonitorService.running }
    val unrestricted = remember(tick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val grantCommand = "adb shell pm grant ${context.packageName} ${AppOpsReader.PERMISSION}"

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monitoraggio", Modifier.weight(1f))
                Switch(
                    checked = running,
                    enabled = granted,
                    onCheckedChange = { on ->
                        if (on) MonitorService.start(context) else MonitorService.stop(context)
                    },
                )
            }
            if (granted) {
                Text("Permesso AppOps: concesso", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "Permesso AppOps mancante. Eseguilo una volta da PC o da Shizuku (rish):",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(grantCommand, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("comando", grantCommand))
                }) { Text("Copia comando") }
            }
            val shizukuRunning = remember(tick) { Blocker.shizukuRunning() }
            val shizukuGranted = remember(tick) { Blocker.shizukuGranted() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Shizuku (per Blocca): " + when {
                        shizukuGranted -> "attivo"
                        shizukuRunning -> "da autorizzare"
                        else -> "spento"
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (shizukuRunning && !shizukuGranted) {
                    OutlinedButton(onClick = { Shizuku.requestPermission(Blocker.SHIZUKU_REQUEST_CODE) }) {
                        Text("Autorizza")
                    }
                }
            }
            if (!unrestricted) {
                Text(
                    "Batteria: metti l'app tra le \"App mai in sospensione\", altrimenti One UI può fermarla.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }) { Text("Apri impostazioni batteria") }
            }
        }
    }
}

@Composable
private fun EventLog(tick: Int) {
    val context = LocalContext.current
    val events = remember(tick) { EventStore.get(context).recent() }
    val format = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALIAN) }
    if (events.isEmpty()) {
        Text(
            "Nessun evento ancora. Alla prima lettura l'app registra solo il punto di partenza.",
            modifier = Modifier.padding(vertical = 16.dp),
        )
        return
    }
    LazyColumn {
        items(events) { event ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(Apps.label(context, event.pkg), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${AppOpsReader.describe(event.op)} · ${format.format(Date(event.time))}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (event.trusted) "fidata" else "avviso",
                    color = if (event.trusted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            HorizontalDivider()
        }
    }
}

private data class AppRow(val pkg: String, val label: String, val system: Boolean)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WhitelistScreen() {
    val context = LocalContext.current
    var trustSystem by remember { mutableStateOf(Whitelist.trustSystemApps(context)) }
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(Whitelist.entries(context)) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val allApps = remember { installedApps(context) }
    val apps = remember(query, showSystem, allApps) {
        val q = query.trim()
        allApps.filter {
            (showSystem || !it.system) &&
                (q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true))
        }
    }
    val firstIndex = remember(apps) {
        LinkedHashMap<Char, Int>().also { map ->
            apps.forEachIndexed { i, app -> map.putIfAbsent(indexLetter(app.label), i) }
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Considera fidate le app di sistema", Modifier.weight(1f))
            Switch(checked = trustSystem, onCheckedChange = {
                Whitelist.setTrustSystemApps(context, it)
                trustSystem = it
            })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Mostra app di sistema", Modifier.weight(1f))
            Switch(checked = showSystem, onCheckedChange = { showSystem = it })
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Cerca app") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 28.dp)) {
                items(apps, key = { it.pkg }) { app ->
                    val scopes = entries.filter { it.startsWith("${app.pkg}|") }.map { it.substringAfter('|') }.toSet()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = if (expanded == app.pkg) null else app.pkg }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            trustSummary(scopes),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (scopes.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        )
                        if (expanded == app.pkg) {
                            val toggle = { scopeName: String ->
                                val on = scopeName !in scopes
                                Whitelist.setTrusted(context, app.pkg, scopeName, on)
                                if (on) Alerts.dismissAll(context, app.pkg)
                                entries = Whitelist.entries(context)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                FilterChip(
                                    selected = Whitelist.ALL in scopes,
                                    onClick = { toggle(Whitelist.ALL) },
                                    label = { Text("Tutti") },
                                )
                                for (perm in Perm.entries) {
                                    FilterChip(
                                        selected = perm.name in scopes,
                                        onClick = { toggle(perm.name) },
                                        label = { Text(perm.title) },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
            LetterIndex(
                letters = firstIndex.keys.toList(),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(24.dp),
            ) { letter ->
                firstIndex[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } }
            }
        }
    }
}

/** Colonna di lettere: tocca o trascina per saltare alla prima app con quella iniziale. */
@Composable
private fun LetterIndex(letters: List<Char>, modifier: Modifier, onLetter: (Char) -> Unit) {
    if (letters.isEmpty()) return
    val currentOnLetter by rememberUpdatedState(onLetter)
    var active by remember { mutableStateOf<Char?>(null) }
    fun pick(y: Float, height: Int) {
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.size - 1)
        val letter = letters[i]
        if (letter != active) {
            active = letter
            currentOnLetter(letter)
        }
    }
    Column(
        modifier
            .pointerInput(letters) {
                detectTapGestures { offset ->
                    active = null
                    pick(offset.y, size.height)
                }
            }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> pick(offset.y, size.height) },
                    onDragEnd = { active = null },
                    onDragCancel = { active = null },
                ) { change, _ -> pick(change.position.y, size.height) }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (letter in letters) {
            Text(
                letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (letter == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun trustSummary(scopes: Set<String>): String = when {
    scopes.isEmpty() -> "Non fidata"
    Whitelist.ALL in scopes -> "Fidata per tutti i permessi"
    else -> "Fidata per: " + Perm.entries.filter { it.name in scopes }.joinToString(", ") { it.title }
}

private fun indexLetter(label: String): Char {
    val first = label.trim().firstOrNull() ?: return '#'
    val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first().uppercaseChar()
    return if (base in 'A'..'Z') base else '#'
}

private fun installedApps(context: Context): List<AppRow> {
    val pm = context.packageManager
    val collator = Collator.getInstance(Locale.ITALIAN)
    return pm.getInstalledApplications(0)
        .filter { it.packageName != context.packageName }
        .map {
            AppRow(
                it.packageName,
                pm.getApplicationLabel(it).toString(),
                it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            )
        }
        .sortedWith(compareBy(collator) { it.label })
}
