package ie.stu.invoker.decks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire types for the Scryfall REST API. `ignoreUnknownKeys = true` on the client covers the many
// fields we don't model. See https://scryfall.com/docs/api.

@Serializable
data class ScryfallIdentifier(
    val name: String? = null,
    val set: String? = null,
    @SerialName("collector_number") val collectorNumber: String? = null,
)

@Serializable
data class ScryfallCollectionRequest(val identifiers: List<ScryfallIdentifier>)

@Serializable
data class ScryfallCollectionResponse(
    val data: List<ScryfallCard> = emptyList(),
    @SerialName("not_found") val notFound: List<ScryfallIdentifier> = emptyList(),
)

@Serializable
data class ScryfallCard(
    val name: String,
    val set: String,
    @SerialName("collector_number") val collectorNumber: String,
    @SerialName("image_uris") val imageUris: ScryfallImageUris? = null,
    @SerialName("card_faces") val cardFaces: List<ScryfallCardFace> = emptyList(),
) {
    /**
     * URL for the requested [quality]. Double-faced cards (transform/MDFC) carry no top-level
     * `image_uris`; the front face is the first entry of [cardFaces].
     */
    fun imageUrl(quality: ImageQuality): String? =
        imageUris?.forKey(quality) ?: cardFaces.firstOrNull()?.imageUris?.forKey(quality)
}

@Serializable
data class ScryfallCardFace(
    val name: String = "",
    @SerialName("image_uris") val imageUris: ScryfallImageUris? = null,
)

@Serializable
data class ScryfallImageUris(
    val small: String? = null,
    val normal: String? = null,
    val large: String? = null,
    val png: String? = null,
    @SerialName("art_crop") val artCrop: String? = null,
    @SerialName("border_crop") val borderCrop: String? = null,
) {
    fun forKey(quality: ImageQuality): String? = when (quality) {
        ImageQuality.Small -> small
        ImageQuality.Normal -> normal
        ImageQuality.Large -> large
        ImageQuality.Best -> png
    } ?: large ?: normal
}

/** One page of a `/cards/search` result. Used to count printings per set for the filename rule. */
@Serializable
data class ScryfallSearchPage(
    val data: List<ScryfallNamedCard> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_page") val nextPage: String? = null,
    @SerialName("total_cards") val totalCards: Int = 0,
)

@Serializable
data class ScryfallNamedCard(val name: String = "")

/** `/symbology` — the full set of mana/tap/etc. symbols with their SVG URLs. */
@Serializable
data class ScryfallSymbolList(val data: List<ScryfallSymbol> = emptyList())

@Serializable
data class ScryfallSymbol(@SerialName("svg_uri") val svgUri: String = "")
