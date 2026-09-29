package com.example.ameliafone
// Última alteração: 29/09/2026 00:35

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.telecom.DisconnectCause
import android.telecom.VideoProfile
import android.util.TypedValue
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat

class InCallActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val sensores by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private var chamada: Call? = null
    private var estado = Call.STATE_NEW
    private var atendida = false
    private var encerrada = false
    private var desligouAqui = false
    private var podeVoltar = false
    private var telaApagada = false
    private var longeDaOrelha = true
    private var numeroConhecido = false
    private var ligando = false
    private var numeroChamado: String? = null
    private var travaOrelha: PowerManager.WakeLock? = null
    private var ultimaPerto: Boolean? = null

    private lateinit var raiz: View
    private lateinit var tvStatus: TextView
    private lateinit var tvNumero: TextView
    private lateinit var tvX: TextView
    private lateinit var btnAnswer: Button
    private lateinit var btnHangup: Button

    private val liberarTela = Runnable {
        if (isDestroyed || encerrada) return@Runnable
        // Só depois dos 5 s a orelha pode acender de novo.
        podeVoltar = true
        if (longeDaOrelha) ligarJanela() else apagarJanela()
    }

    private val ouvido = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val perto = event.values[0] < event.sensor.maximumRange
            if (perto == ultimaPerto) return
            ultimaPerto = perto
            longeDaOrelha = !perto
            aplicarOrelha()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val andamento = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            val anterior = estado
            estado = state
            if (state == Call.STATE_ACTIVE && anterior == Call.STATE_RINGING && !ligando) {
                apagarAoAtender()
            }
            if (ligando && state == Call.STATE_ACTIVE) {
                tvStatus.text = getString(R.string.chamada_andamento)
            }
            if (state == Call.STATE_DISCONNECTED) {
                val codigo = call.details?.disconnectCause?.code
                if (codigo == DisconnectCause.LOCAL || codigo == DisconnectCause.REJECTED) return
                outroLadoDesligou()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acenderParaAtender()
        setContentView(R.layout.activity_in_call)

        raiz = findViewById(R.id.raizChamada)
        afastarDaBarra()
        tvStatus = findViewById(R.id.tvStatus)
        tvNumero = findViewById(R.id.tvNumero)
        tvX = findViewById(R.id.tvX)
        btnAnswer = findViewById(R.id.btnAnswer)
        btnHangup = findViewById(R.id.btnHangup)

        numeroChamado = intent.getStringExtra("NUMERO_CHAMADO")
        if (numeroChamado != null) tvNumero.text = numeroChamado

        // Toque curto: ao soltar o dedo em cima do botão, atende ou desliga.
        aoSoltar(btnAnswer) { atender() }
        aoSoltar(btnHangup) { desligar() }

        chamada = CallService.chamadaAtiva
        estado = chamada?.details?.state ?: Call.STATE_NEW
        chamada?.registerCallback(andamento, handler)

        ligando = intent.getBooleanExtra(CallService.EXTRA_LIGANDO, false) ||
            estado == Call.STATE_DIALING ||
            estado == Call.STATE_CONNECTING
        if (ligando) {
            prepararLigacaoDeSaida()
        } else {
            // Número fora da agenda não ganha o botão verde grande.
            avaliarNumero(numeroChamado)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PEDIDO_CONTATOS) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            tvStatus.text = getString(R.string.recebendo_chamada)
            tvStatus.textSize = 24f
            avaliarNumero(numeroChamado)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Na orelha o rosto acerta DESLIGAR. Enquanto a tela está apagada, o toque não vale.
        if (telaApagada) return true
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        handler.removeCallbacks(liberarTela)
        sensores.unregisterListener(ouvido)
        chamada?.unregisterCallback(andamento)
        if (!encerrada) soltarOrelha()
        super.onDestroy()
    }

    private fun afastarDaBarra() {
        // O desligar não pode ficar em cima de voltar, início e recentes.
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val barra = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            vista.setPadding(vista.paddingLeft, vista.paddingTop, vista.paddingRight, barra)
            insets
        }
        ViewCompat.requestApplyInsets(raiz)
    }

    private fun acenderParaAtender() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun aoSoltar(botao: Button, acao: () -> Unit) {
        botao.isLongClickable = false
        botao.setOnTouchListener { vista, evento ->
            if (telaApagada) return@setOnTouchListener true
            if (evento.actionMasked == MotionEvent.ACTION_UP) {
                val dentro = evento.x >= 0 && evento.y >= 0 &&
                    evento.x <= vista.width && evento.y <= vista.height
                if (dentro) acao()
            }
            true
        }
    }

    private fun atender() {
        val ativa = chamada
        if (ativa == null) {
            tvStatus.text = "Erro: Chamada não encontrada"
            return
        }
        ativa.answer(VideoProfile.STATE_AUDIO_ONLY)
        apagarAoAtender()
    }

    private fun desligar() {
        desligouAqui = true
        chamada?.disconnect()
        finish()
    }

    // Saída só precisa de DESLIGAR. A orelha esconde esse botão, como depois de atender.
    private fun prepararLigacaoDeSaida() {
        tvStatus.text = if (estado == Call.STATE_ACTIVE) {
            getString(R.string.chamada_andamento)
        } else {
            getString(R.string.ligando)
        }
        encolherDesligar()
        podeVoltar = true
        ouvirOrelha()
        avaliarNumero(numeroChamado)
    }

    private fun apagarAoAtender() {
        if (atendida || encerrada) return
        atendida = true
        podeVoltar = false
        tvStatus.text = getString(R.string.chamada_andamento)
        encolherDesligar()
        apagarJanela()
        segurarOrelha()
        ouvirOrelha()
        handler.postDelayed(liberarTela, ORELHA_MS)
    }

    private fun outroLadoDesligou() {
        if (encerrada || desligouAqui) return
        encerrada = true
        podeVoltar = false
        handler.removeCallbacks(liberarTela)
        sensores.unregisterListener(ouvido)
        apagarJanela()
        soltarOrelha()
        agendarDormir()
        finish()
    }

    private fun aplicarOrelha() {
        if (encerrada) return
        if (longeDaOrelha) {
            soltarOrelha()
            if (podeVoltar) ligarJanela() else apagarJanela()
        } else {
            segurarOrelha()
            apagarJanela()
        }
    }

    private fun ouvirOrelha() {
        val sensor = sensores.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return
        sensores.registerListener(ouvido, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun apagarJanela() {
        if (isDestroyed) return
        telaApagada = true
        raiz.setBackgroundColor(Color.BLACK)
        tvStatus.visibility = View.INVISIBLE
        tvNumero.visibility = View.INVISIBLE
        tvX.visibility = View.INVISIBLE
        btnAnswer.visibility = View.INVISIBLE
        btnHangup.visibility = View.INVISIBLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 0f }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(false)
        }
    }

    private fun ligarJanela() {
        if (!podeVoltar || encerrada || isDestroyed) return
        telaApagada = false
        soltarOrelha()
        if (!numeroConhecido && !atendida && !ligando) pintarDesconhecido() else pintarNormal()
        tvStatus.visibility = View.VISIBLE
        tvNumero.visibility = View.VISIBLE
        tvX.visibility = if (atendida || numeroConhecido || ligando) View.GONE else View.VISIBLE
        btnAnswer.visibility = if (atendida || ligando) View.GONE else View.VISIBLE
        btnHangup.visibility = View.VISIBLE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    private fun segurarOrelha() {
        val energia = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!energia.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        if (travaOrelha == null) {
            // Esta trava apaga a tela na orelha. Não é o despertador da chamada.
            travaOrelha = energia.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "TelefoneAmelia::Orelha"
            )
            travaOrelha?.setReferenceCounted(false)
        }
        if (travaOrelha?.isHeld != true) travaOrelha?.acquire()
    }

    private fun soltarOrelha() {
        val trava = travaOrelha ?: return
        if (trava.isHeld) trava.release()
    }

    private fun agendarDormir() {
        val contexto = applicationContext
        val admin = ComponentName(contexto, AdminAparelho::class.java)
        val dormirAgora = Runnable {
            val dpm = contexto.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isAdminActive(admin)) dpm.lockNow()
        }
        handler.post(dormirAgora)
        // A tela inicial pode acender de novo no celular lento. Apaga outra vez se não entrou chamada nova.
        listOf(1_000L, 4_000L).forEach { espera ->
            handler.postDelayed({
                if (CallService.chamadaAtiva != null) return@postDelayed
                dormirAgora.run()
            }, espera)
        }
    }

    private fun avaliarNumero(numero: String?) {
        val mostrado = numero ?: ""
        Thread {
            val resultado = Agenda.buscar(this, numero)
            runOnUiThread {
                if (isDestroyed || atendida || encerrada) return@runOnUiThread
                when (resultado) {
                    is Agenda.Resultado.Achou -> {
                        if (!ligando) mostrarConhecido()
                        mostrarQuemLiga(resultado.nome, mostrado)
                    }
                    is Agenda.Resultado.NaoEsta -> {
                        if (!ligando) mostrarDesconhecido()
                        mostrarQuemLiga(null, mostrado)
                    }
                    Agenda.Resultado.Falhou -> {
                        if (ligando) return@runOnUiThread
                        mostrarDesconhecido()
                        mostrarQuemLiga(null, mostrado)
                        if (!Agenda.podeLer(this)) {
                            tvStatus.text = getString(R.string.erro_contatos)
                            tvStatus.textSize = 32f
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(Manifest.permission.READ_CONTACTS),
                                PEDIDO_CONTATOS
                            )
                        }
                    }
                }
            }
        }.start()
    }

    private fun mostrarQuemLiga(nome: String?, numero: String) {
        if (nome.isNullOrBlank() || numero.isBlank()) {
            tvNumero.text = if (numero.isBlank()) nome ?: "" else numero
            tvNumero.setTypeface(tvNumero.typeface, Typeface.BOLD)
            return
        }
        val corpo = "$nome\n$numero"
        val texto = SpannableString(corpo)
        texto.setSpan(StyleSpan(Typeface.BOLD), 0, nome.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val inicio = nome.length + 1
        texto.setSpan(StyleSpan(Typeface.NORMAL), inicio, corpo.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        texto.setSpan(RelativeSizeSpan(0.7f), inicio, corpo.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvNumero.setTypeface(null, Typeface.NORMAL)
        tvNumero.text = texto
    }

    private fun mostrarConhecido() {
        numeroConhecido = true
        pintarNormal()
        tvX.visibility = View.GONE
        peso(tvX, 0f)
        peso(btnAnswer, 60f)
        btnAnswer.setTextSize(50f)
    }

    private fun mostrarDesconhecido() {
        numeroConhecido = false
        tvX.visibility = View.VISIBLE
        peso(tvStatus, 8f)
        peso(tvNumero, 8f)
        peso(tvX, 54f)
        peso(btnAnswer, 15f)
        peso(btnHangup, 15f)
        btnAnswer.setTextSize(40f)
        pintarDesconhecido()
    }

    // Fundo vermelho e o resto branco. O X cresce para ela ver de longe.
    private fun pintarDesconhecido() {
        raiz.setBackgroundColor(COR_VERMELHA)
        tvStatus.setTextColor(Color.WHITE)
        tvNumero.setTextColor(Color.WHITE)
        tvX.setTextColor(Color.WHITE)
        botaoBranco(btnAnswer)
        botaoBranco(btnHangup)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            tvX,
            96,
            280,
            2,
            TypedValue.COMPLEX_UNIT_SP
        )
    }

    private fun pintarNormal() {
        raiz.setBackgroundColor(Color.WHITE)
        tvStatus.setTextColor(Color.BLACK)
        tvNumero.setTextColor(Color.BLACK)
        TextViewCompat.setAutoSizeTextTypeWithDefaults(tvX, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
        tvX.setTextSize(120f)
        tvX.setTextColor(COR_VERMELHA)
        btnAnswer.backgroundTintList = ColorStateList.valueOf(COR_VERDE)
        btnAnswer.setTextColor(Color.WHITE)
        btnHangup.backgroundTintList = ColorStateList.valueOf(COR_VERMELHA)
        btnHangup.setTextColor(Color.WHITE)
    }

    private fun botaoBranco(botao: Button) {
        botao.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        botao.setTextColor(Color.BLACK)
    }

    // Na ligação o desligar fica baixo e curto. O número usa o resto da tela.
    private fun encolherDesligar() {
        pintarNormal()
        tvX.visibility = View.GONE
        btnAnswer.visibility = View.GONE
        peso(tvX, 0f)
        peso(btnAnswer, 0f)
        peso(tvNumero, 72f)
        peso(btnHangup, 18f)
        btnHangup.setTextSize(32f)
    }

    private fun peso(vista: View, valor: Float) {
        val params = vista.layoutParams as LinearLayout.LayoutParams
        params.weight = valor
        vista.layoutParams = params
    }

    companion object {
        private const val ORELHA_MS = 5_000L
        private const val PEDIDO_CONTATOS = 2
        private val COR_VERMELHA = Color.parseColor("#F44336")
        private val COR_VERDE = Color.parseColor("#4CAF50")
    }
}
