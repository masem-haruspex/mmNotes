package com.mlib.notes

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.json.JSONObject
import java.io.File
import java.util.UUID

const val TAG_DB = "Notes"
const val NOTE_FILE = "note.txt"

enum class BlockType { TEXT, AUDIO, IMAGE, DRAWING }

data class Category(
	val id: String,
	val parentId: String?,
	val name: String,
	val updatedAt: Long = 0
)

data class Note(
	val id: String,
	val categoryId: String?,
	val title: String,
	val createdAt: Long = 0,
	val updatedAt: Long = 0
)

data class Block(
	val id: String = UUID.randomUUID().toString(),
	val noteId: String,
	val type: BlockType,
	val contentJson: String,
	val updatedAt: Long = System.currentTimeMillis()
)

val json = Json { ignoreUnknownKeys = true }

@Serializable
data class TextContent(val html: String, val plain: String = "")
@Serializable
data class AudioContent(val fileName: String, val durationMs: Long = 0, val transcription: String? = null, val transcribedAt: Long? = null)
@Serializable
data class ImageContent(val fileName: String, val source: String, val width: Int = 0, val height: Int = 0)
@Serializable
data class DrawingContent(val fileName: String, val bgColor: String = "#FFFFFF", val strokes: Int = 0)

fun toJson(content: Any): String = when (content) {
	is TextContent -> json.encodeToString(content)
	is AudioContent -> json.encodeToString(content)
	is ImageContent -> json.encodeToString(content)
	is DrawingContent -> json.encodeToString(content)
	else -> throw IllegalArgumentException("bad type")
}

inline fun <reified T> fromJson(s: String): T = json.decodeFromString(s)

private val ILLEGAL_CHARS = Regex("""[\\/:*?"<>|\x00-\x1F]""")

fun sanitizeName(raw: String): String {
	var s = raw.trim().replace(ILLEGAL_CHARS, "-").trimEnd('.', ' ')
	if (s.length > 80) s = s.take(80).trimEnd('.', ' ')
	return s.ifEmpty { "Untitled" }
}

fun uniqueChildName(parent: File, desired: String): String {
	if (!File(parent, desired).exists()) return desired
	var n = 2
	while (File(parent, "$desired ($n)").exists()) n++
	return "$desired ($n)"
}

fun dirForCategory(categoryId: String?): File =
if (categoryId == null) notesRoot else File(notesRoot, categoryId)

private fun joinPath(parentId: String?, name: String) =
if (parentId == null) name else "$parentId/$name"

private fun parentPathOf(id: String): String? {
	val idx = id.lastIndexOf('/')
	return if (idx <= 0) null else id.substring(0, idx)
}

private fun isNoteDir(f: File) = f.isDirectory && File(f, NOTE_FILE).isFile

private val AUDIO_EXTS = setOf("m4a", "mp3", "wav", "aac", "ogg", "opus", "flac")
private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
private val MEDIA_LINE = Regex("""^\$\[([^\]]+)\]$""")

private fun mediaRefFor(line: String): String? {
	val m = MEDIA_LINE.matchEntire(line) ?: return null
	val name = m.groupValues[1].trim()
	if (name.isEmpty() || name.contains('/') || name.contains('\\') || name.contains("..")) return null
	val ext = name.substringAfterLast('.', "").lowercase()
	return if (ext in AUDIO_EXTS || ext in IMAGE_EXTS) name else null
}

private fun fileNameOf(block: Block): String? = when (block.type) {
	BlockType.AUDIO -> fromJson<AudioContent>(block.contentJson).fileName
	BlockType.IMAGE -> fromJson<ImageContent>(block.contentJson).fileName
	BlockType.DRAWING -> fromJson<DrawingContent>(block.contentJson).fileName
	BlockType.TEXT -> null
}

fun deleteBlockFiles(block: Block) {
	val name = fileNameOf(block) ?: return
	mediaFile(block.noteId, name).takeIf { it.exists() }?.delete()
	mediaFile(block.noteId, "$name.txt").takeIf { it.exists() }?.delete()
}

fun blocksToText(blocks: List<Block>): String {
	val lines = mutableListOf<String>()
	for (b in blocks) {
		when (b.type) {
			BlockType.TEXT -> lines.addAll(fromJson<TextContent>(b.contentJson).plain.split("\n"))
			else -> fileNameOf(b)?.let { lines.add("$[$it]") }
		}
	}
	val body = lines.joinToString("\n")
	return if (body.isEmpty()) "" else "$body\n"
}

fun textToBlocks(noteId: String, text: String): List<Block> {
	var lines = text.split("\n")
	if (lines.lastOrNull() == "" && text.endsWith("\n")) lines = lines.dropLast(1)

	val blocks = mutableListOf<Block>()
	val buf = mutableListOf<String>()
	fun flush() {
		if (buf.isNotEmpty()) {
			blocks.add(Block(noteId = noteId, type = BlockType.TEXT,
			contentJson = toJson(TextContent(html = "", plain = buf.joinToString("\n")))))
			buf.clear()
		}
	}
	for (line in lines) {
		val ref = mediaRefFor(line)
		if (ref == null) { buf.add(line); continue }
		flush()
		blocks.add(mediaBlock(noteId, ref))
	}
	flush()
	if (blocks.isEmpty()) {
		blocks.add(Block(noteId = noteId, type = BlockType.TEXT,
		contentJson = toJson(TextContent(html = "", plain = ""))))
	}
	return blocks
}

private fun mediaBlock(noteId: String, fileName: String): Block {
	val ext = fileName.substringAfterLast('.', "").lowercase()
	return when {
		ext in AUDIO_EXTS -> {
			val sidecar = File(noteDir(noteId), "$fileName.txt")
			val content = AudioContent(
				fileName = fileName,
				transcription = if (sidecar.isFile) sidecar.readText() else null,
				transcribedAt = if (sidecar.isFile) sidecar.lastModified() else null
			)
			Block(noteId = noteId, type = BlockType.AUDIO, contentJson = toJson(content))
		}
		fileName.startsWith("drawing_") && ext in IMAGE_EXTS -> {
			Block(noteId = noteId, type = BlockType.DRAWING, contentJson = toJson(DrawingContent(fileName = fileName)))
		}
		else -> {
			Block(noteId = noteId, type = BlockType.IMAGE, contentJson = toJson(ImageContent(fileName = fileName, source = "")))
		}
	}
}

suspend fun getCategories(parentId: String?): List<Category> = withContext(Dispatchers.IO) {
	dirForCategory(parentId)
	.listFiles { f -> f.isDirectory && !isNoteDir(f) }
	?.map { Category(id = joinPath(parentId, it.name), parentId = parentId, name = it.name, updatedAt = it.lastModified()) }
	?.sortedBy { it.name.lowercase() }
	?: emptyList()
}

suspend fun getAllCategories(): List<Category> = withContext(Dispatchers.IO) {
	val out = mutableListOf<Category>()
	fun walk(dir: File, parentId: String?) {
		dir.listFiles { f -> f.isDirectory && !isNoteDir(f) }?.forEach { f ->
			val id = joinPath(parentId, f.name)
			out.add(Category(id = id, parentId = parentId, name = f.name, updatedAt = f.lastModified()))
			walk(f, id)
		}
	}
	walk(notesRoot, null)
	out
}

suspend fun createCategory(name: String, parentId: String?): Category = withContext(Dispatchers.IO) {
	val parent = dirForCategory(parentId).apply { mkdirs() }
	val finalName = uniqueChildName(parent, sanitizeName(name))
	File(parent, finalName).mkdirs()
	Log.d(TAG_DB, "📁 createCategory '$finalName' in ${parentId ?: "root"}")
	Category(id = joinPath(parentId, finalName), parentId = parentId, name = finalName)
}

suspend fun deleteCategory(id: String): Unit = withContext(Dispatchers.IO) {
	File(notesRoot, id).deleteRecursively()
}

suspend fun moveCategory(id: String, newParentId: String?): Unit = withContext(Dispatchers.IO) {
	if (id == newParentId || (newParentId != null && newParentId.startsWith("$id/"))) {
		Log.w(TAG_DB, "🚫 blocked circular move of $id into $newParentId")
		return@withContext
	}
	val src = File(notesRoot, id)
	if (!src.isDirectory) return@withContext
	val dstParent = dirForCategory(newParentId).apply { mkdirs() }
	src.renameTo(File(dstParent, uniqueChildName(dstParent, src.name)))
}

suspend fun breadcrumb(id: String): List<Category> = withContext(Dispatchers.IO) {
	val parts = id.split("/")
	val path = mutableListOf<Category>()
	var cur = ""
	for ((i, part) in parts.withIndex()) {
		cur = if (i == 0) part else "$cur/$part"
		path.add(Category(id = cur, parentId = if (i == 0) null else parts.take(i).joinToString("/"), name = part))
	}
	path
}

private fun noteFromDir(dir: File, id: String, categoryId: String?): Note {
	val f = File(dir, NOTE_FILE)
	return Note(id = id, categoryId = categoryId, title = dir.name,
	createdAt = dir.lastModified(), updatedAt = f.lastModified())
}

suspend fun getNotes(categoryId: String?): List<Note> = withContext(Dispatchers.IO) {
	dirForCategory(categoryId)
	.listFiles { f -> isNoteDir(f) }
	?.map { noteFromDir(it, joinPath(categoryId, it.name), categoryId) }
	?.sortedWith(compareByDescending<Note> { it.updatedAt }.thenBy { it.title.lowercase() })
	?: emptyList()
}

suspend fun getAllNotes(): List<Note> = withContext(Dispatchers.IO) {
	val out = mutableListOf<Note>()
	fun walk(dir: File, parentId: String?) {
		dir.listFiles { f -> f.isDirectory }?.forEach { f ->
			val id = joinPath(parentId, f.name)
			if (isNoteDir(f)) out.add(noteFromDir(f, id, parentId)) else walk(f, id)
		}
	}
	walk(notesRoot, null)
	out
}

suspend fun createNote(title: String, categoryId: String?): Note = withContext(Dispatchers.IO) {
	val parent = dirForCategory(categoryId).apply { mkdirs() }
	val finalName = uniqueChildName(parent, sanitizeName(title))
	File(parent, finalName).apply { mkdirs() }
	File(File(parent, finalName), NOTE_FILE).writeText("")
	Log.d(TAG_DB, "📝 createNote '$finalName'")
	Note(id = joinPath(categoryId, finalName), categoryId = categoryId, title = finalName)
}

suspend fun updateNoteTitle(id: String, newTitle: String): String = withContext(Dispatchers.IO) {
	val src = File(notesRoot, id)
	if (!src.isDirectory) return@withContext id
	val finalName = sanitizeName(newTitle)
	if (finalName == src.name) return@withContext id
	val dstName = uniqueChildName(src.parentFile, finalName)
	src.renameTo(File(src.parentFile, dstName))
	joinPath(parentPathOf(id), dstName)
}

suspend fun deleteNote(id: String): Unit = withContext(Dispatchers.IO) {
	File(notesRoot, id).deleteRecursively()
}

suspend fun moveNote(id: String, newCategoryId: String?): Unit = withContext(Dispatchers.IO) {
	val src = File(notesRoot, id)
	if (!src.isDirectory) return@withContext
	val dstParent = dirForCategory(newCategoryId).apply { mkdirs() }
	src.renameTo(File(dstParent, uniqueChildName(dstParent, src.name)))
}

suspend fun getNoteWithBlocks(id: String): Pair<Note, List<Block>> = withContext(Dispatchers.IO) {
	val dir = File(notesRoot, id)
	val f = File(dir, NOTE_FILE)
	val text = if (f.isFile) f.readText() else ""
	Note(id = id, categoryId = parentPathOf(id), title = dir.name, updatedAt = f.lastModified()) to textToBlocks(id, text)
}

suspend fun saveBlocks(noteId: String, blocks: List<Block>): Unit = withContext(Dispatchers.IO) {
	val dir = noteDir(noteId).apply { mkdirs() }
	val tmp = File(dir, "$NOTE_FILE.tmp")
	tmp.writeText(blocksToText(blocks))
	tmp.renameTo(File(dir, NOTE_FILE))

	val keep = blocks.mapNotNull { fileNameOf(it) }.toSet()
	val keepSidecars = keep
	.filter { it.substringAfterLast('.', "").lowercase() in AUDIO_EXTS }
	.map { "$it.txt" }.toSet()
	dir.listFiles()?.forEach { f ->
		if (f.name != NOTE_FILE && f.name !in keep && f.name !in keepSidecars) f.delete()
	}
}

suspend fun transcribeAudio(noteId: String, fileName: String): String = withContext(Dispatchers.IO) {
	val text = voskTranscribe(mediaFile(noteId, fileName))
	File(noteDir(noteId), "$fileName.txt").writeText(text)
	text
}

suspend fun migrateIfNeeded(context: Context): Unit = withContext(Dispatchers.IO) {
	val dbFile = context.getDatabasePath("notes.db")
	if (!dbFile.exists()) return@withContext
	Log.d(TAG_DB, "🔄 Migrating old database to file storage…")
	val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
	try {
		data class CatRow(val id: String, val parent: String?, val name: String)
		val cats = LinkedHashMap<String, CatRow>()
		db.rawQuery("SELECT id, parent_id, name FROM categories", null).use { c ->
			while (c.moveToNext()) cats[c.getString(0)] = CatRow(c.getString(0), c.getString(1), c.getString(2))
		}

		val newPaths = HashMap<String, String>()
		fun catPath(oldId: String?): String? {
			if (oldId == null) return null
			newPaths[oldId]?.let { return it }
			val row = cats[oldId] ?: return null
			val parentNew = catPath(row.parent)
			val dir = dirForCategory(parentNew)
			val name = uniqueChildName(dir, sanitizeName(row.name))
			File(dir, name).mkdirs()
			val p = joinPath(parentNew, name)
			newPaths[oldId] = p
			return p
		}
		cats.values.forEach { catPath(it.id) }

		db.rawQuery("SELECT id, category_id, title FROM notes", null).use { c ->
			while (c.moveToNext()) {
				val catNew = catPath(c.getString(1))
				val parentDir = dirForCategory(catNew)
				val name = uniqueChildName(parentDir, sanitizeName(c.getString(2)))
				val noteId = joinPath(catNew, name)
				val folder = File(notesRoot, noteId).apply { mkdirs() }

				val blocks = mutableListOf<Block>()
				db.rawQuery("SELECT type, content_json FROM blocks WHERE note_id=? ORDER BY display_order",
				arrayOf(c.getString(0))).use { b ->
					while (b.moveToNext()) {
						val type = try { BlockType.valueOf(b.getString(0)) } catch (_: Exception) { null } ?: continue
						blocks.add(Block(noteId = noteId, type = type, contentJson = b.getString(1)))
					}
				}
				File(folder, NOTE_FILE).writeText(blocksToText(blocks))

				for (bl in blocks) {
					when (bl.type) {
						BlockType.AUDIO -> {
							val jo = JSONObject(bl.contentJson)
							val fn = jo.optString("fileName")
							val src = File(context.filesDir, "audio/$fn")
							if (src.isFile) src.copyTo(File(folder, fn), overwrite = true)
							val tr = jo.optString("transcription")
							if (tr.isNotBlank()) File(folder, "$fn.txt").writeText(tr)
						}
						BlockType.IMAGE -> {
							val fn = JSONObject(bl.contentJson).optString("fileName")
							val src = File(context.filesDir, "images/$fn")
							if (src.isFile) src.copyTo(File(folder, fn), overwrite = true)
						}
						BlockType.DRAWING -> {
							val fn = JSONObject(bl.contentJson).optString("fileName")
							val src = File(context.filesDir, "drawings/$fn")
							if (src.isFile) src.copyTo(File(folder, fn), overwrite = true)
						}
						BlockType.TEXT -> {}
					}
				}
			}
		}
	} finally {
		db.close()
	}
	context.deleteDatabase("notes.db")
	Log.d(TAG_DB, "✅ Migration complete — old database deleted.")
}
