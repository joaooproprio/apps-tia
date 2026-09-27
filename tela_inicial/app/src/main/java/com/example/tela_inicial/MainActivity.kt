// Última alteração: 27/09/2026 01:16
package com.example.tela_inicial

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var botaoTravado: Button? = null
    private var fala: TextToSpeech? = null
    private var falaPronta = false
    private var numeroPendente: String? = null
    private var esperandoPermissao = false
    private var jaEspacou = false
    private lateinit var tvErro: TextView
    private lateinit var tvPreparacao: TextView
    private var avisoAberto = false
    private var acaoAdiada: Runnable? = null
    private var travaAteTrocarJanela = false

    private val soltarToque = Runnable {
        travaAteTrocarJanela = false
        if (isDestroyed) return@Runnable
        botaoTravado?.let { restaurarCor(it) }
        botaoTravado = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prepararJanela()
        igualarEspacos()
        pedirParaSerInicio()

        tvErro = findViewById(R.id.tvErro)
        tvPreparacao = findViewById(R.id.tvPreparacao)
        tvPreparacao.setOnClickListener { checarPermissoes() }
        checarPermissoes()
        val btnJoao = findViewById<Button>(R.id.btnJoao)
        val btnJanete = findViewById<Button>(R.id.btnJanete)
        val btnWhatsapp = findViewById<Button>(R.id.btnWhatsapp)
        val btnApps = findViewById<Button>(R.id.btnApps)

        aoSoltar(btnJoao, getString(R.string.ligando_joao)) { falarELigar("João", NUMERO_JOAO) }
        aoSoltar(btnJanete, getString(R.string.ligando_janete)) { falarELigar("Janete", NUMERO_JANETE) }
        aoSoltar(btnWhatsapp, getString(R.string.abrindo_whatsapp)) { abrirWhatsapp() }
        aoSoltar(btnApps, null) { abrirLista() }
        listOf(btnJoao, btnJanete, btnWhatsapp, btnApps).forEach { restaurarCor(it) }

        fala = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                fala?.language = Locale("pt", "BR")
                fala?.setSpeechRate(0.75f)
                falaPronta = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Cada volta para esta janela sobe brilho e volume de novo.
        prepararJanela()
        if (podeLigar()) ficarPronto()
    }

    override fun onPause() {
        super.onPause()
        // A lista já abriu: solta a trava para a nova janela receber toque.
        if (!travaAteTrocarJanela) return
        travaAteTrocarJanela = false
        TravaToque.liberar()
        handler.removeCallbacks(soltarToque)
        if (!isDestroyed) {
            botaoTravado?.let { restaurarCor(it) }
            botaoTravado = null
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (TravaToque.travada()) return true
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Já estava neste app: o singleTask traz a janela 01 sem criar outra.
    }

    override fun onDestroy() {
        handler.removeCallbacks(soltarToque)
        acaoAdiada?.let { handler.removeCallbacks(it) }
        fala?.stop()
        fala?.shutdown()
        vibrador().cancel()
        super.onDestroy()
    }

    private fun aoSoltar(botao: Button, aviso: String?, acao: () -> Unit) {
        botao.isLongClickable = false
        botao.setOnTouchListener { vista, evento ->
            if (TravaToque.travada()) return@setOnTouchListener true
            if (evento.actionMasked == MotionEvent.ACTION_UP) {
                val dentro = evento.x >= 0 && evento.y >= 0 &&
                    evento.x <= vista.width && evento.y <= vista.height
                if (dentro) encaminhar(botao, aviso, acao)
            }
            true
        }
    }

    private fun encaminhar(botao: Button, aviso: String?, acao: () -> Unit) {
        if (TravaToque.travada()) return
        botaoTravado?.let { restaurarCor(it) }
        botaoTravado = botao
        pintar(botao, getColor(R.color.armado))
        botao.setTextColor(getColor(R.color.armado_texto))
        handler.removeCallbacks(soltarToque)
        acaoAdiada?.let { handler.removeCallbacks(it) }
        val ligacao = botao.id == R.id.btnJoao || botao.id == R.id.btnJanete
        if (ligacao) vibrarPor(AVISO_MS) else vibrar()
        if (aviso == null) {
            acao()
            travarSeAindaNestaJanela()
            return
        }
        mostrarAviso(aviso)
        // Enquanto o texto está na tela, ignora toque. A contagem de 3 s só começa depois.
        TravaToque.travarPor(AVISO_MS)
        val tarefa = Runnable {
            if (isDestroyed) return@Runnable
            esconderAviso()
            TravaToque.liberar()
            acao()
            travarSeAindaNestaJanela()
        }
        acaoAdiada = tarefa
        handler.postDelayed(tarefa, AVISO_MS)
    }

    private fun falarELigar(nome: String, numero: String) {
        if (!podeLigar()) {
            numeroPendente = numero
            mostrarErro(getString(R.string.erro_permissao))
            checarPermissoes()
            return
        }
        esconderErro()
        val motor = fala
        if (!falaPronta || motor == null) {
            ligar(numero)
            return
        }
        motor.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                runOnUiThread { ligar(numero) }
            }
            @Deprecated("API antiga")
            override fun onError(utteranceId: String?) {
                runOnUiThread { ligar(numero) }
            }
        })
        val falou = motor.speak(nome, TextToSpeech.QUEUE_FLUSH, null, "ligar")
        if (falou == TextToSpeech.ERROR) ligar(numero)
    }

    private fun ligar(numero: String) {
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$numero")).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    private fun abrirWhatsapp() {
        val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (intent == null) {
            mostrarErro(getString(R.string.erro_whatsapp))
            return
        }
        esconderErro()
        startActivity(intent)
    }

    private fun abrirLista() {
        startActivity(Intent(this, AppsActivity::class.java))
    }

    private fun travarSeAindaNestaJanela() {
        if (isFinishing || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            soltarToque.run()
            return
        }
        travaAteTrocarJanela = true
        TravaToque.travar()
        handler.postDelayed(soltarToque, TravaToque.ESPERA_MS)
    }

    private fun podeLigar(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PEDIDO_LIGACAO) return
        esperandoPermissao = false
        val numero = numeroPendente
        numeroPendente = null
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ficarPronto()
            if (numero != null) ligar(numero)
        } else {
            mostrarPreparacao()
        }
    }

    private fun checarPermissoes() {
        if (podeLigar()) {
            ficarPronto()
            return
        }
        mostrarPreparacao()
        if (esperandoPermissao) return
        esperandoPermissao = true
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), PEDIDO_LIGACAO)
    }

    private fun mostrarPreparacao() {
        tvPreparacao.visibility = View.VISIBLE
    }

    private fun ficarPronto() {
        esconderErro()
        if (!avisoAberto) tvPreparacao.visibility = View.GONE
    }

    private fun mostrarAviso(texto: String) {
        avisoAberto = true
        tvPreparacao.text = texto
        tvPreparacao.visibility = View.VISIBLE
    }

    private fun esconderAviso() {
        avisoAberto = false
        tvPreparacao.text = getString(R.string.em_preparacao)
        tvPreparacao.visibility = View.GONE
    }

    private fun mostrarErro(texto: String) {
        tvErro.text = texto
        tvErro.visibility = View.VISIBLE
    }

    private fun esconderErro() {
        tvErro.text = ""
        tvErro.visibility = View.GONE
    }

    private fun igualarEspacos() {
        val raiz = findViewById<LinearLayout>(R.id.raiz)
        val vaoTopo = findViewById<View>(R.id.vaoTopo)
        raiz.post {
            if (jaEspacou || vaoTopo.height == 0) return@post
            jaEspacou = true
            val margem = (raiz.width * 0.05f).toInt()
            listOf(R.id.btnJoao, R.id.btnJanete, R.id.linhaBaixo).forEach { id ->
                val vista = findViewById<View>(id)
                val params = vista.layoutParams as LinearLayout.LayoutParams
                params.marginStart = margem
                params.marginEnd = margem
                vista.layoutParams = params
            }
            val meio = findViewById<View>(R.id.vaoMeio)
            meio.layoutParams = meio.layoutParams.apply { width = vaoTopo.height }
            aumentarLogoWhatsapp()
        }
    }

    private fun aumentarLogoWhatsapp() {
        val botao = findViewById<Button>(R.id.btnWhatsapp)
        val texto = botao.paint.fontMetrics.let { it.descent - it.ascent }.toInt()
        val lado = ((minOf(botao.width, botao.height) - texto - botao.paddingTop - botao.paddingBottom) * 0.7f).toInt()
        if (lado <= 0) return
        val icone = ContextCompat.getDrawable(this, R.drawable.ic_whatsapp)?.mutate() ?: return
        icone.setBounds(0, 0, lado, lado)
        botao.setCompoundDrawables(null, icone, null, null)
    }

    private fun prepararJanela() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        listOf(AudioManager.STREAM_RING, AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_MUSIC).forEach { fluxo ->
            audio.setStreamVolume(fluxo, audio.getStreamMaxVolume(fluxo), 0)
        }
    }

    private fun pedirParaSerInicio() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val papeis = getSystemService(RoleManager::class.java) ?: return
        if (!papeis.isRoleAvailable(RoleManager.ROLE_HOME) || papeis.isRoleHeld(RoleManager.ROLE_HOME)) return
        startActivity(papeis.createRequestRoleIntent(RoleManager.ROLE_HOME))
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

    private fun vibrar() {
        vibrador().vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrarPor(ms: Long) {
        vibrador().vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun pintar(botao: Button, cor: Int) {
        val forma = GradientDrawable()
        forma.setColor(cor)
        forma.cornerRadius = 28f * resources.displayMetrics.density
        botao.background = forma
    }

    private fun restaurarCor(botao: Button) {
        val fundo = when (botao.id) {
            R.id.btnJoao -> R.color.joao
            R.id.btnJanete -> R.color.janete
            R.id.btnWhatsapp -> R.color.whatsapp
            else -> R.color.apps
        }
        val texto = when (botao.id) {
            R.id.btnJoao -> R.color.joao_texto
            R.id.btnJanete -> R.color.janete_texto
            R.id.btnWhatsapp -> R.color.whatsapp_texto
            else -> R.color.apps_texto
        }
        pintar(botao, getColor(fundo))
        botao.setTextColor(getColor(texto))
    }

    companion object {
        private const val NUMERO_JOAO = "41997542946"
        private const val NUMERO_JANETE = "41984063959"
        private const val PEDIDO_LIGACAO = 1
        private const val AVISO_MS = 3_000L
    }
}
