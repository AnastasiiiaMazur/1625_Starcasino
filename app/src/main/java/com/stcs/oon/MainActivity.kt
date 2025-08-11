package com.stcs.oon

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.fragment.NavHostFragment
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var pageTitle: TextView
    private lateinit var backButton: ImageView
    private lateinit var settingsButton: ImageView
    private lateinit var newRouteButton: ImageView
    private lateinit var savedRoutesButton: ImageView
    private lateinit var homeButton: ImageView
    private lateinit var statsButton: ImageView
    private lateinit var manualRouteButton: ImageView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        applyFullscreen()

        pageTitle = findViewById(R.id.pageTitle)
        backButton = findViewById(R.id.backButton)
        settingsButton = findViewById(R.id.settingsButton)
        newRouteButton = findViewById(R.id.newRouteButton)
        savedRoutesButton = findViewById(R.id.savedRoutesButton)
        homeButton = findViewById(R.id.homeButton)
        statsButton = findViewById(R.id.statsButton)
        manualRouteButton = findViewById(R.id.manualRouteButton)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            changeTitle(destination.id)
        }

        homeButton.setOnClickListener    { navController.navigate(R.id.homeFragment) }
        newRouteButton.setOnClickListener  { navController.navigate(R.id.newRouteFragment) }
        savedRoutesButton.setOnClickListener   { navController.navigate(R.id.savedRoutesFragment) }
        statsButton.setOnClickListener    { navController.navigate(R.id.statsFragment) }
        manualRouteButton.setOnClickListener  { navController.navigate(R.id.manualRouteFragment) }
        settingsButton.setOnClickListener { navController.navigate(R.id.settingsFragment) }

        backButton.setOnClickListener {
            val currentDestination = navHostFragment.navController.currentDestination?.id

            if (currentDestination == R.id.homeFragment) {
                finish()
            } else if (currentDestination == R.id.settingsFragment) {
                navHostFragment.navController.popBackStack()
            } else {
                navHostFragment.navController.navigate(R.id.homeFragment)
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            val currentDestination = navHostFragment.navController.currentDestination?.id

            if (currentDestination == R.id.homeFragment) {
                finish()
            } else if (currentDestination == R.id.settingsFragment) {
                navHostFragment.navController.popBackStack()
            } else {
                navHostFragment.navController.navigate(R.id.homeFragment)
            }
        }
    }

    private fun changeTitle(@IdRes destId: Int) {
        when (destId) {
            R.id.homeFragment -> { pageTitle.text = "HOME" }
            R.id.newRouteFragment -> { pageTitle.text = "TRIP GENERATOR" }
            R.id.savedRoutesFragment -> { pageTitle.text = "RIDE GALLERY" }
            R.id.statsFragment -> { pageTitle.text = "STATISTICS" }
            R.id.manualRouteFragment -> { pageTitle.text = "CREATE MANUAL RIDE" }
            R.id.settingsFragment -> { pageTitle.text = "SETTINGS" }
            else -> {
                pageTitle.text = ""
            }
        }
    }


    private fun applyFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN              or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION         or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN       or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION  or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = max(sys.bottom, ime.bottom)
            v.setPadding(sys.left, sys.top, sys.right, bottom)
            insets
        }
    }
}