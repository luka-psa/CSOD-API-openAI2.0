package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.hearth.core.model.ShopKind
import app.hearth.core.model.ShoppingItem
import app.hearth.ui.resources.Res
import app.hearth.ui.resources.add
import app.hearth.ui.resources.shopping_add_hint
import app.hearth.ui.resources.shopping_empty
import app.hearth.ui.resources.shopping_list
import org.jetbrains.compose.resources.stringResource

/** The shared shopping list, grouped by shop. Tap anywhere on a line to tick it. */
@Composable
fun ShoppingScreen(sections: List<ShopSection>, onToggle: (ShoppingItem) -> Unit, onAdd: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(Res.string.shopping_list), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.size(18.dp))
        AddRow(onAdd)
        Spacer(Modifier.size(14.dp))
        Surface(Modifier.fillMaxWidth().weight(1f), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
            if (sections.isEmpty()) {
                EmptyHint(stringResource(Res.string.shopping_empty), Modifier.padding(24.dp))
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    sections.forEach { section ->
                        item(key = section.shop.id) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp)) {
                                Box(Modifier.size(10.dp).background(section.shop.kind.color(), CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text(section.shop.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(section.items.count { !it.bought }.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(section.items, key = { it.id }) { item -> ItemRow(item, onToggle, Modifier.animateItem()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, onToggle: (ShoppingItem) -> Unit, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onToggle(item) }.heightIn(min = 60.dp),
    ) {
        RoundCheck(item.bought, { onToggle(item) })
        Spacer(Modifier.width(6.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.bought) TextDecoration.LineThrough else null,
            color = if (item.bought) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        item.quantity?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 14.dp))
        }
    }
}

@Composable
private fun AddRow(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        onAdd(text)
        text = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(Res.string.shopping_add_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = submit, enabled = text.isNotBlank(), modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(Res.string.add))
        }
    }
}

/** A calm colour per kind of shop, so the eye finds "the pharmacy bit" quickly. */
private fun ShopKind.color() = when (this) {
    ShopKind.SUPERMARKET -> Color(0xFF4F7A5E)
    ShopKind.PHARMACY -> Color(0xFF3F6FB0)
    ShopKind.BAKERY -> Color(0xFFC79A5B)
    ShopKind.DIY -> Color(0xFFE0612F)
    ShopKind.FURNITURE -> Color(0xFF7A5BB0)
    ShopKind.OTHER -> Color(0xFF8A8079)
}
