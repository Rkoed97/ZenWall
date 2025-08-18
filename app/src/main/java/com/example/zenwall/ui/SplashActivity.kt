package com.example.zenwall.ui

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.example.zenwall.MainActivity
import com.example.zenwall.R

class SplashActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Immediately continue to MainActivity; finish to remove from back stack
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}