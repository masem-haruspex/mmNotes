package com.mlib.notes.ui

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mlib.future.FutureTheme
import com.mlib.future.components.*
import com.mlib.future.modifiers.*
import com.mlib.notes.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler

@Composable
fun NoteEditorScreen(noteId: String, onBack: () -> Unit) {
	val context = LocalContext.current
	val config = FutureTheme.config
	val scrollState = rememberScrollState()
	var note by remember { mutableStateOf<Note?>(null) }
	var blocks by remember { mutableStateOf<List<Block>>(emptyList()) }
	var title by remember { mutableStateOf("") }
	var currentNoteId by remember { mutableStateOf(noteId) }
	var showRenameDialog by remember { mutableStateOf(false) }
	var tempTitle by remember { mutableStateOf("") }
	var showAddBlockMenu by remember { mutableStateOf(false) }
	var pendingImageFile by remember { mutableStateOf<java.io.File?>(null) }
	val pendingSaveJob = remember { mutableStateOf<Job?>(null) }
	val clipboard = LocalClipboardManager.current
	var showClearDialog by remember { mutableStateOf(false) }
	var showDeleteDialog by remember { mutableStateOf(false) }

	fun flushSave() {
		pendingSaveJob.value?.cancel()
		pendingSaveJob.value = null
		val snapshot = blocks
		MainScope().launch { saveBlocks(currentNoteId, snapshot) }
	}

	fun updateBlocks(newBlocks: List<Block>) {
		blocks = newBlocks
		pendingSaveJob.value?.cancel()
		val snapshot = newBlocks
		pendingSaveJob.value = MainScope().launch {
			delay(500)
			saveBlocks(currentNoteId, snapshot)
		}
	}

	fun saveTitle() {
		val n = note ?: return
		MainScope().launch {
			val newId = updateNoteTitle(n.id, title)
			if (newId != n.id) {
				currentNoteId = newId
				note = n.copy(id = newId, title = title)
			} else {
				note = n.copy(title = title)
			}
		}
	}

	val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
		if (success && pendingImageFile != null) {
			val file = pendingImageFile!!
			val mediaBlock = Block(
				noteId = currentNoteId, type = BlockType.IMAGE,
				contentJson = toJson(ImageContent(fileName = file.name, source = "camera"))
			)
			val textBlock = Block(
				noteId = currentNoteId, type = BlockType.TEXT,
				contentJson = toJson(TextContent(html = "", plain = ""))
			)
			updateBlocks(blocks + mediaBlock + textBlock)
		}
		pendingImageFile = null
	}

	val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
		if (isGranted) {
			val file = newImageFile(currentNoteId)
			pendingImageFile = file
			val uri = androidx.core.content.FileProvider.getUriForFile(
				context, "${context.packageName}.fileprovider", file
			)
			cameraLauncher.launch(uri)
		}
	}

	val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
		if (uri != null) {
			val file = newImageFile(currentNoteId)
			context.contentResolver.openInputStream(uri)?.use { input ->
				file.outputStream().use { output -> input.copyTo(output) }
			}
			val mediaBlock = Block(
				noteId = currentNoteId, type = BlockType.IMAGE,
				contentJson = toJson(ImageContent(fileName = file.name, source = "gallery"))
			)
			val textBlock = Block(
				noteId = currentNoteId, type = BlockType.TEXT,
				contentJson = toJson(TextContent(html = "", plain = ""))
			)
			updateBlocks(blocks + mediaBlock + textBlock)
		}
	}

	val lifecycleOwner = LocalLifecycleOwner.current
	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> flushSave()
				else -> {}
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
			flushSave()
		}
	}

	BackHandler {
		flushSave()
		onBack()
	}

	LaunchedEffect(noteId) {
		val pair = getNoteWithBlocks(noteId)
		note = pair.first
		blocks = pair.second
		title = pair.first.title
	}

	Box(modifier = Modifier.fillMaxSize().background(config.colors.globalBg)) {
		FutureScreenBackground(modifier = Modifier.fillMaxSize())
		Column(modifier = Modifier.fillMaxSize()) {
			BoxWithConstraints(modifier = Modifier.weight(1f)) {
				val maxHeight = constraints.maxHeight
				val density = LocalDensity.current
				val paddingPx = with(density) { 32.dp.toPx().toInt() }
				val spacingPx = with(density) { 16.dp.toPx().toInt() }
				val availableHeight = maxHeight - paddingPx
				var contentHeight by remember { mutableStateOf(0) }

				Box(modifier = Modifier.fillMaxSize()) {
					Column(
						modifier = Modifier
						.verticalScroll(scrollState)
						.fillMaxWidth(),
					) {
						Column(
							modifier = Modifier
							.onSizeChanged { contentHeight = it.height }
							.fillMaxWidth(),
							verticalArrangement = Arrangement.spacedBy(16.dp)
						) {
							if (blocks.size > 1) {
								blocks.dropLast(1).forEach { block ->
									BlockComponent(
										block = block,
										onUpdate = { updatedBlock ->
											updateBlocks(blocks.map { if (it.id == updatedBlock.id) updatedBlock else it })
										},
										onDelete = {
											deleteBlockFiles(block)
											updateBlocks(blocks.filter { it.id != block.id })
										}
									)
								}
							}
						}

						if (blocks.isNotEmpty()) {
							val lastBlock = blocks.last()
							val isLastText = lastBlock.type == BlockType.TEXT
							val actualRemaining = if (isLastText) {
								(availableHeight - contentHeight - spacingPx).coerceAtLeast(0)
							} else {
								0
							}
							val lastBlockModifier = if (isLastText) {
								Modifier.heightIn(min = with(density) { actualRemaining.toDp() })
							} else {
								Modifier
							}
							BlockComponent(
								block = lastBlock,
								modifier = lastBlockModifier,
								onUpdate = { updatedBlock ->
									updateBlocks(blocks.map { if (it.id == updatedBlock.id) updatedBlock else it })
								},
								onDelete = {
									deleteBlockFiles(lastBlock)
									updateBlocks(blocks.filter { it.id != lastBlock.id })
								}
							)
						}
					}

					if (scrollState.maxValue > 0) {
						val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
						Box(
							modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
							.width(3.dp)
							.fillMaxHeight(0.95f)
							.clip(RoundedCornerShape(1.5.dp))
							.background(config.colors.componentBg)
						) {
							Box(
								modifier = Modifier
								.fillMaxWidth()
								.fillMaxHeight(progress.coerceIn(0.05f, 1f))
								.clip(RoundedCornerShape(1.5.dp))
								.background(config.colors.primary.copy(alpha = 0.6f))
							)
						}
					}
				}
			}

			Spacer(Modifier.height(10.dp))

			FutureHeader(
				title = title.ifEmpty { "Untitled" },
				isBottom = true,
				isTwoRows = true,
				onTitleClick = {
					tempTitle = title
					showRenameDialog = true
				},
				actions = {
					FutureButton(
						icon = { FutureIconCopy() },
						onClick = {
							val allText = blocks
							.filter { it.type == BlockType.TEXT }
							.joinToString("\n") { fromJson<TextContent>(it.contentJson).plain }
							.trim()
							if (allText.isNotEmpty()) {
								clipboard.setText(AnnotatedString(allText))
							}
						},
					)
					FutureButton(
						icon = { FutureIconClear() },
						onClick = { showClearDialog = true },
					)
					FutureButton(
						icon = { FutureIconRemove(color = config.colors.error) },
						type = ButtonType.Destructive,
						onClick = { showDeleteDialog = true },
					)
					FutureButton(
						icon = { FutureIconAdd() },
						onClick = { showAddBlockMenu = true },
					)
					FutureButton(
						icon = { FutureIconArrowLeft() },
						onClick = {
							flushSave()
							onBack()
						},
					)
				}
			)
		}
	}

	if (showRenameDialog) {
		FutureDialog("Rename Note", onDismissRequest = { showRenameDialog = false }) {
			FutureTextField(
				label = "Title",
				value = tempTitle,
				onValueChange = { tempTitle = it },
				placeholder = "Enter new title..."
			)
			Spacer(Modifier.height(16.dp))
			FutureButton(
				text = "Save",
				onClick = {
					title = tempTitle
					saveTitle()
					showRenameDialog = false
				},
				modifier = Modifier.fillMaxWidth(),
				type = ButtonType.Highlighted
			)
		}
	}

	if (showAddBlockMenu) {
		FutureDialog("Add Content", onDismissRequest = { showAddBlockMenu = false }) {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				FutureButton("Audio", onClick = {
					val mediaBlock = Block(
						noteId = currentNoteId, type = BlockType.AUDIO,
						contentJson = toJson(AudioContent(fileName = "audio_${System.currentTimeMillis()}.m4a"))
					)
					val textBlock = Block(
						noteId = currentNoteId, type = BlockType.TEXT,
						contentJson = toJson(TextContent(html = "", plain = ""))
					)
					updateBlocks(blocks + mediaBlock + textBlock)
					showAddBlockMenu = false
				}, modifier = Modifier.fillMaxWidth())

				FutureButton("Image (Camera)", onClick = {
					if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
						val file = newImageFile(currentNoteId)
						pendingImageFile = file
						val uri = androidx.core.content.FileProvider.getUriForFile(
							context, "${context.packageName}.fileprovider", file
						)
						cameraLauncher.launch(uri)
					} else {
						cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
					}
					showAddBlockMenu = false
				}, modifier = Modifier.fillMaxWidth())

				FutureButton("Image (Gallery)", onClick = {
					galleryLauncher.launch(
						PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
					)
					showAddBlockMenu = false
				}, modifier = Modifier.fillMaxWidth())

				FutureButton("Drawing", onClick = {
					val mediaBlock = Block(
						noteId = currentNoteId, type = BlockType.DRAWING,
						contentJson = toJson(DrawingContent(fileName = "drawing_${System.currentTimeMillis()}.png"))
					)
					val textBlock = Block(
						noteId = currentNoteId, type = BlockType.TEXT,
						contentJson = toJson(TextContent(html = "", plain = ""))
					)
					updateBlocks(blocks + mediaBlock + textBlock)
					showAddBlockMenu = false
				}, modifier = Modifier.fillMaxWidth())
			}
		}
	}
	if (showClearDialog) {
		FutureDialog(
			title = "Clear Note",
			onDismissRequest = { showClearDialog = false },
		) {
			FutureText("This will remove all text and media from this note. This cannot be undone.")
			Spacer(Modifier.height(20.dp))
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				FutureButton(
					text = "Cancel",
					onClick = { showClearDialog = false },
					modifier = Modifier.weight(1f),
				)
				FutureButton(
					text = "Clear",
					type = ButtonType.Destructive,
					onClick = {
						val emptyBlock = Block(
							noteId = currentNoteId,
							type = BlockType.TEXT,
							contentJson = toJson(TextContent(html = "", plain = "")),
						)
						updateBlocks(listOf(emptyBlock))
						showClearDialog = false
					},
					modifier = Modifier.weight(1f),
				)
			}
		}
	}

	if (showDeleteDialog) {
		FutureDialog(
			title = "Delete Note",
			onDismissRequest = { showDeleteDialog = false },
		) {
			FutureText("Delete \"${title.ifEmpty { "Untitled" }}\" and all of its content permanently?")
			Spacer(Modifier.height(20.dp))
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				FutureButton(
					text = "Cancel",
					onClick = { showDeleteDialog = false },
					modifier = Modifier.weight(1f),
				)
				FutureButton(
					text = "Delete",
					type = ButtonType.Destructive,
					onClick = {
						showDeleteDialog = false
						MainScope().launch {
							deleteNote(currentNoteId)
							onBack()
						}
					},
					modifier = Modifier.weight(1f),
				)
			}
		}
	}
}
