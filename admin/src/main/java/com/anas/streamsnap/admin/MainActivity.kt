package com.anas.streamsnap.admin

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.anas.streamsnap.core.theme.AppTheme

/** FragmentActivity because the fingerprint prompt needs one. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The admin app shows user data: no screenshots and no preview in the recent apps list.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            AppTheme { AdminRoot(this) }
        }
    }

    override fun onStart() {
        super.onStart()
        AdminLock.onStart()
    }

    override fun onStop() {
        AdminLock.onStop()
        super.onStop()
    }
}
