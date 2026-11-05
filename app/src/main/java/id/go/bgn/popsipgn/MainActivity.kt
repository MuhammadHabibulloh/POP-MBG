package id.go.bgn.popsipgn

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.view.View
import android.webkit.WebChromeClient.FileChooserParams
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private var pendingWebPermission: PermissionRequest? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingWebPermission?.let { request ->
                    request.grant(request.resources)
                    pendingWebPermission = null
                }
            } else {
                pendingWebPermission?.deny()
                pendingWebPermission = null
                Toast.makeText(this, "Izin kamera tidak diberikan", Toast.LENGTH_SHORT).show()
            }
        }

    private val chooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileCallback
            fileCallback = null

            if (callback == null) return@registerForActivityResult

            var results: Array<Uri>? = null
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                results = when {
                    data?.clipData != null -> {
                        val clip = data.clipData!!
                        Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                    }
                    data?.data != null -> arrayOf(data.data!!)
                    pendingCameraUri != null && File(
                        cacheDir,
                        "pop_camera_placeholder"
                    ).exists() -> arrayOf(pendingCameraUri!!)
                    // Camera apps commonly return RESULT_OK with a null Intent when EXTRA_OUTPUT is used.
                    pendingCameraUri != null -> arrayOf(pendingCameraUri!!)
                    else -> null
                }
            }
            callback.onReceiveValue(results)
            pendingCameraUri = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            allowFileAccess = false
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                showCameraOrGalleryChooser(params)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val wantsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    if (wantsCamera &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingWebPermission = request
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        request.grant(request.resources)
                    }
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingWebPermission == request) pendingWebPermission = null
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return if (uri.scheme == "http" || uri.scheme == "https") {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Tautan tidak dapat dibuka", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl("https://pop-sipgn.bgn.go.id/")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun showCameraOrGalleryChooser(params: FileChooserParams?) {
        try {
            val accept = params?.acceptTypes?.joinToString(",")?.lowercase(Locale.ROOT).orEmpty()
            val imageOnly = accept.isBlank() || accept.contains("image") || accept.contains("*/*")

            val galleryIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (imageOnly) "image/*" else params?.acceptTypes?.firstOrNull() ?: "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
            }

            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (cameraIntent.resolveActivity(packageManager) != null) {
                val photoFile = createImageFile()
                pendingCameraUri = FileProvider.getUriForFile(
                    this, "$packageName.fileprovider", photoFile
                )
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri)
                cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                putExtra(Intent.EXTRA_INTENT, galleryIntent)
                putExtra(Intent.EXTRA_TITLE, "Pilih foto")
                if (pendingCameraUri != null) {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                }
            }
            chooserLauncher.launch(chooser)
        } catch (e: Exception) {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
            pendingCameraUri = null
            Toast.makeText(this, "Tidak dapat membuka kamera atau galeri", Toast.LENGTH_LONG).show()
        }
    }

    private fun createImageFile(): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File.createTempFile("POP_${stamp}_", ".jpg", cacheDir)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android API; retained for broad WebView compatibility")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView.destroy()
        super.onDestroy()
    }
}
