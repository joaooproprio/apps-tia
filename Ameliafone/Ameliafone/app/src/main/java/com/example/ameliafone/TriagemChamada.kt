package com.example.ameliafone
// Última alteração: 28/09/2026 00:38

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService

// Fora da agenda o sistema não toca. Se a agenda falhar, João e Janete continuam ouvindo.
class TriagemChamada : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val silenciar = !eSaida(details) &&
            Agenda.buscar(this, details.handle?.schemeSpecificPart) is Agenda.Resultado.NaoEsta
        val resposta = CallResponse.Builder().setSilenceCall(silenciar).build()
        respondToCall(details, resposta)
    }

    private fun eSaida(details: Call.Details): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            details.callDirection == Call.Details.DIRECTION_OUTGOING
    }
}
