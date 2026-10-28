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
import androidx.compose.material3.Checkbox
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

private data class AppRow(val pkg: String, val label: String)

@Composable
private fun WhitelistScreen() {
    val context = LocalContext.current
    var trustSystem by remember { mutableStateOf(Whitelist.trustSystemApps(context)) }
    var trusted by remember { mutableStateOf(Whitelist.packages(context)) }
    val apps = remember { userApps(context) }

    LazyColumn {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Considera fidate le app di sistema", Modifier.weight(1f))
                Switch(checked = trustSystem, onCheckedChange = {
                    Whitelist.setTrustSystemApps(context, it)
                    trustSystem = it
                })
            }
            HorizontalDivider()
        }
        items(apps, key = { it.pkg }) { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    Text(app.pkg, style = MaterialTheme.typography.bodySmall)
                }
                Checkbox(checked = app.pkg in trusted, onCheckedChange = { on ->
                    Whitelist.setTrusted(context, app.pkg, on)
                    if (on) Alerts.stopAllFor(context, app.pkg)
                    trusted = Whitelist.packages(context)
                })
            }
        }
    }
}

private fun userApps(context: Context): List<AppRow> {
    val pm = context.packageManager
    return pm.getInstalledApplications(0)
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != context.packageName }
        .map { AppRow(it.packageName, pm.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase() }
}
