package com.stcs.oon.fragments

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.stcs.oon.R

class SettingsFragment: Fragment(R.layout.fragment_settings) {

    private val url = "https://www.google.com/"
    private lateinit var miButton: TextView
    private lateinit var kmButton: TextView
    private lateinit var deleteButton: TextView
    private lateinit var rateButton: ImageView
    private lateinit var shareButton: ImageView
    private lateinit var privacyButton: ImageView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        miButton = view.findViewById(R.id.mi)
        kmButton = view.findViewById(R.id.km)
        deleteButton = view.findViewById(R.id.deleteData)
        rateButton = view.findViewById(R.id.rate)
        shareButton = view.findViewById(R.id.share)
        privacyButton = view.findViewById(R.id.privacy)

        rateButton.setOnClickListener { requireContext().openAppInPlayStore() }
        shareButton.setOnClickListener { requireContext().shareApp() }

        miButton.setOnClickListener {
            kmButton.setTextColor(resources.getColor(R.color.text_red))
            kmButton.setBackgroundResource(R.drawable.button_outline_black)

            miButton.setTextColor(resources.getColor(R.color.white))
            miButton.setBackgroundResource(R.drawable.basic_button_small_corners)
        }

        kmButton.setOnClickListener {
            miButton.setTextColor(resources.getColor(R.color.text_red))
            miButton.setBackgroundResource(R.drawable.button_outline_black)

            kmButton.setTextColor(resources.getColor(R.color.white))
            kmButton.setBackgroundResource(R.drawable.basic_button_small_corners)
        }

    }

    private fun Context.openAppInPlayStore(appId: String = packageName) {
        val marketUri = Uri.parse("market://details?id=$appId")
        val marketIntent = Intent(Intent.ACTION_VIEW, marketUri)
        try {
            startActivity(marketIntent)
        } catch (e: ActivityNotFoundException) {
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$appId")
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun Context.shareApp(appId: String = packageName) {
        val url = "https://play.google.com/store/apps/details?id=$appId"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, "Check out this app: $url")
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }
}