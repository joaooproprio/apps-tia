package com.example.ameliafone
// Última alteração: 29/09/2026 00:09

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvDisplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        afastarDaBarra()
        pedirContatos()

        tvDisplay = findViewById(R.id.tvDisplay)

        val teclas = listOf(
            R.id.btn1 to "1",
            R.id.btn2 to "2",
            R.id.btn3 to "3",
            R.id.btn4 to "4",
            R.id.btn5 to "5",
            R.id.btn6 to "6",
            R.id.btn7 to "7",
            R.id.btn8 to "8",
            R.id.btn9 to "9",
            R.id.btn0 to "0",
            R.id.btnasterisco to "*",
            R.id.btnjogodavelha to "#"
        )
        teclas.forEach { (id, tecla) ->
            findViewById<Button>(id).setOnClickListener { digitar(tecla) }
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            val textoAtual = tvDisplay.text.toString()
            if (textoAtual.isNotEmpty()) {
                tvDisplay.text = textoAtual.dropLast(1)
                encaixarNumero()
            }
        }

        // Botão LIGAR
        val btnCall = findViewById<Button>(R.id.btnCall)
        btnCall.setOnClickListener {
            val numero = tvDisplay.text.toString()
            if (numero.isNotEmpty()) {

                // Verifica se o Android é versão 10 (API 29) ou mais recente
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager = getSystemService(ROLE_SERVICE) as android.app.role.RoleManager
                    if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER)) {
                        if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                            val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
                            @Suppress("DEPRECATION") // Silencia o aviso amarelo do Android Studio
                            startActivityForResult(intent, 1)
                        } else {
                            fazerLigacaoReal(numero)
                        }
                    }
                } else {
                    // Para celulares mais antigos (Android 9 ou inferior)
                    val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
                    if (packageName != telecomManager.defaultDialerPackage) {
                        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, 1)
                    } else {
                        fazerLigacaoReal(numero)
                    }
                }
            } else {
                Toast.makeText(this, "Digite um número primeiro", Toast.LENGTH_SHORT).show()
            }
        }

        // Botão CONTATOS
        val btnContacts = findViewById<Button>(R.id.btnContacts)

        btnContacts.setOnClickListener {
            val intentContatos = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
            startActivity(intentContatos)
        }

        // As últimas ligações devolvem o número para o visor. Quem liga continua sendo o botão verde.
        findViewById<Button>(R.id.btnHistorico).setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(this, HistoricoActivity::class.java), PEDIDO_HISTORICO)
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PEDIDO_HISTORICO || resultCode != RESULT_OK) return
        val numero = data?.getStringExtra(HistoricoActivity.EXTRA_NUMERO)?.trim().orEmpty()
        if (numero.isEmpty()) return
        tvDisplay.text = numero
        encaixarNumero()
    }

    private fun digitar(tecla: String) {
        tvDisplay.append(tecla)
        encaixarNumero()
    }

    // Número longo cortava no visor. A letra desce até a linha caber inteira.
    private fun encaixarNumero() {
        val texto = tvDisplay.text.toString()
        if (texto.isEmpty()) {
            tvDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, TAMANHO_NUMERO)
            return
        }
        val aplicar = Runnable {
            val largura = tvDisplay.width - tvDisplay.totalPaddingLeft - tvDisplay.totalPaddingRight - 8
            if (largura <= 0) return@Runnable
            val medida = TextPaint(tvDisplay.paint)
            var tamanho = TAMANHO_NUMERO
            medida.textSize = spParaPx(tamanho)
            while (tamanho > TAMANHO_MINIMO && medida.measureText(texto) > largura) {
                tamanho -= 1f
                medida.textSize = spParaPx(tamanho)
            }
            tvDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanho)
        }
        if (tvDisplay.width == 0) tvDisplay.post(aplicar) else aplicar.run()
    }

    private fun spParaPx(valor: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, valor, resources.displayMetrics)
    }

    // LIGAR e CONTATOS não podem ficar em cima da barra do sistema.
    private fun afastarDaBarra() {
        val raiz = findViewById<View>(R.id.raizDiscador)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val barra = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            vista.setPadding(vista.paddingLeft, vista.paddingTop, vista.paddingRight, barra)
            insets
        }
        ViewCompat.requestApplyInsets(raiz)
    }

    // Sem a agenda a chamada não sabe quem é. No Android 13 a notificação de perdida pede licença junto.
    private fun pedirContatos() {
        val faltam = permissoesFaltando()
        if (faltam.isEmpty()) {
            pedirAdminDaTela()
            return
        }
        ActivityCompat.requestPermissions(this, faltam, PEDIDO_CONTATOS)
    }

    private fun permissoesFaltando(): Array<String> {
        val lista = ArrayList<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            lista.add(Manifest.permission.READ_CONTACTS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            lista.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return lista.toTypedArray()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PEDIDO_CONTATOS) return
        pedirAdminDaTela()
    }

    // A tela da chamada só apaga de verdade se este app for administrador. O Android pede isso uma vez.
    private fun pedirAdminDaTela() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, AdminAparelho::class.java)
        if (dpm.isAdminActive(admin)) return
        val pedido = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explicacao))
        }
        startActivity(pedido)
    }

    companion object {
        private const val PEDIDO_CONTATOS = 2
        private const val PEDIDO_HISTORICO = 3
        private const val TAMANHO_NUMERO = 40f
        private const val TAMANHO_MINIMO = 8f
    }

    // Criei essa função separada para organizar melhor o código e não repeti-lo
    private fun fazerLigacaoReal(numero: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$numero")
        }
        startActivity(intent)
    }
}