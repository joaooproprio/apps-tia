package com.example.ameliafone
// Última alteração: 28/09/2026 00:38

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

// 041984820199 e o contato salvo como 98482-0199 são o mesmo celular.
object Agenda {

    const val DESCONHECIDO = "Número Desconhecido"

    sealed class Resultado {
        class Achou(val nome: String?) : Resultado()
        object NaoEsta : Resultado()
        object Falhou : Resultado()
    }

    fun podeLer(contexto: Context): Boolean {
        return ContextCompat.checkSelfPermission(contexto, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun buscar(contexto: Context, numero: String?): Resultado {
        if (numero.isNullOrBlank() || numero == DESCONHECIDO) return Resultado.NaoEsta
        if (!podeLer(contexto)) return Resultado.Falhou
        return try {
            peloLookup(contexto, numero) ?: pelaVarredura(contexto, numero) ?: Resultado.NaoEsta
        } catch (e: Exception) {
            Resultado.Falhou
        }
    }

    fun mesmos(a: String, b: String): Boolean {
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        if (da.length < 8 || db.length < 8) return false
        return da.takeLast(8) == db.takeLast(8)
    }

    private fun peloLookup(contexto: Context, numero: String): Resultado.Achou? {
        for (chave in chaves(numero)) {
            val nome = linhaLookup(contexto, chave) ?: continue
            return Resultado.Achou(nome.ifBlank { null })
        }
        return null
    }

    private fun linhaLookup(contexto: Context, chave: String): String? {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon().appendPath(chave).build()
        val cursor = contexto.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        ) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val coluna = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
            return if (coluna >= 0) it.getString(coluna).orEmpty() else ""
        }
    }

    private fun pelaVarredura(contexto: Context, numero: String): Resultado.Achou? {
        val cursor = contexto.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null,
            null,
            null
        ) ?: return null
        cursor.use {
            val colNumero = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val colNormal = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
            val colNome = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            if (colNumero < 0) return null
            while (it.moveToNext()) {
                val salvo = it.getString(colNumero).orEmpty()
                val normal = if (colNormal >= 0) it.getString(colNormal).orEmpty() else ""
                if (mesmos(numero, salvo) || (normal.isNotEmpty() && mesmos(numero, normal))) {
                    val nome = if (colNome >= 0) it.getString(colNome) else null
                    return Resultado.Achou(nome?.ifBlank { null })
                }
            }
        }
        return null
    }

    private fun chaves(numero: String): List<String> {
        val digitos = numero.filter { it.isDigit() }
        val semZero = if (digitos.startsWith("0")) digitos.drop(1) else digitos
        val semPais = if (semZero.startsWith("55") && semZero.length > 11) semZero.drop(2) else semZero
        val lista = ArrayList<String>()
        if (numero.isNotBlank()) lista.add(numero)
        if (digitos.length >= 8) lista.add(digitos)
        if (semZero.length >= 8) lista.add(semZero)
        if (semPais.length >= 9) lista.add(semPais.takeLast(9))
        if (digitos.length >= 8) lista.add(digitos.takeLast(8))
        return lista.distinct()
    }
}
