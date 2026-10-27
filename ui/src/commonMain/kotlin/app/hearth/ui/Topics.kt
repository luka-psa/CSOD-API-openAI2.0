package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hearth.core.model.Lang
import app.hearth.core.model.Topic
import app.hearth.core.model.TopicGroup
import app.hearth.core.model.title
import app.hearth.ui.theme.HearthColors
import app.hearth.ui.theme.style

// One topic, one name, one icon, one colour, on every screen. The name lives in core (Topic.title, so the
// tools and the AI use the same words), the colour in the theme (Topic.style), the icon here.

fun Topic.icon() = when (this) {
    Topic.FREE_TIME -> HearthIcons.Sofa
    Topic.GOALS -> HearthIcons.Star
    Topic.ADMIN -> HearthIcons.Paper
    Topic.PROJECTS -> HearthIcons.Wrench
    Topic.HOME -> HearthIcons.Home
    Topic.RHYTHM -> HearthIcons.Repeat
    Topic.SHOPPING -> HearthIcons.Cart
    Topic.MENU -> HearthIcons.Restaurant
    Topic.FRICTION -> HearthIcons.Bolt
    Topic.RULES -> HearthIcons.Rule
}

/** A topic's card: its name, icon and colour, and optionally a glance ("3 open · 1 dringend") under the name. */
@Composable
fun TopicCard(
    topic: Topic,
    lang: Lang,
    modifier: Modifier = Modifier,
    glance: String? = null,
    action: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = HearthCard(topic.title(lang), topic.icon(), modifier, action, topic.style(), glance, onTitleClick, content)

/** "Doen & eten" and "Ritme & afspraken" above their tiles, so the two groups read the same on every screen. */
@Composable
fun GroupLabel(group: TopicGroup, lang: Lang, modifier: Modifier = Modifier) =
    Text(group.title(lang), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(start = 6.dp))

/** The secondary side: the rhythm and agreements on a shelf a shade deeper than the canvas, next to what we do and eat. */
@Composable
fun Shelf(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = Column(
    modifier.background(HearthColors.Shelf, RoundedCornerShape(32.dp)).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    content = content,
)
