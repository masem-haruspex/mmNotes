package com.mlib.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlib.future.FutureTheme
import com.mlib.future.components.*
import com.mlib.future.modifiers.*
import com.mlib.notes.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import android.util.Log

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(
	categoryId: String?,
	onCategoryClick: (String) -> Unit,
	onNoteClick: (String) -> Unit,
	onNavigateTo: (String?) -> Unit,
	onSettingsClick: () -> Unit
) {
	val config = FutureTheme.config
	var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
	var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
	var breadcrumb by remember { mutableStateOf<List<Category>>(emptyList()) }
	var showAddCategoryDialog by remember { mutableStateOf(false) }
	var showAddNoteDialog by remember { mutableStateOf(false) }
	var newItemName by remember { mutableStateOf("") }
	var searchQuery by remember { mutableStateOf("") }
	var allCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
	var allNotes by remember { mutableStateOf<List<Note>>(emptyList()) }
	var isSelecting by remember { mutableStateOf(false) }
	var selectedCategoryIds by remember { mutableStateOf(setOf<String>()) }
	var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
	var showMoveDialog by remember { mutableStateOf(false) }
	val displayCategories = if (searchQuery.isNotBlank()) allCategories else categories
	val displayNotes = if (searchQuery.isNotBlank()) allNotes else notes
	val focusManager = LocalFocusManager.current

	suspend fun refreshLists() {
		if (searchQuery.isNotBlank()) {
			allCategories = getAllCategories().filter { it.name.contains(searchQuery, ignoreCase = true) }
			allNotes = getAllNotes().filter { it.title.contains(searchQuery, ignoreCase = true) }
		} else {
			categories = getCategories(categoryId)
			notes = getNotes(categoryId)
		}
	}

	fun clearSelection() {
		isSelecting = false
		selectedCategoryIds = emptySet()
		selectedNoteIds = emptySet()
	}

	fun toggleCategorySelection(id: String) {
		selectedCategoryIds = if (selectedCategoryIds.contains(id)) selectedCategoryIds - id else selectedCategoryIds + id
		if (selectedCategoryIds.isEmpty() && selectedNoteIds.isEmpty()) isSelecting = false
	}

	fun toggleNoteSelection(id: String) {
		selectedNoteIds = if (selectedNoteIds.contains(id)) selectedNoteIds - id else selectedNoteIds + id
		if (selectedCategoryIds.isEmpty() && selectedNoteIds.isEmpty()) isSelecting = false
	}

	fun bulkDelete() {
		MainScope().launch {
			selectedCategoryIds.forEach { deleteCategory(it) }
			selectedNoteIds.forEach { deleteNote(it) }
			clearSelection()
			refreshLists()
		}
	}

	LaunchedEffect(categoryId, searchQuery) {
		refreshLists()
		breadcrumb = if (categoryId != null) breadcrumb(categoryId) else emptyList()
	}

	BackHandler(enabled = searchQuery.isNotEmpty() || isSelecting) {
		when {
			searchQuery.isNotEmpty() -> {
				searchQuery = ""
				focusManager.clearFocus()
			}
			isSelecting -> clearSelection()
		}
	}

	Box(modifier = Modifier.fillMaxSize().background(config.colors.globalBg)) {
		FutureScreenBackground(modifier = Modifier.fillMaxSize())
		Column(modifier = Modifier.fillMaxSize()) {

			FutureScrollArea(modifier = Modifier.weight(1f), isBottomToTop = true) {

				if (isSelecting) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						FutureTextHeading("Select Items")
					}
				}

				if (displayNotes.isNotEmpty()) {
					displayNotes.forEach { note ->
						val isSelected = selectedNoteIds.contains(note.id)

						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
							modifier = Modifier
							.fillMaxWidth()
							.combinedClickable(
								onClick = {
									if (isSelecting) toggleNoteSelection(note.id)
									else onNoteClick(note.id)
								},
								onLongClick = {
									isSelecting = true
									selectedNoteIds = selectedNoteIds + note.id
								}
							)
						) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(12.dp)
							) {
								if (isSelecting) {
									FutureCheckbox(
										checked = isSelected,
										onCheckedChange = { toggleNoteSelection(note.id) }
									)
								}
								FutureIconNote(size = 32)
								FutureText(note.title, style = FutureTheme.typography.bodyLarge.copy(fontSize = 18.sp))
							}

							if (!isSelecting) {
								FutureButton(
									onClick = {
										MainScope().launch {
											deleteNote(note.id)
											refreshLists()
										}
									},
									icon = { FutureIconRemove(color = config.colors.error) },
									type = ButtonType.Destructive
								)
							}
						}
					}
				}

				if (displayCategories.isNotEmpty() && displayNotes.isNotEmpty()) {
					FutureSeparator(modifier = Modifier.fillMaxWidth())
				}

				if (displayCategories.isNotEmpty()) {
					displayCategories.forEach { cat ->
						val isSelected = selectedCategoryIds.contains(cat.id)

						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
							modifier = Modifier
							.fillMaxWidth()
							.combinedClickable(
								onClick = {
									if (isSelecting) toggleCategorySelection(cat.id)
									else onCategoryClick(cat.id)
								},
								onLongClick = {
									isSelecting = true
									selectedCategoryIds = selectedCategoryIds + cat.id
								}
							)
						) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(12.dp)
							) {
								if (isSelecting) {
									FutureCheckbox(
										checked = isSelected,
										onCheckedChange = { toggleCategorySelection(cat.id) }
									)
								}
								FutureIconFolder(size = 32)
								FutureText(cat.name, style = FutureTheme.typography.bodyLarge.copy(fontSize = 18.sp))
							}

							if (!isSelecting) {
								FutureButton(
									onClick = {
										MainScope().launch {
											deleteCategory(cat.id)
											refreshLists()
										}
									},
									icon = { FutureIconRemove(color = config.colors.error) },
									type = ButtonType.Destructive
								)
							}
						}
					}
				}

				if (displayCategories.isEmpty() && displayNotes.isEmpty()) {
					Box(
						modifier = Modifier.fillMaxWidth().padding(32.dp),
						contentAlignment = Alignment.Center
					) {
						FutureTextMuted(if (searchQuery.isNotBlank()) "No matches found." else "This folder is empty.")
					}
				}
			}

			Column(
				modifier = Modifier
				.fillMaxWidth()
				.background(config.colors.componentBg)
				.padding(16.dp)
			) {
				if (isSelecting) {
					val totalSelected = selectedCategoryIds.size + selectedNoteIds.size
					FutureTextMuted("$totalSelected item(s) selected", modifier = Modifier.padding(bottom = 12.dp))

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(12.dp)
					) {
						FutureButton(
							onClick = { bulkDelete() },
							text = "Delete",
							type = ButtonType.Destructive,
							modifier = Modifier.weight(1f)
						)
						FutureButton(
							onClick = { showMoveDialog = true },
							text = "Move",
							modifier = Modifier.weight(1f),
							enabled = totalSelected > 0
						)
						FutureButton(
							onClick = { clearSelection() },
							text = "Cancel",
							modifier = Modifier.weight(1f),
							enabled = totalSelected > 0
						)
					}
				} else {
					val breadcrumbItems = listOf("#") + breadcrumb.map { it.name }
					FutureBreadcrumb(
						items = breadcrumbItems,
						onItemClick = { index ->
							if (index == 0) onNavigateTo(null)
							else onNavigateTo(breadcrumb[index - 1].id)
						},
						modifier = Modifier.padding(bottom = 16.dp)
					)
					FutureSearch(
						placeholder = "Search folders and notes...",
						value = searchQuery,
						onValueChange = { searchQuery = it },
						modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						FutureButton(onClick = onSettingsClick, icon = { FutureIconSettings() })
						FutureButton(onClick = { showAddCategoryDialog = true }, icon = { FutureIconAddFolder() })
						FutureButton(onClick = { showAddNoteDialog = true }, icon = { FutureIconAddNote() })
						FutureButton(
							onClick = { onNavigateTo(breadcrumb.lastOrNull()?.parentId) },
							icon = { FutureIconArrowLeft() },
							enabled = categoryId != null,
							type = if (categoryId != null) ButtonType.Primary else ButtonType.Disabled
						)
					}
				}
			}
		}
	}

	if (showAddCategoryDialog || showAddNoteDialog) {
		FutureDialog(
			title = if (showAddCategoryDialog) "New Folder" else "New Note",
			onDismissRequest = {
				showAddCategoryDialog = false
				showAddNoteDialog = false
				newItemName = ""
			}
		) {
			FutureTextField(
				label = "Name",
				value = newItemName,
				onValueChange = { newItemName = it },
				placeholder = "Enter title..."
			)
			Spacer(Modifier.height(16.dp))
			FutureButton(
				text = "Create",
				onClick = {
					if (newItemName.isBlank()) return@FutureButton
					val isCategory = showAddCategoryDialog
					val name = newItemName
					val parentId = categoryId
					showAddCategoryDialog = false
					showAddNoteDialog = false
					newItemName = ""
					MainScope().launch {
						if (isCategory) {
							createCategory(name, parentId)
						} else {
							val newNote = createNote(name, parentId)
							onNoteClick(newNote.id)
						}
						refreshLists()
					}
				},
				modifier = Modifier.fillMaxWidth(),
				type = ButtonType.Highlighted
			)
		}
	}

	if (showMoveDialog) {
		MoveItemsDialog(
			selectedCategoryIds = selectedCategoryIds,
			selectedNoteIds = selectedNoteIds,
			onDismiss = { showMoveDialog = false },
			onMoveComplete = {
				clearSelection()
				refreshLists()
			}
		)
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoveItemsDialog(
	selectedCategoryIds: Set<String>,
	selectedNoteIds: Set<String>,
	onDismiss: () -> Unit,
	onMoveComplete: suspend () -> Unit
) {
	var currentFolderId by remember { mutableStateOf<String?>(null) } 
	var folders by remember { mutableStateOf<List<Category>>(emptyList()) }
	var breadcrumb by remember { mutableStateOf<List<Category>>(emptyList()) }

	LaunchedEffect(currentFolderId) {
		folders = getCategories(currentFolderId)
		breadcrumb = if (currentFolderId != null) com.mlib.notes.breadcrumb(currentFolderId!!) else emptyList()
	}

	FutureDialog(
		title = "Move Items",
		onDismissRequest = onDismiss
	) {
		Column(modifier = Modifier.fillMaxWidth()) {
			val breadcrumbItems = listOf("#") + breadcrumb.map { it.name }
			FutureBreadcrumb(
				items = breadcrumbItems,
				onItemClick = { index ->
					currentFolderId = if (index == 0) null else breadcrumb[index - 1].id
				},
				modifier = Modifier.padding(bottom = 16.dp)
			)

			FutureScrollArea(
				modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
			) {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					if (folders.isEmpty()) {
						FutureTextMuted("No subfolders in this directory.")
					}
					folders.forEach { folder ->
						val isRestricted = selectedCategoryIds.contains(folder.id)

						FutureCard(
							modifier = Modifier
							.fillMaxWidth()
							.combinedClickable(
								onClick = { if (!isRestricted) currentFolderId = folder.id },
								onLongClick = {}
							),
							onClick = null 
						) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(12.dp),
								modifier = Modifier.fillMaxWidth()
							) {
								FutureText("📁")
								FutureText(
									folder.name,
									color = if (isRestricted) FutureTheme.config.colors.textMuted else FutureTheme.config.colors.textPrimary
								)
								if (isRestricted) {
									FutureTextMuted("(Cannot move into self)")
								}
							}
						}
					}
				}
			}

			Spacer(Modifier.height(24.dp))

			FutureButton(
				text = "MOVE HERE",
				onClick = {
					MainScope().launch {
						selectedCategoryIds.forEach { moveCategory(it, currentFolderId) }
						selectedNoteIds.forEach { moveNote(it, currentFolderId) }
						onMoveComplete()
						onDismiss()
					}
				},
				type = ButtonType.Highlighted,
				modifier = Modifier.fillMaxWidth()
			)
		}
	}
}
