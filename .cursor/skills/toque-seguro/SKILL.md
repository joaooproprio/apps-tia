---
name: toque-seguro
description: Implementa confirmação de toque duplo com vibração, mudança de cor, fala do nome em 0,8 vezes e trava de 10 segundos ao abrir o telefone. Use ao editar clique de botão, ACTION_CALL, tel:, answer, disconnect, ou quando o pedido falar de toque acidental, toque duplo, vibração, cor ou trava.
---

# Toque seguro

Uma função por Activity (ou um helper pequeno no mesmo módulo). Não use um listener de clique simples para ligar, atender, desligar ou abrir app.

Tempos fixos: segundo toque em até 2 segundos; trava de 10 segundos só depois de abrir o telefone.

## Comportamento

1. Primeiro toque arma aquele botão, vibra ~200 ms e escurece a cor dele (JOÃO e JANETE voltam à cor original se o toque expirar).
2. Depois de uma pausa curta (~400 ms), fala só o nome com `TextToSpeech` em `setSpeechRate(0.8f)`: "João" ou "Janete". Crie o engine uma vez no `onCreate` e chame `shutdown` no `onDestroy`.
3. Segundo toque no mesmo botão, dentro de 2 s, vibra de novo e roda a ação uma vez.
4. Outro botão, ou 2 s sem toque, cancela o primeiro e restaura a cor. Nada é executado.
5. Ação que abre o telefone grava `SystemClock.elapsedRealtime() + 10_000`. Enquanto isso, toques de ligar retornam na hora. Junte `FLAG_ACTIVITY_CLEAR_TOP` ou `FLAG_ACTIVITY_SINGLE_TOP` no `Intent`.
6. ATENDER e DESLIGAR têm estado separado. Atender não inicia a trava de 10 s, para a pessoa conseguir desligar em seguida. Os dois ainda exigem toque duplo. Não falam nome.

## Vibração

`minSdk` 27/28. `VibrationEffect` existe. `VibratorManager` só no API 31.

Declare `android.permission.VIBRATE` no manifest. Não peça em runtime.

```kotlin
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
```

Cancele o `Runnable` dos 2 s em `onDestroy` para um toque antigo não disparar com a Activity já fechada.

## Onde ligar

- JOÃO e JANETE na tela inicial: toque duplo, fala em 0,8, depois trava de 10 s, depois um único `ACTION_CALL` para o número que aquele botão já usa.
- Abrir um app na lista: toque duplo, sem fala e sem trava de 10 s.
- `btnAnswer` / `btnHangup` em `InCallActivity`: toque duplo, sem trava de 10 s.
