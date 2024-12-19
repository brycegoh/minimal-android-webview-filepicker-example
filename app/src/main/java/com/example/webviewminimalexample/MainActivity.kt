package com.example.webviewminimalexample

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    private val filePickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            println(result)
            if (result.resultCode == Activity.RESULT_OK) {
                val uris = mutableListOf<Uri>()

                val data = result.data
                val clipData = data?.clipData

                if (clipData != null || data?.data != null) {
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            uris.add(clipData.getItemAt(i).uri)
                        }
                    } else if (data?.data != null) {
                        uris.add(data.data!!)
                    }
                } else if (cameraImageUri != null) {
                    uris.add(cameraImageUri!!)
                }

                fileChooserCallback?.onReceiveValue(uris.toTypedArray())
            } else {
                fileChooserCallback?.onReceiveValue(null)
            }
            fileChooserCallback = null
            cameraImageUri = null // Reset URI after use
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        setupWebView()
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            domStorageEnabled = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback = filePathCallback

                // Create intent for capturing a photo
                val takePictureIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent.resolveActivity(packageManager) != null) {
                    val photoFile = createImageFile()
                    cameraImageUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${applicationContext.packageName}.fileprovider",
                        photoFile
                    )
                    takePictureIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraImageUri)
                }

                // Intent for picking an image from the gallery
                val galleryIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }

                // Combine intents into a chooser
                val chooserIntent = Intent.createChooser(galleryIntent, "Select Image")
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))

                filePickerLauncher.launch(chooserIntent)
                return true
            }
        }

        webView.loadUrl("https://renderspace.co/app/render...")
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        println("storage dir")
        println(storageDir)
        return File.createTempFile("IMG_$timeStamp", ".jpg", storageDir)
    }
}
