package com.example.hyundai_contest

import android.app.Application
import com.example.hyundai_contest.signal.Announcer
import com.example.hyundai_contest.signal.PleosSignalSource
import com.example.hyundai_contest.signal.Recorder
import com.example.hyundai_contest.store.CoachDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CoachApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db by lazy { CoachDb(this) }
    val recorder by lazy { Recorder(PleosSignalSource(this), db, Announcer(this), scope) }
}
