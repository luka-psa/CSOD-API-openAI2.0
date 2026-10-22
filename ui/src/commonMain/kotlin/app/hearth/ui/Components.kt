package app.hearth.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.model.dayLabel
import app.hearth.ui.theme.HearthColors
import app.hearth.ui.theme.style
import kotlinx.datetime.LocalDate

/** The one card style used everywhere: white, soft corners, no borders, a title row. */
@Composable
fun HearthCard(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 36.dp)) {
                if (icon != null) {
                    Box(
                        Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}

@Composable
fun CardAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Icon(HearthIcons.ChevronRight, null, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 10.dp),
    )
}

/** Small coloured label: the section an item belongs to. */
@Composable
fun SectionTag(item: BacklogItem, lang: Lang) {
    val s = item.section.style()
    Text(
        item.section.label(lang),
        style = MaterialTheme.typography.labelSmall,
        color = s.ink,
        modifier = Modifier.background(s.tint, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** "vandaag", "klaar tegen vrijdag", "volgende" as a quiet label. Earlier days read "sinds dinsdag", never "late". */
fun planText(item: BacklogItem, today: LocalDate, lang: Lang): String? {
    val nl = lang == Lang.NL
    return when (item.plan) {
        Plan.UNPLANNED -> null
        Plan.NEXT -> if (nl) "volgende" else "next up"
        Plan.LATER -> if (nl) "later" else "later"
        Plan.DAY -> item.localDate?.let { d ->
            val day = dayLabel(d, today, lang)
            if (item.finishBy) (if (nl) "klaar tegen $day" else "done by $day") else day
        }
    }
}

/** A big round tick target. Fills with the accent when done. */
@Composable
fun RoundCheck(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(if (checked) MaterialTheme.colorScheme.secondary else Color.Transparent)
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(fill, CircleShape)
                .border(2.dp, if (checked) MaterialTheme.colorScheme.secondary else HearthColors.InkFaint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(HearthIcons.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** One backlog line: tick, text, section + plan, and an optional trailing action. */
@Composable
fun BacklogRow(
    item: BacklogItem,
    today: LocalDate,
    lang: Lang,
    onDone: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .heightIn(min = 64.dp)
            .padding(end = 4.dp),
    ) {
        RoundCheck(checked = false, onClick = onDone)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTag(item, lang)
                planText(item, today, lang)?.let {
                    if (item.finishBy) Icon(HearthIcons.Flag, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        trailing?.invoke()
    }
}

/** Selectable rounded chip used for filters and plan choices. */
@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Int? = null, icon: ImageVector? = null) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface)
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        if (badge != null && badge > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                badge.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}
