package ie.stu.invoker.decks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire types for Archidekt's open deck API: GET https://archidekt.com/api/decks/{id}/
// Only the fields we need; the client ignores unknown keys.

@Serializable
data class ArchidektDeck(
    val name: String = "",
    val cards: List<ArchidektCard> = emptyList(),
)

@Serializable
data class ArchidektCard(
    val quantity: Int = 1,
    val categories: List<String> = emptyList(),
    val card: ArchidektInner = ArchidektInner(),
)

@Serializable
data class ArchidektInner(
    @SerialName("collectorNumber") val collectorNumber: String = "",
    val edition: ArchidektEdition = ArchidektEdition(),
    val oracleCard: ArchidektOracle = ArchidektOracle(),
)

@Serializable
data class ArchidektEdition(
    @SerialName("editioncode") val editionCode: String = "",
)

@Serializable
data class ArchidektOracle(
    val name: String = "",
)
