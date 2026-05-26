package ie.stu.invoker.download

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

data class DownloadProgress(val bytesRead: Long, val total: Long?) {
    val fraction: Float? = total?.takeIf { it > 0 }?.let { bytesRead.toFloat() / it.toFloat() }
}

class Downloader(private val client: HttpClient = defaultClient()) {

    private val _progress = MutableStateFlow(DownloadProgress(0, null))
    val progress: StateFlow<DownloadProgress> = _progress

    /** Tries [primary] first, then each entry of [mirrors] until one succeeds. */
    suspend fun download(primary: String, mirrors: List<String> = emptyList(), destination: Path): Path {
        val urls = (listOf(primary) + mirrors).filter { it.isNotBlank() }
        var lastError: Exception? = null
        for (url in urls) {
            try {
                return downloadOne(url, destination)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("All download URLs failed for $destination", lastError)
    }

    private suspend fun downloadOne(url: String, destination: Path): Path {
        Files.createDirectories(destination.parent ?: destination.toAbsolutePath().parent)
        client.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw IOException("HTTP ${response.status.value} for $url")
            }
            val total = response.contentLength()
            _progress.value = DownloadProgress(0, total)
            val channel: ByteReadChannel = response.bodyAsChannel()
            Files.newOutputStream(destination).use { out ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead = 0L
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    bytesRead += read
                    _progress.value = DownloadProgress(bytesRead, total)
                }
            }
        }
        return destination
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 60 * 60 * 1000L
                socketTimeoutMillis = 5 * 60 * 1000L
            }
        }
    }
}
