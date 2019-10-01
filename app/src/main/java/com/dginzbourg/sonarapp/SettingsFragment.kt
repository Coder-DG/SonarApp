package com.dginzbourg.sonarapp

import android.app.AlertDialog
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.DialogInterface



class Settings : Fragment() {

    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity()).get(SettingsViewModel::class.java)
        initializeButtons()
    }

    private fun initializeButtons() {
        initializeSpeechSpeed()
        initializeSpeechVolume()
    }

    private fun initializeSpeechSpeed() {
        val speeds = arrayOf("slow", "medium", "fast")

        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Set speech speed")
        builder.setItems(speeds) { dialog, which ->
            when (which) {
                1 -> viewModel.updateTTSSpeed(0.5f)
                2 -> viewModel.updateTTSSpeed(1.0f)
                3 -> viewModel.updateTTSSpeed(2.0f)
            }
        }
        builder.show()
    }

    private fun initializeSpeechVolume() {
        val speeds = arrayOf("low", "medium", "high")

        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Set speech volume")
        builder.setItems(speeds) { dialog, which ->
            when (which) {
                1 -> viewModel.updateTTSVolume(0.2f)
                2 -> viewModel.updateTTSVolume(0.5f)
                3 -> viewModel.updateTTSVolume(1.0f)
            }
        }
        builder.show()
    }
}