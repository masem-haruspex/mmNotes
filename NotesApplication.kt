package com.mlib.notes

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NotesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initFiles(this)
        if (getDatabasePath("notes.db").exists()) {
            runBlocking { migrateIfNeeded(this@NotesApplication) }
        }
        CoroutineScope(Dispatchers.IO).launch { voskInit(this@NotesApplication) }
    }
}
