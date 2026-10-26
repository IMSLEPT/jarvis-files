# Jarvis Files

App Android che mette in ordine il telefono con l'intelligenza artificiale: scrivi o detti cosa fare, Jarvis prepara un'anteprima e, dopo la tua conferma, sposta, rinomina, crea cartelle e cancella (nel cestino per 30 giorni, con tasto **Annulla**).

## Funzioni

| Area | Cosa fa |
|---|---|
| **Jarvis (chat)** | Comandi in linguaggio naturale, a voce o scritti. Riordina, rinomina, crea cartelle, cancella, trova file e foto, risponde a domande sul telefono. Quando la richiesta è ambigua fa domande con risposte pronte da toccare. |
| **Foto** | Cerca come in Google Foto: persone con nome ("foto di Leo"), oggetti e scene ("montagne", "lago"), luoghi dal GPS e dalla descrizione AI. Analizza fotocamera, WhatsApp, Download e screenshot. |
| **Persone** | Raggruppa i volti simili: dai un nome e Jarvis trova le altre foto di quella persona. Correggi con "Non è lui/lei". |
| **Pulizia** | Cache delle app (sicure / da valutare), file inutili (APK già installati, temporanei, cartelle vuote), doppioni (confronto del contenuto), WhatsApp (vocali, media inviati, ricevuti, per età), file grandi e vecchi, screenshot vecchi, foto sfocate o scure. |
| **Sicurezza** | App installate fuori dal Play Store, permessi pericolosi, accessibilità, amministratori dispositivo, debug USB, patch vecchie, APK in memoria. Verifica facoltativa con VirusTotal (chiave gratuita). |
| **Automazioni** | Programmi tu cosa fare e quando: riordino Download, analisi foto (di notte in carica), controllo sicurezza, avviso spazio, pulizia, doppioni, resoconto settimanale, comandi personalizzati a Jarvis (con conferma). |
| **Altro** | Cartella segreta, batteria e app più usate, trova documenti, cestino, blocco con impronta. |

## Intelligenza artificiale

- **Nel telefono (offline, gratis):** Gemma 4 E2B / E4B (open source, Google) tramite LiteRT-LM. Si scarica dalle Impostazioni (2-5 GB). File e foto non escono dal telefono.
- **Su internet (gratis, modelli open source):** qualsiasi servizio compatibile OpenAI; preimpostati Groq (GPT-OSS 120B), OpenRouter e Cerebras. Vengono inviati i nomi dei file e la richiesta, mai il contenuto dei file o le foto.
- **Visione:** ML Kit (volti e 400+ oggetti), FaceNet 512 per riconoscere i volti, Gemma 4 per descrivere scene e luoghi. Tutto sul telefono.

## Installazione

1. Apri **Releases** di questo repository e scarica l'ultimo `JarvisFiles-v1.0.N.apk`.
2. Aprilo sul telefono e consenti "Installa app sconosciute" per il browser o per Files.
3. Al primo avvio concedi **Accesso a tutti i file**; poi in Impostazioni scegli l'AI.

## Limiti onesti

- Android non permette a un'app di svuotare la cache delle singole app: Jarvis usa la richiesta di sistema "svuota tutte le cache" (con conferma) oppure apre la scheda dell'app.
- Non è un antivirus in tempo reale (Android non lo consente alle app normali): controlla i segnali di rischio. Play Protect resta attivo.
- Google Foto non concede alle app esterne l'accesso all'intera libreria cloud: Jarvis analizza le foto presenti sul telefono.
- Il riconoscimento dei volti dei neonati è meno affidabile: controlla e correggi i gruppi nella schermata Persone.
- Il consumo batteria per app in percentuale non è accessibile: si usa il tempo di utilizzo.

## Sviluppo

- Build: GitHub Actions (`Build APK`) a ogni push su `main`, APK firmato pubblicato in Releases.
- Test: `Test su emulatore` esegue test strumentati reali (operazioni sui file, annulla, cestino, doppioni, riconoscimento volti e ricerca, tutte le schermate) e pubblica rapporto e screenshot nel branch `test-results`.
- Licenze di terze parti: FaceNet TFLite da [shubham0204/FaceRecognition_With_FaceNet_Android](https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android) (Apache 2.0); immagini di test da [ageitgey/face_recognition](https://github.com/ageitgey/face_recognition) (MIT).


## Aggiornamento qualità interfaccia e AI locale

- Home e navigazione adattive: rispettano dimensioni dello schermo e caratteri di sistema; sfondo bianco, accenti arancione e grafite. Grafico basato sullo spazio reale, categorie sempre raggiungibili da File.
- Chat: bozza conservata tra schede, invio da tastiera, interruzione, nuova chat, riprova, testo selezionabile. Le richieste ambigue richiedono una scelta prima di mostrare un piano eseguibile.
- Al primo avvio dell'aggiornamento viene selezionata **Solo telefono**; le credenziali eventualmente presenti restano salvate. La modalità può essere cambiata esplicitamente in Impostazioni.
- Modello consigliato per qualità: **Gemma 4 E4B**, Apache 2.0, circa 3,7 GB. **E2B** (circa 2,6 GB) resta disponibile per velocità e consumi. Serve internet per il download iniziale; l'inferenza locale non richiede API o abbonamenti. Un modello E2B già scaricato continua a funzionare: non viene sostituito né eliminato automaticamente.
- Generazione locale con JSON vincolato, budget di contesto e output; la visione viene caricata soltanto quando serve per le immagini. Le operazioni sono validate dall'app e richiedono il tasto Esegui.
- Protezioni contro doppia esecuzione, percorsi esterni, filtri di gruppo senza cartella e sovrascritture. I file nel cestino continuano a occupare spazio fino allo svuotamento.
- La cartella segreta richiede lo sblocco anche quando il blocco generale dell'app è disattivato.

Fonti della selezione modello (verificate il 26 ottobre 2026):
- https://developers.google.com/edge/litert-lm/models/gemma-4
- https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
- https://ai.google.dev/gemma/docs/capabilities/text/function-calling-gemma4

Verifica riproducibile: `./gradlew assembleDebug assembleDebugAndroidTest lintDebug` e `./gradlew connectedDebugAndroidTest` su emulatore API 33. Il workflow `Verifica correzioni` conserva report e screenshot nel branch `validation-results/<commit>`. Le regressioni automatiche verificano interfaccia, parser e operazioni reali sui file di prova; la qualità e la velocità del modello E4B vanno misurate sul dispositivo reale. Nessun risultato di benchmark su altri telefoni dimostra quale sia il modello migliore sul Pixel 9 Pro XL.

## Ripristino grafica originale

Ripristinati il design della Home precedente alla v1.0.42 (logo originale, sfera grande, grafico memoria, categorie compatte, suggerimento illustrato), la barra inferiore flottante e i colori originali. Restano le correzioni di AI, file, ricerca, cestino, ripristino e cartella segreta. La Home usa di nuovo le proporzioni del design approvato; i testi della Home seguono il ridimensionamento del mockup, non la scala dei caratteri di sistema.


## Correzioni comandi e interfaccia (ottobre 2026)

- Comandi comuni (APK, PDF, foto, video, audio e archivi) risolti sull'indice dei file accessibili, senza dipendere dal modello generativo. I comandi di eliminazione preparano sempre un'anteprima selezionabile e recuperabile dal cestino. Sono supportate cartelle e soglie in giorni; richieste con eccezioni non vengono semplificate automaticamente.
- Ricerca per tipo/nome, riordino dei Download per categoria e rinomina esplicita tra virgolette. Le risposte generative senza un piano valido non vengono presentate come operazioni riuscite.
- Home con impaginazione a flusso e caratteri di sistema; illustrazioni e grafico originali mantenuti. Invio e microfono separati, risposte in schede, controlli delle operazioni senza il precedente limite di 400 elementi e lista WhatsApp senza limite di 300.
- Verifica del modello locale dalle Impostazioni: carica realmente il modello sul dispositivo e controlla una risposta JSON in italiano, senza modificare file. Non equivale a una certificazione dell'accuratezza di Gemma per ogni richiesta.
- Test strumentati sui comandi e sui tasti Invio, Esegui, Annulla, ripristino, impostazioni e revisione di oltre 400 operazioni. Il test LLM richiede un modello presente sul dispositivo: se assente viene saltato. Le prestazioni su Pixel devono essere verificate sul telefono.
