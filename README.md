# VineVM AI — Android

Cliente Android nativo para conversar com um Ollama exposto por uma URL HTTPS pública (por exemplo, Cloudflare Quick Tunnel).

## Recursos
- Chat com histórico durante a sessão.
- Campo para URL pública do Ollama; a URL fica salva no aparelho.
- Campo para escolher o nome do modelo instalado no Ollama.
- Controle para ativar/desativar Thinking.
- Caixa separada para exibir o conteúdo de `message.thinking`, quando o modelo e a versão do Ollama disponibilizarem esse campo.

## Conectar ao Cloudflare Tunnel

O endereço público precisa encaminhar para a API do Ollama, normalmente na porta `11434`. Exemplo no computador onde o Ollama está rodando:

```bash
cloudflared tunnel --url http://localhost:11434
```

Cole no app somente a URL HTTPS gerada, por exemplo:

```
https://seu-subdominio.trycloudflare.com/
```

O VineVM acrescenta automaticamente `/api/chat`. Se você colar uma URL que já termine em `/api/chat`, ela será usada como está. Isso evita enviar o POST para a raiz do domínio — que pode responder HTTP 405.

No app, informe também o nome exato do modelo que está instalado no Ollama (por exemplo, `llama3.2`). O app envia `POST /api/chat` com `model`, `messages`, `stream: false` e `think` conforme o botão. A resposta esperada é o JSON padrão do Ollama, com `message.content` e, quando disponível, `message.thinking`.

**Importante:** o túnel deve apontar para o serviço Ollama na porta 11434, e não para uma página web, painel ou outro serviço. Mantenha o túnel protegido se não quiser expor seu Ollama publicamente; qualquer pessoa com acesso ao endereço pode tentar usar a API.

## Compilar
Abra a pasta do projeto no Android Studio (JDK 17, Android SDK 35) e execute a configuração Gradle. O pacote é `com.vinevm.ai`.

Também há uma GitHub Action em **Actions → Android Debug APK** que compila o APK de debug e publica o arquivo como artefato para baixar.
