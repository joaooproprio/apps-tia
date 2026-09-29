package com.example.ameliafone
// Última alteração: 29/09/2026 00:35

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telecom.Call
import android.telecom.DisconnectCause
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class CallService : InCallService() {

    private var eraToque = false
    private var atendeu = false
    private var jaAvisou = false
    private var nomeAviso: String? = null
    private var numeroAviso: String? = null
    private var perdidasSeguidas = 0

    private val andamento = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state != Call.STATE_RINGING) pararVibracao()
            if (state == Call.STATE_ACTIVE) atendeu = true
            if (state == Call.STATE_DISCONNECTED) avisarSePerdida(call)
        }
    }

    companion object {
        var chamadaAtiva: Call? = null
        const val EXTRA_LIGANDO = "LIGANDO"
        private const val ID_PERDIDA = 7
        private const val CANAL_PERDIDAS = "chamadas_perdidas"
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        chamadaAtiva = call
        val numeroChamado = call.details?.handle?.schemeSpecificPart ?: Agenda.DESCONHECIDO

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
            putExtra(EXTRA_LIGANDO, ehSaida(call))
        }

        startActivity(intent)

        eraToque = false
        atendeu = false
        jaAvisou = false
        nomeAviso = null
        numeroAviso = numeroChamado
        call.registerCallback(andamento)
        // Ligação de saída não vibra. Desconhecido não tem toque, então vibra no lugar.
        if (call.details.state != Call.STATE_RINGING) return
        eraToque = true
        val resultado = Agenda.buscar(this, numeroChamado)
        if (resultado is Agenda.Resultado.Achou) nomeAviso = resultado.nome
        if (resultado is Agenda.Resultado.NaoEsta) vibrarDesconhecido()
    }

    // Ligação feita neste aparelho abre só com DESLIGAR.
    private fun ehSaida(call: Call): Boolean {
        val estado = call.details.state
        if (estado == Call.STATE_DIALING || estado == Call.STATE_CONNECTING) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return call.details.callDirection == Call.Details.DIRECTION_OUTGOING
        }
        return false
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        pararVibracao()
        avisarSePerdida(call)
        call.unregisterCallback(andamento)
        if (chamadaAtiva == call) {
            chamadaAtiva = null
        }
    }

    override fun onDestroy() {
        pararVibracao()
        super.onDestroy()
    }

    // O toque forte assusta. Desconhecido vibra em pulsos curtos e fracos.
    private fun vibrarDesconhecido() {
        val tempos = longArrayOf(0, 180, 720)
        val forcas = intArrayOf(0, 60, 0)
        val efeito = VibrationEffect.createWaveform(tempos, forcas, 0)
        vibrador().vibrate(efeito)
    }

    private fun pararVibracao() {
        vibrador().cancel()
    }

    private fun vibrador(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun avisarSePerdida(call: Call) {
        if (jaAvisou || !eraToque || atendeu) return
        val codigo = call.details?.disconnectCause?.code ?: return
        if (codigo != DisconnectCause.MISSED &&
            codigo != DisconnectCause.CANCELED &&
            codigo != DisconnectCause.REMOTE
        ) return
        if (!podeNotificar()) return
        jaAvisou = true
        val gerente = NotificationManagerCompat.from(this)
        val aindaLa = gerente.activeNotifications.any { it.id == ID_PERDIDA }
        if (!aindaLa) perdidasSeguidas = 0
        perdidasSeguidas++
        val quem = nomeAviso?.takeIf { it.isNotBlank() } ?: numeroAviso ?: Agenda.DESCONHECIDO
        val titulo = if (perdidasSeguidas == 1) {
            getString(R.string.titulo_perdida)
        } else {
            getString(R.string.titulo_perdidas, perdidasSeguidas)
        }
        criarCanal()
        val aviso = NotificationCompat.Builder(this, CANAL_PERDIDAS)
            .setSmallIcon(R.drawable.ic_perdida)
            .setContentTitle(titulo)
            .setContentText(quem)
            .setStyle(NotificationCompat.BigTextStyle().bigText(quem))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(abrirApp())
            .build()
        gerente.notify(ID_PERDIDA, aviso)
    }

    private fun podeNotificar(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun criarCanal() {
        val canal = NotificationChannel(
            CANAL_PERDIDAS,
            getString(R.string.canal_perdidas),
            NotificationManager.IMPORTANCE_HIGH
        )
        canal.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        canal.enableVibration(false)
        canal.setSound(
            Settings.System.DEFAULT_NOTIFICATION_URI,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    private fun abrirApp(): PendingIntent {
        val abrir = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, abrir, flags)
    }
}
