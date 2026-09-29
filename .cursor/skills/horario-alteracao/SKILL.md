---
name: horario-alteracao
description: Grava abaixo do package o horário da última alteração. Use ao criar ou editar arquivos .kt, .kts, .java ou .xml.
---

# Horário da última alteração

Ao criar ou editar um arquivo `.kt`, `.kts`, `.java` ou `.xml`, a linha seguinte ao `package` leva a data e a hora dessa edição.

Use o relógio do momento da gravação, fuso `America/Sao_Paulo`, formato `dd/MM/yyyy HH:mm`. Não invente o horário.

## Onde escrever

- Kotlin, Kotlin Script e Java: a linha seguinte ao `package`.

```kotlin
package com.example.tela_inicial
// Última alteração: 27/09/2026 00:49
```

- XML: a linha seguinte à declaração `<?xml ...?>`. Um comentário antes dessa declaração quebra o arquivo.

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Última alteração: 27/09/2026 00:49 -->
```

Se essa marca já existir, troque só o horário. Não empilhe outra linha.

Não coloque a marca em arquivo que você não editou.
