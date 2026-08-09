package com.puppycoder.relay.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.puppycoder.relay.PuppyCoderApplication

class OutboxWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val conversationId = inputData.getString(CONVERSATION_ID) ?: return Result.failure()
        val repository = (applicationContext as PuppyCoderApplication).repository
        repository.drainOutbox(conversationId)
        return Result.success()
    }

    companion object {
        const val CONVERSATION_ID = "conversation_id"
    }
}
