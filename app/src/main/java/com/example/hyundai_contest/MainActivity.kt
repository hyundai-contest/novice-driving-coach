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

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Start either way: without location places stay empty, without car speed the app falls back to GPS speed.
        CoachService.start(this)
        app.recorder.source.connectCarSpeed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missing = PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) CoachService.start(this) else askPermissions.launch(missing.toTypedArray())
        setContent { CoachTheme { AppScreen(app) } }
    }

    private companion object {
        val PERMISSIONS = listOf(Manifest.permission.ACCESS_FINE_LOCATION, "android.car.permission.CAR_SPEED")
    }
}
