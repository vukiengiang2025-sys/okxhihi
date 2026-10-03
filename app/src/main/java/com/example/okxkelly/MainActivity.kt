package com.example.okxkelly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.okxkelly.ui.SimulatorScreen
import com.example.okxkelly.ui.SimulatorViewModel
import com.example.okxkelly.ui.theme.OKXKellyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OKXKellyTheme {
                val viewModel: SimulatorViewModel = viewModel()
                SimulatorScreen(viewModel = viewModel)
            }
        }
    }
}
