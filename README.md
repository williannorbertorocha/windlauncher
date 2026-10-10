# Launcher Tab — home launcher para tablet Android

Launcher nativo (Kotlin + Jetpack Compose) com visual claro, vidro fosco (blur), widgets arredondados e animações suaves.
Feito para o Galaxy Tab S10 FE+ e qualquer tablet 16:9 / 16:10, na horizontal e na vertical. Requer Android 12 (API 31) ou superior.

## Como gerar o APK (GitHub Actions)

1. Crie uma conta em github.com (se não tiver) e clique em **New repository** (pode ser privado).
2. Envie **todo o conteúdo desta pasta** para o repositório (botão *Add file → Upload files*; mantenha a pasta `.github`).
3. Abra a aba **Actions**. O workflow "Build APK" roda sozinho a cada envio (ou clique em *Run workflow*).
4. Quando terminar (uns 5–8 min), abra a execução e baixe **launcher-tab-apk** em *Artifacts*. Dentro do zip está o `app-debug.apk`.
5. Passe o APK para o tablet, toque nele e permita "instalar apps desconhecidos" quando o Android pedir.
6. Aperte o botão Home → escolha **Launcher Tab** → **Sempre**. (Ou: Configurações → Apps → Apps padrão → App da tela inicial.)

Alternativa: abrir a pasta no Android Studio e usar *Build → Build APK(s)*.

## Como usar

- **Segurar o fundo por 2 segundos** → "Configurações do Launcher" (wallpaper, grade, dock, widgets, pesquisa, tema).
- **Segurar app ou widget** → arrastar para mover. Arrastar para a dock troca o app; arrastar widget para o topo remove.
- **Arrastar para as bordas** ou deslizar com o dedo → muda de página.
- **Barra de pesquisa** → abre o Google (web, imagens ou mapas) e também encontra apps instalados.

## Limitações conhecidas

- Clima e Música do launcher são visuais de exemplo (sem dados reais). Para dados reais, use *Adicionar widget* e escolha os widgets do próprio tablet.
- Os itens se organizam em sequência (sem espaços vazios livres entre eles).
- O projeto foi escrito sem poder compilar no ambiente de criação; se o Actions mostrar erro, copie o log e peça o ajuste.
