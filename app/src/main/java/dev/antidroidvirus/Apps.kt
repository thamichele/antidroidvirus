package dev.antidroidvirus

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object Apps {
    private val labels = HashMap<String, String>()

    fun label(context: Context, pkg: String): String = synchronized(labels) {
        labels.getOrPut(pkg) {
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg
            }
        }
    }

    fun isSystem(context: Context, pkg: String): Boolean = try {
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    } catch (e: PackageManager.NameNotFoundException) {
        // UID condivisi di sistema (es. "android", "media") non sono pacchetti installabili.
        true
    }
}
