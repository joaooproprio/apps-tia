package com.example.ameliafone
// Última alteração: 29/09/2026 00:09

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.CallLog
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoricoActivity : AppCompatActivity() {

    private val formato = SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR"))
    private lateinit var lista: LinearLayout
    private lateinit var rolagem: View
    private lateinit var aviso: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historico)
        lista = findViewById(R.id.listaHistorico)
        rolagem = findViewById(R.id.rolagemHistorico)
        aviso = findViewById(R.id.tvAvisoHistorico)
        afastarDaBarra()
        findViewById<Button>(R.id.btnVoltarHistorico).setOnClickListener { finish() }
        if (podeLer()) carregar() else pedirPermissao()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PEDIDO_HISTORICO) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            carregar()
        } else {
            mostrarAviso(getString(R.string.erro_historico))
        }
    }

    private fun afastarDaBarra() {
        val raiz = findViewById<View>(R.id.raizHistorico)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val barra = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            vista.setPadding(vista.paddingLeft, vista.paddingTop, vista.paddingRight, barra)
            insets
        }
        ViewCompat.requestApplyInsets(raiz)
    }

    private fun podeLer(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun pedirPermissao() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CALL_LOG), PEDIDO_HISTORICO)
    }

    // A leitura fica fora da tela. Vinte linhas cabem no celular lento.
    private fun carregar() {
        val contexto = applicationContext
        Thread {
            val itens = lerHistorico(contexto)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                mostrar(itens)
            }
        }.start()
    }

    private fun lerHistorico(contexto: Context): List<Item>? {
        return try {
            val uri = CallLog.Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter("limit", LIMITE.toString())
                .build()
            val cursor = contexto.contentResolver.query(
                uri,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            ) ?: return null
            cursor.use { lerCursor(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun lerCursor(cursor: Cursor): List<Item> {
        val colNumero = cursor.getColumnIndex(CallLog.Calls.NUMBER)
        val colNome = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
        val colTipo = cursor.getColumnIndex(CallLog.Calls.TYPE)
        val colData = cursor.getColumnIndex(CallLog.Calls.DATE)
        if (colNumero < 0 || colTipo < 0) return emptyList()
        val itens = ArrayList<Item>()
        while (cursor.moveToNext() && itens.size < LIMITE) {
            val tipo = cursor.getInt(colTipo)
            if (!tipoVisivel(tipo)) continue
            val numero = cursor.getString(colNumero).orEmpty()
            val nome = if (colNome >= 0) cursor.getString(colNome) else null
            val quando = if (colData >= 0) cursor.getLong(colData) else 0L
            itens.add(Item(numero, nome, tipo, quando))
        }
        return itens
    }

    private fun mostrar(itens: List<Item>?) {
        lista.removeAllViews()
        if (itens == null) {
            mostrarAviso(getString(R.string.erro_historico))
            return
        }
        if (itens.isEmpty()) {
            mostrarAviso(getString(R.string.historico_vazio))
            return
        }
        aviso.visibility = View.GONE
        rolagem.visibility = View.VISIBLE
        itens.forEach { lista.addView(linha(it)) }
    }

    private fun mostrarAviso(texto: String) {
        rolagem.visibility = View.GONE
        aviso.visibility = View.VISIBLE
        aviso.text = texto
    }

    // Um toque só devolve o número ao discador. Quem liga é o botão verde.
    private fun linha(item: Item): Button {
        val botao = layoutInflater.inflate(R.layout.item_historico, lista, false) as Button
        botao.transformationMethod = null
        botao.isAllCaps = false
        botao.text = textoDaLinha(item)
        val perdida = item.tipo == CallLog.Calls.MISSED_TYPE || item.tipo == CallLog.Calls.REJECTED_TYPE
        val cor = if (perdida) Color.parseColor("#FFCDD2") else Color.WHITE
        botao.backgroundTintList = ColorStateList.valueOf(cor)
        botao.setOnClickListener {
            if (!item.numero.any { digito -> digito.isDigit() }) return@setOnClickListener
            setResult(RESULT_OK, Intent().putExtra(EXTRA_NUMERO, item.numero))
            finish()
        }
        return botao
    }

    private fun textoDaLinha(item: Item): CharSequence {
        val titulo = item.nome?.takeIf { it.isNotBlank() }
            ?: item.numero.ifBlank { Agenda.DESCONHECIDO }
        val tipo = rotulo(item.tipo).orEmpty()
        val quando = if (item.quando > 0L) formato.format(Date(item.quando)) else ""
        val detalhe = listOf(tipo, quando).filter { it.isNotBlank() }.joinToString("  ")
        val meio = if (!item.nome.isNullOrBlank() && item.numero.isNotBlank()) item.numero else ""
        val corpo = listOf(titulo, meio, detalhe).filter { it.isNotBlank() }.joinToString("\n")
        val texto = SpannableString(corpo)
        val fimTitulo = titulo.length.coerceAtMost(corpo.length)
        texto.setSpan(StyleSpan(Typeface.BOLD), 0, fimTitulo, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (corpo.length > fimTitulo) {
            texto.setSpan(
                RelativeSizeSpan(0.7f),
                fimTitulo + 1,
                corpo.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return texto
    }

    private fun tipoVisivel(tipo: Int): Boolean {
        return tipo == CallLog.Calls.OUTGOING_TYPE ||
            tipo == CallLog.Calls.INCOMING_TYPE ||
            tipo == CallLog.Calls.MISSED_TYPE ||
            tipo == CallLog.Calls.REJECTED_TYPE
    }

    // Recusada entra como perdida: ela precisa ver quem tentou falar.
    private fun rotulo(tipo: Int): String? {
        return when (tipo) {
            CallLog.Calls.OUTGOING_TYPE -> getString(R.string.ligacao_feita)
            CallLog.Calls.INCOMING_TYPE -> getString(R.string.ligacao_recebida)
            CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> getString(R.string.ligacao_perdida)
            else -> null
        }
    }

    private class Item(
        val numero: String,
        val nome: String?,
        val tipo: Int,
        val quando: Long
    )

    companion object {
        const val EXTRA_NUMERO = "NUMERO"
        private const val PEDIDO_HISTORICO = 4
        private const val LIMITE = 20
    }
}
