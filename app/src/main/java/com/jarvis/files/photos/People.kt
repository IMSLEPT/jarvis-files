package com.jarvis.files.photos

import android.content.Context

/** Gruppo di volti simili non ancora associato a un nome. */
data class FaceCluster(val id: Long, val faces: List<FaceRow>)

/** Raggruppa i volti e gestisce le persone con nome (come in Google Foto). */
object People {
    /** Somiglianza minima (coseno) per assegnare automaticamente un volto a una persona con nome. */
    const val ASSIGN_THRESHOLD = 0.62f
    /** Somiglianza minima per mettere due volti sconosciuti nello stesso gruppo. */
    private const val CLUSTER_THRESHOLD = 0.66f

    /** Raggruppamento greedy dei volti senza nome. Restituisce i gruppi dal più grande. */
    fun clusterUnknown(c: Context, minSize: Int = 2): List<FaceCluster> {
        val db = PhotoDb.get(c)
        val faces = db.faces("person_id = 0")
        val centroids = ArrayList<FloatArray>()
        val sums = ArrayList<FloatArray>()
        val members = ArrayList<MutableList<FaceRow>>()
        for (f in faces) {
            var best = -1
            var bestSim = CLUSTER_THRESHOLD
            for (i in centroids.indices) {
                val s = FaceEmbedder.cosine(centroids[i], f.emb)
                if (s > bestSim) { bestSim = s; best = i }
            }
            if (best < 0) {
                centroids.add(f.emb.copyOf()); sums.add(f.emb.copyOf()); members.add(mutableListOf(f))
            } else {
                val sum = sums[best]
                for (k in sum.indices) sum[k] += f.emb[k]
                centroids[best] = FaceEmbedder.normalize(sum)
                members[best].add(f)
            }
        }
        val assign = HashMap<Long, Long>()
        val out = members.mapIndexedNotNull { i, list ->
            val id = (i + 1).toLong()
            list.forEach { assign[it.id] = id }
            if (list.size >= minSize) FaceCluster(id, list) else null
        }.sortedByDescending { it.faces.size }
        db.setClusters(assign)
        return out
    }

    /** Dà un nome a un gruppo di volti; se il nome esiste già, li unisce a quella persona. */
    fun nameFaces(c: Context, faceIds: Collection<Long>, name: String): Long {
        val db = PhotoDb.get(c)
        val pid = db.createPerson(name)
        db.setFacePerson(faceIds, pid)
        refresh(c, pid)
        return pid
    }

    /** Ricalcola il "volto medio" della persona e cerca altre sue foto tra i volti senza nome. */
    fun refresh(c: Context, personId: Long) {
        val db = PhotoDb.get(c)
        val mine = db.faces("person_id = ?", arrayOf(personId.toString()))
        if (mine.isEmpty()) return
        val sum = FloatArray(mine[0].emb.size)
        mine.forEach { f -> for (k in sum.indices) sum[k] += f.emb[k] }
        val centroid = FaceEmbedder.normalize(sum)
        db.setCentroid(personId, centroid)
        // Un volto sconosciuto entra se somiglia al volto medio OPPURE molto a uno dei volti già confermati
        val extra = db.faces("person_id = 0").filter { u ->
            FaceEmbedder.cosine(centroid, u.emb) >= ASSIGN_THRESHOLD ||
                mine.any { FaceEmbedder.cosine(it.emb, u.emb) >= 0.72f }
        }.map { it.id }
        if (extra.isNotEmpty()) db.setFacePerson(extra, personId)
    }

    /** "Non è lui/lei": toglie il volto dalla persona e lo esclude dai suggerimenti automatici. */
    fun rejectFace(c: Context, faceId: Long, personId: Long) {
        PhotoDb.get(c).setFacePerson(listOf(faceId), -1)
        refresh(c, personId)
    }

    fun mergePersons(c: Context, keep: Long, remove: Long) {
        val db = PhotoDb.get(c)
        val ids = db.faces("person_id = ?", arrayOf(remove.toString())).map { it.id }
        db.setFacePerson(ids, keep)
        db.deletePerson(remove)
        refresh(c, keep)
    }
}
