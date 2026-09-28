package com.mlib.notes

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
	private var rec: MediaRecorder? = null
	private var current: File? = null

	fun start(out: File) {
		current = out
		rec = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()).apply {
			setAudioSource(MediaRecorder.AudioSource.MIC)
			setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
			setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
			setOutputFile(out.absolutePath)
			prepare()
			start()
		}
	}

	fun stop(): File? {
		val f = current
		try { rec?.apply { stop(); reset(); release() } }
		catch (e: Exception) { Log.e("AudioRecorder", "stop failed", e) }
		finally { rec = null; current = null }
		return f
	}

	fun cancel() {
		val f = current
		stop()
		f?.delete()
	}
}
