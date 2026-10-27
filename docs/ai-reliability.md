# Risposte locali e affidabilità

Le domande sui file più grandi vengono risolte sull’indice reale, ordinando per byte decrescenti. Sono mostrati fino a 20 risultati per impostazione predefinita (fino a 200 se richiesti esplicitamente), apribili dalla chat. Il campione di nomi inviato al modello non viene usato per dedurre una classifica.

Il seguito «mostrameli» conserva il risultato precedente e aggiorna i file disponibili. Senza un elenco precedente viene recuperata soltanto una richiesta informativa riconosciuta; altrimenti si chiede quale elenco mostrare. Questo seguito non genera operazioni sui file. La vista «Vedi tutti» conserva l’ordine dei risultati; le dimensioni precedono i percorsi, così restano leggibili anche con cartelle lunghe.

La generazione locale viene letta come stream tramite l’API Flow di LiteRT-LM 0.18.0. I frammenti restano interni finché la risposta completa non supera i controlli: nessun testo parziale né operazione parziale viene esposto. Ripetizioni patologiche, output eccessivo o timeout di generazione (90 secondi, escluso caricamento iniziale) interrompono il backend e producono un messaggio comprensibile. Lo Stop dell’utente conserva la cancellazione della coroutine.

Il budget locale passa da 80 a 24 righe di file e da 1600 a 768 token di risposta. Le risposte anomale vengono rifiutate anche se il JSON è formalmente valido, e non sono reinviate nella cronologia. Grafica, modello selezionato e modalità offline rimangono quelli approvati.

Verifica: test su file veri di ordinamento/dimensioni, vincoli non ignorati, seguito e file rimossi; percorso completo nella chat senza modello configurato; stream controllati per ripetizioni, timeout e Stop. I test con stream controllati verificano le protezioni, non misurano la velocità del modello su un telefono fisico. Il test Gemma reale richiede un modello installato nell’ambiente di test.
