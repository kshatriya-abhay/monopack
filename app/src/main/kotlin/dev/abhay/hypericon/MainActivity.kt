package dev.abhay.hypericon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.abhay.hypericon.ui.MainScreen
import dev.abhay.hypericon.ui.theme.HyperIconTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HyperIconTheme {
                MainScreen()
            }
        }
    }
}
