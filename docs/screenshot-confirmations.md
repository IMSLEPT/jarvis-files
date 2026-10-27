# Screenshot e risposte Sì/No

I comandi diretti per screenshot/schermate identificano immagini nelle cartelle Screenshot/Screenshots o con nomi Screenshot_, Screenshot- e varianti definite nel codice. Non selezionano indistintamente tutte le immagini del telefono. Sono mantenuti i filtri di cartella ed età; vincoli non riconosciuti non vengono scartati. Le richieste esplicite «tutti i file nella cartella ...» includono ogni tipo solo nella cartella indicata e nelle sue sottocartelle, come dichiarato nell’anteprima.

Le domande strutturate vengono collegate dall’app alla richiesta originale. Il campo request eventualmente prodotto dal modello non viene usato come fonte. I pulsanti e le risposte Sì/No digitate conservano tale collegamento. Una scelta Sì su una richiesta riconosciuta ricostruisce l’anteprima dopo una nuova scansione; nessuna operazione avviene prima di Esegui. Per richieste di modifica non riconosciute si richiede un elenco preciso, senza ricavare nuove autorizzazioni dal testo della domanda del modello. No annulla senza avviare un modello o una scansione.

La funzione Riprova recupera anche il vecchio formato «domanda\nScelgo: Sì» conservando il perimetro originale. I pulsanti di vecchie domande rimangono disabilitati. Il modello selezionato, la modalità locale, la grafica e le protezioni precedenti rimangono invariati.

Verifica: test su file reali per riconoscimento screenshot, cartella, età, file vicini, altri tipi, ricerca senza cancellazione, nessun risultato, sorgente delle domande e scelte senza modello. Test UI su Sì → anteprima aggiornata → Esegui → Annulla modifiche, No, Riprova del vecchio messaggio e richiesta diretta. Il JSON delle domande è una fixture dei test; questi controlli non misurano l’inferenza di Gemma su un telefono fisico.
