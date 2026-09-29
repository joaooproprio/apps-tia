package com.example.tela_inicial
// Última alteração: 29/09/2026 00:53


import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class AppsActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var linhaTravada: View? = null

    private val soltarToque = Runnable {
        if (isDestroyed) return@Runnable
        linhaTravada?.setBackgroundColor(getColor(R.color.fundo))
        linhaTravada?.findViewById<TextView>(R.id.nomeApp)?.setTextColor(getColor(R.color.apps))
        linhaTravada = null
        findViewById<Button>(R.id.btnVoltar)?.let { pintarVoltar(it, R.color.apps, R.color.apps_texto) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        prepararJanela()
        reservarBotoesDoSistema()

        val btnVoltar = findViewById<Button>(R.id.btnVoltar)
        val lista = findViewById<ListView>(R.id.listaApps)
        val apps = aplicativos()
        lista.adapter = AppsAdapter(apps)

        btnVoltar.isLongClickable = false
        btnVoltar.setOnTouchListener { vista, evento ->
            if (TravaToque.travada()) return@setOnTouchListener true
            if (evento.actionMasked == MotionEvent.ACTION_UP &&
                evento.x >= 0 && evento.y >= 0 &&
                evento.x <= vista.width && evento.y <= vista.height
            ) {
                pintarVoltar(btnVoltar, R.color.armado, R.color.armado_texto)
                vibrar()
                TravaToque.travar()
                handler.removeCallbacks(soltarToque)
                handler.postDelayed(soltarToque, TravaToque.ESPERA_MS)
                finish()
            }
            true
        }

        val abrirLinha = fun(linha: View, posicao: Int) {
            if (TravaToque.travada()) return
            linhaTravada = linha
            linha.setBackgroundColor(getColor(R.color.armado))
            linha.findViewById<TextView>(R.id.nomeApp)?.setTextColor(getColor(R.color.armado_texto))
            vibrar()
            TravaToque.travar()
            handler.removeCallbacks(soltarToque)
            handler.postDelayed(soltarToque, TravaToque.ESPERA_MS)
            val pacote = apps[posicao].activityInfo.packageName
            val abrir = packageManager.getLaunchIntentForPackage(pacote) ?: return
            startActivity(abrir)
        }
        lista.setOnItemClickListener { _, linha, posicao, _ -> abrirLinha(linha, posicao) }
        lista.setOnItemLongClickListener { _, linha, posicao, _ ->
            abrirLinha(linha, posicao)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        prepararJanela()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (TravaToque.travada()) return true
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        handler.removeCallbacks(soltarToque)
        super.onDestroy()
    }

    private fun aplicativos(): List<ResolveInfo> {
        val consulta = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(consulta, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun pintarVoltar(botao: Button, fundo: Int, texto: Int) {
        botao.setBackgroundColor(getColor(fundo))
        botao.setTextColor(getColor(texto))
        botao.compoundDrawableTintList = ColorStateList.valueOf(getColor(texto))
    }

    // A lista para antes dos botões de voltar, início e recentes. Sem isso as linhas ficam em cima deles.
    private fun reservarBotoesDoSistema() {
        val raiz = findViewById<View>(R.id.raizApps)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(0, barras.top, 0, barras.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(raiz)
    }

    private fun prepararJanela() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        listOf(AudioManager.STREAM_RING, AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_MUSIC).forEach { fluxo ->
            audio.setStreamVolume(fluxo, audio.getStreamMaxVolume(fluxo), 0)
        }
    }

    private fun vibrar() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private inner class AppsAdapter(private val apps: List<ResolveInfo>) : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val linha = convertView ?: LayoutInflater.from(this@AppsActivity)
                .inflate(R.layout.item_app, parent, false)
            val info = apps[position]
            linha.findViewById<ImageView>(R.id.iconeApp).setImageDrawable(info.loadIcon(packageManager))
            linha.findViewById<TextView>(R.id.nomeApp).text = info.loadLabel(packageManager)
            return linha
        }
    }
}
