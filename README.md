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
