package com.example.hyundai_contest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.hyundai_contest.ui.AppScreen
import com.example.hyundai_contest.ui.CoachTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as CoachApp

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Start either way: without location the coaching still works, places just stay empty.
        CoachService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            CoachService.start(this)
        } else {
            askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        setContent { CoachTheme { AppScreen(app) } }
    }
}
