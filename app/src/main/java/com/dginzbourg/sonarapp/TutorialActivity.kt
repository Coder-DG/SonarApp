package com.dginzbourg.sonarapp

import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class TutorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        if (shouldSkipActivity()) startMainActivity()

        findViewById<Button>(R.id.tutorialStartButton).setOnClickListener { startMainActivity() }
    }

    private fun shouldSkipActivity(): Boolean {
        return getPreferences(Context.MODE_PRIVATE)
            .getBoolean(VISITED_TUTORIAL_PREFERENCES_KEY, false)
    }

    private fun saveVisitedTutorial() {
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean(VISITED_TUTORIAL_PREFERENCES_KEY, true)
            apply()
        }
    }

    private fun startMainActivity() {
        saveVisitedTutorial()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        const val VISITED_TUTORIAL_PREFERENCES_KEY = "visited_tutorial"
    }
}
