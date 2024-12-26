package com.example.webviewminimalexample

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private val REQUEST_CAMERA_PERMISSION = 200
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

                // Check if data came from gallery
                if (clipData != null || data?.data != null) {
                    // Gallery image(s) selected, so do NOT use cameraImageUri.
                    // Just add the gallery URI(s) to 'uris'.
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            uris.add(clipData.getItemAt(i).uri)
                        }
                    } else if (data?.data != null) {
                        uris.add(data.data!!)
                    }
                } else if (cameraImageUri != null) {
                    // No gallery data present, so we assume user used the camera option
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

        webView.addJavascriptInterface(JavaScriptInterface(this), "AndroidInterface")

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            println(url)
            if (url.startsWith("blob:")) {
                fetchBlobData(url) // Fetch the blob content via JavaScript
            }
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

        webView.loadUrl("https://renderspace.co/app/listing/3600?viewsrc=propnexapp&auth-code=example")
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        println("storage dir")
        println(storageDir)
        return File.createTempFile("IMG_$timeStamp", ".jpg", storageDir)
    }


    private fun fetchBlobData(blobUrl: String) {
        webView.evaluateJavascript(
            """
    (async function() {
    try {
        const response = await fetch("$blobUrl");
        if (!response.ok) throw new Error("Failed to fetch blob. Status: " + response.status);
        const textContent = await response.text();
        const imageResponse = await fetch(textContent);
        if (!imageResponse.ok) throw new Error("Failed to fetch image. Status: " + imageResponse.status);
        const blob = await imageResponse.blob();
        const reader = new FileReader();
        return new Promise((resolve, reject) => {
            reader.onloadend = function() {
                const base64 = reader.result.split(',')[1];
                console.log("Base64 data length: ", base64);
                AndroidInterface.saveImage(base64)
                resolve(base64);
            };
            reader.onerror = function(error) {
                reject("FileReader error: " + error.message);
            };
            reader.readAsDataURL(blob);
        });
    } catch (error) {
        console.error("Error in JavaScript: ", error);
        return "error:" + error.message;
    }
})();

    """.trimIndent()
        ) {}
    }

    class JavaScriptInterface(private val context: Context) {

        @JavascriptInterface
        fun saveImage(base64Data: String) {
            try {
                val base64Image = base64Data.substringAfter("base64,")

                val imageData = Base64.decode(base64Image, Base64.DEFAULT)

                val fileName = "image_${System.currentTimeMillis()}.jpg"

                val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                val imageFile = File(picturesDir, fileName)

                FileOutputStream(imageFile).use { fos ->
                    fos.write(imageData)
                    fos.flush()
                }

                // Add the file to the Media Gallery
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )

                uri?.let { outputStream ->
                    context.contentResolver.openOutputStream(outputStream)?.use { fos ->
                        fos.write(imageData)
                        fos.flush()
                    }
                    Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_LONG).show()
                } ?: run {
                    Toast.makeText(context, "Failed to add image to gallery", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to save image: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }



}

