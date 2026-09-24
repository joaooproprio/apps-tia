package com.example.ameliafone

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Visor
        val tvDisplay = findViewById<TextView>(R.id.tvDisplay)

        // Botão 1
        val btn1 = findViewById<Button>(R.id.btn1)
        btn1.setOnClickListener { tvDisplay.append("1") }

        // Botão 2
        val btn2 = findViewById<Button>(R.id.btn2)
        btn2.setOnClickListener { tvDisplay.append("2") }

        // Botão 3
        val btn3 = findViewById<Button>(R.id.btn3)
        btn3.setOnClickListener { tvDisplay.append("3") }

        // Botão 4
        val btn4 = findViewById<Button>(R.id.btn4)
        btn4.setOnClickListener { tvDisplay.append("4") }

        // Botão 5
        val btn5 = findViewById<Button>(R.id.btn5)
        btn5.setOnClickListener { tvDisplay.append("5") }

        // Botão 6
        val btn6 = findViewById<Button>(R.id.btn6)
        btn6.setOnClickListener { tvDisplay.append("6") }

        // Botão 7
        val btn7 = findViewById<Button>(R.id.btn7)
        btn7.setOnClickListener { tvDisplay.append("7") }

        // Botão 8
        val btn8 = findViewById<Button>(R.id.btn8)
        btn8.setOnClickListener { tvDisplay.append("8") }

        // Botão 9
        val btn9 = findViewById<Button>(R.id.btn9)
        btn9.setOnClickListener { tvDisplay.append("9") }

        // Botão 0
        val btn0 = findViewById<Button>(R.id.btn0)
        btn0.setOnClickListener { tvDisplay.append("0") }

        // Botão *
        val btnasterisco = findViewById<Button>(R.id.btnasterisco)
        btnasterisco.setOnClickListener { tvDisplay.append("*") }

        // Botão #
        val btnjogodavelha = findViewById<Button>(R.id.btnjogodavelha)
        btnjogodavelha.setOnClickListener { tvDisplay.append("#") }

        // Botão Limpar (X) - Remove apenas o último dígito
        val btnClear = findViewById<Button>(R.id.btnClear)
        btnClear.setOnClickListener {
            val textoAtual = tvDisplay.text.toString()
            if (textoAtual.isNotEmpty()) {
                tvDisplay.text = textoAtual.dropLast(1)
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
            // Visualizar a lista de contatos do Android
            val intentContatos = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)

            // Manda o celular executar a ordem!
            startActivity(intentContatos)
        }
    }

    // Criei essa função separada para organizar melhor o código e não repeti-lo
    private fun fazerLigacaoReal(numero: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$numero")
        }
        startActivity(intent)
    }
}