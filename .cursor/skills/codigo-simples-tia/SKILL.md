---
name: codigo-simples-tia
description: Mantém a tela inicial e o Ameliafone simples, comentados em português e leves para celular lento de idosa com baixa visão. Use ao criar ou editar Kotlin, layout XML, permissões, botões João e Janete, lista de aplicativos, ligações, bloqueio de tela ou a tela de atender e desligar.
---

# Código simples da Tia

Antes de mudar navegação, permissão ou qual tela abre, leia [referencia-apps.md](referencia-apps.md).

## Ao editar

1. A tela inicial não é o `ui_tia`. O código dela não está no repositório; não converta o WebView nesse telefone.
2. `Ameliafone` / `InCallActivity` é só atender e desligar.
3. Altere a Activity e o XML que já existem. Activity nova só se a lista de aplicativos ainda não tiver uma.
4. Comente em português o motivo. Uma função por comportamento, nome claro.
5. Rode o checklist abaixo antes de encerrar.

## Arranque da tela inicial

No `onCreate`, nesta ordem:

1. Tela cheia.
2. Brilho da janela no máximo, sem permissão extra: `window.attributes = window.attributes.apply { screenBrightness = 1f }`.
3. Volume máximo em `STREAM_RING`, `STREAM_VOICE_CALL` e `STREAM_MUSIC`. Exige `MODIFY_AUDIO_SETTINGS`.
4. Peça em runtime só o que essa tela usa, antes da ação. `CALL_PHONE` antes de ligar. `VIBRATE` vai no manifest, sem diálogo.

## Voltar ao bloquear

`launchMode="singleTask"`. Receiver dinâmico de `ACTION_USER_PRESENT`: abre a tela inicial com `FLAG_ACTIVITY_NEW_TASK or CLEAR_TOP or SINGLE_TOP`. Se ela já estava na frente, o `onNewIntent` retorna sem recriar. Sem Service.

## Checklist

- [ ] JOÃO 30%, JANETE 30%, espaço, APLICATIVOS 15%; laranja, azul e cinza; texto branco em negrito
- [ ] Ligar, atender, desligar ou abrir app passa pelo toque duplo da skill `toque-seguro`
- [ ] Primeiro toque vibra, escurece a cor e fala o nome em 0,8 vezes
- [ ] Abrir o telefone não empilha Activity (trava de 10 s + `SINGLE_TOP` ou `CLEAR_TOP`)
- [ ] Bloquear a tela volta para a tela inicial; se já estava nela, só retoma
- [ ] Lista de aplicativos inalterada: uma linha, "VOLTAR PARA A TELA INICIAL"
- [ ] Chamada sem teclado, contatos ou opção extra
- [ ] Sem WebView, animação ou dependência Gradle
- [ ] Erro visível em `TextView` grande, com comentário do porquê
