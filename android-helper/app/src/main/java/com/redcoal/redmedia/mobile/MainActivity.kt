package com.redcoal.redmedia.mobile

import android.Manifest
import android.app.DownloadManager
import android.content.ClipDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.redcoal.redmedia.mobile.ui.RedMediaRoot
import com.redcoal.redmedia.mobile.ui.RedMediaTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        requestNotificationPermission()
        setContent {
            RedMediaTheme {
                RedMediaRoot(
                    viewModel = viewModel,
                    onOpenDownloads = {
                        runCatching { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> if (intent.type == ClipDescription.MIMETYPE_TEXT_PLAIN) {
                intent.getStringExtra(Intent.EXTRA_TEXT)
            } else null
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        sharedText?.let { viewModel.setUrl(it, autoAnalyze = true) }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
