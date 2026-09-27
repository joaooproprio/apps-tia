package com.example.tela_inicial

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

// Sem Service: o processo só escuta o desbloqueio enquanto estiver vivo.
class InicioApp : Application() {

    private val aoDesbloquear = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != Intent.ACTION_USER_PRESENT) return
            val abrir = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(abrir)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filtro = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(aoDesbloquear, filtro, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(aoDesbloquear, filtro)
        }
    }
}
