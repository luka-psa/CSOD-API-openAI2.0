package app.hearth.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.add
import app.hearth.ui.resources.delete
import androidx.compose.material3.IconButton
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import app.hearth.core.model.BacklogItem
import app.hearth.core.model.Ease
import app.hearth.core.model.isHabit
import app.hearth.ui.resources.ease_easy
import app.hearth.ui.resources.ease_effort
import app.hearth.ui.resources.ease_hard
import app.hearth.ui.resources.small_step_line
import app.hearth.core.model.Lang
import app.hearth.core.model.Plan
import app.hearth.core.model.Section
import app.hearth.core.model.dayLabel
import app.hearth.core.model.intervalLabel
import app.hearth.core.model.isRoutine
import app.hearth.core.model.lastDoneLabel
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

/** "Open lijst ›"; [compact] shows only the arrow in a soft circle, for narrow dashboard columns. */
@Composable
fun CardAction(label: String, onClick: () -> Unit, compact: Boolean = false) {
    if (compact) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(HearthIcons.ChevronRight, label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
        return
    }
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

/** The project a step belongs to, in the project colour. */
@Composable
fun ProjectTag(name: String) {
    val s = Section.PROJECT.style()
    Text(
        name,
        style = MaterialTheme.typography.labelSmall,
        color = s.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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

/**
 * The quiet status line for any open item: a routine's rhythm ("om de 2 weken · 5 dagen geleden"),
 * waiting admin ("wacht op antwoord · opvolgen maandag"), or the plan.
 */
fun statusText(item: BacklogItem, today: LocalDate, lang: Lang): String? {
    val nl = lang == Lang.NL
    return when {
        item.waiting -> listOfNotNull(
            if (nl) "wacht op antwoord" else "waiting for a reply",
            item.localDate?.let { (if (nl) "opvolgen " else "follow up ") + dayLabel(it, today, lang) },
        ).joinToString(" · ")
        // Habits are lived, not ticked: no "done" language.
        item.isHabit && item.plan != Plan.DAY -> item.intervalDays?.let { intervalLabel(it, lang) }
        item.isRoutine() && item.plan != Plan.DAY -> "${intervalLabel(item.intervalDays, lang)} · ${lastDoneLabel(item, today, lang)}"
        else -> planText(item, today, lang)
    }
}

/** "dringend" in the accent colour: a nudge, not an alarm. */
@Composable
fun UrgentTag(lang: Lang) {
    Text(
        if (lang == Lang.NL) "dringend" else "urgent",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** One backlog line: tick, text, section (or project) + status, and an optional trailing action. */
@Composable
fun BacklogRow(
    item: BacklogItem,
    today: LocalDate,
    lang: Lang,
    onDone: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    projectName: String? = null,
    showSection: Boolean = true,
    onDelete: (() -> Unit)? = null,
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
        // Habits are lived, not ticked off.
        if (item.isHabit) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(HearthIcons.Sun, null, tint = Section.HABIT.style().ink, modifier = Modifier.size(20.dp))
            }
        } else {
            RoundCheck(checked = item.done, onClick = onDone)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.urgent) UrgentTag(lang)
                if (projectName != null) ProjectTag(projectName) else if (showSection) SectionTag(item, lang)
                statusText(item, today, lang)?.let {
                    if (item.finishBy) Icon(HearthIcons.Flag, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item.smallStep?.let {
                Text(stringResource(Res.string.small_step_line, it), style = MaterialTheme.typography.labelMedium, color = Section.HABIT.style().ink)
            }
        }
        trailing?.invoke()
        if (onDelete != null) DeleteButton(onDelete)
    }
}

/** How much something asks: green, amber or orange, with a word. Never red. */
@Composable
fun EasePill(ease: Ease, label: String? = null, onClick: (() -> Unit)? = null) {
    val (ink, tint) = easeColors(ease)
    Text(
        label ?: stringResource(ease.label()),
        style = MaterialTheme.typography.labelMedium,
        color = ink,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(tint)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

fun Ease.label() = when (this) {
    Ease.EASY -> Res.string.ease_easy
    Ease.EFFORT -> Res.string.ease_effort
    Ease.HARD -> Res.string.ease_hard
}

/** Green runs easily, amber takes effort, orange is too much. */
@Composable
fun easeColors(ease: Ease): Pair<Color, Color> = when (ease) {
    Ease.EASY -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer
    Ease.EFFORT -> Section.RHYTHM.style().ink to Section.RHYTHM.style().tint
    Ease.HARD -> Section.PROJECT.style().ink to Section.PROJECT.style().tint
}

/** The quiet X at the end of a line. It always asks before anything goes. */
@Composable
fun DeleteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(HearthIcons.Close, stringResource(Res.string.delete), tint = HearthColors.InkFaint, modifier = Modifier.size(20.dp))
    }
}

/** Selectable rounded chip used for filters and plan choices. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int? = null,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface)
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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

/** A one-line "add" field with a button; Enter adds too. [compact] shows a round + instead of "Voeg toe". */
@Composable
fun AddRow(hint: String, onAdd: (String) -> Unit, modifier: Modifier = Modifier, muted: Boolean = false, compact: Boolean = false) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        onAdd(text)
        text = ""
    }
    val fill = if (muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = fill,
                focusedContainerColor = fill,
                unfocusedBorderColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        if (compact) {
            FilledTonalIconButton(onClick = submit, enabled = text.isNotBlank(), modifier = Modifier.size(52.dp)) {
                Icon(HearthIcons.Add, stringResource(Res.string.add))
            }
        } else {
            FilledTonalButton(onClick = submit, enabled = text.isNotBlank(), modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(Res.string.add))
            }
        }
    }
}
