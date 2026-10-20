package com.example.myweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.myweather.ui.theme.MyWeatherTheme

class HistoryActivity : ComponentActivity() {

    private val viewModel: HistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyWeatherTheme {
                HistoryScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onReplay = { record -> startActivity(buildReplayIntent(this, record)) }
                )
            }
        }
    }
}
