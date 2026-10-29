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

## Comportamenti sospetti (v0.4)

- Ogni uso nel registro dice se è avvenuto in background, a schermo spento, di notte (1-6) o molto più
  spesso del solito (ultime 24 ore contro la media dei 7 giorni prima).
- Anche le app in whitelist avvisano se usano fotocamera o microfono di notte a schermo spento, o un
  permesso con frequenza tripla. "Fidati" su quell'avviso silenzia solo i comportamenti insoliti.
- Permessi sorvegliati in più: foto e file, telefono, sensori, dispositivi vicini.
- Pagina **Controllo › App**: punteggio 0-100 per ogni app installata dall'utente, con i motivi. Conta
  accessi speciali attivi (accessibilità, amministratore, lettore di notifiche, overlay, installazione app,
  dati di utilizzo), permessi chiesti, app senza icona, provenienza fuori dagli store, firma cambiata, uso di
  fotocamera e microfono a schermo spento, traffico in background. Sopra 50 arriva una notifica.
  Il lettore di notifiche si vede solo con Shizuku attivo.

## Protezione preventiva (v0.5)

- **Download**: ogni file che arriva nella cartella Download viene controllato appena finisce di scaricare.
  Riconosce il tipo vero dai primi byte e lo confronta con l'estensione, poi guarda dentro: APK (permessi,
  firma, copie di app installate), ZIP e pacchetti XAPK/APKS, PDF (JavaScript, lancio di programmi, allegati,
  nomi offuscati), Word/Excel/PowerPoint vecchi e nuovi e LibreOffice (macro, oggetti incorporati, modelli
  remoti), RTF, HTML e SVG (codice, pagine di login finte, HTML smuggling), CSV con formule pericolose,
  eseguibili Windows e Linux, script, collegamenti .lnk, immagini disco, RAR e 7z. Segnala anche doppie
  estensioni e caratteri che invertono il nome. Nessuna firma antivirus e nessun accesso a internet:
  sono controlli sulla struttura, non trovano tutto. I risultati sono in **Controllo › File scaricati**.
- **App appena installate**: analizzate subito, prima che tu le apra. Se sono sospette e Shizuku è attivo
  vengono sospese (`pm suspend`) e la notifica offre Riattiva o Disinstalla.
- **Aggiornamenti**: avviso se un aggiornamento aggiunge permessi pericolosi.
- **Chi può installare app**: avviso se un'app fuori dalla lista ottiene "Installa app sconosciute", con
  Togli (via Shizuku, o la pagina di sistema) o Consenti. Di default sono consentite solo F-Droid.

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
   Facoltativo, per il traffico di rete per app:

   ```
   adb shell appops set dev.antidroidvirus GET_USAGE_STATS allow
   ```
4. Apri l'app, consenti le notifiche e attiva **Monitoraggio**. Per il controllo dei download tocca
   **Concedi** su "Controllo dei download" e attiva l'accesso a tutti i file.
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
