# AntiDroidVirus

App Android per uso personale che registra quali app usano posizione, fotocamera, microfono,
contatti, SMS, registro chiamate, appunti e altri permessi, avvisa quando lo fa un'app non in
whitelist o quando un'app si comporta in modo insolito, e controlla i file scaricati e le app
appena installate. Pensata per un Samsung Galaxy S26 Ultra (One UI 9, Android 17), senza root,
senza PC e senza Play Store.

## Come funziona

- Legge lo stato AppOps di tutte le app (`AppOpsManager.getPackagesForOps`), che dice per ogni
  app e permesso l'ultimo accesso e se è in uso adesso. Serve il permesso `GET_APP_OPS_STATS`,
  che l'app si concede una volta tramite Shizuku (o via ADB da PC).
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
  Appunti, Foto e file, Telefono, Sensori, Dispositivi vicini) o per tutti, con ricerca per nome e
  indice alfabetico laterale.
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

## Profilo di lavoro (Shelter)

- Anche le app del profilo di lavoro creato da Shelter compaiono in **Controllo › App** (con l'etichetta
  "lavoro", e "congelata" se Shelter le ha congelate) e nella whitelist.
- Un pacchetto è installato una sola volta sul telefono: un'app presente in entrambi i profili ha lo stesso
  APK, quindi del profilo di lavoro contano solo gli accessi speciali attivi lì (accessibilità, lettore di
  notifiche, overlay, installazione app, dati di utilizzo). Le app che stanno solo nel profilo di lavoro
  ricevono l'analisi completa. Shelter, che amministra il profilo, non viene segnalato per questo.
- Le app installate nel profilo di lavoro vengono analizzate subito, come quelle del profilo principale;
  con Shizuku attivo quelle sospette vengono sospese, e **Disinstalla** le toglie solo dal profilo di lavoro.
- Con Shizuku attivo l'elenco è completo (anche app senza icona e app congelate). Senza Shizuku si vedono le
  app con un'icona e quelle presenti sul telefono ma non installate nel profilo principale.
- Se il profilo di lavoro è in pausa, le app restano analizzabili ma le impostazioni dell'app non si aprono.

## Installazione

Tutto dal telefono, senza PC.

1. In F-Droid apri Impostazioni › Repository, tocca **+** e incolla
   `https://thamichele.github.io/antidroidvirus/repo?fingerprint=52C6192939571634951B82EE34424660F5055BE2EFB8C50280F0DFA772EAE9EB`.
   Poi installa AntiDroidVirus da F-Droid: gli aggiornamenti arrivano da lì, a ogni push su main.
   In alternativa scarica `antidroidvirus.apk` dalla release [**latest**](../../releases/tag/latest).
2. Installa [Shizuku](https://shizuku.rikka.app/) e avvialo con il **debug wireless**
   (Impostazioni › Opzioni sviluppatore › Debug wireless; la guida è dentro Shizuku).
3. Apri AntiDroidVirus e consenti le notifiche. Nella pagina **Stato** tocca **Autorizza** su
   "Permessi senza PC", conferma in Shizuku, poi tocca **Concedi**. L'app si concede da sola:
   - il permesso AppOps (`GET_APP_OPS_STATS`), che serve al monitoraggio;
   - i dati di utilizzo (`GET_USAGE_STATS`), per il traffico di rete per app;
   - l'accesso a tutti i file, per il controllo dei download;
   - la batteria senza restrizioni, così One UI non chiude il servizio.
4. Attiva **Monitoraggio**.

Questi permessi restano anche dopo un riavvio con Shizuku spento. Shizuku serve di nuovo solo per
le azioni immediate (Blocca, sospendere un'app nuova, togliere il permesso di installare app) e per
vedere chi legge le notifiche; senza root va riavviato dopo ogni riavvio del telefono.

### Senza Shizuku, da PC

```
adb shell pm grant dev.antidroidvirus android.permission.GET_APP_OPS_STATS
adb shell appops set dev.antidroidvirus GET_USAGE_STATS allow
```

Accesso ai file e batteria si concedono dalla pagina Stato (pulsanti **Concedi** e **Apri**).

## Da verificare sul telefono

- Il rumore: con "Considera fidate le app di sistema" attivo (default) i servizi di sistema e Google
  non generano avvisi, ma restano nel registro.

## Firma

Le build sono firmate con la chiave privata salvata nei secret del repository (`SIGNING_KEYSTORE_BASE64`,
`SIGNING_PASSWORD`). Senza quei secret la CI compila lo stesso ma non pubblica release né il repository F-Droid.

## Prossimi passi

- Avvisi in tempo reale via Shizuku (`startWatchingActive`) per eliminare la lettura periodica.
- Servizio di accessibilità opzionale per registrare anche le richieste di permesso.
