package ie.stu.invoker.decks

import ie.stu.invoker.BuildInfo
import ie.stu.invoker.ui.Strings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

enum class CardStatus { Downloaded, SkippedExisting, NotFound, Failed }

data class CardResult(
    val display: String,
    val setCode: String?,
    val status: CardStatus,
    val detail: String? = null,
)

data class SyncProgress(
    val completed: Int,
    val total: Int,
    val results: List<CardResult>,
    val done: Boolean,
)

/**
 * Resolves a deck's cards and downloads their images into XMage's `plugins/images` cache.
 *
 * Preferred path: when [cardDb] can be read, each card is resolved through **XMage's own card
 * database**, giving the exact set + collector number + `usesVariousArt` XMage will render — the only
 * way to guarantee the image filenames match. Scryfall is then used purely to fetch the image bytes.
 *
 * Fallback path (DB missing/locked): resolve via Scryfall directly (strict-exact printing) and infer
 * `usesVariousArt` from a per-set print count. Less accurate — XMage may render a different printing —
 * but keeps the feature working.
 *
 * Emits a [SyncProgress] after each card. Owns its own image [HttpClient] and progress accounting;
 * image downloads hit the un-rate-limited `cards.scryfall.io` CDN.
 */
class CardImageSyncService(
    private val scryfall: ScryfallService,
    private val imagesRoot: Path,
    private val cardDb: XMageCardDb? = null,
    private val http: HttpClient = imageClient(),
    private val concurrency: Int = 6,
) {
    private data class Ident(val id: ScryfallIdentifier, val display: String, val setHint: String?)

    fun sync(entries: List<DeckEntry>, quality: ImageQuality): Flow<SyncProgress> = channelFlow {
        val session = cardDb?.open()
        try {
            if (session != null) runViaCardDb(entries, quality, session)
            else runViaScryfall(entries, quality)
        } finally {
            session?.close()
        }
    }.flowOn(Dispatchers.IO)

    // ── Preferred path: resolve through XMage's card DB ──────────────────────

    private data class DbItem(val display: String, val printing: ResolvedPrinting?)

    private suspend fun ProducerScope<SyncProgress>.runViaCardDb(
        entries: List<DeckEntry>,
        quality: ImageQuality,
        session: XMageCardDb.Session,
    ) {
        // Resolve each distinct deck card to the printing XMage renders. A null printing means XMage's
        // DB doesn't know the card — it couldn't show an image for it either, so it's genuinely NotFound.
        val distinct = entries.distinctBy { "${it.name}|${it.setCode}|${it.collectorNumber}".lowercase() }
        val items = distinct.map { e -> DbItem(e.name, session.resolve(e.name, e.setCode, e.collectorNumber)) }
        val resolved = items.mapNotNull { it.printing }
        val distinctSets = resolved.map { it.setCode }.distinct()
        val total = items.size + 1 + distinctSets.size // + mana symbols + per-set symbols
        send(SyncProgress(0, total, emptyList(), false))
        if (items.isEmpty()) {
            send(SyncProgress(0, 0, emptyList(), true))
            return
        }

        // Primary image lookup by the exact (set, number) XMage uses.
        val byKey = scryfall.resolveCollection(
            resolved.map { ScryfallIdentifier(set = it.setCode.lowercase(), collectorNumber = it.cardNumber) }
                .distinctBy { "${it.set}|${it.collectorNumber}" },
        ).data.associateBy { "${it.set}|${it.collectorNumber}".lowercase() }
        // For printings Scryfall lacks (rare set/number differences), fall back to a name lookup just
        // for the image bytes — the filename still uses XMage's set/number.
        val needName = resolved.filter { byKey[keyOf(it)] == null }.map { it.name }.distinct()
        val byName = if (needName.isNotEmpty()) {
            scryfall.resolveCollection(needName.map { ScryfallIdentifier(name = it) })
                .data.associateBy { it.name.lowercase() }
        } else {
            emptyMap()
        }

        val results = CopyOnWriteArrayList<CardResult>()
        val completed = AtomicInteger(0)
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            items.forEach { item ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        val result = resolveAndDownload(item, quality, byKey, byName)
                        results.add(result)
                        send(SyncProgress(completed.incrementAndGet(), total, results.toList(), false))
                    }
                }
            }
            launchSymbolTasks(this, this@runViaCardDb, distinctSets, semaphore, results, completed, total)
        }
        send(SyncProgress(completed.get(), total, results.toList(), true))
    }

    /**
     * Launches the mana-symbol and per-set-symbol fetch tasks on [scope] (so the enclosing
     * `coroutineScope` awaits them), emitting progress through [producer]. Shared by both sync paths.
     */
    private fun launchSymbolTasks(
        scope: kotlinx.coroutines.CoroutineScope,
        producer: ProducerScope<SyncProgress>,
        sets: List<String>,
        semaphore: Semaphore,
        results: CopyOnWriteArrayList<CardResult>,
        completed: AtomicInteger,
        total: Int,
    ) {
        scope.launch(Dispatchers.IO) {
            semaphore.withPermit {
                results.add(fetchManaSymbols())
                producer.send(SyncProgress(completed.incrementAndGet(), total, results.toList(), false))
            }
        }
        sets.forEach { set ->
            scope.launch(Dispatchers.IO) {
                semaphore.withPermit {
                    results.add(fetchSetSymbols(set))
                    producer.send(SyncProgress(completed.incrementAndGet(), total, results.toList(), false))
                }
            }
        }
    }

    private suspend fun resolveAndDownload(
        item: DbItem,
        quality: ImageQuality,
        byKey: Map<String, ScryfallCard>,
        byName: Map<String, ScryfallCard>,
    ): CardResult {
        val rp = item.printing
            ?: return CardResult(item.display, null, CardStatus.NotFound, "not in XMage's card database")
        val card = byKey[keyOf(rp)] ?: byName[rp.name.lowercase()]
        val url = card?.imageUrl(quality)
            ?: return CardResult(rp.name, rp.setCode, CardStatus.Failed, "no image on Scryfall")

        // Filename uses XMage's authoritative set/number/various-art. The display name is Scryfall's
        // (which matches XMage's for split/DFC combined names); we also write the DB's own name when it
        // differs (e.g. a DFC stored under its front-face name) so either convention is covered.
        val targets = buildList {
            add(CardImageFilename.build(rp.setCode, card.name, rp.cardNumber, rp.usesVariousArt))
            if (!rp.name.equals(card.name, ignoreCase = true)) {
                add(CardImageFilename.build(rp.setCode, rp.name, rp.cardNumber, rp.usesVariousArt))
            }
        }.distinct().map { resolveRelative(imagesRoot, it) }

        return downloadToTargets(url, targets, rp.name, rp.setCode)
    }

    private fun keyOf(rp: ResolvedPrinting) = "${rp.setCode.lowercase()}|${rp.cardNumber.lowercase()}"

    // ── Fallback path: resolve via Scryfall only ─────────────────────────────

    private suspend fun ProducerScope<SyncProgress>.runViaScryfall(entries: List<DeckEntry>, quality: ImageQuality) {
        val idents = buildIdentifiers(entries)
        val total = idents.size
        send(SyncProgress(0, total, emptyList(), false))
        if (total == 0) {
            send(SyncProgress(0, 0, emptyList(), true))
            return
        }

        val response = scryfall.resolveCollection(idents.map { it.id })
        val cards = response.data
        val matches: List<Pair<Ident, ScryfallCard?>> = idents.map { it to findCard(it, cards) }
        val distinctSets = matches.mapNotNull { it.second?.set?.uppercase() }.distinct()
        distinctSets.forEach { scryfall.warmSet(it.lowercase()) }
        val fullTotal = total + 1 + distinctSets.size // + mana symbols + per-set symbols

        val results = CopyOnWriteArrayList<CardResult>()
        val completed = AtomicInteger(0)
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            matches.forEach { (ident, card) ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        val result = if (card == null) {
                            CardResult(ident.display, ident.setHint, CardStatus.NotFound)
                        } else {
                            val usesVariousArt = scryfall.usesVariousArt(card.name, card.set)
                            val url = card.imageUrl(quality)
                            if (url == null) {
                                CardResult(card.name, card.set, CardStatus.Failed, "no image available")
                            } else {
                                val rel = CardImageFilename.build(card.set, card.name, card.collectorNumber, usesVariousArt)
                                downloadToTargets(url, listOf(resolveRelative(imagesRoot, rel)), card.name, card.set)
                            }
                        }
                        results.add(result)
                        send(SyncProgress(completed.incrementAndGet(), fullTotal, results.toList(), false))
                    }
                }
            }
            launchSymbolTasks(this, this@runViaScryfall, distinctSets, semaphore, results, completed, fullTotal)
        }
        send(SyncProgress(completed.get(), fullTotal, results.toList(), true))
    }

    private fun buildIdentifiers(entries: List<DeckEntry>): List<Ident> {
        val jobs = LinkedHashMap<String, Ident>()
        for (e in entries) {
            val id = when {
                e.setCode != null && e.collectorNumber != null ->
                    ScryfallIdentifier(set = e.setCode.lowercase(), collectorNumber = e.collectorNumber)
                e.setCode != null -> ScryfallIdentifier(name = e.name, set = e.setCode.lowercase())
                else -> ScryfallIdentifier(name = e.name)
            }
            val key = listOf(id.name, id.set, id.collectorNumber).joinToString("|").lowercase()
            jobs.putIfAbsent(key, Ident(id, e.name, e.setCode))
        }
        return jobs.values.toList()
    }

    private fun findCard(ident: Ident, cards: List<ScryfallCard>): ScryfallCard? {
        val id = ident.id
        return when {
            id.collectorNumber != null && id.set != null ->
                cards.firstOrNull { it.set.equals(id.set, true) && it.collectorNumber.equals(id.collectorNumber, true) }
            id.name != null && id.set != null ->
                cards.firstOrNull { it.name.equals(id.name, true) && it.set.equals(id.set, true) }
                    ?: cards.firstOrNull { it.name.equals(id.name, true) }
            id.name != null ->
                cards.firstOrNull { it.name.equals(id.name, true) }
                    ?: cards.firstOrNull { it.name.startsWith(id.name, true) }
            else -> null
        }
    }

    // ── Shared download ──────────────────────────────────────────────────────

    /** Downloads [url] once and ensures every path in [targets] holds the image. */
    private suspend fun downloadToTargets(
        url: String,
        targets: List<Path>,
        display: String,
        setCode: String,
    ): CardResult {
        if (targets.all { Files.exists(it) && Files.size(it) > 0 }) {
            return CardResult(display, setCode, CardStatus.SkippedExisting)
        }
        val first = targets.first()
        val tmp = first.resolveSibling(first.fileName.toString() + ".part")
        return try {
            Files.createDirectories(first.parent)
            streamTo(url, tmp)
            for ((index, target) in targets.withIndex()) {
                if (Files.exists(target) && Files.size(target) > 0) continue
                Files.createDirectories(target.parent)
                if (index == 0) {
                    try {
                        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: Exception) {
                        Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                } else {
                    Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            CardResult(display, setCode, CardStatus.Downloaded)
        } catch (e: Exception) {
            CardResult(display, setCode, CardStatus.Failed, e.message)
        } finally {
            runCatching { Files.deleteIfExists(tmp) }
        }
    }

    private suspend fun streamTo(url: String, tmp: Path) {
        http.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value}")
            val channel: ByteReadChannel = response.bodyAsChannel()
            Files.newOutputStream(tmp).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    private fun resolveRelative(root: Path, rel: String): Path =
        rel.split('/').fold(root) { acc, segment -> acc.resolve(segment) }

    // ── Symbols (mana + set) ─────────────────────────────────────────────────

    private val symbolsRoot: Path get() = imagesRoot.resolve("symbols")

    // XMage rarity letter -> the word used in the Gatherer set-symbol URL.
    private val setSymbolRarities = listOf("C" to "common", "U" to "uncommon", "R" to "rare", "M" to "mythic")

    /**
     * Fetches the complete mana/tap/etc. symbol set (SVG) from Scryfall into `symbols/svg/`. It's a
     * small, fixed set (~70 files), so this is a one-time global fetch, not per-deck. XMage renders
     * mana symbols from these SVGs.
     */
    private suspend fun fetchManaSymbols(): CardResult {
        val urls = runCatching { scryfall.symbolSvgUrls() }.getOrDefault(emptyList())
        if (urls.isEmpty()) return CardResult(Strings.DECKS_SYMBOL_MANA, null, CardStatus.Failed, "couldn't list symbols")
        val dir = symbolsRoot.resolve("svg")
        var downloaded = 0
        var skipped = 0
        for (url in urls) {
            val fileName = url.substringAfterLast('/').substringBefore('?')
            if (fileName.isBlank() || !fileName.endsWith(".svg")) continue
            val target = dir.resolve(fileName)
            if (Files.exists(target) && Files.size(target) > 0) {
                skipped++
                continue
            }
            if (writeUrl(url, target)) downloaded++
        }
        val status = if (downloaded > 0) CardStatus.Downloaded else CardStatus.SkippedExisting
        return CardResult(Strings.DECKS_SYMBOL_MANA, null, status, "$downloaded new · $skipped present")
    }

    /**
     * Fetches a set's rarity symbols (`symbols/large/<SET>-<C|U|R|M>.png`) from Wizards' Gatherer CDN.
     * Missing rarities (e.g. a set with no mythics) simply 404 and are skipped.
     */
    private suspend fun fetchSetSymbols(setCode: String): CardResult {
        val set = setCode.uppercase()
        val dir = symbolsRoot.resolve("large")
        var downloaded = 0
        var skipped = 0
        for ((letter, word) in setSymbolRarities) {
            val target = dir.resolve("$set-$letter.png")
            if (Files.exists(target) && Files.size(target) > 0) {
                skipped++
                continue
            }
            val url = "https://gatherer-static.wizards.com/set_symbols/$set/large-$word-$set.png"
            if (writeUrl(url, target)) downloaded++
        }
        val status = when {
            downloaded > 0 -> CardStatus.Downloaded
            skipped > 0 -> CardStatus.SkippedExisting
            else -> CardStatus.Failed
        }
        return CardResult(String.format(Strings.DECKS_SYMBOL_SET, set), set, status, "$downloaded new · $skipped present")
    }

    /** Downloads [url] to [target] atomically. Returns false on any failure (e.g. 404), never throws. */
    private suspend fun writeUrl(url: String, target: Path): Boolean = runCatching {
        Files.createDirectories(target.parent)
        val tmp = target.resolveSibling(target.fileName.toString() + ".part")
        try {
            streamTo(url, tmp)
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            true
        } finally {
            runCatching { Files.deleteIfExists(tmp) }
        }
    }.getOrDefault(false)

    companion object {
        fun imageClient(): HttpClient = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
            install(HttpRequestRetry) {
                maxRetries = 2
                retryOnServerErrors(maxRetries = 2)
                retryOnExceptionIf(maxRetries = 2) { _, cause -> cause is java.io.IOException }
                exponentialDelay(base = 2.0, maxDelayMs = 5_000)
            }
            install(DefaultRequest) {
                header(HttpHeaders.UserAgent, "Invoker/${BuildInfo.LAUNCHER_VERSION}")
                header(HttpHeaders.Accept, "image/*,*/*;q=0.8")
            }
        }
    }
}
