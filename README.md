# VineVM

Ambiente de desenvolvimento do VineVM preparado para GitHub Codespaces.

## Abrir o ambiente

1. Abra este repositório no GitHub.
2. Toque em **Code → Codespaces → Create codespace on main**.
3. O Codespace será criado com Node.js 22.
4. A porta **5173** está configurada para encaminhamento automático do preview.

## Executar o app

O código-fonte do app ainda precisa ser adicionado à raiz deste repositório. Quando houver um projeto Vite, execute:

```bash
npm install
npm run dev -- --host 0.0.0.0
```

Para um HTML estático, abra o arquivo no VS Code e use a extensão Live Server.

> Não coloque tokens, senhas ou URLs privadas no repositório.
