package id.go.bgn.popsipgn

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File

/**
 * WebView POP SIPGN.
 * - Input unggah foto: pengguna dapat memilih Galeri atau Kamera.
 * - Kamera JavaScript (getUserMedia): izin kamera diberikan hanya untuk origin POP SIPGN.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorLayout: View

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    private var pendingWebPermission: PermissionRequest? = null
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>

    companion object {
        const val TARGET_URL = "https://pop-sipgn.bgn.go.id/"
        const val USER_AGENT_SUFFIX = " POPSIPGN-WebView/1.1"
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        setupLaunchers()
        setupWebView()
        setupBackNavigation()
        webView.loadUrl(TARGET_URL)
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        progressBar = findViewById(R.id.progressBar)
        errorLayout = findViewById(R.id.errorLayout)

        findViewById<View>(R.id.btnRetry)?.setOnClickListener {
            errorLayout.visibility = View.GONE
            webView.reload()
        }
        swipeRefreshLayout.setOnRefreshListener { webView.reload() }
    }

    private fun setupLaunchers() {
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val callback = filePathCallback
            if (callback != null) {
                val data = result.data
                val clip = data?.clipData
                val selected = if (result.resultCode == RESULT_OK) {
                    when {
                        clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                        data?.data != null -> arrayOf(data.data!!)
                        pendingCameraUri != null -> arrayOf(pendingCameraUri!!)
                        else -> null
                    }
                } else null
                callback.onReceiveValue(selected)
            }
            filePathCallback = null
            pendingCameraUri = null
        }

        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            val request = pendingWebPermission
            pendingWebPermission = null
            if (request != null) {
                if (granted && isTrustedPopOrigin(request.origin)) {
                    val videoResources = request.resources.filter {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    }.toTypedArray()
                    if (videoResources.isNotEmpty()) request.grant(videoResources)
                    else request.deny()
                } else {
                    request.deny()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.userAgentString = settings.userAgentString + USER_AGENT_SUFFIX

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                errorLayout.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                // Tambahkan tombol "Pilih dari Galeri" pada modal kamera POP SIPGN.
                if (url?.startsWith(TARGET_URL) == true) {
                    injectSipgnGalleryScript()
                }
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                    errorLayout.visibility = View.VISIBLE
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme.orEmpty()
                if (scheme == "http" || scheme == "https") return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) progressBar.visibility = View.GONE
            }

            // Untuk <input type="file">: tampilkan pemilih foto dan aplikasi kamera.
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                launchGalleryAndCamera(params)
                return true
            }

            // Untuk kamera yang tampil langsung di halaman melalui getUserMedia().
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                runOnUiThread {
                    if (!isTrustedPopOrigin(request.origin)) {
                        request.deny()
                        return@runOnUiThread
                    }
                    if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                        } else {
                            pendingWebPermission?.deny()
                            pendingWebPermission = request
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    } else {
                        request.deny()
                    }
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                if (pendingWebPermission == request) pendingWebPermission = null
            }

            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
            ): Boolean {
                val newWebView = WebView(this@MainActivity)
                newWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean {
                        webView.loadUrl(request?.url.toString())
                        return true
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newWebView
                resultMsg?.sendToTarget()
                return true
            }
        }
    }

    private fun injectSipgnGalleryScript() {
        try {
            val script = assets.open("sipgn-gallery.js").bufferedReader().use { it.readText() }
            webView.evaluateJavascript(script, null)
        } catch (e: Exception) {
            Toast.makeText(this, "Script galeri gagal dimuat: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isTrustedPopOrigin(origin: Uri): Boolean {
        val host = origin.host?.lowercase() ?: return false
        return host == "pop-sipgn.bgn.go.id" || host.endsWith(".pop-sipgn.bgn.go.id")
    }

    private fun launchGalleryAndCamera(params: WebChromeClient.FileChooserParams?) {
        try {
            val acceptTypes = params?.acceptTypes
                ?.filter { it.isNotBlank() }
                ?.flatMap { it.split(",") }
                ?.map { it.trim().lowercase() }
                .orEmpty()
            val imageOnly = acceptTypes.isEmpty() ||
                acceptTypes.any { it.startsWith("image/") || it == "image/*" }

            val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (imageOnly) "image/*" else "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
            }

            val initialIntents = if (imageOnly) {
                createCameraIntent()?.let { arrayOf(it) } ?: emptyArray()
            } else emptyArray()

            val chooser = Intent.createChooser(contentIntent, "Pilih foto atau ambil foto").apply {
                if (initialIntents.isNotEmpty()) {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents)
                }
            }
            fileChooserLauncher.launch(chooser)
        } catch (e: ActivityNotFoundException) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            Toast.makeText(this, "Aplikasi galeri tidak tersedia", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            Toast.makeText(this, "Tidak dapat membuka pilihan foto: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createCameraIntent(): Intent? {
        return try {
            val photoFile = File.createTempFile("popsipgn_photo_", ".jpg", cacheDir)
            val uri = FileProvider.getUriForFile(
                this, packageName + FILE_PROVIDER_SUFFIX, photoFile
            )
            pendingCameraUri = uri
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
                clipData = ClipData.newRawUri("Foto POP SIPGN", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.takeIf { it.resolveActivity(packageManager) != null }
        } catch (_: Exception) {
            pendingCameraUri = null
            null
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        pendingWebPermission?.deny()
        pendingWebPermission = null
        webView.stopLoading()
        webView.clearHistory()
        super.onDestroy()
    }
}
