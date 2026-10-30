package dev.antidroidvirus

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

private val TITLES = listOf("Stato", "Registro", "Controllo", "Whitelist")

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

    Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Tutti", !onlyAlerts) { onlyAlerts = false }
        Pill("Solo avvisi", onlyAlerts) { onlyAlerts = true }
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
                            Apps.label(context, event.pkg), fontSize = 15.sp, fontWeight = FontWeight.Medium,
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

private data class AppRow(val pkg: String, val label: String, val system: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhitelistScreen() {
    val context = LocalContext.current
    val p = palette()
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
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Fidati del sistema", trustSystem) {
                Whitelist.setTrustSystemApps(context, !trustSystem)
                trustSystem = !trustSystem
            }
            Pill("Mostra sistema", showSystem) { showSystem = !showSystem }
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize().padding(end = 28.dp),
            ) {
                items(apps, key = { it.pkg }) { app ->
                    val scopes = entries.filter { it.startsWith("${app.pkg}|") }.map { it.substringAfter('|') }.toSet()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.panel)
                            .border(1.dp, if (expanded == app.pkg) p.accent else p.border, RoundedCornerShape(12.dp))
                            .clickable { expanded = if (expanded == app.pkg) null else app.pkg }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                app.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = p.text,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            TrustIcons(scopes)
                        }
                        if (expanded == app.pkg) {
                            Text(app.pkg, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = p.secondary)
                            val toggle = { scopeName: String ->
                                val on = scopeName !in scopes
                                Whitelist.setTrusted(context, app.pkg, scopeName, on)
                                if (on) Alerts.dismissAll(context, app.pkg)
                                entries = Whitelist.entries(context)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 10.dp),
                            ) {
                                Pill("Tutti", Whitelist.ALL in scopes, Icons.Outlined.VerifiedUser) { toggle(Whitelist.ALL) }
                                for (perm in Perm.entries) {
                                    Pill(perm.title, perm.name in scopes, perm.icon()) { toggle(perm.name) }
                                }
                                Pill("Comportamenti insoliti", Whitelist.ANOMALY in scopes, Icons.Outlined.NightsStay) {
                                    toggle(Whitelist.ANOMALY)
                                }
                                Pill("Controllo", Whitelist.CHECKS in scopes, Icons.Outlined.Policy) {
                                    toggle(Whitelist.CHECKS)
                                }
                            }
                        }
                    }
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

/** Icone dei permessi su cui l'app è fidata; uno scudo se è fidata su tutto. */
@Composable
private fun TrustIcons(scopes: Set<String>) {
    val p = palette()
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (Whitelist.ALL in scopes) {
            Icon(Icons.Outlined.VerifiedUser, "Fidata su tutto", tint = p.ok, modifier = Modifier.size(18.dp))
        } else {
            for (perm in Perm.entries) {
                if (perm.name in scopes) Icon(perm.icon(), perm.title, tint = p.ok, modifier = Modifier.size(18.dp))
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

/** Pagina Controllo: app installate dall'utente ordinate per punteggio di rischio, con i motivi. */
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
        items(visible, key = { it.pkg }) { report ->
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
                    .border(1.dp, if (expanded == report.pkg) p.accent else p.border, RoundedCornerShape(12.dp))
                    .clickable { expanded = if (expanded == report.pkg) null else report.pkg }
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
                    Tag(if (ignored) "va bene" else report.level.title, if (ignored) p.ok else color)
                }
                if (expanded == report.pkg) {
                    Text(
                        report.pkg, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = p.secondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    for (finding in report.findings) {
                        Text("• ${finding.text}", fontSize = 13.sp, color = p.text, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Va bene così", ignored, Icons.Outlined.VerifiedUser) {
                            Whitelist.setTrusted(context, report.pkg, Whitelist.CHECKS, !ignored)
                            if (!ignored) {
                                RiskCheck.forgetSigner(context, report.pkg)
                                Alerts.dismiss(context, Alerts.checkId(report.pkg))
                            }
                            entries = Whitelist.entries(context)
                        }
                        Pill("Impostazioni", false, Icons.Outlined.Tune) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", report.pkg, null),
                                )
                            )
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
