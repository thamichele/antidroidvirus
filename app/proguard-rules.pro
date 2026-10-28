# Codice dell'app: niente offuscamento, usa riflessione su API di sistema.
-keep class dev.antidroidvirus.** { *; }

# Blocker invoca per riflessione il metodo privato Shizuku.newProcess.
-keep class rikka.shizuku.Shizuku { *; }
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }
-keep class moe.shizuku.server.** { *; }

-dontwarn rikka.**
