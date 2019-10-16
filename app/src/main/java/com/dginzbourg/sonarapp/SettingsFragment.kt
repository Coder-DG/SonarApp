package com.dginzbourg.sonarapp

import android.app.AlertDialog
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_settings.*


class SettingsFragment : Fragment() {

    val TAG = "SettingsFragment"
    val PACKAGE_NAME = "com.dginzbourg.sonarapp"
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
        initializeContactUsButton()
        initializeRateUsButton()
    }

    private fun initializeSpeechSpeedButton() {
        tts_speed_button.setOnClickListener {
            val speeds = arrayOf("slow", "medium", "fast")

            val builder = AlertDialog.Builder(requireContext())
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

    private fun initializeContactUsButton() {
        contact_us_button.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO)
            emailIntent.data = Uri.parse("mailto:") // only email apps should handle this
            emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf("nitzan.wagner1@mail.huji.ac.il", "david.ginzbourg@mail.huji.ac.il"))
            emailIntent.putExtra(Intent.EXTRA_SUBJECT,"Sonar app feedback")
            try {
                startActivity(Intent.createChooser(emailIntent, "Choose Email Client..."))
            }
            catch (e: Exception){
                //if any thing goes wrong for example no email client application or any exception
                //get and show exception message
                Toast.makeText(activity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initializeRateUsButton() {
        play_store_button.setOnClickListener {
            try {
                val storeUri = Uri.parse("market://details?id=$PACKAGE_NAME")
                val storeIntent = Intent(Intent.ACTION_VIEW, storeUri)
                startActivity(storeIntent)
            }catch (exp:Exception){
                val storeFallbackUri = Uri.parse("http://play.google.com/store/apps/details?id=$PACKAGE_NAME")
                val storeIntent2 = Intent(Intent.ACTION_VIEW, storeFallbackUri)
                startActivity(storeIntent2)
            }
        }
    }
}