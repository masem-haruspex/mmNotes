package com.mlib.notes

import androidx.activity.compose.BackHandler
import com.mlib.notes.ui.SettingsScreen
import com.mlib.notes.ui.NoteEditorScreen
import com.mlib.notes.ui.CategoryScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.mlib.future.FutureTheme
import com.mlib.notes.ui.ThemePreferences
import com.mlib.notes.ui.themeSets
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			val context = LocalContext.current
			val themePreferences = remember { ThemePreferences(context) }
			val scope = rememberCoroutineScope()
			val isDarkMode = isSystemInDarkTheme()
			val selectedThemeIndex by themePreferences.selectedThemeIndexFlow.collectAsState(initial = 0)
			val config = if (isDarkMode) themeSets[selectedThemeIndex].dark else themeSets[selectedThemeIndex].light

			FutureTheme(config = config) {
				val categoryBackstack = remember { mutableStateListOf<String?>(null) }
				val currentCategoryId = categoryBackstack.lastOrNull()
				var editingNoteId by remember { mutableStateOf<String?>(null) }
				var showSettings by remember { mutableStateOf(false) }

				BackHandler(enabled = showSettings || categoryBackstack.size > 1) {
					when {
						showSettings -> showSettings = false
						categoryBackstack.size > 1 -> categoryBackstack.removeLast()
					}
				}

				when {

					showSettings -> SettingsScreen(
						selectedThemeIndex = selectedThemeIndex,
						onThemeSelected = { newIndex -> scope.launch { themePreferences.updateSelectedThemeIndex(newIndex) } },
						onBack = { showSettings = false }
					)

					editingNoteId != null -> NoteEditorScreen(
						noteId = editingNoteId!!,
						onBack = { editingNoteId = null }
					)

					else -> CategoryScreen(
						categoryId = currentCategoryId,
						onCategoryClick = { categoryBackstack.add(it) },
						onNoteClick = { editingNoteId = it },
						onNavigateTo = { targetId ->
							if (targetId == null) {
								categoryBackstack.clear(); categoryBackstack.add(null)
							} else {
								val index = categoryBackstack.indexOf(targetId)
								if (index != -1) {
									while (categoryBackstack.size > index + 1) categoryBackstack.removeLast()
								} else {
									categoryBackstack.add(targetId)
								}
							}
						},
						onSettingsClick = { showSettings = true }
					)
				}
			}
		}
	}
}
