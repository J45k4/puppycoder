package com.puppycoder.relay

import android.app.Application
import com.puppycoder.relay.data.ChatRepository
import com.puppycoder.relay.data.ConversationRemoteClient
import com.puppycoder.relay.data.PuppyCoderDatabase
import com.puppycoder.relay.data.SecretStore
import com.puppycoder.relay.update.AppUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PuppyCoderApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val updateManager: AppUpdateManager by lazy {
        AppUpdateManager(this, applicationScope)
    }

    val repository: ChatRepository by lazy {
        ChatRepository(
            context = this,
            dao = PuppyCoderDatabase.get(this).chatDao(),
            client = ConversationRemoteClient(),
            secretStore = SecretStore(),
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { repository.initialize() }
        updateManager.start()
    }
}
