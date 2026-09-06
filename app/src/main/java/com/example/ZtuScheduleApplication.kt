package com.example

import android.app.Application
import android.content.res.Configuration
import com.example.data.local.AppDatabase
import com.example.data.repository.ScheduleRepository

class ZtuScheduleApplication : Application() {

    lateinit var repository: ScheduleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        repository = ScheduleRepository(this, dao = database.scheduleDao())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::repository.isInitialized) {
            repository.notifyWidgetUpdate()
        }
    }
}
