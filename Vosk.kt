package com.mlib.notes

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream

private const val TAG = "Vosk"
private const val SAMPLE_RATE = 16000f
private var model: Model? = null

fun voskInit(context: Context) {
	if (model != null) return
	synchronized(Unit) {
		if (model != null) return
		LibVosk.setLogLevel(LogLevel.WARNINGS)
		val modelDir = File(context.filesDir, "vosk-model-en-us-0.22-lgraph")
		if (!modelDir.exists() || modelDir.list()?.isEmpty() != false) {
			extractModel(context, modelDir)
		}
		if (!modelDir.exists() || modelDir.list()?.isEmpty() != false) {
			throw Exception("Vosk model missing")
		}
		model = Model(modelDir.absolutePath)
		Log.d(TAG, "Model loaded")
	}
}

private fun extractModel(context: Context, target: File) {
	target.mkdirs()
	copyAssetDir(context.assets, "vosk-model-en-us-0.22-lgraph", target.absolutePath)
	Log.d(TAG, "Model extracted")
}

private fun copyAssetDir(am: android.content.res.AssetManager, assetPath: String, targetPath: String) {
	val files = am.list(assetPath)
	if (files.isNullOrEmpty()) {
		am.open(assetPath).use { input -> File(targetPath).outputStream().use { input.copyTo(it) } }
	} else {
		File(targetPath).mkdirs()
		files.forEach { copyAssetDir(am, "$assetPath/$it", "$targetPath/$it") }
	}
}

suspend fun voskTranscribe(audioFile: File): String {
	while (model == null) {
		kotlinx.coroutines.delay(100)
	}

	val m = model ?: throw IllegalStateException("Vosk not initialized")

	if (!audioFile.exists()) throw Exception("Audio file missing")

	val sb = StringBuilder()
	FileInputStream(audioFile).use { fis ->
		Recognizer(m, SAMPLE_RATE).use { rec ->
			val buf = ByteArray(4096)
			var n: Int
			while (fis.read(buf).also { n = it } >= 0) {
				if (rec.acceptWaveForm(buf, n)) appendResult(sb, rec.result)
			}
			appendResult(sb, rec.finalResult)
		}
	}
	return sb.toString().trim()
}

private fun appendResult(sb: StringBuilder, chunk: String) {
	try {
		val text = JSONObject(chunk).optString("text", "").trim()
		if (text.isNotEmpty()) {
			if (sb.isNotEmpty()) sb.append(" ")
			sb.append(text)
		}
	} catch (_: Exception) {
		val raw = chunk.trim()
		if (raw.isNotEmpty()) {
			if (sb.isNotEmpty()) sb.append(" ")
			sb.append(raw)
		}
	}
}
