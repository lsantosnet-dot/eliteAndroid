package com.eliteandroid.save

import com.badlogic.gdx.Gdx

/**
 * Guarda o comandante nas Preferences do LibGDX (SharedPreferences no Android,
 * ~/.prefs no desktop).
 */
class PreferencesStorage(private val name: String = "elite-commander") : SaveStorage {
    private val prefs get() = Gdx.app.getPreferences(name)

    override fun read(): String? = prefs.getString(KEY, null)

    override fun write(text: String) {
        prefs.putString(KEY, text).flush()
    }

    override fun clear() {
        prefs.remove(KEY); prefs.flush()
    }

    private companion object { const val KEY = "commander" }
}
