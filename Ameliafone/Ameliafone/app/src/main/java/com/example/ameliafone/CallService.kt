package com.example.ameliafone

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.telecom.Call
import android.telecom.InCallService

class CallService : InCallService() {

    companion object {
        var chamadaAtiva: Call? = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        chamadaAtiva = call
        val numeroChamado = call.details?.handle?.schemeSpecificPart ?: "Número Desconhecido"

        // --- COMEÇO DO DESPERTADOR DA TIA ---
        // Pega o controle de energia do celular
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        // Cria um feitiço para forçar a tela a acender (ACQUIRE_CAUSES_WAKEUP)
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "Ameliafone::AcordaTelaTia"
        )

        // Dá o choque para acordar o celular por 3 segundos (tempo suficiente para a tela abrir)
        wakeLock.acquire(3000)
        // --- FIM DO DESPERTADOR ---

        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NUMERO_CHAMADO", numeroChamado)
        }

        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (chamadaAtiva == call) {
            chamadaAtiva = null
        }
    }
}