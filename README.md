# VineVM AI — Android

Primeira versão nativa Android do VineVM, feita com Kotlin e Jetpack Compose.

## O que já tem
- Interface de chat escura e adaptada para celular.
- Campo para URL HTTPS do seu Cloudflare Worker.
- URL salva localmente no aparelho.
- Envio de histórico de mensagens em JSON.
- Exibição de respostas e erros HTTP.

## Contrato esperado do Worker

O app envia um `POST` diretamente para a URL informada, com `Content-Type: application/json`:

```json
{
  "messages": [
    { "role": "user", "content": "Oi!" }
  ]
}
```

O Worker pode responder com JSON contendo `response`, `output`, `result.response` ou `choices[0].message.content`. A URL deve ser HTTPS e o endpoint precisa aceitar esse formato. Não coloque segredos no app ou no repositório.

## Compilar
Abra a pasta do projeto no Android Studio (JDK 17, Android SDK 35) e execute a configuração Gradle. O pacote é `com.vinevm.ai`.

Também há uma GitHub Action em **Actions → Android Debug APK** que compila o APK de debug e publica o arquivo como artefato para baixar.
