package com.acgames.painel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Depois de reiniciar o celular, volta a monitorar se o painel já foi aberto antes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && SecureStore.get(context) != null) NotifyService.start(context)
    }
}
