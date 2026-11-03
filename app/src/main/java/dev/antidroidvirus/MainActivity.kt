package dev.antidroidvirus

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.core.graphics.drawable.toBitmap
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.os.Environment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.text.Collator
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Hardening.protect(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = palette().background) { MainScreen() }
            }
        }
    }
}

/** Protezioni della finestra contro overlay malevoli, tapjacking e cattura schermo. */
object Hardening {
    fun protect(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) activity.window.setHideOverlayWindows(true)
        activity.window.decorView.filterTouchesWhenObscured = true
    }
}

fun Perm.icon(): ImageVector = when (this) {
    Perm.LOCATION -> Icons.Outlined.LocationOn
    Perm.CAMERA -> Icons.Outlined.PhotoCamera
    Perm.MICROPHONE -> Icons.Outlined.Mic
    Perm.CONTACTS -> Icons.Outlined.Contacts
    Perm.CALL_LOG -> Icons.Outlined.Call
    Perm.SMS -> Icons.Outlined.Sms
    Perm.CLIPBOARD -> Icons.Outlined.ContentPaste
    Perm.MEDIA -> Icons.Outlined.Photo
    Perm.PHONE -> Icons.Outlined.PhoneAndroid
    Perm.SENSORS -> Icons.Outlined.Sensors
    Perm.NEARBY -> Icons.Outlined.Bluetooth
}

private val TITLES = listOf("Stato", "Registro", "Controllo", "Permessi")

@Composable
private fun MainScreen() {
    // Aggiorna stato e registro ogni 2 secondi mentre la schermata è visibile.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            tick++
        }
    }
    var tab by remember { mutableIntStateOf(0) }
    val p = palette()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Header(TITLES[tab], tick)
            when (tab) {
                0 -> StatusScreen(tick)
                1 -> EventLog(tick)
                2 -> CheckScreen()
                else -> WhitelistScreen()
            }
        }
        NavigationBar(containerColor = p.panel, tonalElevation = 0.dp, modifier = Modifier.border(1.dp, p.border)) {
            val items = listOf(Icons.Outlined.Dashboard, Icons.Outlined.History, Icons.Outlined.Policy, Icons.Outlined.VerifiedUser)
            for (i in TITLES.indices) {
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Icon(items[i], null) },
                    label = { Text(TITLES[i], fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = p.text, selectedTextColor = p.text,
                        unselectedIconColor = p.secondary, unselectedTextColor = p.secondary,
                        indicatorColor = p.accent.copy(alpha = 0.20f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun Header(title: String, tick: Int) {
    val context = LocalContext.current
    val running = remember(tick) { MonitorService.running }
    val p = palette()
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (running) p.ok else p.warn))
                Text(
                    if (running) "AntiDroidVirus · attivo" else "AntiDroidVirus · spento",
                    fontSize = 13.sp, color = p.secondary, modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = p.text, letterSpacing = (-0.5).sp)
        }
        var about by remember { mutableStateOf(false) }
        IconButton(onClick = { about = true }, modifier = Modifier.padding(end = 4.dp)) {
            Icon(Icons.Outlined.Info, "Informazioni", tint = p.secondary)
        }
        if (about) AboutDialog { about = false }
        // Kill switch: un tocco e l'app si spegne del tutto.
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(p.error.copy(alpha = 0.16f))
                .clickable {
                    (context as? Activity)?.finishAndRemoveTask()
                    KillSwitch.engage(context)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PowerSettingsNew, "Spegni tutto", tint = p.error)
        }
    }
}

/** Finestra "Informazioni": nome, versione, cosa fa l'app e dove trovarla. */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val p = palette()
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(context, "Nessun browser disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.panel,
        titleContentColor = p.text,
        textContentColor = p.text,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi", color = p.accent, fontWeight = FontWeight.SemiBold) }
        },
        icon = {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(p.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Shield, null, tint = p.accent, modifier = Modifier.size(28.dp)) }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("AntiDroidVirus", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                Text("Versione $version", fontSize = 13.sp, color = p.secondary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Registra quali app usano posizione, fotocamera, microfono, contatti, SMS e altri permessi, " +
                        "avvisa per le app fuori whitelist o con comportamenti insoliti, e controlla file scaricati " +
                        "e app appena installate, anche nel profilo di lavoro.",
                    fontSize = 14.sp, color = p.text,
                )
                Text(
                    "Uso personale, senza Play Store. Nessun accesso a internet: tutte le analisi avvengono sul telefono. " +
                        "Permessi avanzati concessi tramite Shizuku.",
                    fontSize = 13.sp, color = p.secondary,
                )
                AboutLink("Codice sorgente", "github.com/thamichele/antidroidvirus") {
                    open("https://github.com/thamichele/antidroidvirus")
                }
                AboutLink("Aggiornamenti (repository F-Droid)", "thamichele.github.io/antidroidvirus/repo") {
                    open("https://thamichele.github.io/antidroidvirus/repo")
                }
                AboutLink("Shizuku", "shizuku.rikka.app") { open("https://shizuku.rikka.app/") }
                Text("Di Michele (thamichele)", fontSize = 12.sp, color = p.secondary)
            }
        },
    )
}

@Composable
private fun AboutLink(title: String, url: String, onClick: () -> Unit) {
    val p = palette()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(p.raised)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = p.text)
            Text(url, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = p.secondary)
        }
        Icon(Icons.Outlined.OpenInNew, null, tint = p.secondary, modifier = Modifier.size(16.dp))
    }
}

private enum class Level { OK, WARN, ERROR }

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = palette()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.panel)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
    ) { content() }
}

@Composable
private fun StatusScreen(tick: Int) {
    val context = LocalContext.current
    val p = palette()
    val granted = remember(tick) { AppOpsReader.hasPermission(context) }
    val running = remember(tick) { MonitorService.running }
    val shizukuRunning = remember(tick) { Blocker.shizukuRunning() }
    val shizukuGranted = remember(tick) { Blocker.shizukuGranted() }
    val unrestricted = remember(tick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val today = remember(tick) {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        EventStore.get(context).recent().filter { it.time >= start }
    }
    val grantCommand = "adb shell pm grant ${context.packageName} ${AppOpsReader.PERMISSION}"
    val usageAccess = remember(tick) { RiskCheck.hasUsageAccess(context) }
    val usageCommand = "adb shell appops set ${context.packageName} GET_USAGE_STATS allow"
    val missing = remember(tick) { Privileges.missing(context) }
    var granting by remember { mutableStateOf(false) }
    var grantResult by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Usi oggi", today.size.toString(), "registrati", p.text, Modifier.weight(1f))
            StatCard(
                "Avvisi oggi", today.count { !it.trusted }.toString(), "app fuori whitelist",
                if (today.any { !it.trusted }) p.error else p.text, Modifier.weight(1f),
            )
        }
        if (missing && shizukuRunning) {
            Panel {
                StatusRow(
                    Icons.Outlined.AdminPanelSettings, "Permessi senza PC",
                    when {
                        granting -> "Concessione in corso…"
                        grantResult != null -> grantResult!!
                        shizukuGranted -> "Shizuku può concedere tutto in un tocco"
                        else -> "Autorizza Shizuku, poi concedi tutto"
                    },
                    if (grantResult != null) Level.ERROR else Level.WARN,
                ) {
                    TextButton(
                        enabled = !granting,
                        onClick = {
                            if (!shizukuGranted) {
                                Shizuku.requestPermission(Blocker.SHIZUKU_REQUEST_CODE)
                            } else {
                                granting = true
                                Thread {
                                    val failed = Privileges.grantAll(context)
                                    (context as? Activity)?.runOnUiThread {
                                        granting = false
                                        grantResult = if (failed.isEmpty()) null else "Non concessi: ${failed.joinToString()}"
                                        // Il servizio riparte per usare subito i nuovi permessi (es. il controllo dei download).
                                        MonitorService.restart(context)
                                    }
                                }.start()
                            }
                        },
                    ) {
                        Text(if (shizukuGranted) "Concedi" else "Autorizza", color = p.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Panel {
            StatusRow(
                Icons.Outlined.Shield, "Monitoraggio",
                if (running) "Attivo" else "Spento",
                if (running) Level.OK else Level.WARN,
            ) {
                Switch(
                    checked = running,
                    enabled = granted,
                    onCheckedChange = { on -> if (on) MonitorService.start(context) else MonitorService.stop(context) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = p.accent, checkedThumbColor = p.background,
                        uncheckedTrackColor = p.raised, uncheckedBorderColor = p.border, uncheckedThumbColor = p.secondary,
                    ),
                )
            }
            Divider()
            StatusRow(
                Icons.Outlined.Key, "Permesso AppOps",
                if (granted) "Concesso" else "Mancante: usa Shizuku o il comando ADB",
                if (granted) Level.OK else Level.ERROR,
            ) {
                if (!granted) {
                    IconButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("comando", grantCommand))
                    }) { Icon(Icons.Outlined.ContentCopy, "Copia comando", tint = p.text) }
                }
            }
            if (!granted) {
                Text(
                    grantCommand,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = p.secondary,
                    modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 10.dp),
                )
            }
            Divider()
            StatusRow(
                Icons.Outlined.Insights, "Dati di utilizzo",
                if (usageAccess) "Concessi: traffico per app attivo" else "Mancanti: niente traffico per app",
                if (usageAccess) Level.OK else Level.WARN,
            ) {
                if (!usageAccess) {
                    IconButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("comando", usageCommand))
                    }) { Icon(Icons.Outlined.ContentCopy, "Copia comando", tint = p.text) }
                }
            }
            if (!usageAccess) {
                Text(
                    usageCommand,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = p.secondary,
                    modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 10.dp),
                )
            }
            Divider()
            StatusRow(
                Icons.Outlined.AdminPanelSettings, "Shizuku per Blocca",
                when {
                    shizukuGranted -> "Attivo"
                    shizukuRunning -> "Da autorizzare"
                    else -> "Spento: Blocca apre le impostazioni"
                },
                if (shizukuGranted) Level.OK else Level.WARN,
            ) {
                if (shizukuRunning && !shizukuGranted) {
                    TextButton(onClick = { Shizuku.requestPermission(Blocker.SHIZUKU_REQUEST_CODE) }) {
                        Text("Autorizza", color = p.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Divider()
            StatusRow(
                Icons.Outlined.BatteryAlert, "Batteria",
                if (unrestricted) "Senza restrizioni" else "One UI può fermare il servizio",
                if (unrestricted) Level.OK else Level.WARN,
            ) {
                if (!unrestricted) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }) { Text("Apri", color = p.accent, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        ProtectionPanel(tick)
        Text(
            "Il pulsante rosso in alto spegne subito tutto: servizio, notifiche e avvio automatico.",
            fontSize = 13.sp, color = p.secondary,
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = palette().border)

@Composable
private fun StatCard(title: String, value: String, caption: String, valueColor: Color, modifier: Modifier) {
    val p = palette()
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.panel)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(title, fontSize = 13.sp, color = p.secondary)
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
        Text(caption, fontSize = 12.sp, color = p.secondary)
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    title: String,
    detail: String,
    level: Level,
    trailing: @Composable () -> Unit,
) {
    val p = palette()
    val tint = when (level) {
        Level.OK -> p.ok
        Level.WARN -> p.warn
        Level.ERROR -> p.error
    }
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.text)
            Text(detail, fontSize = 13.sp, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Pillola del kit: piena quando selezionata, contornata altrimenti. */
@Composable
private fun Pill(text: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = palette()
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) p.text else p.panel)
            .border(1.dp, if (selected) p.text else p.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) p.background else p.secondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) p.background else p.text,
        )
    }
}

@Composable
private fun PermBadge(perm: Perm, tint: Color) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(perm.icon(), perm.title, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EventLog(tick: Int) {
    val context = LocalContext.current
    val p = palette()
    var onlyAlerts by remember { mutableStateOf(false) }
    val events = remember(tick, onlyAlerts) {
        EventStore.get(context).recent().filter { !onlyAlerts || !it.trusted }
    }
    val time = remember { SimpleDateFormat("HH:mm", Locale.ITALIAN) }
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(HistoryExport.MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val rows = withContext(Dispatchers.IO) { HistoryExport.write(context, uri) }
            Toast.makeText(
                context, if (rows < 0) "Esportazione non riuscita" else "Esportati $rows eventi", Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill("Tutti", !onlyAlerts) { onlyAlerts = false }
        Pill("Solo avvisi", onlyAlerts) { onlyAlerts = true }
        Spacer(Modifier.weight(1f))
        // Tutto lo storico (30 giorni) in CSV, non solo gli eventi mostrati.
        Pill("Esporta", false, Icons.Outlined.Download) { export.launch(HistoryExport.fileName()) }
    }
    if (events.isEmpty()) {
        Text(
            "Nessun evento. Alla prima lettura l'app registra solo il punto di partenza.",
            fontSize = 15.sp, color = p.secondary,
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        var lastDay = ""
        for (event in events) {
            val day = dayLabel(event.time)
            if (day != lastDay) {
                lastDay = day
                item(key = "day-$day") {
                    Text(
                        day.replaceFirstChar { it.uppercase() },
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.secondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item {
                val perm = Perm.fromOp(event.op) ?: Perm.LOCATION
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.panel)
                        .border(1.dp, p.border, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PermBadge(perm, if (event.trusted) p.secondary else p.error)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            Alerts.labelOf(context, event.pkg, event.user), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val where = Monitor.describeFlags(event.flags)
                        Text(
                            "${perm.title} · ${time.format(Date(event.time))}" + if (where.isEmpty()) "" else " · $where",
                            fontSize = 13.sp, color = if (where.isEmpty()) p.secondary else p.warn,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Tag(if (event.trusted) "fidata" else "avviso", if (event.trusted) p.ok else p.error)
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private fun dayLabel(time: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = time }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun same(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        same(cal, today) -> "Oggi"
        same(cal, yesterday) -> "Ieri"
        else -> SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(Date(time))
    }
}

/** In quali profili è installata. La fiducia vale per il pacchetto in tutti i profili. */
private data class AppRow(
    val pkg: String,
    val label: String,
    val system: Boolean,
    val main: Boolean,
    val work: Boolean,
    /** Utente del profilo di lavoro, se l'app c'è. */
    val workUser: Int? = null,
) {
    val where: String get() = when {
        main && work -> "entrambi"
        work -> "lavoro"
        else -> "principale"
    }
}

private enum class PermPage { APPS, VENDORS, REVOKED }

/** Scheda Permessi: elenco delle app con i sottomenu Produttori e Revocati. */
@Composable
private fun WhitelistScreen() {
    var page by remember { mutableStateOf(PermPage.APPS) }
    BackHandler(enabled = page != PermPage.APPS) { page = PermPage.APPS }
    when (page) {
        PermPage.APPS -> AppPermissions { page = it }
        PermPage.VENDORS -> SubPage("Produttori", onBack = { page = PermPage.APPS }) { VendorList() }
        PermPage.REVOKED -> SubPage("Revocati", onBack = { page = PermPage.APPS }) { RevokedList() }
    }
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val p = palette()
    Column {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Indietro", tint = p.accent, modifier = Modifier.size(20.dp))
            Text(
                "Permessi › $title", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.accent,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        content()
    }
}

/** Permessi sorvegliati che Android ha concesso, dai nomi dei permessi runtime. */
private fun permsOf(granted: Set<String>?): Set<Perm> =
    granted?.let { set -> Perm.entries.filter { perm -> perm.permissions.any { it in set } }.toSet() } ?: emptySet()

/**
 * Permessi concessi da Android a ogni app: profilo principale da PackageManager, app solo nel profilo di lavoro
 * via Shizuku. Va chiamato fuori dal main thread.
 */
private fun grantedByApp(context: Context, apps: List<AppRow>): Map<String, Set<String>> {
    val result = HashMap<String, Set<String>>()
    for (app in apps) {
        val user = if (app.main) Profiles.myUserId() else app.workUser ?: continue
        Profiles.grantedPermissions(context, app.pkg, user)?.let { result[app.pkg] = it }
    }
    return result
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppPermissions(onOpen: (PermPage) -> Unit) {
    val context = LocalContext.current
    val p = palette()
    var trustSystem by remember { mutableStateOf(Whitelist.trustSystemApps(context)) }
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(Whitelist.entries(context)) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var help by remember { mutableStateOf(false) }
    var importDialog by remember { mutableStateOf(false) }
    var allApps by remember { mutableStateOf(installedApps(context)) }
    var granted by remember { mutableStateOf<Map<String, Set<String>>?>(null) }
    LaunchedEffect(Unit) {
        // Le app del profilo di lavoro e i loro permessi si leggono via Shizuku: fuori dal main thread.
        val apps = withContext(Dispatchers.IO) { installedApps(context, Profiles.apps(context, includeSystem = true)) }
        allApps = apps
        granted = withContext(Dispatchers.IO) { grantedByApp(context, apps) }
    }
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

    if (importDialog) {
        AlertDialog(
            onDismissRequest = { importDialog = false },
            containerColor = p.panel, titleContentColor = p.text, textContentColor = p.text,
            title = { Text("Importa da Android") },
            text = {
                Text(
                    "Ogni app diventa fidata sui permessi che Android le ha già concesso (posizione, fotocamera, " +
                        "contatti…). Non toglie niente e non cambia i permessi del telefono: dopo scorri la lista e correggi.",
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    importDialog = false
                    scope.launch {
                        val map = granted ?: withContext(Dispatchers.IO) { grantedByApp(context, allApps) }
                        granted = map
                        val added = withContext(Dispatchers.IO) { Whitelist.importGranted(context, map) }
                        entries = Whitelist.entries(context)
                        Toast.makeText(context, "Aggiunti $added permessi fidati", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Importa", color = p.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { importDialog = false }) { Text("Annulla", color = p.secondary) } },
        )
    }

    // Pulsanti, interruttore e ricerca scorrono insieme alla lista: tutto lo schermo resta per le app.
    // L'indice delle lettere compare quando l'intestazione è uscita dallo schermo, per non coprirla.
    val headerGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Column {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 10.dp),
                    ) {
                        Pill("Produttori", false, Icons.Outlined.Factory) { onOpen(PermPage.VENDORS) }
                        Pill("Revocati", false, Icons.Outlined.Restore) { onOpen(PermPage.REVOKED) }
                        Pill("Importa da Android", false, Icons.Outlined.Input) { importDialog = true }
                        Pill("Come funziona", help, Icons.Outlined.Lightbulb) { help = !help }
                    }
                    if (help) PermissionsHelp()
                    // Impostazione, non filtro: cambia quali avvisi arrivano.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.panel)
                            .border(1.dp, p.border, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Fidati delle app di sistema", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.text)
                            Text(
                                if (trustSystem) "Le app preinstallate non danno avvisi sui permessi"
                                else "Anche le app preinstallate danno avvisi",
                                fontSize = 12.sp, color = p.secondary,
                            )
                        }
                        Switch(
                            checked = trustSystem,
                            onCheckedChange = { on ->
                                Whitelist.setTrustSystemApps(context, on)
                                trustSystem = on
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = p.accent, checkedThumbColor = p.background,
                                uncheckedTrackColor = p.raised, uncheckedBorderColor = p.border, uncheckedThumbColor = p.secondary,
                            ),
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Cerca app", color = p.secondary) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = p.secondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = p.panel, unfocusedContainerColor = p.panel,
                            focusedBorderColor = p.accent, unfocusedBorderColor = p.border,
                            focusedTextColor = p.text, unfocusedTextColor = p.text, cursorColor = p.accent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Mostra:", fontSize = 13.sp, color = p.secondary)
                        Pill("Anche app di sistema", showSystem) { showSystem = !showSystem }
                    }
                }
            }
            items(apps, key = { it.pkg }) { app ->
                val scopes = entries.filter { it.startsWith("${app.pkg}|") }.map { it.substringAfter('|') }.toSet()
                val androidPerms = permsOf(granted?.get(app.pkg))
                val systemTrusted = trustSystem && app.system
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = if (headerGone) 28.dp else 0.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.panel)
                        .border(1.dp, if (expanded == app.pkg) p.accent else p.border, RoundedCornerShape(12.dp))
                        .clickable { expanded = if (expanded == app.pkg) null else app.pkg }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.pkg, Modifier.size(36.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                app.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            PermIcons(androidPerms, scopes, systemTrusted, granted != null)
                        }
                        Tag(app.where, if (app.work) p.accent else p.secondary)
                    }
                    if (expanded == app.pkg) {
                        Text(
                            app.pkg, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = p.secondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            if (androidPerms.isEmpty()) "Android non le ha concesso nessuno dei permessi sorvegliati."
                            else "Concessi da Android: " + androidPerms.joinToString(", ") { it.title },
                            fontSize = 12.sp, color = p.secondary,
                        )
                        if (systemTrusted) {
                            Text(
                                "App di sistema: già fidata dall'interruttore in alto.",
                                fontSize = 12.sp, color = p.ok,
                            )
                        }
                        if (app.main && app.work) {
                            Text(
                                "Installata in entrambi i profili: la fiducia vale per tutte e due le copie.",
                                fontSize = 12.sp, color = p.secondary,
                            )
                        }
                        val toggle = { scopeName: String ->
                            val on = scopeName !in scopes
                            Whitelist.setTrusted(context, app.pkg, scopeName, on)
                            if (on) Alerts.dismissAll(context, app.pkg)
                            entries = Whitelist.entries(context)
                        }
                        Text(
                            "Fidata per:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = p.text,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        ScopePills(scopes, Modifier.padding(top = 6.dp), toggle)
                    }
                }
            }
        }
        if (headerGone) {
            LetterIndex(
                letters = firstIndex.keys.toList(),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(24.dp),
            ) { letter ->
                // +1: il primo elemento della lista è l'intestazione.
                firstIndex[letter]?.let { index -> scope.launch { listState.scrollToItem(index + 1) } }
            }
        }
    }
}

/** Pillole degli ambiti di fiducia, uguali per app e produttori. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScopePills(scopes: Set<String>, modifier: Modifier, toggle: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Pill("Tutti", Whitelist.ALL in scopes, Icons.Outlined.VerifiedUser) { toggle(Whitelist.ALL) }
        for (perm in Perm.entries) {
            Pill(perm.title, perm.name in scopes, perm.icon()) { toggle(perm.name) }
        }
        Pill("Comportamenti insoliti", Whitelist.ANOMALY in scopes, Icons.Outlined.NightsStay) { toggle(Whitelist.ANOMALY) }
        Pill("Controllo", Whitelist.CHECKS in scopes, Icons.Outlined.Policy) { toggle(Whitelist.CHECKS) }
    }
}

/** Legenda dei tasti della scheda Permessi. */
@Composable
private fun PermissionsHelp() {
    val p = palette()
    val lines = listOf(
        "Fidati delle app di sistema" to "interruttore: acceso, le app preinstallate (Samsung, Google, Android) non danno avvisi sui permessi.",
        "Anche app di sistema" to "solo un filtro: le mostra nella lista, non cambia gli avvisi.",
        "Icone sotto il nome" to "i permessi che Android ha concesso all'app. Verde: è fidata, nessun avviso. Arancione: concesso ma non fidato, l'uso dà un avviso.",
        "Tocca un'app" to "scegli per cosa è fidata. Tutti: ogni permesso. Un permesso: solo quello. Comportamenti insoliti: niente avvisi per usi di notte o troppo frequenti. Controllo: esclusa dal punteggio di rischio.",
        "Importa da Android" to "rende fidata ogni app sui permessi che le hai già concesso. Poi correggi a mano.",
        "Produttori" to "le app raggruppate per chi le firma (Samsung, Google…): fidati di tutte in un colpo.",
        "Revocati" to "i permessi tolti con Blocca, da ripristinare se un'app smette di funzionare.",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.panel)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((title, text) in lines) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = p.text)) { append(title) }
                    append(": $text")
                },
                fontSize = 13.sp, color = p.secondary,
            )
        }
    }
}

/**
 * Icone dei permessi concessi da Android: verdi se fidati, arancioni se danno avvisi. Uno scudo se l'app è fidata
 * su tutto. Finché i permessi non sono letti si vedono solo quelli fidati.
 */
@Composable
private fun PermIcons(androidPerms: Set<Perm>, scopes: Set<String>, systemTrusted: Boolean, loaded: Boolean) {
    val p = palette()
    val all = Whitelist.ALL in scopes || systemTrusted
    val shown = if (loaded) androidPerms else Perm.entries.filter { it.name in scopes }.toSet()
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
        if (all) Icon(Icons.Outlined.VerifiedUser, "Fidata su tutto", tint = p.ok, modifier = Modifier.size(16.dp))
        for (perm in Perm.entries) {
            if (perm !in shown) continue
            val trusted = all || perm.name in scopes
            Icon(perm.icon(), perm.title, tint = if (trusted) p.ok else p.warn, modifier = Modifier.size(16.dp))
        }
        if (loaded && shown.isEmpty() && !all) Text("nessun permesso sorvegliato", fontSize = 12.sp, color = p.secondary)
    }
}

private val iconCache = LruCache<String, ImageBitmap>(400)

/** Icona dell'app, caricata fuori dal main thread e tenuta in memoria. */
@Composable
private fun AppIcon(pkg: String, modifier: Modifier) {
    val context = LocalContext.current
    val p = palette()
    val bitmap by produceState<ImageBitmap?>(iconCache.get(pkg), pkg) {
        if (value == null) value = withContext(Dispatchers.IO) { loadIcon(context, pkg) }
    }
    val image = bitmap
    if (image != null) {
        Image(image, null, modifier.clip(RoundedCornerShape(8.dp)))
    } else {
        Box(modifier.clip(RoundedCornerShape(8.dp)).background(p.raised))
    }
}

private fun loadIcon(context: Context, pkg: String): ImageBitmap? = try {
    val pm = context.packageManager
    val info = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
    val px = (36 * context.resources.displayMetrics.density).toInt()
    pm.getApplicationIcon(info).toBitmap(px, px).asImageBitmap().also { iconCache.put(pkg, it) }
} catch (e: Exception) {
    null
}

/** Sottomenu Produttori: le app raggruppate per chiave di firma, con la fiducia per tutto il gruppo. */
@Composable
private fun VendorList() {
    val context = LocalContext.current
    val p = palette()
    var vendors by remember { mutableStateOf<List<Vendor>?>(null) }
    var entries by remember { mutableStateOf(Whitelist.entries(context)) }
    var expanded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        vendors = withContext(Dispatchers.IO) {
            val rows = installedApps(context, Profiles.apps(context, includeSystem = true))
            Vendors.group(context, rows.map { VendorApp(it.pkg, it.label, it.system) })
        }
    }
    val list = vendors
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                "App raggruppate per chi le ha firmate. Fidarti di un produttore vale per tutte le sue app, anche quelle che " +
                    "installerai poi con la stessa firma. Un'app che si spaccia per un produttore senza averne la firma finisce in un gruppo a parte.",
                fontSize = 13.sp, color = p.secondary,
            )
            if (list == null) Text("Lettura delle firme…", fontSize = 15.sp, color = p.secondary)
        }
        items(list.orEmpty(), key = { it.name }) { vendor ->
            val scopes = Whitelist.vendorScopes(entries, vendor)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.panel)
                    .border(1.dp, if (expanded == vendor.name) p.accent else p.border, RoundedCornerShape(12.dp))
                    .clickable { expanded = if (expanded == vendor.name) null else vendor.name }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(vendor.apps.first().pkg, Modifier.size(36.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            vendor.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val keys = if (vendor.keys.size == 1) "1 firma" else "${vendor.keys.size} firme"
                        Text(
                            "${vendor.apps.size} app · ${vendor.systemCount} di sistema · $keys",
                            fontSize = 12.sp, color = p.secondary,
                        )
                    }
                    PermIcons(emptySet(), scopes, systemTrusted = false, loaded = false)
                }
                if (expanded == vendor.name) {
                    val names = vendor.apps.take(15).joinToString(", ") { it.label }
                    val more = vendor.apps.size - 15
                    Text(
                        names + if (more > 0) " e altre $more" else "",
                        fontSize = 12.sp, color = p.secondary, modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Firma " + vendor.keys.joinToString(", ") { it.take(16) },
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = p.secondary,
                    )
                    Text(
                        "Tutte le app fidate per:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = p.text,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    ScopePills(scopes, Modifier.padding(top = 6.dp)) { scopeName ->
                        val on = scopeName !in scopes
                        Whitelist.setVendorTrusted(context, vendor, scopeName, on)
                        if (on) vendor.apps.forEach { Alerts.dismissAll(context, it.pkg) }
                        entries = Whitelist.entries(context)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListCard(content: @Composable () -> Unit) {
    val p = palette()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.panel)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) { content() }
}

/** Nome leggibile di un permesso Android o di un'op AppOps sorvegliati. */
private fun permissionTitle(name: String): String = when {
    name == Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Posizione sempre"
    else -> Perm.entries.firstOrNull { name in it.permissions || name in it.appOps }?.title ?: name.substringAfterLast('.')
}

/** Sottomenu Revocati: annulla le revoche e ridà ai servizi critici i permessi che mancano. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevokedList() {
    val context = LocalContext.current
    val p = palette()
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf(RevokeLog.all(context)) }
    var plan by remember { mutableStateOf<List<Repair>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val shizuku = remember { Blocker.shizukuGranted() }
    val date = remember { SimpleDateFormat("d MMM HH:mm", Locale.ITALIAN) }

    fun act(work: () -> Boolean, done: String, after: () -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { work() }
            Toast.makeText(context, if (ok) done else "Non riuscito: Shizuku è attivo?", Toast.LENGTH_SHORT).show()
            if (ok) after()
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                "Permessi tolti con il tasto Blocca. Se un'app ha smesso di funzionare (sessione scaduta, account da " +
                    "ricollegare, store che non aggiorna) ripristinali da qui.",
                fontSize = 13.sp, color = p.secondary,
            )
            if (!shizuku) Text("Serve Shizuku attivo per ripristinare.", fontSize = 13.sp, color = p.warn)
        }
        if (log.isEmpty()) {
            item {
                Text(
                    "Nessuna revoca registrata. Quelle fatte con le versioni precedenti non sono qui: usa il controllo qui sotto.",
                    fontSize = 14.sp, color = p.secondary,
                )
            }
        }
        items(log, key = { "${it.time}|${it.pkg}|${it.user}|${it.perm}" }) { r ->
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(r.pkg, Modifier.size(36.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            Alerts.labelOf(context, r.pkg, r.user), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            (r.permissions + r.appOps).map(::permissionTitle).distinct().joinToString(", ") +
                                " · " + date.format(Date(r.time)),
                            fontSize = 12.sp, color = p.secondary,
                        )
                    }
                    TextButton(enabled = shizuku, onClick = {
                        act({ RevokeLog.restore(context, r) }, "Ripristinato") { log = RevokeLog.all(context) }
                    }) { Text("Ripristina", color = if (shizuku) p.accent else p.secondary, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        item {
            Text(
                "Servizi Google e Samsung", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Cerca tra i servizi da cui dipendono account, store e telefono quelli a cui manca un permesso sorvegliato. " +
                    "Tocca un permesso per ridarlo solo a quel servizio.",
                fontSize = 13.sp, color = p.secondary, modifier = Modifier.padding(vertical = 6.dp),
            )
            Pill(if (searching) "Controllo in corso…" else "Controlla", false, Icons.Outlined.Search) {
                if (!shizuku || searching) return@Pill
                searching = true
                scope.launch {
                    plan = withContext(Dispatchers.IO) { RevokeLog.repairPlan(context) }
                    searching = false
                }
            }
        }
        val repairs = plan
        if (repairs != null && repairs.isEmpty()) {
            item { Text("Nessun permesso mancante.", fontSize = 14.sp, color = p.ok) }
        }
        items(repairs.orEmpty(), key = { "${it.pkg}@${it.user}" }) { repair ->
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(repair.pkg, Modifier.size(36.dp))
                    Text(
                        Alerts.labelOf(context, repair.pkg, repair.user), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    for (permission in repair.permissions) {
                        Pill(permissionTitle(permission), false, Icons.Outlined.Restore) {
                            val single = Repair(repair.pkg, repair.user, listOf(permission), emptyList())
                            act({ RevokeLog.apply(single) }, "Ridato a ${Apps.label(context, repair.pkg)}") {
                                plan = plan?.mapNotNull {
                                    if (it != repair) it
                                    else it.copy(permissions = it.permissions - permission).takeIf { r -> r.permissions.isNotEmpty() || r.appOps.isNotEmpty() }
                                }
                            }
                        }
                    }
                    for (op in repair.appOps) {
                        Pill(permissionTitle(op), false, Icons.Outlined.Restore) {
                            val single = Repair(repair.pkg, repair.user, emptyList(), listOf(op))
                            act({ RevokeLog.apply(single) }, "Ridato a ${Apps.label(context, repair.pkg)}") {
                                plan = plan?.mapNotNull {
                                    if (it != repair) it
                                    else it.copy(appOps = it.appOps - op).takeIf { r -> r.permissions.isNotEmpty() || r.appOps.isNotEmpty() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Colonna di lettere: tocca o trascina per saltare alla prima app con quella iniziale. */
@Composable
private fun LetterIndex(letters: List<Char>, modifier: Modifier, onLetter: (Char) -> Unit) {
    if (letters.isEmpty()) return
    val p = palette()
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
                fontSize = 11.sp,
                fontWeight = if (letter == active) FontWeight.Bold else FontWeight.Medium,
                color = if (letter == active) p.accent else p.secondary,
            )
        }
    }
}

private fun indexLetter(label: String): Char {
    val first = label.trim().firstOrNull() ?: return '#'
    val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).first().uppercaseChar()
    return if (base in 'A'..'Z') base else '#'
}

/** App del profilo principale e, se date, quelle degli altri profili; le copie doppie sono una riga sola. */
private fun installedApps(context: Context, profile: List<ProfileApp> = emptyList()): List<AppRow> {
    val pm = context.packageManager
    val collator = Collator.getInstance(Locale.ITALIAN)
    val work = profile.associate { it.pkg to it.userId }
    val rows = LinkedHashMap<String, AppRow>()
    for (it in pm.getInstalledApplications(0)) {
        if (it.packageName == context.packageName) continue
        rows[it.packageName] = AppRow(
            it.packageName,
            pm.getApplicationLabel(it).toString(),
            it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            main = true,
            work = it.packageName in work,
            workUser = work[it.packageName],
        )
    }
    for (app in profile) {
        if (app.pkg == context.packageName || app.pkg in rows) continue
        rows[app.pkg] = AppRow(app.pkg, Apps.label(context, app.pkg), app.system, main = false, work = true, workUser = app.userId)
    }
    return rows.values.sortedWith(compareBy(collator) { it.label })
}

/** Pagina Controllo: app installate dall'utente (anche nel profilo di lavoro) ordinate per rischio, con i motivi. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckScreen() {
    val context = LocalContext.current
    val p = palette()
    var reports by remember { mutableStateOf(RiskCheck.lastReports) }
    var lastRun by remember { mutableStateOf(RiskCheck.lastRun) }
    var running by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(Whitelist.entries(context)) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var showFiles by remember { mutableStateOf(false) }

    fun analyze() {
        if (running) return
        running = true
        Thread {
            val result = try {
                RiskCheck.run(context)
            } catch (e: Exception) {
                RiskCheck.lastReports
            }
            (context as? Activity)?.runOnUiThread {
                reports = result
                lastRun = RiskCheck.lastRun
                running = false
            }
        }.start()
    }
    LaunchedEffect(Unit) { if (lastRun == 0L) analyze() }

    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("App", !showFiles, Icons.Outlined.Policy) { showFiles = false }
        Pill("File scaricati", showFiles, Icons.Outlined.Download) { showFiles = true }
    }
    if (showFiles) {
        FileList()
        return
    }

    val visible = reports.filter { report ->
        val ignored = "${report.pkg}|${Whitelist.CHECKS}" in entries
        if (showAll) true else !ignored && report.level != RiskLevel.LOW
    }
    val time = remember { SimpleDateFormat("HH:mm", Locale.ITALIAN) }

    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill("Da guardare", !showAll) { showAll = false }
        Pill("Tutte", showAll) { showAll = true }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { analyze() }, enabled = !running) {
            Text(if (running) "Analisi…" else "Analizza", color = p.accent, fontWeight = FontWeight.SemiBold)
        }
    }
    if (lastRun > 0) {
        Text(
            "Ultima analisi alle ${time.format(Date(lastRun))} · ${reports.count { it.level == RiskLevel.HIGH }} da controllare",
            fontSize = 13.sp, color = p.secondary, modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    if (visible.isEmpty()) {
        Text(
            if (running) "Analisi delle app in corso…" else "Nessuna app con indizi rilevanti.",
            fontSize = 15.sp, color = p.secondary,
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(visible, key = { it.key }) { report ->
            val color = when (report.level) {
                RiskLevel.HIGH -> p.error
                RiskLevel.MEDIUM -> p.warn
                RiskLevel.LOW -> p.secondary
            }
            val ignored = "${report.pkg}|${Whitelist.CHECKS}" in entries
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.panel)
                    .border(1.dp, if (expanded == report.key) p.accent else p.border, RoundedCornerShape(12.dp))
                    .clickable { expanded = if (expanded == report.key) null else report.key }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(report.score.toString(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            Apps.label(context, report.pkg), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            report.findings.first().text, fontSize = 13.sp, color = p.secondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Tag(
                        when {
                            report.profileUser == null -> "principale"
                            report.frozen -> "lavoro · congelata"
                            else -> "lavoro"
                        },
                        if (report.profileUser == null) p.secondary else p.accent,
                    )
                    Spacer(Modifier.width(6.dp))
                    Tag(if (ignored) "va bene" else report.level.title, if (ignored) p.ok else color)
                }
                if (expanded == report.key) {
                    Text(
                        report.pkg, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = p.secondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    for (finding in report.findings) {
                        Text("• ${finding.text}", fontSize = 13.sp, color = p.text, modifier = Modifier.padding(top = 4.dp))
                    }
                    // Permessi concessi a questa copia: le copie nei due profili possono averne di diversi.
                    val granted = report.granted
                    Text(
                        when {
                            granted == null -> "Permessi concessi: serve Shizuku per leggerli nel profilo di lavoro"
                            granted.isEmpty() -> "Nessun permesso sorvegliato concesso"
                            else -> "Permessi concessi: ${granted.joinToString(", ")}"
                        },
                        fontSize = 13.sp, color = p.secondary, modifier = Modifier.padding(top = 8.dp),
                    )
                    FlowRow(
                        Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Pill("Va bene così", ignored, Icons.Outlined.VerifiedUser) {
                            Whitelist.setTrusted(context, report.pkg, Whitelist.CHECKS, !ignored)
                            if (!ignored) {
                                RiskCheck.forgetSigner(context, report.pkg)
                                Alerts.dismiss(context, Alerts.checkId(report.key))
                            }
                            entries = Whitelist.entries(context)
                        }
                        Pill("Impostazioni", false, Icons.Outlined.Tune) {
                            val user = report.profileUser
                            if (user == null) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.fromParts("package", report.pkg, null),
                                    )
                                )
                            } else if (!Profiles.openSettings(context, report.pkg, user)) {
                                Toast.makeText(context, "Profilo di lavoro in pausa o non raggiungibile", Toast.LENGTH_SHORT).show()
                            }
                        }
                        val user = report.profileUser
                        if (user != null && Blocker.shizukuGranted()) {
                            // Toglie l'app solo dal profilo di lavoro, senza passare da Shelter.
                            Pill("Disinstalla", false, Icons.Outlined.Delete) {
                                Thread {
                                    val ok = Profiles.uninstall(report.pkg, user)
                                    (context as? Activity)?.runOnUiThread {
                                        Toast.makeText(
                                            context,
                                            if (ok) "Disinstallata dal profilo di lavoro" else "Disinstallazione non riuscita",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        if (ok) analyze()
                                    }
                                }.start()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Protezione preventiva: download, app nuove e chi può installare app. */
@Composable
private fun ProtectionPanel(tick: Int) {
    val context = LocalContext.current
    val p = palette()
    val filesAccess = remember(tick) { Environment.isExternalStorageManager() }
    var hadFilesAccess by remember { mutableStateOf(filesAccess) }
    LaunchedEffect(filesAccess) {
        // Appena concesso l'accesso ai file, il servizio riparte per iniziare a sorvegliare Download.
        if (filesAccess && !hadFilesAccess) MonitorService.restart(context)
        hadFilesAccess = filesAccess
    }
    var autoSuspend by remember { mutableStateOf(InstallGuard.autoSuspend(context)) }
    val shizuku = remember(tick) { Blocker.shizukuGranted() }
    var allowed by remember { mutableStateOf(InstallGuard.allowedInstallers(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    val installers = remember(tick / 5, refresh) { InstallGuard.currentInstallers(context) }

    Text("Protezione preventiva", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.text)
    Panel {
        StatusRow(
            Icons.Outlined.Download, "Controllo dei download",
            if (filesAccess) "Attivo sulla cartella Download" else "Serve l'accesso a tutti i file",
            if (filesAccess) Level.OK else Level.WARN,
        ) {
            if (!filesAccess) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.fromParts("package", context.packageName, null),
                        )
                    )
                }) { Text("Concedi", color = p.accent, fontWeight = FontWeight.SemiBold) }
            }
        }
        Divider()
        StatusRow(
            Icons.Outlined.PauseCircle, "Sospendi app nuove sospette",
            when {
                !autoSuspend -> "Spento: solo avviso"
                shizuku -> "Prima che tu le apra"
                else -> "Serve Shizuku attivo"
            },
            if (autoSuspend && shizuku) Level.OK else Level.WARN,
        ) {
            Switch(
                checked = autoSuspend,
                onCheckedChange = { on ->
                    InstallGuard.setAutoSuspend(context, on)
                    autoSuspend = on
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = p.accent, checkedThumbColor = p.background,
                    uncheckedTrackColor = p.raised, uncheckedBorderColor = p.border, uncheckedThumbColor = p.secondary,
                ),
            )
        }
        Divider()
        StatusRow(
            Icons.Outlined.InstallMobile, "Possono installare app",
            if (installers.isEmpty()) "Nessuna app" else "${installers.size} app",
            if (installers.all { it in allowed }) Level.OK else Level.ERROR,
        ) {}
        for (pkg in installers) {
            val ok = pkg in allowed
            Row(
                Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Apps.label(context, pkg), fontSize = 14.sp, color = p.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (ok) {
                    Tag("consentita", p.ok)
                } else {
                    TextButton(onClick = {
                        InstallGuard.setInstallerAllowed(context, pkg, true)
                        allowed = InstallGuard.allowedInstallers(context)
                    }) { Text("Consenti", color = p.secondary) }
                }
                TextButton(onClick = {
                    InstallGuard.setInstallerAllowed(context, pkg, false)
                    allowed = InstallGuard.allowedInstallers(context)
                    if (shizuku) {
                        Thread {
                            InstallGuard.revokeInstaller(pkg)
                            (context as? Activity)?.runOnUiThread { refresh++ }
                        }.start()
                    } else {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                android.net.Uri.fromParts("package", pkg, null),
                            )
                        )
                    }
                }) { Text("Togli", color = p.error, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
    Text(
        "Consiglio: lascia il permesso di installare solo a F-Droid e al file manager con cui installi le APK.",
        fontSize = 13.sp, color = p.secondary,
    )
}

/** File scaricati controllati di recente. */
@Composable
private fun FileList() {
    val context = LocalContext.current
    val p = palette()
    var files by remember { mutableStateOf(FileLog.all(context)) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val time = remember { SimpleDateFormat("d MMM HH:mm", Locale.ITALIAN) }
    if (files.isEmpty()) {
        Text(
            if (Environment.isExternalStorageManager()) "Nessun file scaricato da quando il controllo è attivo."
            else "Concedi l'accesso ai file dalla pagina Stato per controllare i download.",
            fontSize = 15.sp, color = p.secondary,
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(files, key = { it.path }) { file ->
            val color = when (file.verdict) {
                FileVerdict.DANGEROUS -> p.error
                FileVerdict.SUSPICIOUS -> p.warn
                FileVerdict.OK -> p.ok
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.panel)
                    .border(1.dp, if (expanded == file.path) p.accent else p.border, RoundedCornerShape(12.dp))
                    .clickable { expanded = if (expanded == file.path) null else file.path }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${time.format(Date(file.time))} · ${file.findings.firstOrNull()?.text ?: "nessun indizio"}",
                            fontSize = 13.sp, color = p.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Tag(file.verdict.title, color)
                }
                if (expanded == file.path) {
                    for (finding in file.findings) {
                        Text("• ${finding.text}", fontSize = 13.sp, color = p.text, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text(
                        "SHA-256 ${file.sha256}", fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = p.secondary, modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Elimina", false, null) {
                            if (DownloadWatcher.isInDownloads(file.path) && java.io.File(file.path).delete()) {
                                FileLog.remove(context, file.path)
                                files = FileLog.all(context)
                            }
                        }
                        Pill("Copia hash", false, Icons.Outlined.ContentCopy) {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("sha256", file.sha256))
                        }
                    }
                }
            }
        }
    }
}
