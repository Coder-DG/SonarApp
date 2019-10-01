package com.dginzbourg.sonarapp

import android.app.AlertDialog
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_settings.*


class SettingsFragment : Fragment() {

    val TAG = "SettingsFragment"
    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity()).get(SettingsViewModel::class.java)
        initializeButtons()
    }

    private fun initializeButtons() {
        initializeSpeechSpeedButton()
        initializeSpeechVolumeButton()
        initializeStartTutorialButton()
    }

    private fun initializeSpeechSpeedButton() {
        tts_speed_button.setOnClickListener {
            val speeds = arrayOf("slow", "medium", "fast")

            val builder = AlertDialog.Builder(activity)
                .setTitle("Set speech speed")
                .setCancelable(false)
                .setItems(speeds) { _, which ->
                    when (which) {
                        0 -> viewModel.updateTTSSpeed(0.5f)
                        1 -> viewModel.updateTTSSpeed(1.0f)
                        2 -> viewModel.updateTTSSpeed(2.0f)
                    }
                }
            builder.show()
        }

    }

    private fun initializeSpeechVolumeButton() {
        tts_volume_button.setOnClickListener {
            val speeds = arrayOf("low", "medium", "high")

            val builder = AlertDialog.Builder(activity)
                .setTitle("Set speech volume")
                .setCancelable(false)
                .setItems(speeds) { _, which ->
                    when (which) {
                        0 -> viewModel.updateTTSVolume(0.2f)
                        1 -> viewModel.updateTTSVolume(0.5f)
                        2 -> viewModel.updateTTSVolume(1.0f)
                    }
                }
            builder.show()
        }
    }

    private fun initializeStartTutorialButton() {
        tutorial_button.setOnClickListener {
            (activity as BaseActivity).startTutorial()
        }
    }
}