# Chat: cartelle vuote reali

La chat risolve localmente le richieste di ricerca delle cartelle vuote, senza
caricare Gemma e senza ricavare le cartelle dall'indice dei soli file. Esempi:

- «trovami le cartelle vuote dentro la cartella download»;
- «se ci sono cartelle vuote mostramele tutte»;
- «si intendo quelle senza file» dopo una ricerca, anche dopo un vecchio errore AI;
- «mostramele tutte» conserva il percorso e ripete la verifica attuale;
- «Elenca le cartelle vuote solo direttamente in 'Download'» limita al primo livello.

La ricerca predefinita include tutte le sottocartelle senza limite di profondità,
anche nascoste. Vuota significa priva di qualsiasi elemento: un file da zero byte,
un file nascosto o una sottocartella sono contenuto. La cartella di partenza non è
inclusa fra i suoi discendenti. Percorsi mancanti e cartelle non leggibili non sono
presentati come cartelle vuote. Dati Android protetti, cestino e trasferimenti
interni restano esclusi; il riepilogo dichiara quante aree non ha potuto verificare.
Non segue link simbolici. Interrompi interrompe anche questa ricerca.

I risultati hanno un tipo distinto dai file: cartella + percorso + apertura nel
browser interno. La chat mostra otto righe; «Vedi tutte» apre una lista virtualizzata
con **tutti** i risultati, anche oltre 200. Nessuna ricerca modifica o elimina file.
Le modifiche manuali rimangono nel browser con i consueti controlli/anteprime.
I risultati sono uno snapshot: aprendo la cartella si vede il suo contenuto attuale.

Filtri non riconosciuti, esclusioni, richieste miste o mutazioni di cartelle vuote
non vengono trasformati in una ricerca più ampia o in eliminazioni inventate.
Il messaggio chiede una richiesta verificabile e rimanda alla gestione manuale.
Le normali ricerche di file e le loro risposte successive conservano il proprio flusso.

Verifica: FolderCommandTest copre scansione reale, cartelle nascoste/profonde,
file zero byte, ambiti e chiarimenti, nessun indice/modello, limiti Android,
annullamento e oltre 200 risultati. FolderChatFlowTest esercita il vero composer,
la risposta in chat, «Vedi tutte» e l'apertura nell'esplora-cartelle.

## Conversazione continua

Il contesto è strutturato dall'app, separato dal testo generato dall'AI: percorso,
cartelle candidate e ultimo elenco di file. «Trova la cartella Download» seguito da
«dentro cerca i PDF» mantiene il percorso. Se manca il nome, «dentro cercami questo
file» chiede il nome; il messaggio successivo può contenere solo «Bolletta.pdf».
Con più cartelle viene chiesto quale; una scelta completa, un nome univoco o
«la prima» riprende la ricerca lasciata in sospeso. I primi quattro percorsi hanno
pulsanti; tutti gli altri restano raggiungibili nell'elenco o scrivendo il percorso.

Le ricerche successive ereditano il percorso; un nuovo percorso esplicito lo
sostituisce. «Mostrameli» riusa i risultati precedenti, «eliminali» genera soltanto
l'anteprima dei file di quell'elenco, verificati nuovamente. Un comando di modifica
relativo alla cartella non viene esteso al resto del telefono. I riferimenti ambigui,
i percorsi scomparsi e i filtri non riconosciuti non producono operazioni più ampie.
«Nuova chat» azzera i riferimenti; Riprova ripristina il contesto precedente a quella
richiesta. Il contesto vale nella conversazione attuale, non sopravvive al riavvio
dell'app (come la cronologia chat esistente).

ConversationContextTest verifica anche esecuzione/annullamento reali sui soli file
selezionati, cambio percorso, scelta ambigua e rinomina con maiuscole/estensione.
FolderChatFlowTest verifica messaggi consecutivi e azzeramento della chat nella UI.
