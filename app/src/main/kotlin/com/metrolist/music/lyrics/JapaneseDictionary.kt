package com.metrolist.music.lyrics

import android.content.Context
import com.atilika.kuromoji.ipadic.Tokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * The dictionary kanji are read with: 33 MB, more than half of the app, so it is not shipped but downloaded when the
 * user asks for it. It comes as kuromoji's own published package from Maven Central, checked against its fingerprint,
 * and only its dictionary files are kept. Without it kana still romanize and kanji stay as written.
 */
object JapaneseDictionary {
    private const val URL = "https://repo1.maven.org/maven2/com/atilika/kuromoji/kuromoji-ipadic/0.9.0/kuromoji-ipadic-0.9.0.jar"
    private const val SHA256 = "24909fd751c0b439f7af5131b080eb65bc85062f0c4977ca4a71c76abe74e0b6"
    private const val PREFIX = "com/atilika/kuromoji/ipadic/"
    private val FILES = listOf(
        "characterDefinitions.bin", "connectionCosts.bin", "doubleArrayTrie.bin", "tokenInfoDictionary.bin",
        "tokenInfoFeaturesMap.bin", "tokenInfoPartOfSpeechMap.bin", "tokenInfoTargetMap.bin", "unknownDictionary.bin",
    )
    const val DOWNLOAD_MB = 13

    @Volatile private var dir: File? = null
    private var tokenizer: Tokenizer? = null
    private var unavailable = false

    private val _installed = MutableStateFlow(false)
    val installed: StateFlow<Boolean> = _installed

    private val _progress = MutableStateFlow<Float?>(null)
    /** 0..1 while downloading, null otherwise. */
    val progress: StateFlow<Float?> = _progress

    fun init(context: Context) {
        val d = File(context.filesDir, "kuromoji")
        dir = d
        _installed.value = FILES.all { File(d, it).isFile }
    }

    private class FromFiles(dir: File) : Tokenizer.Builder() {
        init {
            resolver = com.atilika.kuromoji.util.ResourceResolver { name -> File(dir, name).inputStream().buffered() }
        }
    }

    /** The tokenizer, or null while the dictionary isn't installed. */
    fun tokenizer(): Tokenizer? = synchronized(this) {
        tokenizer ?: if (unavailable) null else {
            val d = dir
            // tests and older builds still carry the dictionary inside the app
            val made = if (d != null && _installed.value) runCatching { FromFiles(d).build() }.getOrNull() else runCatching { Tokenizer() }.getOrNull()
            if (made == null) unavailable = true
            tokenizer = made
            made
        }
    }

    suspend fun install(): Result<Unit> = withContext(Dispatchers.IO) {
        val d = dir ?: return@withContext Result.failure(IllegalStateException("not ready"))
        runCatching {
            _progress.value = 0f
            val part = File(d.parentFile, "kuromoji.download")
            OkHttpClient().newCall(Request.Builder().url(URL).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val total = r.body.contentLength().takeIf { it > 0 } ?: (DOWNLOAD_MB * 1_000_000L)
                val digest = MessageDigest.getInstance("SHA-256")
                part.outputStream().use { out ->
                    val input = r.body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        _progress.value = (done.toFloat() / total).coerceAtMost(0.99f)
                    }
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                if (hash != SHA256) {
                    part.delete()
                    error("The download didn't match")
                }
            }
            d.mkdirs()
            ZipInputStream(part.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.removePrefix(PREFIX)
                    if (entry.name.startsWith(PREFIX) && name in FILES) File(d, name).outputStream().use { zip.copyTo(it) }
                }
            }
            part.delete()
            if (!FILES.all { File(d, it).isFile }) error("The download was incomplete")
            synchronized(this@JapaneseDictionary) {
                tokenizer = null
                unavailable = false
            }
            _installed.value = true
        }.also { _progress.value = null }
    }

    fun remove() = synchronized(this) {
        dir?.deleteRecursively()
        tokenizer = null
        unavailable = true
        _installed.value = false
    }
}
