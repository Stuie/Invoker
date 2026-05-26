package ie.stu.invoker.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteConfig(
    val java: JavaInfo,
    @SerialName("XMage") val xmage: XMageInfo,
)

@Serializable
data class JavaInfo(
    val version: String,
    val location: String,
)

@Serializable
data class XMageInfo(
    val version: String,
    val location: String,
    val locations: List<String> = emptyList(),
    val full: String = "",
    val torrent: String = "",
    val images: String = "",
    @SerialName("Launcher") val launcher: LauncherInfo,
)

@Serializable
data class LauncherInfo(
    val version: String,
    val location: String,
)
