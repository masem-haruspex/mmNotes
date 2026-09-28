package com.mlib.notes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mlib.future.FutureTheme
import com.mlib.future.components.*
import com.mlib.notes.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun BlockComponent(
	block: Block,
	modifier: Modifier = Modifier,
	onUpdate: (Block) -> Unit,
	onDelete: () -> Unit
) {
	when (block.type) {
		BlockType.TEXT -> TextBlockContent(block, onUpdate, modifier)
		BlockType.AUDIO -> AudioBlockContent(block, onUpdate, onDelete)
		BlockType.IMAGE -> ImageBlockContent(block, onDelete)
		BlockType.DRAWING -> DrawingBlockContent(block, onUpdate, onDelete)
	}
}

@Composable
fun MediaContextMenu(
	block: Block,
	onDelete: () -> Unit,
	onTranscribe: (() -> Unit)? = null,
	onEdit: (() -> Unit)? = null,
	content: @Composable () -> Unit
) {
	var showMenu by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier
		.fillMaxWidth()
		.pointerInput(Unit) {
			detectTapGestures(onLongPress = { showMenu = true })
		}
	) {
		content()

		if (showMenu) {
			FutureDialog(
				title = "Block Options",
				onDismissRequest = { showMenu = false }
			) {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					FutureButton(
						text = "Remove",
						onClick = { onDelete(); showMenu = false },
						type = ButtonType.Destructive,
						modifier = Modifier.fillMaxWidth()
					)
					if (onTranscribe != null) {
						FutureButton(
							text = "Transcribe",
							onClick = { onTranscribe(); showMenu = false },
							modifier = Modifier.fillMaxWidth()
						)
					}
					if (onEdit != null) {
						FutureButton(
							text = "Edit",
							onClick = { onEdit(); showMenu = false },
							modifier = Modifier.fillMaxWidth()
						)
					}
					FutureButton(
						text = "Cancel",
						onClick = { showMenu = false },
						modifier = Modifier.fillMaxWidth()
					)
				}
			}
		}
	}
}

@Composable
fun TextBlockContent(block: Block, onUpdate: (Block) -> Unit, modifier: Modifier = Modifier) {
	val content = fromJson<TextContent>(block.contentJson)
	var text by remember(block.id) { mutableStateOf(content.plain) }

	FutureTextArea(
		value = text,
		onValueChange = {
			text = it
			val newContent = content.copy(plain = it, html = it)
			onUpdate(block.copy(contentJson = toJson(newContent), updatedAt = System.currentTimeMillis()))
		},
		placeholder = "Start typing...",
		showUnderline = false,
		fillHeight = true,
		modifier = modifier.fillMaxWidth()
	)
}

@Composable
fun AudioBlockContent(block: Block, onUpdate: (Block) -> Unit, onDelete: () -> Unit) {
	val context = LocalContext.current
	val recorder = remember { AudioRecorder(context) }
	var isRecording by remember { mutableStateOf(false) }
	val content = fromJson<AudioContent>(block.contentJson)

	fun startRecording() {
		recorder.start(mediaFile(block.noteId, content.fileName))
		isRecording = true
	}

	val audioPermissionLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { isGranted -> if (isGranted) startRecording() }

	MediaContextMenu(
		block = block,
		onDelete = onDelete,
		onTranscribe = {
			MainScope().launch {
				try {
					val text = transcribeAudio(block.noteId, content.fileName)
					val updated = content.copy(transcription = text, transcribedAt = System.currentTimeMillis())
					onUpdate(block.copy(contentJson = toJson(updated), updatedAt = System.currentTimeMillis()))
				} catch (e: Exception) {
					Log.e("AudioBlock", "Transcription failed", e)
				}
			}
		}
	) {
		Column {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				FutureButton(
					text = if (isRecording) "STOP" else "RECORD",
					onClick = {
						if (isRecording) {
							recorder.stop()
							isRecording = false
						} else {
							if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
								startRecording()
							} else {
								audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
							}
						}
					},
					type = if (isRecording) ButtonType.Destructive else ButtonType.Primary
				)
			}
			if (content.transcription != null) {
				Spacer(Modifier.height(8.dp))
				FutureTextHeading("Transcription:")
				FutureText(content.transcription!!)
			}
		}
	}
}

@Composable
fun ImageBlockContent(block: Block, onDelete: () -> Unit) {
	val content = fromJson<ImageContent>(block.contentJson)
	val file = mediaFile(block.noteId, content.fileName).takeIf { it.exists() }

	MediaContextMenu(block = block, onDelete = onDelete) {
		Column {
			if (file != null) {
				val bitmap = remember(file.absolutePath) {
					android.graphics.BitmapFactory.decodeFile(file.absolutePath)
				}
				if (bitmap != null) {
					androidx.compose.foundation.Image(
						bitmap = bitmap.asImageBitmap(),
						contentDescription = "Note Image",
						modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
					)
				} else {
					FutureTextMuted("Failed to decode image.")
				}
			} else {
				FutureTextMuted("Image file missing.")
			}
		}
	}
}

@Composable
fun DrawingBlockContent(block: Block, onUpdate: (Block) -> Unit, onDelete: () -> Unit) {
	val config = FutureTheme.config
	val strokeColor = config.colors.primary
	val content = fromJson<DrawingContent>(block.contentJson)
	val file = mediaFile(block.noteId, content.fileName).takeIf { it.exists() }

	MediaContextMenu(
		block = block,
		onDelete = onDelete,
		onEdit = {
			mediaFile(block.noteId, content.fileName).delete()
			val newContent = DrawingContent(fileName = "drawing_${System.currentTimeMillis()}.png")
			onUpdate(block.copy(contentJson = toJson(newContent), updatedAt = System.currentTimeMillis()))
		}
	) {
		if (file != null) {
			val bitmap = remember(file.absolutePath) {
				android.graphics.BitmapFactory.decodeFile(file.absolutePath)
			}
			if (bitmap != null) {
				androidx.compose.foundation.Image(
					bitmap = bitmap.asImageBitmap(),
					contentDescription = "Saved Drawing",
					modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
				)
			}
		} else {
			data class Stroke(val points: List<Pair<Float, Float>>)
			val strokes = remember { mutableStateListOf<Stroke>() }
			var currentStroke by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }

			FutureText("Drawing Canvas:")
			Spacer(Modifier.height(8.dp))
			Canvas(
				modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(9f / 16f)
				.clip(RoundedCornerShape(8.dp))
				.background(Color.White)
				.border(1.dp, config.colors.primary, RoundedCornerShape(8.dp))
				.pointerInput(Unit) {
					detectDragGestures(
						onDragStart = { offset ->
							currentStroke = listOf(Pair(offset.x / size.width, offset.y / size.height))
						},
						onDrag = { change, _ ->
							change.consume()
							currentStroke = currentStroke + Pair(change.position.x / size.width, change.position.y / size.height)
						},
						onDragEnd = {
							if (currentStroke.size > 1) strokes.add(Stroke(currentStroke))
							currentStroke = emptyList()
						}
					)
				}
			) {
				fun drawStroke(stroke: List<Pair<Float, Float>>) {
					if (stroke.size > 1) {
						val path = Path()
						path.moveTo(stroke[0].first * size.width, stroke[0].second * size.height)
						for (i in 1 until stroke.size) {
							path.lineTo(stroke[i].first * size.width, stroke[i].second * size.height)
						}
						drawPath(path, color = strokeColor, style = Stroke(width = 15f, cap = StrokeCap.Round))
					}
				}
				strokes.forEach { drawStroke(it.points) }
				drawStroke(currentStroke)
			}
			Spacer(Modifier.height(8.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				FutureButton("Clear", onClick = {
					strokes.clear()
					currentStroke = emptyList()
				}, type = ButtonType.Destructive)

				FutureButton("Save Drawing", onClick = {
					val saveFile = newDrawingFile(block.noteId)
					val bitmap = android.graphics.Bitmap.createBitmap(1080, 1920, android.graphics.Bitmap.Config.ARGB_8888)
					val androidCanvas = android.graphics.Canvas(bitmap)
					androidCanvas.drawColor(android.graphics.Color.WHITE)
					val paint = android.graphics.Paint().apply {
						color = strokeColor.toArgb()
						strokeWidth = 60f
						style = android.graphics.Paint.Style.STROKE
						strokeCap = android.graphics.Paint.Cap.ROUND
						isAntiAlias = true
					}
					val allStrokes = strokes + Stroke(currentStroke)
					allStrokes.forEach { stroke ->
						if (stroke.points.size > 1) {
							val path = android.graphics.Path()
							path.moveTo(stroke.points[0].first * 1080f, stroke.points[0].second * 1920f)
							for (i in 1 until stroke.points.size) {
								path.lineTo(stroke.points[i].first * 1080f, stroke.points[i].second * 1920f)
							}
							androidCanvas.drawPath(path, paint)
						}
					}
					saveFile.outputStream().use { out ->
						bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
					}
					val newContent = DrawingContent(fileName = saveFile.name)
					onUpdate(block.copy(contentJson = toJson(newContent), updatedAt = System.currentTimeMillis()))
					strokes.clear()
					currentStroke = emptyList()
				}, type = ButtonType.Highlighted)
			}
		}
	}
}
