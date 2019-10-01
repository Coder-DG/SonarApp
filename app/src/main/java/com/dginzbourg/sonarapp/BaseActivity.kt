package com.dginzbourg.sonarapp

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.v4.app.Fragment
import android.support.v7.app.ActionBar
import android.support.v7.app.AppCompatActivity

class BaseActivity: AppCompatActivity() {

    lateinit var toolbar: ActionBar
    private lateinit var viewModel: SettingsViewModel
    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_main -> {
                toolbar.title = "SonarApp"
                val fragment = HomeFragment()
                openFragment(fragment)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_settings -> {
                toolbar.title = "Settings"
                val fragment = SettingsFragment()
                openFragment(fragment)
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_help -> {
                toolbar.title = "Help"
                val fragment = HelpFragment()
                openFragment(fragment)
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base)

        viewModel = ViewModelProviders.of(this).get(SettingsViewModel::class.java)
        toolbar = supportActionBar!!
        val bottomNavigation: BottomNavigationView = findViewById(R.id.navigationView)
        bottomNavigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)
        if (savedInstanceState == null) {
            val sharedPref = getPreferences(Context.MODE_PRIVATE)
            bottomNavigation.selectedItemId = R.id.navigation_main
            sharedPref.edit().putBoolean(HomeFragment.TRANSMISSION_SHOULD_START_KEY, false).apply()
        }
    }

    fun openFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.baseContainer, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    fun startTutorial() {
        val bottomNavigation: BottomNavigationView = findViewById(R.id.navigationView)!!
        bottomNavigation.selectedItemId = R.id.navigation_help
        val helpFragment = HelpFragment()
        openFragment(helpFragment)
    }
}