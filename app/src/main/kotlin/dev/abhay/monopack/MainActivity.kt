package dev.abhay.monopack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.abhay.monopack.ui.AppRoot
import dev.abhay.monopack.ui.theme.MonopackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MonopackTheme {
                AppRoot()
            }
        }
    }
}
