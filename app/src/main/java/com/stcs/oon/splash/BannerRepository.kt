package com.stcs.oon.splash

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

object BannerRepository {
    private const val URL = "https://stcsoon.com/stcsData"


    fun fetchBanner(callback: (BannerResponse?) -> Unit) {
        val request = Request.Builder().url(URL).build()

        NetworkClient.okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBodyStr = response.body?.string() ?: ""

                    try {
                        val json = JSONObject(responseBodyStr)
                        val banner = BannerResponse(
                            casinoNav = json.optString("casinoNav", null),
                            stcsImg = json.optString("stcsImg", null)
                        )
                        callback(banner)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(null)
                    }
                } else {
                    callback(null)
                }
            }
        })
    }

}