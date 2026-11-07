package de.davscha.bildtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Lädt eine Datei mit Fortsetzen (die halbe Datei liegt als ".part" daneben). */
object Downloader {

    suspend fun download(f: ModelFile, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val target = f.file
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        var url = URL(f.url)
        var redirects = 0
        while (true) {
            val already = if (part.exists()) part.length() else 0L
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "Bildtest-Android")
                if (already > 0) setRequestProperty("Range", "bytes=$already-")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IOException("Umleitung ohne Ziel")
                conn.disconnect()
                if (++redirects > 10) throw IOException("Zu viele Umleitungen")
                url = URL(url, location)
                continue
            }
            if (code == 416 && already > 0) {
                // Schon komplett
                conn.disconnect()
                if (!part.renameTo(target)) throw IOException("Datei konnte nicht gespeichert werden")
                return@withContext
            }
            val append = code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !append) {
                conn.disconnect()
                throw IOException("Server antwortet mit Fehler $code (${f.path})")
            }
            val total = conn.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: conn.contentLengthLong.takeIf { it > 0 && !append }
                ?: -1L
            var done = if (append) already else 0L
            val buffer = ByteArray(1 shl 16)
            var last = 0L
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (done - last > 4_000_000) {
                            last = done
                            onProgress(done, total)
                        }
                    }
                }
            }
            conn.disconnect()
            onProgress(done, total)
            if (total > 0 && part.length() != total) throw IOException("Datei unvollständig (${f.path})")
            if (!part.renameTo(target)) throw IOException("Datei konnte nicht gespeichert werden")
            return@withContext
        }
    }
}
