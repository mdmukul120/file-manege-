package com.example.aifiles

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Organizer {
    val CATEGORIES = listOf("Images", "Videos", "Audio", "Documents", "Archives", "Apps", "Other")

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun storage(): File = Environment.getExternalStorageDirectory()

    // Scans Download, Documents and the storage root (not recursive, never touches other apps' folders).
    fun scan(): List<File> {
        val s = storage()
        val out = LinkedHashMap<String, File>()
        for (dir in listOf(File(s, "Download"), File(s, "Documents"), s)) {
            dir.listFiles()?.forEach { f ->
                val n = f.name.lowercase()
                val partial = n.endsWith(".crdownload") || n.endsWith(".part") || n.endsWith(".tmp")
                val tooNew = System.currentTimeMillis() - f.lastModified() < 2 * 60 * 1000
                if (f.isFile && !n.startsWith(".") && !partial && !tooNew) out[f.absolutePath] = f
            }
        }
        return out.values.take(300)
    }

    fun localCategory(f: File): String = when (f.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp" -> "Images"
        "mp4", "mkv", "avi", "mov", "3gp", "webm" -> "Videos"
        "mp3", "wav", "m4a", "aac", "ogg", "flac", "opus" -> "Audio"
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "epub" -> "Documents"
        "zip", "rar", "7z", "tar", "gz" -> "Archives"
        "apk", "xapk" -> "Apps"
        else -> "Other"
    }

    private fun askGemini(files: List<File>, key: String, model: String): Map<Int, String> {
        val prompt = "You organize files on a phone. Categories: ${CATEGORIES.joinToString()}. " +
            "For each numbered file choose exactly one category based on its name and type. " +
            "Reply ONLY with a JSON object mapping the number to the category.\n" +
            files.mapIndexed { i, f -> "$i: ${f.name} (${f.length() / 1024} KB)" }.joinToString("\n")
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-goog-api-key", key)
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
        if (code !in 200..299) throw IOException("Gemini error $code: ${text.take(200)}")
        val answer = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        val obj = JSONObject(answer)
        val result = HashMap<Int, String>()
        for (k in obj.keys()) {
            val cat = obj.getString(k)
            val idx = k.toIntOrNull() ?: continue
            result[idx] = if (cat in CATEGORIES) cat else "Other"
        }
        return result
    }

    private fun dedupeKey(f: File) =
        f.nameWithoutExtension.replace(Regex(" ?\\(\\d+\\)$"), "") + "." + f.extension + "|" + f.length()

    private fun uniqueTarget(dir: File, name: String): File {
        var t = File(dir, name)
        var i = 1
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        while (t.exists()) { t = File(dir, "$base ($i)$ext"); i++ }
        return t
    }

    private fun move(f: File, target: File): Boolean {
        target.parentFile?.mkdirs()
        if (f.renameTo(target)) return true
        return try { f.copyTo(target, overwrite = false); f.delete(); true } catch (e: Exception) { false }
    }

    /** Returns number of files moved. Never deletes: duplicates go to Organized/Duplicates. */
    fun run(ctx: Context, log: (String) -> Unit): Int {
        if (!hasAccess(ctx)) throw IOException("All files access permission not granted")
        val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val key = prefs.getString("key", "") ?: ""
        val model = prefs.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        val files = scan()
        log("Found ${files.size} files")
        if (files.isEmpty()) return 0

        val dupes = files.groupBy { dedupeKey(it) }.values.filter { it.size > 1 }
            .flatMap { g -> g.sortedBy { it.name.length }.drop(1) }.toSet()

        val categories = HashMap<File, String>()
        val rest = files.filter { it !in dupes }
        for (chunk in rest.chunked(100)) {
            val map = try {
                if (key.isBlank()) throw IOException("No API key, using local rules")
                askGemini(chunk, key, model)
            } catch (e: Exception) {
                log("AI unavailable (${e.message}); using local rules for ${chunk.size} files")
                emptyMap()
            }
            chunk.forEachIndexed { i, f -> categories[f] = map[i] ?: localCategory(f) }
        }

        val outRoot = File(storage(), "Organized")
        var moved = 0
        val record = JSONArray()
        for (f in files) {
            val folder = if (f in dupes) "Duplicates" else categories[f] ?: "Other"
            val target = uniqueTarget(File(outRoot, folder), f.name)
            val from = f.absolutePath
            if (move(f, target)) {
                moved++
                record.put(JSONObject().put("from", from).put("to", target.absolutePath))
                log("${f.name}  \u2192  $folder")
            } else log("Failed: ${f.name}")
        }
        prefs.edit().putString("lastMoves", record.toString())
            .putInt("total", prefs.getInt("total", 0) + moved)
            .putLong("lastRun", System.currentTimeMillis()).apply()
        log("Done. Moved $moved files into ${outRoot.path}")
        return moved
    }

    /** Restores every file moved by the last run back to its original place. */
    fun undo(ctx: Context, log: (String) -> Unit): Int {
        if (!hasAccess(ctx)) throw IOException("All files access permission not granted")
        val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("lastMoves", "[]"))
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val from = File(o.getString("from"))
            val to = File(o.getString("to"))
            if (!to.exists()) continue
            val dest = if (from.exists()) uniqueTarget(from.parentFile ?: continue, from.name) else from
            if (move(to, dest)) { n++; log("Restored ${dest.name}") }
        }
        prefs.edit().putString("lastMoves", "[]").putInt("total", maxOf(0, prefs.getInt("total", 0) - n)).apply()
        return n
    }
}
