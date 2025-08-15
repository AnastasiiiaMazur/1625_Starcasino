package com.stcs.oon

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.stcs.oon.splash.isInternetAvailable
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.math.max


class WebActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var offlineView: View
    private lateinit var retryButton: TextView
    private lateinit var goToDemo: TextView

    private lateinit var retryProgress: ProgressBar
    private var loadFailed = false

    private var popupDialog: Dialog? = null
    private var popupWebView: WebView? = null

    private lateinit var startUrl: String
    private var lastUrl: String? = null

    companion object {
        var isInForeground = false
    }

    private lateinit var webContainer: FrameLayout

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    private val messengerMappings = mapOf(
        "wa.me" to Pair("com.whatsapp", "com.whatsapp"),
        "whatsapp" to Pair("com.whatsapp", "com.whatsapp"),
        "viber" to Pair("com.viber.voip", "com.viber.voip"),
        "line.me" to Pair("jp.naver.line.android", "jp.naver.line.android"),
        "99sabal" to Pair("jp.naver.line.android", "jp.naver.line.android")
    )

    private val REQUEST_WRITE_STORAGE = 1
    private var cameraImageUri: Uri? = null
    private val FILE_PROVIDER_AUTHORITY get() = "$packageName.fileprovider"
    private val REQUEST_CAMERA = 1234
    private var pendingPermissionRequest: PermissionRequest? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_web)

        val url = intent.getStringExtra("url") ?: "https://google.com" // link to web page
        startUrl = url

        retryProgress = findViewById(R.id.retryProgress)

        webView = findViewById(R.id.webView)
        offlineView = findViewById(R.id.offlineContainer)
        retryButton = findViewById(R.id.retryButton)
        retryButton.setText("TRY AGAIN")
        goToDemo = findViewById(R.id.goToDemo)

        webContainer = findViewById(R.id.webContainer)

        applyFullscreen()

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        cookieManager.setAcceptThirdPartyCookies(webView, true)

        retryButton.setOnClickListener {
            retryProgress.visibility = View.VISIBLE
            lifecycleScope.launch {
                if (isInternetAvailable(this@WebActivity)) {
                    if (loadFailed) {
                        webView.reload()
                        Log.d("WebView Reload", "Has history")
                    } else {
                        webView.loadUrl(lastUrl ?: startUrl)
                        Log.d("WebView Reload", "No history")
                    }

                    loadFailed = false
                } else {
                    retryProgress.visibility = View.GONE
                    toastNoConnection()
                }
            }
        }

        goToDemo.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        webView.settings.apply {
            javaScriptCanOpenWindowsAutomatically = true
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadsImagesAutomatically = true
            setSupportMultipleWindows(true)
            allowFileAccess = true
            databaseEnabled = true
            useWideViewPort = true
            blockNetworkImage = false
            blockNetworkLoads = false
            setSaveFormData(true)
            setSavePassword(true)
            userAgentString = "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 GooglePay/1.0"

            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            WebView.setWebContentsDebuggingEnabled(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isAlgorithmicDarkeningAllowed = false
                setAlgorithmicDarkeningAllowed(false)
            }

            webView.isForceDarkAllowed = false
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loadFailed = false
                lastUrl = url

                webView.visibility = View.VISIBLE
                super.onPageStarted(view, url, favicon)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    loadFailed = true
                    showOfflinePage()
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()

                return when {
                    url.contains("profi.travel") -> {
                        startActivity(Intent(this@WebActivity, MainActivity::class.java))
                        finish()
                        true
                    }

                    url.startsWith("tel:") || url.matches(Regex("\\d{10}")) -> { // Handles "0670000000"
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${url.filter { it.isDigit() }}"))
                        launchOrMarket(intent, "com.android.dialer")
                        true
                    }

                    url.startsWith("https://diia.app") -> {
                        launchDiiaApp(url)
                        true
                    }

                    url.startsWith("intent://") -> {
                        launchIntentLink(this@WebActivity, url) { /*Log.d("INTENT", "Launching intent") */ }
                        true
                    }

                    url.startsWith("mailto:") || url.contains("@") -> { // Handles "[email protected]"
                        val email = if (url.startsWith("mailto:")) url else "mailto:${url.trim()}"
                        Log.d("WebView", "Creating email intent for: $email")
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(email))
                        if (intent.resolveActivity(packageManager) != null) {
                            try {
                                startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                launchOrMarket(intent, "com.google.android.gm")
                            }
                        } else {
                            launchOrMarket(intent, "com.google.android.gm")
                        }
                        true
                    }

                    url.startsWith("market://") || url.contains("play.google.com") -> {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            setPackage("com.android.vending")
                        }
                        launchOrMarket(intent, "com.android.vending")
                        true
                    }

                    url.startsWith("tg:") -> {
                        Log.d("WebView", "Opening tg: link in Telegram app: $url")
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        if (intent.resolveActivity(packageManager) != null) {
                            try {
                                startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                launchOrMarket(intent, "org.telegram.messenger")
                            }
                        } else {
                            launchOrMarket(intent, "org.telegram.messenger")
                        }
                        true
                    }

                    url.contains("t.me") -> {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        true
                    }

                    messengerMappings.keys.any { url.contains(it) || url.startsWith(it) } -> {
                        val (appPackage, marketPackageId) = messengerMappings.entries.find { url.contains(it.key) || url.startsWith(it.key) }?.value
                            ?: Pair("com.whatsapp", "com.whatsapp") // Default fallback
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            setPackage(appPackage)
                        }
                        launchOrMarket(intent, marketPackageId)
                        true
                    }

                    url.startsWith("http://") || url.startsWith("https://") -> {
                        view?.loadUrl(url)
                        true
                    }

                    else -> {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@WebActivity, "No app found, redirecting to Play Store: $url", Toast.LENGTH_SHORT).show()
                            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$url"))
                            try {
                                startActivity(marketIntent)
                            } catch (e: ActivityNotFoundException) {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$url")))
                            }
                        }
                        true
                    }
                }
            }

            override fun onPageFinished(view: WebView?, urlNow: String?) {
                super.onPageFinished(view, urlNow)

                if (!loadFailed) {
                    Log.d("WebView", "Page finished OK")
                    Handler(Looper.getMainLooper()).postDelayed({
                        offlineView.visibility = View.GONE
                        webView.visibility = View.VISIBLE
                    }, 3000)
                }

                if (urlNow == url) {
                    webView.clearHistory() // forget previous pages
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message
            ): Boolean {
                popupWebView = WebView(this@WebActivity).apply {
                    settings.apply {
                        javaScriptCanOpenWindowsAutomatically = true
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadsImagesAutomatically = true
                        setSupportMultipleWindows(true)
                        allowFileAccess = true
                        databaseEnabled = true
                        useWideViewPort = true
                        blockNetworkImage = false
                        blockNetworkLoads = false
                        setSaveFormData(true)
                        setSavePassword(true)
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 GooglePay/1.0"

                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        WebView.setWebContentsDebuggingEnabled(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            isAlgorithmicDarkeningAllowed = false
                            setAlgorithmicDarkeningAllowed(false)
                        }

                        webView.isForceDarkAllowed = false
                        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    }
                    webViewClient   = webView.webViewClient
                    webChromeClient = webView.webChromeClient
                }

                popupDialog = Dialog(this@WebActivity).apply {
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                    window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
                    setContentView(popupWebView!!)
                    window?.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setOnDismissListener {
                        popupWebView?.destroy()
                        popupWebView = null
                    }
                    show()
                }

                val transport = (resultMsg.obj as WebView.WebViewTransport)
                transport.webView = popupWebView
                resultMsg.sendToTarget()

                return true
            }

            override fun onCloseWindow(window: WebView) {
                popupDialog?.dismiss()
                popupDialog = null
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                if (ContextCompat.checkSelfPermission(
                        this@WebActivity, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    request.grant(request.resources)
                } else {
                    pendingPermissionRequest = request
                    ActivityCompat.requestPermissions(
                        this@WebActivity,
                        arrayOf(Manifest.permission.CAMERA),
                        REQUEST_CAMERA
                    )
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCb: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                this@WebActivity.filePathCallback?.onReceiveValue(null)
                this@WebActivity.filePathCallback = filePathCb

                if (params.isCaptureEnabled) {
                    val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    if (capture.resolveActivity(packageManager) != null) {
                        val file = createImageFile()
                        cameraImageUri = FileProvider.getUriForFile(
                            this@WebActivity, FILE_PROVIDER_AUTHORITY, file
                        )
                        capture.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                        capture.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        fileChooserLauncher.launch(capture)
                    } else {
                        launchGallery(params)
                    }
                } else {
                    val pick = Intent(
                        Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    ).apply { type = "image/*" }
                    fileChooserLauncher.launch(Intent.createChooser(pick, "Select Image"))
                }
                return true
            }
        }

        fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback
            var picked: Uri? = null

            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                data?.clipData?.let { clip ->
                    if (clip.itemCount > 0) {
                        picked = clip.getItemAt(0).uri
                    }
                }
                if (picked == null) {
                    picked = data?.data
                }
                if (picked == null && cameraImageUri != null) {
                    picked = cameraImageUri
                }
            }
            if (picked != null) {
                callback?.onReceiveValue(arrayOf(picked!!))
            } else {
                callback?.onReceiveValue(null)
            }

            filePathCallback = null
            cameraImageUri = null
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_WRITE_STORAGE
                )
            }

            val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                setTitle(filename)
                setDescription("Downloading $filename")
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    filename
                )
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                allowScanningByMediaScanner()
            }
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)
            Toast.makeText(this, "Downloading $filename …", Toast.LENGTH_SHORT).show()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                popupDialog?.let { dialog ->
                    popupWebView?.let { webV ->
                        if (webV.canGoBack()) {
                            webV.goBack()
                        } else {
                            dialog.dismiss()
                        }
                        return
                    }
                }
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })


        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.loadUrl(url)

    }

    private fun showOfflinePage() {
        webView.visibility = View.GONE
        offlineView.visibility = View.VISIBLE
        retryProgress.visibility = View.GONE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            val req = pendingPermissionRequest
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                req?.grant(req.resources)
            } else {
                req?.deny()
            }
            pendingPermissionRequest = null
        }
    }

    private fun launchOrMarket(intent: Intent, marketPackageId: String) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.d("WebView", "Play Store app not found, trying browser fallback for: $marketPackageId")
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$marketPackageId")
            ).apply {
                setPackage("com.android.vending")
            }
            try {
                startActivity(marketIntent)
            } catch (e: ActivityNotFoundException) {
                Log.d("WebView", "Browser fallback URL: https://play.google.com/store/apps/details?id=$marketPackageId")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$marketPackageId")))
            }
        }
    }

    private fun launchDiiaApp(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            `package` = "ua.gov.diia.app"
        }
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            val market = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=ua.gov.diia.app")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            runCatching { startActivity(market) }
                .onFailure {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=ua.gov.diia.app")
                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    )
                }
            true
        }
    }

    fun launchIntentLink(context: Context, url: String, onError: () -> Unit){
        Log.d("DDD", "launch intent link: $url")
        context.let {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            launchIntent(context, intent, onError)
        }
    }

    fun launchIntent(context: Context, intent: Intent, onError: () -> Unit) {
        try {
            context.startActivity(intent)
        } catch (ex: Exception) {
            ex.printStackTrace()
            onError()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val storageDir = cacheDir
        return File.createTempFile("captured_", ".jpg", storageDir)
    }

    private fun launchGallery(params: WebChromeClient.FileChooserParams) {
        val pick = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            .apply {
                type = "image/*"
                putExtra(
                    Intent.EXTRA_ALLOW_MULTIPLE,
                    params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
                )
            }
        fileChooserLauncher.launch(Intent.createChooser(pick, "Select image"))
    }

    private fun toastNoConnection() = Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
        isInForeground = false
    }

    override fun onStop() {
        super.onStop()
        CookieManager.getInstance().flush()
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

        ViewCompat.setOnApplyWindowInsetsListener(webContainer) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = max(sys.bottom, ime.bottom)
            v.setPadding(sys.left, sys.top, sys.right, bottom)
            insets
        }
    }
}