# AntiDroidVirus

App Android per uso personale che registra quali app usano posizione, fotocamera, microfono,
contatti, SMS, registro chiamate e appunti, e avvisa con 3 notifiche quando lo fa un'app
non in whitelist. Pensata per un Samsung Galaxy S26 Ultra (One UI 9, Android 17), senza root
e senza Play Store.

## Come funziona

- Legge lo stato AppOps di tutte le app (`AppOpsManager.getPackagesForOps`), che dice per ogni
  app e permesso l'ultimo accesso e se è in uso adesso. Serve il permesso `GET_APP_OPS_STATS`,
  concesso una volta via ADB.
- Le letture sono guidate da eventi: apertura di fotocamera o microfono (callback di sistema),
  accensione dello schermo. In più una lettura ogni 60 secondi a schermo acceso e ogni 15 minuti
  a schermo spento, necessaria per la posizione che non ha callback pubbliche.
- Siccome AppOps conserva l'orario dell'ultimo accesso, un uso avvenuto a schermo spento viene
  comunque registrato alla lettura successiva.
- Ogni uso finisce nel registro locale (30 giorni). Se l'app non è in whitelist per quel permesso
  parte una notifica (al massimo una ogni 10 minuti per la stessa app e lo stesso permesso).
  - **Fidati** la mette in whitelist per quel permesso.
  - **Blocca** revoca il permesso all'app via Shizuku (`pm revoke`); se Shizuku è spento apre la
    pagina dei permessi dell'app. Android chiude l'app quando le si revoca un permesso.
  - **Ignora** chiude la notifica.
- Whitelist per permesso (Posizione, Fotocamera, Microfono, Contatti, Registro chiamate, SMS,
  Appunti) o per tutti, con ricerca per nome e indice alfabetico laterale.
- Nessun accesso a internet.

## Installazione

1. Dal telefono apri la release [**latest**](../../releases/tag/latest) e scarica
   `antidroidvirus.apk` (viene ricompilato a ogni push).
2. Sul telefono disattiva temporaneamente **Blocco automatico** (Impostazioni › Sicurezza e
   privacy) e installa l'APK.
3. Concedi il permesso AppOps una volta sola, da PC:

   ```
   adb shell pm grant dev.antidroidvirus android.permission.GET_APP_OPS_STATS
   ```

   oppure dal telefono con il terminale `rish` di Shizuku, togliendo `adb shell`.
4. Apri l'app, consenti le notifiche e attiva **Monitoraggio**.
5. Metti l'app tra le **App mai in sospensione** (Impostazioni › Batteria › Limiti utilizzo in
   background), altrimenti One UI può chiudere il servizio.
6. Puoi riattivare Blocco automatico.

## Da verificare sul telefono

- Che il permesso concesso via ADB resti dopo un riavvio.
- Il rumore: con "Considera fidate le app di sistema" attivo (default) i servizi di sistema e Google
  non generano avvisi, ma restano nel registro.

## Shizuku (per "Blocca")

Installa Shizuku, avvialo con il debug wireless e autorizza AntiDroidVirus dalla schermata
principale dell'app (o al primo "Blocca"). Senza root Shizuku va riavviato dopo ogni riavvio del
telefono; il monitoraggio funziona comunque anche senza.

## Prossimi passi

- Avvisi in tempo reale via Shizuku (`startWatchingActive`) per eliminare la lettura periodica.
- Servizio di accessibilità opzionale per registrare anche le richieste di permesso.
