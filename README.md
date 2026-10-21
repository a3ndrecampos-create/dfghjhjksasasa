# Painel AC (app Android)

App que abre `https://acgamespay.shardweb.app/admin` com uma senha do próprio app.

## Gerar o APK pelo GitHub
1. Crie um repositório no GitHub e envie estes arquivos (incluindo a pasta `.github`).
2. Abra a aba **Actions**, escolha **Build APK** e, se ele não rodar sozinho, clique em **Run workflow**.
3. Quando terminar (uns 3–5 min), abra a execução e baixe **PainelAC-apk** em *Artifacts*.
4. Descompacte o zip, passe o `app-debug.apk` para o celular e instale (permita "instalar apps desconhecidos").

## Senha
- Na primeira abertura o app pede para você criar a senha; depois ela é pedida ao abrir e quando ficar mais de 1 minuto fora do app.
- 5 erros seguidos travam por 30 segundos.
- Esqueceu a senha: Configurações do Android → Apps → Painel AC → Armazenamento → Limpar dados (isso também apaga o login do painel no app).

Para mudar o endereço do painel, edite `URL` e `HOST` no topo de `MainActivity.kt`.

## Segurança e avisos (v1.2)
- **Tela apagou** → ao voltar, o app pede a senha do app **e** a chave do painel de novo.
- **Avisos:** depois do login no painel, um serviço consulta o servidor a cada 10s e notifica nova solicitação de suporte, nova mensagem de cliente e novo pagamento pendente (este só com a chave principal). Para isso a chave fica guardada criptografada no celular (Android Keystore).
- Aceite a permissão de notificações e "sem restrições de bateria" quando o app pedir.
- Clicar em **Sair** no painel desliga os avisos e apaga a chave guardada.
