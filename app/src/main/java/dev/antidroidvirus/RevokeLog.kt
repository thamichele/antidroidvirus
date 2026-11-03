package dev.antidroidvirus

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

/** Una revoca fatta dall'app: si può annullare con [RevokeLog.restore]. */
data class Revocation(
    val time: Long,
    val pkg: String,
    val user: Int,
    /** Permessi runtime revocati con `pm revoke`. */
    val permissions: List<String>,
    /** Op AppOps messe su "ignore". */
    val appOps: List<String>,
    val perm: String,
)

/** Cosa ripristinare per un'app critica: permessi chiesti ma non concessi, op AppOps bloccate. */
data class Repair(val pkg: String, val user: Int, val permissions: List<String>, val appOps: List<String>)

/**
 * Registro delle revoche fatte tramite Shizuku, per poterle annullare se un'app smette di funzionare.
 * Le revoche fatte da versioni precedenti non sono registrate: per quelle c'è [repairPlan].
 */
object RevokeLog {
    private const val PREFS = "revocations"
    private const val KEY = "list"
    private const val MAX = 200

    fun all(context: Context): List<Revocation> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Revocation(
                    o.getLong("time"), o.getString("pkg"), o.getInt("user"),
                    o.getJSONArray("permissions").let { a -> (0 until a.length()).map { a.getString(it) } },
                    o.getJSONArray("appOps").let { a -> (0 until a.length()).map { a.getString(it) } },
                    o.optString("perm"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(context: Context, revocation: Revocation) {
        save(context, (listOf(revocation) + all(context)).take(MAX))
    }

    @Synchronized
    fun remove(context: Context, revocation: Revocation) {
        save(context, all(context).filter { it != revocation })
    }

    private fun save(context: Context, list: List<Revocation>) {
        val array = JSONArray()
        for (r in list) {
            array.put(
                JSONObject()
                    .put("time", r.time).put("pkg", r.pkg).put("user", r.user).put("perm", r.perm)
                    .put("permissions", JSONArray(r.permissions)).put("appOps", JSONArray(r.appOps))
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    /** Annulla una revoca. Va chiamato fuori dal main thread; serve Shizuku. */
    fun restore(context: Context, revocation: Revocation): Boolean {
        val ok = apply(revocation.pkg, revocation.user, revocation.permissions, revocation.appOps)
        if (ok) remove(context, revocation)
        return ok
    }

    fun apply(repair: Repair): Boolean = apply(repair.pkg, repair.user, repair.permissions, repair.appOps)

    private fun apply(pkg: String, user: Int, permissions: List<String>, appOps: List<String>): Boolean {
        if (!Blocker.shizukuGranted() || !Blocker.isPackageName(pkg)) return false
        val userArgs = arrayOf("--user", user.toString())
        var ok = true
        // L'ordine conta: la posizione in background si concede solo dopo quella in primo piano.
        for (permission in permissions) {
            if (Blocker.exec("pm", "grant", *userArgs, pkg, permission) != 0) ok = false
        }
        for (op in appOps) {
            if (Blocker.exec("appops", "set", *userArgs, pkg, op, "allow") != 0) ok = false
        }
        return ok
    }

    /**
     * Per le app critiche di Google, Samsung e Android, nel profilo principale e in quello di lavoro: i permessi
     * sorvegliati che chiedono ma non hanno, e gli appunti bloccati. Va chiamato fuori dal main thread.
     */
    fun repairPlan(context: Context): List<Repair> {
        if (!Blocker.shizukuGranted()) return emptyList()
        val users = listOf(Profiles.myUserId()) + Profiles.others(context).map { Profiles.userId(it) }
        val watched = Perm.entries.flatMap { it.permissions }
        val plan = ArrayList<Repair>()
        for (pkg in CriticalApps.installed(context)) {
            if (pkg == "android") continue
            val requested = Profiles.packageInfo(context, pkg, Profiles.myUserId(), PackageManager.GET_PERMISSIONS)
                ?.requestedPermissions?.toSet() ?: continue
            for (user in users) {
                if (user != Profiles.myUserId() && !installedFor(pkg, user)) continue
                val granted = Profiles.grantedPermissions(context, pkg, user) ?: continue
                val missing = watched.filter { it in requested && it !in granted }
                val ops = Perm.entries.flatMap { it.appOps }.filter { op ->
                    Blocker.output("appops", "get", "--user", user.toString(), pkg, op)?.contains(": ignore") == true
                }
                if (missing.isNotEmpty() || ops.isNotEmpty()) plan += Repair(pkg, user, missing, ops)
            }
        }
        return plan
    }

    /** `pm list packages` filtra per sottostringa: serve la riga esatta. */
    private fun installedFor(pkg: String, user: Int): Boolean =
        Blocker.output("pm", "list", "packages", "--user", user.toString(), pkg)
            ?.lineSequence()?.any { it.trim() == "package:$pkg" } == true
}
