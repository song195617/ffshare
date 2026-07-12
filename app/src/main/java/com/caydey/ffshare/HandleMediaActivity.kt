package com.caydey.ffshare

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.caydey.ffshare.extensions.parcelable
import com.caydey.ffshare.extensions.parcelableArrayList
import com.caydey.ffshare.utils.MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
import com.caydey.ffshare.utils.MediaCompressor
import com.caydey.ffshare.utils.Settings
import com.caydey.ffshare.utils.Utils
import timber.log.Timber


class HandleMediaActivity : AppCompatActivity() {
    // by lazy means load when variable is used, lazy-loading helps performance
    // also without it there is a null error for applicationContext
    private val mediaCompressor: MediaCompressor by lazy { MediaCompressor(applicationContext) }
    private val utils: Utils by lazy { Utils(applicationContext) }
    private val settings: Settings by lazy { Settings(applicationContext) }

    // Launch share chooser and finish activity after it closes,
    // so the process stays alive while the target app reads the FileProvider URI
    private val shareLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_handle_media)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (utils.isReadPermissionGranted) {
            onMediaReceive()
        } else {
            Timber.d("Requesting read permissions")
            utils.requestReadPermissions(this)
        }
    }

    override fun finish() {
        mediaCompressor.cancelAllOperations()
        scheduleCacheCleanup()
        super.finish()
    }

    override fun onStop() {
        mediaCompressor.cancelAllOperations()
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // first time running app user is requested to allow app to read external storage,
        // after clicking "allow" the app will continue handling media it was shared
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            Timber.d("Read permissions granted, continuing...")
            onMediaReceive()
        }
    }

    private fun onMediaReceive() {
        // "intent" variable is the shared item
        val receivedMedia =
            when (intent.action) {
                Intent.ACTION_SEND -> arrayListOf(intent.parcelable(Intent.EXTRA_STREAM)!!)
                Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayList(Intent.EXTRA_STREAM)!!
                else -> ArrayList<Uri>()
            }

        // unable to get file from intent
        if (receivedMedia.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_uri_intent), Toast.LENGTH_LONG).show()
            Timber.d("No files found in shared intent")
            finish()
        } else {
            // callback
            mediaCompressor.compressFiles(this, receivedMedia) { compressedMedia, outputFiles, _ ->
                // always save to DCIM/FFShare
                if (outputFiles.isNotEmpty()) {
                    var savedCount = 0
                    for ((outputFile, sourceUri) in outputFiles) {
                        val mediaType = utils.getMediaType(sourceUri)
                        val result = utils.saveToOutputDirectory(outputFile, mediaType)
                        if (result != null) savedCount++
                    }
                    if (savedCount > 0) {
                        runOnUiThread {
                            Toast.makeText(this, "已保存 $savedCount 个文件到 DCIM/FFShare", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                if (compressedMedia.isNotEmpty()) {
                    shareMedia(compressedMedia)
                } else {
                    finish()
                }
            }
        }
    }

    private fun shareMedia(mediaUris: ArrayList<Uri>) {
        val shareIntent = Intent()

        // temp permissions for other app to view file
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // add compressed media files
        if (mediaUris.size == 1) {
            Timber.d("Creating share intent for single item")
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, mediaUris[0])
        } else {
            Timber.d("Creating share intent for multiple items")
            shareIntent.action = Intent.ACTION_SEND_MULTIPLE
            shareIntent.putExtra(Intent.EXTRA_STREAM, mediaUris)
        }

        // set mime type from first file
        val mimeType = contentResolver.getType(mediaUris[0]) ?: "*/*"
        shareIntent.type = mimeType

        val chooser = Intent.createChooser(shareIntent, "media")
        shareLauncher.launch(chooser)
    }

    private fun scheduleCacheCleanup() {
        Timber.d("Scheduling cleanup alarm")
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(applicationContext, CacheCleanUpReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)

        // every 12 hours clear cache
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime(),
            AlarmManager.INTERVAL_HALF_DAY,
            pendingIntent
        )
    }
}
