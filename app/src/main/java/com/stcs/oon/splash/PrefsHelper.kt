package com.stcs.oon.splash

import android.content.Context

class PrefsHelper(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)


    fun saveBanner(banner: BannerResponse) {
        prefs.edit()
            .putString("casinoNav", banner.casinoNav)
            .putString("stcsImg", banner.stcsImg)
            .apply()
    }

    fun getCachedBanner(): BannerResponse? {
        val source = prefs.getString("source", null)
        return source?.let {
            BannerResponse(
                casinoNav = prefs.getString("casinoNav", null),
                stcsImg = prefs.getString("stcsImg", null)
            )
        }
    }
}