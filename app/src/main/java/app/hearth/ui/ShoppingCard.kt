package app.hearth.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.hearth.R
import app.hearth.core.model.ShopKind
import app.hearth.core.model.ShoppingItem

@Composable
fun ShoppingCard(
    sections: List<ShopSection>,
    onToggle: (ShoppingItem) -> Unit,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("🛒 " + stringResource(R.string.shopping_list), style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                if (sections.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.shopping_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                sections.forEach { section ->
                    item(key = section.shop.id) {
                        Text(
                            "${section.shop.kind.emoji()} ${section.shop.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(section.items, key = { it.id }) { item -> ItemRow(item, onToggle) }
                }
            }
            AddRow(onAdd)
        }
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, onToggle: (ShoppingItem) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onToggle(item) },
    ) {
        Checkbox(checked = item.bought, onCheckedChange = { onToggle(item) })
        Text(
            text = if (item.quantity != null) "${item.name}  ·  ${item.quantity}" else item.name,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.bought) TextDecoration.LineThrough else null,
            color = if (item.bought) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AddRow(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        onAdd(text)
        text = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.shopping_add_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = submit, enabled = text.isNotBlank(), modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.add))
        }
    }
}

private fun ShopKind.emoji() = when (this) {
    ShopKind.SUPERMARKET -> "🛒"
    ShopKind.PHARMACY -> "💊"
    ShopKind.BAKERY -> "🥖"
    ShopKind.DIY -> "🔨"
    ShopKind.FURNITURE -> "🛋️"
    ShopKind.OTHER -> "🏷️"
}
