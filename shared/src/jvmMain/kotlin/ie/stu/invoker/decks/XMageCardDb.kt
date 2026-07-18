package ie.stu.invoker.decks

import ie.stu.invoker.install.Paths
import ie.stu.invoker.process.AppLog
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.Driver
import java.util.Properties
import kotlin.io.path.name

/** The printing XMage will actually render for a card — the authority for the image filename. */
data class ResolvedPrinting(
    val name: String,
    val setCode: String,
    val cardNumber: String,
    val usesVariousArt: Boolean,
)

/**
 * Reads XMage's own card database (`mage-client/db/cards.h2.mv.db`) to resolve each deck card to the
 * exact printing XMage renders — set code, collector number, and the `usesVariousArt` flag that
 * decides whether the collector number appears in the image filename. This is the only reliable way
 * to make image filenames match, because XMage resolves cards through this DB (often remapping a
 * deck's pinned printing to its own preferred one) rather than honouring the deck or Scryfall.
 *
 * Resolution mirrors XMage's `CardRepository.findCard` + `findPreferredOrLatestCard` and the
 * `DckDeckImporter` flow: exact (set, number) match first, then the card's preferred/latest printing.
 *
 * Access details:
 * - We load H2 from XMage's own bundled `h2-*.jar`, guaranteeing file-format compatibility.
 * - We connect with `AUTO_SERVER=TRUE` (XMage's own mode) and empty credentials, so we can read the
 *   database whether or not XMage is currently running — while it runs we join its H2 auto-server;
 *   while it's closed we open the file directly. `IGNORECASE=TRUE` matches XMage's case-insensitive
 *   name lookups.
 */
class XMageCardDb(private val paths: Paths) {

    private val dbBase: Path = paths.xmageRoot.resolve("mage-client").resolve("db").resolve("cards.h2")
    private val dbFile: Path = paths.xmageRoot.resolve("mage-client").resolve("db").resolve("cards.h2.mv.db")

    fun isInstalled(): Boolean = Files.exists(dbFile) && h2Jar() != null

    /** Opens a resolver session, or null if the DB/driver is missing or won't open (callers then fall back). */
    fun open(): Session? {
        val jar = h2Jar() ?: return null
        if (!Files.exists(dbFile)) return null
        return try {
            val driver = loadDriver(jar)
            val base = dbBase.toString().replace('\\', '/')
            val url = "jdbc:h2:file:$base;IFEXISTS=TRUE;AUTO_SERVER=TRUE;IGNORECASE=TRUE"
            // XMage creates the DB with an empty username; the H2 default "sa" is rejected.
            val props = Properties().apply {
                setProperty("user", "")
                setProperty("password", "")
            }
            val conn = driver.connect(url, props) ?: return null
            Session(conn)
        } catch (e: Exception) {
            AppLog.w("Card DB: couldn't open (${e.message}); falling back to Scryfall resolution")
            null
        }
    }

    private fun h2Jar(): Path? {
        val libDir = paths.xmageRoot.resolve("mage-client").resolve("lib")
        if (!Files.exists(libDir)) return null
        return Files.list(libDir).use { stream ->
            stream.filter { it.name.startsWith("h2-") && it.name.endsWith(".jar") }.findFirst().orElse(null)
        }
    }

    private fun loadDriver(jar: Path): Driver {
        val loader = URLClassLoader(arrayOf(jar.toUri().toURL()), javaClass.classLoader)
        val cls = Class.forName("org.h2.Driver", true, loader)
        return cls.getDeclaredConstructor().newInstance() as Driver
    }

    private data class CardRow(
        val name: String,
        val setCode: String,
        val cardNumber: String,
        val variousArt: Boolean,
    )

    private data class SetInfo(val standardLegal: Boolean, val releaseDate: String)

    inner class Session(private val conn: Connection) : AutoCloseable {

        // setCode -> (standardLegal, releaseDate). Loaded once; small table.
        private val expansions: Map<String, SetInfo> = loadExpansions()

        private fun loadExpansions(): Map<String, SetInfo> {
            val map = HashMap<String, SetInfo>()
            conn.prepareStatement("SELECT code, type, releaseDate FROM expansion").use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val code = rs.getString("code") ?: continue
                        val type = rs.getString("type") ?: ""
                        val standardLegal = type == "CORE" || type == "EXPANSION" || type == "SUPPLEMENTAL_STANDARD_LEGAL"
                        map[code.uppercase()] = SetInfo(standardLegal, rs.getString("releaseDate") ?: "")
                    }
                }
            }
            return map
        }

        /**
         * Resolves a deck card to the printing XMage will render, or null if XMage's DB doesn't know
         * it (XMage couldn't show it either). Mirrors [DckDeckImporter]: exact (set, number) first,
         * then the preferred/latest printing.
         */
        fun resolve(name: String, setCode: String?, cardNumber: String?): ResolvedPrinting? {
            if (setCode != null && cardNumber != null) {
                val exact = findExact(setCode, cardNumber)
                if (exact != null && exact.name.equals(name, ignoreCase = true)) return exact.toResolved()
            }
            val candidates = findByName(name)
            return findPreferredOrLatest(candidates, setCode ?: "").toResolved()
        }

        private fun CardRow?.toResolved(): ResolvedPrinting? =
            this?.let { ResolvedPrinting(it.name, it.setCode, it.cardNumber, it.variousArt) }

        private fun findExact(setCode: String, cardNumber: String): CardRow? {
            conn.prepareStatement(
                "SELECT name, setCode, cardNumber, variousArt FROM card " +
                    "WHERE setCode = ? AND cardNumber = ? AND nightCard = FALSE LIMIT 1",
            ).use { ps ->
                ps.setString(1, setCode)
                ps.setString(2, cardNumber)
                ps.executeQuery().use { rs -> return if (rs.next()) rs.toCardRow() else null }
            }
        }

        private fun findByName(name: String): List<CardRow> {
            var rows = queryByColumn("name", name)
            if (rows.isEmpty() && name.contains(" // ")) {
                rows = queryByColumn("name", name.substringBefore(" // "))
            }
            if (rows.isEmpty()) {
                rows = queryByAltNames(name)
            }
            return rows
        }

        private fun queryByColumn(column: String, value: String): List<CardRow> {
            conn.prepareStatement(
                "SELECT name, setCode, cardNumber, variousArt FROM card WHERE $column = ? AND nightCard = FALSE",
            ).use { ps ->
                ps.setString(1, value)
                ps.executeQuery().use { rs ->
                    val out = ArrayList<CardRow>()
                    while (rs.next()) out += rs.toCardRow()
                    return out
                }
            }
        }

        private fun queryByAltNames(name: String): List<CardRow> {
            conn.prepareStatement(
                "SELECT name, setCode, cardNumber, variousArt FROM card WHERE nightCard = FALSE AND " +
                    "(flipCardName = ? OR secondSideName = ? OR spellOptionCardName = ? OR doubleFacedSecondSideName = ?)",
            ).use { ps ->
                for (i in 1..4) ps.setString(i, name)
                ps.executeQuery().use { rs ->
                    val out = ArrayList<CardRow>()
                    while (rs.next()) out += rs.toCardRow()
                    return out
                }
            }
        }

        private fun java.sql.ResultSet.toCardRow() = CardRow(
            name = getString("name") ?: "",
            setCode = (getString("setCode") ?: "").uppercase(),
            cardNumber = getString("cardNumber") ?: "",
            variousArt = getBoolean("variousArt"),
        )

        /** Replicates XMage's CardRepository.findPreferredOrLatestCard. */
        private fun findPreferredOrLatest(cards: List<CardRow>, preferredSetCode: String): CardRow? {
            if (cards.isEmpty()) return null
            val preferred = preferredSetCode.uppercase()
            var cardToUse: CardRow? = null
            var lastExpansionDate: String? = null
            var lastReleaseDate: String? = null
            for (card in cards) {
                val set = expansions[card.setCode] ?: continue
                if (preferred.isNotEmpty() && preferred == card.setCode) return card
                if (set.standardLegal && (lastExpansionDate == null || set.releaseDate > lastExpansionDate!!)) {
                    cardToUse = card
                    lastExpansionDate = set.releaseDate
                }
                if (lastExpansionDate == null && (lastReleaseDate == null || set.releaseDate > lastReleaseDate!!)) {
                    cardToUse = card
                    lastReleaseDate = set.releaseDate
                }
            }
            return cardToUse
        }

        override fun close() {
            runCatching { conn.close() }
        }
    }
}
