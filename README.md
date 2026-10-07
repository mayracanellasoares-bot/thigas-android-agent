# Thigas Agent v0.8 — Professor Action Engine

Esta versão adiciona um aplicativo Android nativo que abre a Sala do Futuro em WebView e executa um fluxo controlado de Frequência + Registro de aulas.

## Segurança

- login é manual;
- JavaScript de automação só é injetado em `*.educacao.sp.gov.br`;
- nada é injetado nas páginas `gov.br`;
- cada lançamento exige confirmação explícita no Android;
- se aluno, horário, botão ou confirmação não forem encontrados com segurança, o agente para;
- não usa coordenadas fixas da tela;
- o modo `Mapear` salva um mapa local do DOM para depuração.

## Arquitetura

- `core/`: núcleo Termux/Python, agenda, memória e chat;
- `android/`: projeto Android Studio Kotlin;
- `android/app/src/main/assets/portal_automation.js`: executor DOM;
- `docs/PORTAL_MAPPING.md`: fluxo observado na gravação.

## Rodar o núcleo no Termux

```bash
cd core
bash install-termux.sh
bash start-termux.sh
```

O núcleo fica em `http://127.0.0.1:8765`.

## APK

Abra a pasta `android/` no Android Studio e gere o APK (`Build > Build APK`).
A aplicação usa `compileSdk/targetSdk 36`.

## Primeiro teste recomendado

1. Inicie o núcleo no Termux.
2. Abra o APK.
3. Toque `Sala` e faça o login manualmente.
4. Vá ao Diário de Classe.
5. Toque `Mapear` nas telas de Frequência e Registro de aulas.
6. Use uma turma de teste e toque `Preparar ação`.
7. Confira o resumo e confirme.
8. Se o agente não identificar um elemento de forma segura, ele interrompe o fluxo.

**Não use a primeira execução para um lançamento que você não possa conferir imediatamente no portal.**
