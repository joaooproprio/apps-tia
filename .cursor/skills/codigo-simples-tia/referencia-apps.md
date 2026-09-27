# O que já existe

Dois projetos Gradle neste repositório. Nenhum dos dois é a tela inicial das fotos.

## Tela inicial — código ausente

A janela do telefone (JOÃO laranja, JANETE azul, MARCELA laranja, APLICATIVOS cinza) não está no git. A lista de aplicativos também não: fundo preto, ícone e nome grande por linha, barra "VOLTAR PARA A TELA INICIAL". Apps vistos na foto: pomome, nRF Connect, invest+, iFood, g1, e-Título, assistente_tia.

Destino, quando o código aparecer ou for escrito de novo:

- Só JOÃO e JANETE, cada um com o número que esse botão já liga. MARCELA sai.
- Alturas: 30%, 30%, 15% aplicativos, o resto espaço.
- Ao abrir: brilho no máximo, volume de toque, chamada e mídia no máximo.
- Primeiro toque trava o segundo, vibra, escurece a cor e fala o nome em 0,8 vezes. O segundo toque liga, com trava de 10 s.
- Bloquear a tela traz essa janela de volta. Se já era ela, só retoma.
- A lista de aplicativos mantém a lógica atual.

## ui_tia — aplicativo errado

`ui_tia/ui_tia/app/src/main/java/com/example/ui_tia/MainActivity.kt` e `activity_main.xml`.

WebView (80%) com a URL placeholder `SUA_URL_DO_GOOGLE_AI_STUDIO_AQUI`, mais "SAIR PARA HOME", que abre o launcher do Android. Não é a tela inicial. Não converta esse WebView nos botões JOÃO e JANETE.

No arranque: tela cheia, só `STREAM_MUSIC` no máximo, pede `RECORD_AUDIO` e `CALL_PHONE`. Não mexe no brilho. O manifest declara `INTERNET`, `MODIFY_AUDIO_SETTINGS`, `CALL_PHONE` e `READ_CONTACTS`. Sem `VIBRATE`.

## Ameliafone — janela de telefone durante a chamada

- `MainActivity.kt` + `activity_main.xml`: teclado 0–9, `*`, `#`, limpar, LIGAR e CONTATOS. Discador antigo. Não estender.
- `InCallActivity.kt` + `activity_in_call.xml`: status, número, ATENDER (verde, 50sp) e DESLIGAR (vermelho, 40sp). Toque ainda é clique único. `answer(VideoProfile.STATE_AUDIO_ONLY)` e `disconnect()`. Variável `llamada` no desligar.
- `CallService.kt`: `InCallService` guarda `chamadaAtiva`, acende a tela com `FULL_WAKE_LOCK` obsoleto por 3 s e abre `InCallActivity`.

Manifest: `CALL_PHONE`, `READ_PHONE_STATE`, `MANAGE_OWN_CALLS`, `WAKE_LOCK`, `DISABLE_KEYGUARD`. Sem `VIBRATE` e sem `MODIFY_AUDIO_SETTINGS`. Papel de discador padrão é pedido em `MainActivity` no Android 10+ (`ROLE_DIALER`).

`InCallActivity` já usa `setShowWhenLocked`, `setTurnScreenOn` e `FLAG_KEEP_SCREEN_ON`.
