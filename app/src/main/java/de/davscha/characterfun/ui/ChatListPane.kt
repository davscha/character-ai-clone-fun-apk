package de.davscha.characterfun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davscha.characterfun.data.Character

/** Tablet-Layout: links die Chatliste (wie bei einem Messenger), rechts der offene Chat. */
@Composable
fun ChatListPane(vm: AppViewModel, modifier: Modifier) {
    val current = vm.chatCharacter
    // Der offene Chat steht immer oben, auch wenn noch nichts geschrieben wurde
    val chats: List<Pair<Character, String>> = buildList {
        current?.takeIf { c -> vm.recentChats.none { it.characterId == c.id } }?.let { add(it to tr("Neues Gespräch")) }
        vm.recentChats.forEach { chat -> vm.character(chat.characterId)?.let { add(it to chat.preview) } }
    }
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Chats"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { while (vm.screen != Screen.Gallery && vm.back()) Unit }) {
                Icon(Icons.Default.GridView, null)
                Spacer(Modifier.width(6.dp))
                Text(tr("Alle Figuren"))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        LazyColumn(Modifier.fillMaxSize()) {
            items(chats, key = { it.first.id }) { (c, preview) ->
                val selected = c.id == current?.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface)
                        .clickable(enabled = !selected) { vm.openChat(c, replace = true) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c, vm.repo.avatar(c), size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            preview,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
