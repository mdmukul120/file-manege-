package com.example.aifiles

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class OrganizeWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result = try {
        Organizer.run(applicationContext) { }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
