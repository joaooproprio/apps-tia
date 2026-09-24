package com.example.ameliafone

import android.os.Build
import android.os.Bundle
import android.telecom.VideoProfile
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class InCallActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- COMEÇO DA MÁGICA DA TIA PARA ACORDAR A TELA ---
        // Se o celular do vovô/vovó for um Android mais moderno (Android 8.1 ou superior)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true) // Faz a atividade aparecer por cima da tela de bloqueio
            setTurnScreenOn(true)   // Força a tela a acender imediatamente
        } else {
            // Se for um modelo de Oba Box com Android mais antigo, usamos as flags clássicas
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // Isso impede que a tela apague sozinha enquanto o telefone estiver tocando
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // --- FIM DA MÁGICA ---

        // Agora sim, carregamos o seu layout bonitão
        setContentView(R.layout.activity_in_call)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvNumero = findViewById<TextView>(R.id.tvNumero)
        val btnAnswer = findViewById<Button>(R.id.btnAnswer)
        val btnHangup = findViewById<Button>(R.id.btnHangup)

        val numero = intent.getStringExtra("NUMERO_CHAMADO")
        if (numero != null) {
            tvNumero.text = numero
        }

        // --- LÓGICA REAL DE ATENDER A LIGAÇÃO ---
        btnAnswer.setOnClickListener {
            // Vamos buscar a chamada que o CallService guardou na caixa global
            val chamada = CallService.chamadaAtiva

            if (chamada != null) {
                // Dá a ordem real ao Android para atender a chamada (apenas áudio)
                chamada.answer(VideoProfile.STATE_AUDIO_ONLY)

                // Atualiza o ecrã para o utilizador saber que correu bem
                tvStatus.text = "Chamada em andamento..."
                btnAnswer.visibility = View.GONE
            } else {
                tvStatus.text = "Erro: Chamada não encontrada"
            }
        }

        // --- LÓGICA REAL DE DESLIGAR A LIGAÇÃO ---
        btnHangup.setOnClickListener {
            val llamada = CallService.chamadaAtiva
            if (llamada != null) {
                // Dá a ordem real ao Android para desligar ou rejeitar a chamada
                llamada.disconnect()
            }
            // Fecha este ecrã e volta para o ecrã principal do telemóvel
            finish()
        }
    }
}