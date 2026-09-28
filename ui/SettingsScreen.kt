package com.mlib.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlib.future.FutureTheme
import com.mlib.future.components.*
import com.mlib.future.modifiers.*

@Composable
fun SettingsScreen(
	selectedThemeIndex: Int,
	onThemeSelected: (Int) -> Unit,
	onBack: () -> Unit
) {
	val config = FutureTheme.config
	var dropdownExpanded by remember { mutableStateOf(false) }

	Box(modifier = Modifier.fillMaxSize().background(config.colors.globalBg)) {
		FutureScreenBackground(modifier = Modifier.fillMaxSize())

		Column(modifier = Modifier.fillMaxSize()) {

			Spacer(Modifier.height(18.dp))
			Column(
				modifier = Modifier
				.weight(1f)
				.fillMaxWidth()
				.padding(16.dp)
			) {
				FutureText("Theme:")
				Spacer(Modifier.height(8.dp))
				FutureDropdown(
					expanded = dropdownExpanded,
					onExpandedChange = { dropdownExpanded = it },
					selectedText = themeSets[selectedThemeIndex].name,
					options = themeSets.map { it.name },
					onOptionSelected = { name ->
						val idx = themeSets.indexOfFirst { it.name == name }
						if (idx != -1) onThemeSelected(idx)
						dropdownExpanded = false
					}
				)
			}

			Spacer(Modifier.height(18.dp))

			FutureHeader(
				title = "Settings",
				actions = { FutureButton(onClick = onBack, icon = { FutureIconArrowLeft() }) },
				isBottom = true,
			)
		}
	}
}
