package de.davscha.characterfun.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Wählt die aktuelle Seite; die Zurück-Taste geht eine Seite zurück. */
@Composable
fun App(vm: AppViewModel) {
    BackHandler(enabled = vm.screen != Screen.Gallery) { vm.back() }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (val s = vm.screen) {
            Screen.Gallery -> GalleryScreen(vm)
            Screen.Models -> ModelsScreen(vm)
            is Screen.Chat -> ChatScreen(vm)
            is Screen.Editor -> EditorScreen(vm, s.characterId)
        }
    }
}
