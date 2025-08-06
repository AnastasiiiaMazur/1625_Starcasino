package com.stcs.oon

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

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

        pageTitle = findViewById(R.id.pageTitle)
        backButton = findViewById(R.id.backButton)
        settingsButton = findViewById(R.id.settingsButton)
        newRouteButton = findViewById(R.id.newRouteButton)
        savedRoutesButton = findViewById(R.id.savedRoutesButton)
        homeButton = findViewById(R.id.homeButton)
        statsButton = findViewById(R.id.statsButton)
        manualRouteButton = findViewById(R.id.manualRouteButton)


    }
}