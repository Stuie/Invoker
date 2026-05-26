package ie.stu.invoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Article
import com.composables.icons.materialsymbols.outlined.Chat
import com.composables.icons.materialsymbols.outlined.Code
import com.composables.icons.materialsymbols.outlined.Open_in_new
import com.composables.icons.materialsymbols.outlined.Public
import ie.stu.invoker.ui.theme.Theme
import java.awt.Desktop
import java.net.URI

private data class CommunityLink(
    val title: String,
    val url: String,
    val description: String,
    val icon: ImageVector,
)

private val LINKS = listOf(
    CommunityLink(Strings.COMMUNITY_LINK_SITE_TITLE, "https://xmage.today/", Strings.COMMUNITY_LINK_SITE_DESC, MaterialSymbols.Outlined.Public),
    CommunityLink(Strings.COMMUNITY_LINK_NEWS_TITLE, "https://xmage.today/news/", Strings.COMMUNITY_LINK_NEWS_DESC, MaterialSymbols.Outlined.Article),
    CommunityLink(Strings.COMMUNITY_LINK_DISCORD_TITLE, "https://discord.gg/xmage", Strings.COMMUNITY_LINK_DISCORD_DESC, MaterialSymbols.Outlined.Chat),
    CommunityLink(Strings.COMMUNITY_LINK_GITHUB_TITLE, "https://github.com/magefree/mage", Strings.COMMUNITY_LINK_GITHUB_DESC, MaterialSymbols.Outlined.Code),
)

@Composable
fun CommunityPane() {
    Column(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 44.dp, bottom = 28.dp)) {
        PaneHeader(Strings.COMMUNITY, subtitle = Strings.COMMUNITY_SUBTITLE)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            LINKS.chunked(2).forEach { rowLinks ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                    rowLinks.forEach { link ->
                        Box(Modifier.weight(1f)) { CommunityCard(link) }
                    }
                    if (rowLinks.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            Strings.COMMUNITY_TRADEMARK,
            color = Theme.Fg3,
            fontSize = 12.5.sp,
        )
    }
}

@Composable
private fun CommunityCard(link: CommunityLink) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.RadiusCard.dp))
            .background(Theme.Surface1)
            .border(1.dp, Theme.Line1, RoundedCornerShape(Theme.RadiusCard.dp))
            .clickableNoRipple { openUrl(link.url) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Theme.Line1, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(link.icon, contentDescription = null, tint = Theme.Fg1, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(link.title, color = Theme.Fg1, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(link.url, color = Theme.Fg3, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(14.dp))
        Icon(MaterialSymbols.Outlined.Open_in_new, contentDescription = null, tint = Theme.Fg3, modifier = Modifier.size(16.dp))
    }
}

private fun openUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}
