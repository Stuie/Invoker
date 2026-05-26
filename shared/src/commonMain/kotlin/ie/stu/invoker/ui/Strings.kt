package ie.stu.invoker.ui

/**
 * Every user-visible string in the app. Grouped by surface so a future Compose Resources
 * (or other i18n) migration is a single-file impl swap — no string-hunting through the UI.
 *
 * Conventions:
 * - Templates use `%s` (string) / `%d` (int) placeholders and are passed through `String.format`.
 * - Where pluralisation matters, separate singular/plural constants exist (e.g. JAVA_DETECTION_FOUND_*).
 *   English-style "(s)" suffixes are avoided.
 * - contentDescription for icons that are purely decorative and have an adjacent label is left null
 *   at the call site (not centralised here).
 */
object Strings {
    // ── Frame & destinations (used by nav rail and pane headers) ────────────
    const val FRAME_TITLE = "Invoker — XMage Launcher"
    const val HOME = "Home"
    const val SETTINGS = "Settings"
    const val COMMUNITY = "Community"
    const val ABOUT = "About"

    // ── Accessibility / window titles ───────────────────────────────────────
    const val CONTENT_DESC_WORDMARK = "XMage"
    const val CONSOLE_TITLE_CLIENT = "XMage Client %d"
    const val CONSOLE_TITLE_SERVER = "XMage Server"

    // ── Home pane ────────────────────────────────────────────────────────────
    const val HOME_TAGLINE = "A MAGIC: THE GATHERING CLIENT"

    // Chip labels (status)
    const val HOME_CHIP_NOT_INSTALLED = "Not installed"
    const val HOME_CHIP_UP_TO_DATE = "Up to date"
    const val HOME_CHIP_UPDATE_AVAILABLE = "Update available"
    const val HOME_CHIP_DOWNLOADING = "Downloading…"
    const val HOME_CHIP_OFFLINE = "Offline"

    // Version line phrases
    const val HOME_VL_READY_TO_INSTALL = "Ready to install"
    const val HOME_VL_INSTALLED = "Installed"
    const val HOME_VL_UPDATE_TO_AVAILABLE = "Update to %s available"
    const val HOME_VL_WORKING_ON_IT = "Working on it"
    const val HOME_VL_OFFLINE = "Couldn't reach the XMage server"
    const val HOME_VL_VERSION_PREFIX = "v%s"

    // Buttons
    const val HOME_BTN_INSTALL_XMAGE = "Install XMage v%s"
    const val HOME_BTN_INSTALL_UPDATE = "Install Update"
    const val HOME_BTN_RUN_CLIENT = "Run Client"
    const val HOME_BTN_RUN_CLIENT_SERVER = "Run Client + Server"
    const val HOME_BTN_RUN_SERVER = "Run Server"
    const val HOME_BTN_STOP_SERVER = "Stop Server"
    const val HOME_BTN_CHECK_UPDATES = "Check for updates"
    const val HOME_BTN_OPEN_SETTINGS = "Open Settings"
    const val HOME_BTN_RETRY = "Retry"

    // Update / offline banners
    const val HOME_BANNER_UPDATE_HEADING = "Update available · %s"
    const val HOME_BANNER_UPDATE_SUB = "You're on %s. Updates include client fixes and rule patches."
    const val HOME_BANNER_OFFLINE_HEADING = "Couldn't reach the XMage server"
    const val HOME_BANNER_OFFLINE_SUB = "Check your connection, or open Settings to change the home URL."

    // Progress
    const val HOME_PROGRESS_DEFAULT_LABEL = "Downloading…"
    const val HOME_PROGRESS_PERCENT = "%d%%"

    // Action-meta lines
    const val HOME_META_FRESH = "A bundled Java runtime will be installed alongside XMage."
    const val HOME_META_LAST_CHECKED = "Last checked · just now"
    const val HOME_META_SERVER_RUNNING = "Server is running locally"
    const val HOME_META_DOWNLOADING = "This won't take long. The launcher will reopen when it's done."
    const val HOME_META_OFFLINE = "Open Settings to change the home URL."

    // Placeholder when a version is missing
    const val PLACEHOLDER_VERSION = "—"

    // ── About pane ───────────────────────────────────────────────────────────
    const val ABOUT_APP_NAME = "Invoker"
    const val ABOUT_APP_DESCRIPTION_PREFIX = "A reimagined launcher for XMage · "
    const val ABOUT_CHIP_READY_TO_INSTALL = "Ready to install · %s"
    const val ABOUT_CHIP_READY_TO_INSTALL_NO_VERSION = "Ready to install"
    const val ABOUT_CHIP_UPDATE_AVAILABLE = "Update available · %s"
    const val ABOUT_CHIP_UPDATE_AVAILABLE_NO_VERSION = "Update available"
    const val ABOUT_CHIP_UP_TO_DATE = "Everything's current"
    const val ABOUT_CHIP_OFFLINE = "Offline"
    const val ABOUT_BTN_INSTALL_XMAGE = "Install XMage"
    const val ABOUT_BTN_INSTALL_UPDATE = "Install Update"
    const val ABOUT_ATTR_XMAGE_CLIENT = "XMage client"
    const val ABOUT_ATTR_LATEST = "Latest available"
    const val ABOUT_ATTR_CHANNEL = "Channel"
    const val ABOUT_ATTR_LICENSE = "License"
    const val ABOUT_VALUE_NOT_INSTALLED = "Not installed"
    const val ABOUT_VALUE_LICENSE = "MIT · Open source"

    // ── Community pane ───────────────────────────────────────────────────────
    const val COMMUNITY_SUBTITLE = "Open in your browser."
    const val COMMUNITY_LINK_SITE_TITLE = "xmage.today"
    const val COMMUNITY_LINK_SITE_DESC = "Home, docs, downloads"
    const val COMMUNITY_LINK_NEWS_TITLE = "News"
    const val COMMUNITY_LINK_NEWS_DESC = "Patch notes & announcements"
    const val COMMUNITY_LINK_DISCORD_TITLE = "Discord"
    const val COMMUNITY_LINK_DISCORD_DESC = "Community chat & matchmaking"
    const val COMMUNITY_LINK_GITHUB_TITLE = "GitHub"
    const val COMMUNITY_LINK_GITHUB_DESC = "Source code & issues"
    const val COMMUNITY_TRADEMARK =
        "Invoker is an independent launcher for XMage, a volunteer-built open-source Magic: " +
            "The Gathering client. Magic: The Gathering and all related properties are " +
            "trademarks of Wizards of the Coast. Neither Invoker nor XMage is affiliated " +
            "with Wizards of the Coast."

    // ── Settings pane ────────────────────────────────────────────────────────
    const val SETTINGS_SUBTITLE = "Changes apply on next launch."
    const val SETTINGS_GROUP_BRANCH_RUNTIME = "Branch & Runtime"
    const val SETTINGS_GROUP_CONSOLE = "Console"
    const val SETTINGS_GROUP_ADVANCED_JAVA = "Advanced Java"

    const val SETTINGS_CHANNEL_LABEL = "Update channel"
    const val SETTINGS_CHANNEL_DESC = "Which XMage source the launcher tracks."
    const val SETTINGS_CHANNEL_MAIN = "Main"
    const val SETTINGS_CHANNEL_CUSTOM = "Custom"

    const val SETTINGS_JAVA_LABEL = "Java for XMage"
    const val SETTINGS_JAVA_DESC = "Which Java runtime is used to launch the XMage client and server. XMage expects Java 8."

    const val SETTINGS_HOME_URL_LABEL = "Home URL"
    const val SETTINGS_HOME_URL_DESC = "Where the launcher checks for updates."

    const val SETTINGS_SHOW_CLIENT_CONSOLE_LABEL = "Show client console"
    const val SETTINGS_SHOW_CLIENT_CONSOLE_DESC = "Open a separate window with live client output when launching."
    const val SETTINGS_SHOW_SERVER_CONSOLE_LABEL = "Show server console"
    const val SETTINGS_SHOW_SERVER_CONSOLE_DESC = "Open a separate window with live server output when launching."
    const val SETTINGS_TEST_MODE_LABEL = "Server test mode"
    const val SETTINGS_TEST_MODE_DESC = "Pass -testMode=true to the server. Enables XMage's testing tools."

    const val SETTINGS_CLIENT_OPTS_LABEL = "Client JVM options"
    const val SETTINGS_CLIENT_OPTS_DESC = "Power users only. Default sets a 2GB heap and UTF-8 encoding."
    const val SETTINGS_SERVER_OPTS_LABEL = "Server JVM options"
    const val SETTINGS_SERVER_OPTS_DESC = "Power users only. Default sets a 1GB heap."
    const val SETTINGS_STARTUP_DELAY_LABEL = "Client startup delay"
    const val SETTINGS_STARTUP_DELAY_DESC = "Seconds to wait after launching the server before launching the client."

    // ── JavaSourcePicker ─────────────────────────────────────────────────────
    const val JAVA_BTN_CHANGE = "Change"
    const val JAVA_BTN_DONE = "Done"
    const val JAVA_SUMMARY_BUNDLED_TITLE = "Bundled"
    const val JAVA_SUMMARY_BUNDLED_SUB = "downloaded by Invoker"
    const val JAVA_SUMMARY_CUSTOM_TITLE = "Custom Java"
    const val JAVA_OPTION_BUNDLED_TITLE = "Bundled — downloaded by Invoker"
    const val JAVA_OPTION_BUNDLED_DETAIL = "Java 8 fetched from the configured XMage home. Recommended."
    const val JAVA_OPTION_CUSTOM_TITLE = "Custom path"
    const val JAVA_OPTION_CHOOSE_FOLDER_TITLE = "Choose folder…"
    const val JAVA_OPTION_CHOOSE_FOLDER_DETAIL = "Point Invoker at any Java installation on this machine."
    const val JAVA_OPTION_SELECTED = "Selected"
    const val JAVA_OPTION_HOME_ENV_SUFFIX = " · JAVA_HOME"
    const val JAVA_DETECTION_SCANNING = "Scanning for installed JREs…"
    const val JAVA_DETECTION_FOUND_SINGULAR = "Found 1 compatible JRE on this machine."
    const val JAVA_DETECTION_FOUND_PLURAL = "Found %d compatible JREs on this machine."
    const val JAVA_DETECTION_REFRESH = "Refresh"
    const val JAVA_ERROR_WRONG_VERSION = "Selected %s, but XMage expects Java 8."
    const val JAVA_ERROR_VALIDATION_GENERIC = "Couldn't validate that folder."
    const val JAVA_ERROR_NOT_VALID = "Not a valid Java installation. Expected a JDK or JRE directory containing bin/%s."
    const val JAVA_OVERRIDE_BUTTON = "Use this folder anyway"
    const val JAVA_OVERRIDE_HINT = "Invoker won't be able to verify it works until you click Run."
    const val JAVA_DIALOG_TITLE = "Choose a Java 8 installation folder"
    // Formatting helpers (consumed by ui-side `displayName(DetectedJava)`)
    const val JAVA_DISPLAY_NAME = "Java %s"
    const val JAVA_DISPLAY_NAME_WITH_VENDOR = "Java %s · %s"

    // ── Progress labels (progressLabel set by MainViewModel) ────────────────
    const val PROGRESS_DOWNLOADING_JAVA = "Downloading Java"
    const val PROGRESS_DOWNLOADING_XMAGE = "Downloading XMage"
    const val PROGRESS_INSTALLING_JAVA = "Installing Java"
    const val PROGRESS_INSTALLING_XMAGE = "Installing XMage"

    // ── Snackbar messages emitted by MainViewModel ──────────────────────────
    const val SNACKBAR_LATEST = "You're on the latest version of XMage."
    const val SNACKBAR_UPDATE_AVAILABLE = "Version %s available. Click Install Update to update."
    const val SNACKBAR_UPDATED = "Updated to version %s."
    const val SNACKBAR_INSTALLED = "XMage %s installed."
    const val SNACKBAR_UPDATE_ERROR = "Couldn't install updates: %s"
    const val SNACKBAR_INSTALL_ERROR = "Couldn't install XMage: %s"
    const val SNACKBAR_CONFIG_ERROR = "Couldn't reach the XMage server."
    const val SNACKBAR_RUNNING_SINGULAR = "Close XMage before installing updates. 1 running instance."
    const val SNACKBAR_RUNNING_PLURAL = "Close XMage before installing updates. %d running instances."
}
