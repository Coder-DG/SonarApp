package com.dginzbourg.sonarapp

import android.arch.lifecycle.Observer
import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.GestureDetectorCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import kotlinx.android.synthetic.main.activity_main.*

class HelpFragment: Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_tutorial, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mDistanceTextView = view?.findViewById(R.id.distanceTextView)!!
        mDistanceString.observe(this, object : Observer<String> {
            override fun onChanged(t: String?) {
                mDistanceTextView.text = t ?: return
            }
        })
        fab.setOnClickListener {
            speak(R.string.help_msg, addToQueue = false)
        }

        setViewModelObservers()
        setFont()
        mTTSVolumeSeekBarTitle = view.findViewById(R.id.ttsVolumeSeekBarTitle)!!
        val text = getString(R.string.tts_volume_seekbar_title) + " (${stringPercent(getTTSVolume())}%)"
        mTTSVolumeSeekBarTitle.text = text
        mDetector = GestureDetectorCompat(context, MyGestureListener())
    }

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
}