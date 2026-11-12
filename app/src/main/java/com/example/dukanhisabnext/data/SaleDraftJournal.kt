package com.example.dukanhisabnext.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Remembers, for each cart (draftId), the exact moment (createdAt) its last save ATTEMPT used.
 * It is written BEFORE the save transaction starts. If the app is killed right after the sale was
 * saved but before the cart was emptied, the cart comes back after the restart with the same
 * draftId; SaleRepository then finds the sale saved at that exact moment and returns it instead of
 * saving the same cart a second time.
 */
interface SaleDraftJournal {
    fun attemptOf(draftId: String): Long?
    fun recordAttempt(draftId: String, createdAt: Long)
}

/** In memory only (tests, and the default). */
class InMemorySaleDraftJournal : SaleDraftJournal {
    private val attempts = LinkedHashMap<String, Long>()

    @Synchronized
    override fun attemptOf(draftId: String): Long? = attempts[draftId]

    @Synchronized
    override fun recordAttempt(draftId: String, createdAt: Long) {
        attempts.remove(draftId)
        attempts[draftId] = createdAt
        while (attempts.size > MAX_ENTRIES) attempts.remove(attempts.keys.first())
    }
}

/**
 * The journal in a small text file (one "draftId=createdAt" line per cart, the newest last, at most
 * [MAX_ENTRIES]). Written to a temporary file, flushed to the disk (fsync) and renamed, so a crash
 * leaves either the old or the new file. Outside the database and outside the backup.
 */
class FileSaleDraftJournal(private val file: File) : SaleDraftJournal {
    private val attempts = LinkedHashMap<String, Long>()

    init {
        try {
            if (file.exists()) {
                for (line in file.readLines(Charsets.UTF_8)) {
                    val i = line.indexOf('=')
                    val at = if (i > 0) line.substring(i + 1).toLongOrNull() else null
                    if (at != null) attempts[line.substring(0, i)] = at
                }
            }
        } catch (e: IOException) {
            attempts.clear() // unreadable: start empty (the in-memory double-tap guard still works)
        }
    }

    @Synchronized
    override fun attemptOf(draftId: String): Long? = attempts[draftId]

    @Synchronized
    override fun recordAttempt(draftId: String, createdAt: Long) {
        attempts.remove(draftId)
        attempts[draftId] = createdAt
        while (attempts.size > MAX_ENTRIES) attempts.remove(attempts.keys.first())
        val text = attempts.entries.joinToString("") { "${it.key}=${it.value}\n" }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("cannot write ${file.name}")
        }
    }
}

private const val MAX_ENTRIES = 50
