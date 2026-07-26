package ie.stu.invoker.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Group
import com.composables.icons.materialsymbols.outlined.Home
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.Playing_cards
import com.composables.icons.materialsymbols.outlined.Settings

enum class Destination(val label: String, val icon: ImageVector) {
    Home(Strings.HOME, MaterialSymbols.Outlined.Home),
    Decks(Strings.DECKS, MaterialSymbols.Outlined.Playing_cards),
    Settings(Strings.SETTINGS, MaterialSymbols.Outlined.Settings),
    Community(Strings.COMMUNITY, MaterialSymbols.Outlined.Group),
    About(Strings.ABOUT, MaterialSymbols.Outlined.Info),
}
