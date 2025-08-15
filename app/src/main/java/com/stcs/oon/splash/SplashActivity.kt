package com.stcs.oon.splash

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.stcs.oon.R
import com.stcs.oon.StartActivity
import com.stcs.oon.WebActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class SplashActivity : AppCompatActivity() {

    private lateinit var bannerImage: ImageView
    private lateinit var percentage: TextView
    private lateinit var progressImage: ImageView

    private val prefs by lazy { PrefsHelper(this) }
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressValue = 0

    private var hasNavigated = false

    private var bannerLink: String? = null
    private var imageBannerLink: String? = null
    private var preloadedDrawable: Drawable? = null
    private var bannerReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        bannerImage = findViewById(R.id.bannerImageView)
        percentage = findViewById(R.id.percentage)
        progressImage = findViewById(R.id.progressImage)

        applyFullscreen()

        startFakeLoadingAnimation(
            durationMillis = 15_000L,
            block = {
                val isOnline = withContext(Dispatchers.IO) {
                    isInternetAvailable(this@SplashActivity)
                }

                Log.d("banner", "isOnline $isOnline")

                val timeoutMillis = if (isOnline) 15_000L else 8_000L

                val bannerCheckJob = lifecycleScope.launch {
                    delay(timeoutMillis)
                    if (!bannerReady || preloadedDrawable == null) {
                        goToMainIfNotAlready()
                    }
                }

                if (isOnline) {
                    val cached = prefs.getCachedBanner()
                    Log.d("banner", "cached $cached")

                    if (cached != null) {
                        loadImage(cached.casinoNav, cached.stcsImg)
                    } else {
                        loadBanner()
                        Log.d("banner", "loading banner")
                    }

                    waitForBannerThenShow {
                        Log.d("banner", "waiting for banner")
                        if (bannerCheckJob.isActive) bannerCheckJob.cancel()
                        showBanner()
                    }
                } else {
                    delay(8000)
                    goToMainIfNotAlready()
                }
            },
            onFinished = {
                // Optional: clean up or fade
            }
        )
    }

    private suspend fun waitForBannerThenShow(onReady: () -> Unit) {
        withContext(Dispatchers.Default) {
            while (!bannerReady || preloadedDrawable == null) {
                delay(200)
            }
            Log.d("banner", "waiting for banner")
            withContext(Dispatchers.Main) {
                onReady()
            }
        }
    }

    private fun startFakeLoadingAnimation(durationMillis: Long, block: suspend () -> Unit, onFinished: () -> Unit) {
        progressValue = 0

        val imageChangeEveryPercent = 5

        progressHandler.post(object : Runnable {
            var lastSwapAt = 0
            var showFilled = false

            override fun run() {
                if (progressValue < 100) {
                    progressValue++

                    if (progressValue - lastSwapAt >= imageChangeEveryPercent) {
                        lastSwapAt = progressValue
                        showFilled = !showFilled

                        progressImage.setImageResource(
                            if (showFilled) R.drawable.star_filled else R.drawable.star_empty
                        )

                        progressImage.animate()
                            .rotationBy(45f)
                            .setDuration(200L)
                            .start()
                    }

                    percentage.text = "$progressValue%"
                    progressHandler.postDelayed(this, durationMillis / 100)
                } else {
                    onFinished()
                }
            }
        })

        lifecycleScope.launch {
            block()
        }
    }


    private fun loadBanner() {
        BannerRepository.fetchBanner { banner ->
            runOnUiThread {
                if (banner != null) {
                    prefs.saveBanner(banner)
                    loadImage(banner.casinoNav, banner.stcsImg)
                }
            }
        }
    }

    private fun loadImage(valuesUrl: String?, trackUrl: String?) {
        if (isFinishing || isDestroyed) {
            return
        }

        Glide.with(this)
            .load(trackUrl)
            .timeout(15000)
            .centerCrop()
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    if (isFinishing || isDestroyed) return
                    preloadedDrawable = resource
                    bannerLink = valuesUrl
                    imageBannerLink = trackUrl

                    if (!valuesUrl.isNullOrBlank() && !trackUrl.isNullOrBlank()) {
                        hasNavigated = true
                        openWebActivity(valuesUrl)
                        return
                    }

                    bannerReady = true
                    if (!hasNavigated) {
                        showBanner()
                    }

                    bannerImage.setOnClickListener {
                        goToMainIfNotAlready()
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    if (isFinishing || isDestroyed) return
                    super.onLoadFailed(errorDrawable)
                    bannerReady = false
                    preloadedDrawable = null
                    goToMainIfNotAlready()
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            }
            )
    }

    private fun showBanner() {
        bannerImage.setImageDrawable(preloadedDrawable)
        bannerImage.visibility = View.VISIBLE
        progressImage.visibility = View.GONE
        percentage.visibility = View.GONE

        bannerImage.setOnClickListener {
            goToMainIfNotAlready()
        }

        Log.d("banner", "Banner shown")
    }

    private fun openWebActivity(link: String) {
        val intent = Intent(this, WebActivity::class.java)
        intent.putExtra("url", link)
        intent.putExtra("bannerImageUrl", imageBannerLink)
        startActivity(intent)
        finish()
    }

    private fun goToMainIfNotAlready() {
        if (!hasNavigated) {
            hasNavigated = true
            startActivity(Intent(this, StartActivity::class.java))
            finish()
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