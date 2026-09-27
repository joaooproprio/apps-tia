// Última alteração: 27/09/2026 00:55
package com.example.tela_inicial

import android.os.SystemClock

// Depois que um botão encaminha, a tela ignora toques por 3 s.
object TravaToque {
    const val ESPERA_MS = 3_000L

    private var ate = 0L

    fun travada(): Boolean = SystemClock.elapsedRealtime() < ate

    fun travar() {
        travarPor(ESPERA_MS)
    }

    fun travarPor(ms: Long) {
        ate = SystemClock.elapsedRealtime() + ms
    }

    fun liberar() {
        ate = 0L
    }
}
