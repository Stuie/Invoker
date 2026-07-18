package ie.stu.invoker

import ie.stu.invoker.config.ConfigService
import ie.stu.invoker.decks.CardImageSyncService
import ie.stu.invoker.decks.DeckUrlImporter
import ie.stu.invoker.decks.ScryfallService
import ie.stu.invoker.decks.XMageCardDb
import ie.stu.invoker.download.Downloader
import ie.stu.invoker.install.InstalledState
import ie.stu.invoker.install.Paths
import ie.stu.invoker.platform.JavaDetector
import ie.stu.invoker.platform.PlatformInfo
import ie.stu.invoker.platform.detectPlatform
import ie.stu.invoker.process.AppLog
import ie.stu.invoker.process.XMageRunner

/** Single composition root constructed at app startup. */
class AppEnvironment(
    val platform: PlatformInfo = detectPlatform(),
    val paths: Paths = Paths(Paths.defaultInstallRoot(detectPlatform()), detectPlatform()),
) {
    val installedState: InstalledState = InstalledState(paths.installedProperties)
    val configService: ConfigService = ConfigService()
    val downloader: Downloader = Downloader()
    val runner: XMageRunner = XMageRunner(paths)
    val javaDetector: JavaDetector = JavaDetector(platform)
    val scryfallService: ScryfallService = ScryfallService()
    val deckUrlImporter: DeckUrlImporter = DeckUrlImporter()
    val xmageCardDb: XMageCardDb = XMageCardDb(paths)
    val cardImageSyncService: CardImageSyncService =
        CardImageSyncService(scryfallService, paths.cardImagesRoot, xmageCardDb)

    init {
        // Start persisting launcher diagnostics as early as possible so a failed launch leaves a
        // trail in xmage_launcher.log even if the user never opens the F3 overlay.
        AppLog.setLogFile(paths.logFile)
        AppLog.i("Install root: ${paths.installRoot}  (os=${platform.os})")
    }
}
