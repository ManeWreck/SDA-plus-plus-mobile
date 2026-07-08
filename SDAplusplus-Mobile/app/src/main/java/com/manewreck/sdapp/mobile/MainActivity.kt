package com.manewreck.sdapp.mobile

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.manewreck.sdapp.mobile.ui.SdaMobileRoot
import com.manewreck.sdapp.mobile.ui.theme.SdaMobileTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SdaMobileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SdaMobileRoot()
                }
            }
        }
    }
}
