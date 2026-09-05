package com.example

import android.app.Application
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
}
