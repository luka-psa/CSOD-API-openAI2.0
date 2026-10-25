package app.hearth.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.hearth.core.model.Lang
import app.hearth.core.model.Topic
import app.hearth.core.model.title
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
