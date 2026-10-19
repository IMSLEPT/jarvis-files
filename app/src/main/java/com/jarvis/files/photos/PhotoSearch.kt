package com.jarvis.files.photos

import android.content.Context
import com.jarvis.files.ai.AiRouter
import com.jarvis.files.ai.ChatMsg
import com.jarvis.files.ai.extractJson
import com.jarvis.files.core.Fmt
import java.util.Locale

data class PhotoHit(val path: String, val score: Float, val why: String)

data class PhotoQuery(
    val people: List<String>,
    val labelsEn: List<String>,
    val keywordsIt: List<String>,
    val place: String?,
    val fromIso: String?,
    val toIso: String?,
)

/** Ricerca nelle foto analizzate: persone con nome, oggetti, scene, luoghi, date. */
object PhotoSearch {

    // Traduzioni di base per le etichette di ML Kit (in inglese), usate anche senza AI.
    private val dict = mapOf(
        "montagna" to "mountain", "montagne" to "mountain", "lago" to "lake", "mare" to "sea", "spiaggia" to "beach",
        "cielo" to "sky", "neve" to "snow", "fiume" to "river", "bosco" to "forest", "foresta" to "forest", "albero" to "tree",
        "fiori" to "flower", "fiore" to "flower", "gatto" to "cat", "gatti" to "cat", "cane" to "dog", "cani" to "dog",
        "cibo" to "food", "pizza" to "pizza", "torta" to "cake", "auto" to "car", "macchina" to "car", "bici" to "bicycle",
        "bambino" to "baby", "neonato" to "baby", "bambini" to "child", "sorriso" to "smile", "festa" to "party",
        "matrimonio" to "wedding", "chiesa" to "church", "città" to "city", "edificio" to "building", "ponte" to "bridge",
        "tramonto" to "sunset", "notte" to "night", "piscina" to "swimming pool", "barca" to "boat", "aereo" to "airplane",
        "documento" to "paper", "scontrino" to "receipt", "testo" to "text", "occhiali" to "glasses", "selfie" to "selfie",
        "vestito" to "dress", "scarpe" to "shoe", "computer" to "computer", "telefono" to "mobile phone", "giocattolo" to "toy",
        "ristorante" to "restaurant", "vino" to "wine", "birra" to "beer", "casa" to "house", "giardino" to "garden",
        "cascata" to "waterfall", "roccia" to "rock", "erba" to "grass", "nuvole" to "cloud", "luna" to "moon",
    )

    suspend fun parse(c: Context, text: String): PhotoQuery {
        val persons = PhotoDb.get(c).persons().map { it.name }
        val low = text.lowercase(Locale.ITALY)
        val namedPeople = persons.filter { Regex("\\b${Regex.escape(it.lowercase(Locale.ITALY))}\\b").containsMatchIn(low) }
        // Prova con l'AI per capire meglio la richiesta (sinonimi, traduzioni, date)
        val ai = runCatching {
            val engine = AiRouter.engine(c)
            val sys = "Converti la richiesta di ricerca foto in JSON. Rispondi SOLO JSON: " +
                "{\"people\":[nomi di persone citate],\"labels_en\":[3-8 etichette inglesi semplici di oggetti/scene, es. mountain, lake, snow, dog, beach, food],\"keywords_it\":[parole chiave italiane e sinonimi],\"place\":\"nome luogo o vuoto\",\"from\":\"AAAA-MM-GG o vuoto\",\"to\":\"AAAA-MM-GG o vuoto\"}. " +
                "Persone conosciute: ${persons.joinToString()}. Oggi è ${Fmt.iso(System.currentTimeMillis())}."
            extractJson(engine.complete(sys, listOf(ChatMsg("user", text)), 400))
        }.getOrNull()
        fun list(k: String) = ai?.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it).trim() } }?.filter { it.isNotBlank() } ?: emptyList()
        val words = low.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }
        val stop = setOf("foto", "trova", "trovami", "mostra", "fammi", "vedere", "tutte", "delle", "dove", "sono", "con", "nelle", "della", "dello")
        val kw = (list("keywords_it") + words.filter { it !in stop && namedPeople.none { p -> p.equals(it, true) } }).distinct()
        val labels = (list("labels_en") + kw.mapNotNull { dict[it] }).map { it.lowercase(Locale.ROOT) }.distinct()
        return PhotoQuery(
            people = (namedPeople + list("people").filter { p -> persons.any { it.equals(p, true) } }).distinct(),
            labelsEn = labels,
            keywordsIt = kw,
            place = ai?.optString("place")?.takeIf { it.isNotBlank() },
            fromIso = ai?.optString("from")?.takeIf { it.length >= 10 },
            toIso = ai?.optString("to")?.takeIf { it.length >= 10 },
        )
    }

    fun run(c: Context, q: PhotoQuery, limit: Int = 2000): List<PhotoHit> {
        val db = PhotoDb.get(c)
        val persons = db.persons()
        // Persone: tutte le foto in cui compare ciascuna persona richiesta
        val personSets = q.people.mapNotNull { name -> persons.firstOrNull { it.name.equals(name, true) } }
            .map { db.photosOfPerson(it.id).toHashSet() }
        val onlyPeople = personSets.isNotEmpty() && q.labelsEn.isEmpty() && q.place == null &&
            q.keywordsIt.all { k -> q.people.any { it.equals(k, true) } }
        val hits = ArrayList<PhotoHit>()
        for (p in db.allPhotos()) {
            val iso = Fmt.iso(p.modified)
            if (q.fromIso != null && iso < q.fromIso) continue
            if (q.toIso != null && iso > q.toIso) continue
            if (personSets.isNotEmpty() && personSets.any { p.path !in it }) continue
            var score = if (personSets.isNotEmpty()) 5f else 0f
            val why = ArrayList<String>()
            if (personSets.isNotEmpty()) why.add(q.people.joinToString(" e "))
            val labels = p.labels.split(',')
            val cap = p.caption.lowercase(Locale.ITALY)
            val place = p.place.lowercase(Locale.ITALY)
            q.labelsEn.forEach { l -> if (labels.any { it == l || it.contains(l) }) { score += 2f; why.add(l) } }
            q.keywordsIt.forEach { k ->
                val kk = k.lowercase(Locale.ITALY)
                if (cap.contains(kk)) { score += 3f; why.add(k) }
                if (place.contains(kk)) { score += 4f; why.add("📍$k") }
            }
            q.place?.lowercase(Locale.ITALY)?.let { pl ->
                if (place.contains(pl) || cap.contains(pl)) { score += 6f; why.add("📍${q.place}") }
            }
            if (onlyPeople || score > 0f) hits.add(PhotoHit(p.path, score, why.distinct().joinToString(" · ")))
        }
        return hits.sortedWith(compareByDescending<PhotoHit> { it.score }).take(limit)
    }

    suspend fun search(c: Context, text: String): Pair<PhotoQuery, List<PhotoHit>> {
        val q = parse(c, text)
        return q to run(c, q)
    }
}
