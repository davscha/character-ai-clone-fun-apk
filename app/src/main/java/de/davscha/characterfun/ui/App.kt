package de.davscha.characterfun.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Wählt die aktuelle Seite; die Zurück-Taste geht eine Seite zurück. */
@Composable
fun App(vm: AppViewModel) {
    BackHandler(enabled = vm.screen != Screen.Gallery) { vm.back() }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        // Breiter Bildschirm (Tablet quer): Chatliste links, Chat rechts
        val wide = maxWidth >= 900.dp
        when (val s = vm.screen) {
            Screen.Gallery -> GalleryScreen(vm)
            Screen.Models -> ModelsScreen(vm)
            is Screen.Chat -> if (wide) {
                Row(Modifier.fillMaxSize()) {
                    ChatListPane(vm, Modifier.width(320.dp).fillMaxHeight())
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                    Box(Modifier.weight(1f)) { ChatScreen(vm) }
                }
            } else ChatScreen(vm)
            is Screen.Editor -> EditorScreen(vm, s.characterId)
            is Screen.Room -> RoomScreen(vm)
            is Screen.RoomEditor -> RoomEditorScreen(vm, s.roomId)
        }
    }
}
