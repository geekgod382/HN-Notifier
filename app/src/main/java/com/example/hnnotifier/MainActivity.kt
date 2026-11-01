package com.example.hnnotifier

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.hnnotifier.data.HNPreferences
import com.example.hnnotifier.ui.theme.HNNotifierTheme
import com.example.hnnotifier.worker.HNNotificationWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setupBackgroundWorker(applicationContext)

        setContent {
            HNNotifierTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun setupBackgroundWorker(context: android.content.Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<HNNotificationWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HNNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { HNPreferences(context) }

    var keywordsInput by remember { mutableStateOf(prefs.keywords) }
    var savedMessage by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "HN Notifier",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Background service runs every 15 minutes to notify you of new Hacker News posts.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = keywordsInput,
            onValueChange = { keywordsInput = it },
            label = { Text("Filter Keywords (comma separated)") },
            placeholder = { Text("e.g. python, rust, cyber, security") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Button(
            onClick = {
                prefs.keywords = keywordsInput
                savedMessage = "Keywords saved successfully!"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Filters")
        }

        if (savedMessage.isNotEmpty()) {
            Text(
                text = savedMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

    }
}
