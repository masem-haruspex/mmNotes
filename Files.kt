package com.mlib.notes

import android.content.Context
import java.io.File
import java.util.UUID

lateinit var notesRoot: File
lateinit var audioDir: File
lateinit var imagesDir: File
lateinit var drawingsDir: File

fun initFiles(context: Context) {
    val base = context.getExternalFilesDir(null) ?: context.filesDir

    notesRoot   = File(base, "notes").apply { mkdirs() }
    audioDir    = File(base, "audio").apply { mkdirs() }
    imagesDir   = File(base, "images").apply { mkdirs() }
    drawingsDir = File(base, "drawings").apply { mkdirs() }
}

fun noteDir(noteId: String): File = File(notesRoot, noteId)


fun mediaFile(noteId: String, fileName: String): File = File(noteDir(noteId), fileName)

private fun uniqueName(prefix: String, ext: String): String =
    "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext"

fun newAudioFile(noteId: String): File   = File(noteDir(noteId), uniqueName("audio",   "m4a"))
fun newImageFile(noteId: String): File   = File(noteDir(noteId), uniqueName("img",     "jpg"))
fun newDrawingFile(noteId: String): File = File(noteDir(noteId), uniqueName("drawing", "png"))

fun deleteFile(fileName: String, type: BlockType) {
    val dir = when (type) {
        BlockType.AUDIO   -> audioDir
        BlockType.IMAGE   -> imagesDir
        BlockType.DRAWING -> drawingsDir
        BlockType.TEXT    -> return
    }
    File(dir, fileName).delete()
}

fun getFile(fileName: String, type: BlockType): File? {
    val dir = when (type) {
        BlockType.AUDIO   -> audioDir
        BlockType.IMAGE   -> imagesDir
        BlockType.DRAWING -> drawingsDir
        BlockType.TEXT    -> return null
    }
    val f = File(dir, fileName)
    return if (f.exists()) f else null
}
