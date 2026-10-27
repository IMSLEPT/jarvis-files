# Interfaccia approvata, ottobre 2026

Il redesign applica alle schermate native Compose le anteprime approvate: Home, File,
Jarvis, Pulizia e Altro. Le immagini delle anteprime non vengono usate come sfondo:
ricerca, categorie, suggerimenti e navigazione sono controlli reali.

- Home: logo originale, pulsante assistente unico, ricerca, spazio libero con barra,
  sei categorie, collegamenti Download/cartelle. Piano in attesa e annullamento restano disponibili.
- File: ricerca e categorie con gli stessi simboli della Home. Le cartelle rapide,
  la cartella segreta e il cestino restano accessibili tramite scorrimento.
- Jarvis: suggerimenti compatti, impostazioni e nuova chat, input e microfono separati.
  Conversazioni, anteprime e conferma delle operazioni mantengono la logica precedente.
- Pulizia: quattro schede principali; Grandi e vecchi e Screenshot e foto nel menu
  “Altri strumenti di pulizia”. Dettagli e guida permessi sono espandibili. Le azioni
  sulla cache continuano a passare da Android, senza affermare che siano già eseguite.
- Altro: Protezione, Strumenti e Impostazioni; tutte le otto destinazioni esistenti.

Superfici bianche con bordi sottili, testo navy, accento arancione, azioni principali
blu scuro. Logo, icona adattiva e sfera approvati vengono conservati. I testi grandi
usano più righe o una colonna, anziché essere ridotti o sovrapposti.

## Verifica riproducibile

Il workflow `Test su emulatore` compila ed esegue l'intera suite Android. `UiTest`
salva le schermate reali; `DesignFlowTest` controlla ricerca Home, otto destinazioni
Altro, strumenti di pulizia aggiuntivi e apertura/chiusura dei dettagli cache.
Restano i test dei comandi sui file, invio/anteprima/esecuzione/annullamento,
protezione della cartella segreta e caratteri di sistema al 150%.

Il modello Gemma reale richiede un download separato: il suo test viene saltato
quando il modello non è installato. Le modifiche grafiche non cambiano modello o motore AI.
