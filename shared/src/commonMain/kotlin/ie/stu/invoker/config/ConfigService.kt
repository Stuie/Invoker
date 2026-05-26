package ie.stu.invoker.config

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val DEFAULT_XMAGE_HOME = "https://xmage.today"

class ConfigService(
    private val client: HttpClient = defaultClient(),
) {
    suspend fun fetch(homeUrl: String = DEFAULT_XMAGE_HOME): RemoteConfig {
        val base = homeUrl.trimEnd('/')
        return client.get("$base/config.json").body()
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
