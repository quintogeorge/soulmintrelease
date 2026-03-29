package com.soulmint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.soulmint.navigation.SoulMintApp
import com.soulmint.ui.theme.SoulMintTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SoulMintTheme {
                SoulMintApp()
            }
        }
    }
}
