package com.rakdatak.app.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberWearLiveMetrics(): WearLiveMetrics {
    val context = LocalContext.current
    val repository = remember {
        WearLiveMetricsRepository(context.applicationContext)
    }
    val state by repository.state.collectAsState()

    DisposableEffect(repository) {
        repository.start()
        onDispose { repository.stop() }
    }

    return state
}
