package dev.antidroidvirus

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log

/** App installata in un altro profilo, es. il profilo di lavoro creato da Shelter. */
data class ProfileApp(
    val pkg: String,
    val userId: Int,
    /** Congelata da Shelter: nascosta nel profilo, non può partire finché non la scongeli. */
    val frozen: Boolean,
    val installer: String?,
    val system: Boolean,
)

/**
 * Gli altri profili dell'utente (in pratica il profilo di lavoro di Shelter). Un pacchetto è installato una
 * volta sola sul telefono e ogni profilo ne ha solo lo stato (installata, congelata, permessi, accessi
 * speciali): un'app presente in entrambi i profili ha lo stesso APK, uno solo nel lavoro va analizzata a parte.
 *
 * Con Shizuku l'elenco è completo (anche app senza icona e app congelate); senza, si vedono le app con icona
 * e quelle presenti sul telefono ma non installate nel profilo principale.
 */
object Profiles {
    private const val TAG = "Profiles"
    private const val PER_USER_RANGE = 100000
    private val LINE = Regex("^package:(\\S+)(?:\\s+installer=(\\S+))?")

    fun userOf(uid: Int) = uid / PER_USER_RANGE

    fun myUserId() = userOf(Process.myUid())

    /** UID di un'app in un altro profilo: stessa parte "app", utente diverso. */
    fun uidIn(userId: Int, uid: Int) = userId * PER_USER_RANGE + uid % PER_USER_RANGE

    /** UserHandle.hashCode() è l'id utente (getIdentifier è un'API di sistema). */
    fun userId(handle: UserHandle) = handle.hashCode()

    /** Profili collegati diversi da quello in cui gira l'app. */
    fun others(context: Context): List<UserHandle> = try {
        context.getSystemService(UserManager::class.java).userProfiles.filter { it != Process.myUserHandle() }
    } catch (e: Exception) {
        emptyList()
    }

    fun handle(context: Context, userId: Int): UserHandle? = others(context).firstOrNull { userId(it) == userId }

    /** Pacchetti installati nel profilo principale. */
    fun installedHere(context: Context): Set<String> =
        context.packageManager.getInstalledApplications(0).map { it.packageName }.toSet()

    /** App degli altri profili. Va chiamato fuori dal main thread se Shizuku è attivo. */
    fun apps(context: Context, includeSystem: Boolean): List<ProfileApp> {
        val profiles = others(context)
        if (profiles.isEmpty()) return emptyList()
        val here = installedHere(context)
        val result = ArrayList<ProfileApp>()
        for (handle in profiles) {
            val user = userId(handle)
            result += (if (Blocker.shizukuGranted()) fromShell(user, includeSystem, here) else null)
                ?: withoutShizuku(context, handle, includeSystem, here, singleProfile = profiles.size == 1)
        }
        return result
    }

    private fun fromShell(user: Int, includeSystem: Boolean, here: Set<String>): List<ProfileApp>? {
        val u = user.toString()
        val third = parse(Blocker.output("pm", "list", "packages", "-3", "-i", "--user", u)) ?: return null
        // -u aggiunge le app nascoste (congelate) ma anche quelle mai installate nel profilo: le seconde,
        // se sono nel profilo principale, hanno lo stesso APK già analizzato lì.
        val known = parse(Blocker.output("pm", "list", "packages", "-3", "-i", "-u", "--user", u)) ?: emptyMap()
        val result = third.map { (pkg, installer) -> ProfileApp(pkg, user, false, installer, false) }.toMutableList()
        for ((pkg, installer) in known) {
            if (pkg !in third && pkg !in here) result += ProfileApp(pkg, user, true, installer, false)
        }
        if (includeSystem) {
            val all = parse(Blocker.output("pm", "list", "packages", "--user", u)) ?: emptyMap()
            for (pkg in all.keys - third.keys) result += ProfileApp(pkg, user, false, null, true)
        }
        return result
    }

    private fun parse(output: String?): Map<String, String?>? {
        output ?: return null
        val map = LinkedHashMap<String, String?>()
        for (line in output.lineSequence()) {
            val match = LINE.find(line.trim()) ?: continue
            val pkg = match.groupValues[1]
            if (!Blocker.isPackageName(pkg)) continue
            map[pkg] = match.groupValues[2].takeIf { it.isNotEmpty() && it != "null" }
        }
        return map
    }

    private fun withoutShizuku(
        context: Context, handle: UserHandle, includeSystem: Boolean, here: Set<String>, singleProfile: Boolean,
    ): List<ProfileApp> {
        val user = userId(handle)
        val pkgs = LinkedHashSet<String>()
        try {
            context.getSystemService(LauncherApps::class.java).getActivityList(null, handle)
                .mapTo(pkgs) { it.componentName.packageName }
        } catch (e: Exception) {
            Log.w(TAG, "Elenco app del profilo $user non disponibile", e)
        }
        // Presenti sul telefono ma non installate qui: se c'è un solo altro profilo sono sue.
        if (singleProfile) {
            context.packageManager.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                .filter { it.flags and ApplicationInfo.FLAG_INSTALLED == 0 }
                .mapTo(pkgs) { it.packageName }
        }
        return pkgs.filter { it != context.packageName }.mapNotNull { pkg ->
            val system = Apps.isSystem(context, pkg)
            if (system && !includeSystem) null else ProfileApp(pkg, user, false, null, system)
        }
    }

    /** PackageInfo di un'app che può non essere installata nel profilo principale. */
    fun packageInfo(context: Context, pkg: String, userId: Int, flags: Int): PackageInfo? {
        val pm = context.packageManager
        try {
            return pm.getPackageInfo(pkg, flags or PackageManager.MATCH_UNINSTALLED_PACKAGES)
        } catch (e: PackageManager.NameNotFoundException) {
            // Prova sotto: si legge direttamente l'APK.
        }
        if (!Blocker.shizukuGranted() || !Blocker.isPackageName(pkg)) return null
        val path = Blocker.output("pm", "path", "--user", userId.toString(), pkg)
            ?.lineSequence()?.firstOrNull { it.startsWith("package:") }?.removePrefix("package:")?.trim()
            ?: return null
        return pm.getPackageArchiveInfo(path, flags)?.also {
            it.applicationInfo?.sourceDir = path
            it.applicationInfo?.publicSourceDir = path
        }
    }

    fun hasLauncher(context: Context, pkg: String, userId: Int): Boolean {
        val handle = handle(context, userId) ?: return false
        return try {
            context.getSystemService(LauncherApps::class.java).getActivityList(pkg, handle).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /** Valori di Impostazioni › secure di un profilo, es. le accessibilità attive. Serve Shizuku. */
    fun secureSetting(userId: Int, key: String): Set<String> {
        if (!Blocker.shizukuGranted()) return emptySet()
        val raw = Blocker.output("settings", "--user", userId.toString(), "get", "secure", key) ?: return emptySet()
        return raw.trim().split(':').mapNotNull { it.substringBefore('/').takeIf { p -> p.isNotBlank() && p != "null" } }.toSet()
    }

    /** Installatore di un'app del profilo, letto da shell. */
    fun installer(userId: Int, pkg: String): String? {
        if (!Blocker.shizukuGranted() || !Blocker.isPackageName(pkg)) return null
        val map = parse(Blocker.output("pm", "list", "packages", "-i", "--user", userId.toString(), pkg)) ?: return null
        return map[pkg]
    }

    /** Pagina di sistema dell'app nel suo profilo (Impostazioni del profilo di lavoro). */
    fun openSettings(context: Context, pkg: String, userId: Int): Boolean {
        val handle = handle(context, userId) ?: return false
        return try {
            context.getSystemService(LauncherApps::class.java)
                .startAppDetailsActivity(ComponentName(pkg, pkg), handle, null, null)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Impostazioni di $pkg nel profilo $userId non disponibili", e)
            false
        }
    }

    /** Disinstalla l'app solo dal profilo indicato, via Shizuku. Va chiamato fuori dal main thread. */
    fun uninstall(pkg: String, userId: Int): Boolean =
        Blocker.isPackageName(pkg) && Blocker.exec("pm", "uninstall", "--user", userId.toString(), pkg) == 0
}
