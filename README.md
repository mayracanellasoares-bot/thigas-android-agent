# Thigas Android Agent v0.8

Aplicativo Android nativo para o modo **Professor Action Engine** do Thigas.

## O que está neste repositório

- app Android em Kotlin;
- WebView controlado para abrir a Sala do Futuro;
- mapeador local do DOM;
- executor sem coordenadas fixas;
- confirmação explícita antes de qualquer gravação;
- verificação das mensagens de salvamento;
- GitHub Actions para gerar o APK automaticamente.

O núcleo conversacional/agenda continua rodando localmente no **Termux**, em `127.0.0.1:8765`, usando o pacote Thigas Agent v0.8. O APK consulta esse núcleo para obter turma, disciplina, horários e conteúdo do dia.

## Segurança

- o login é feito manualmente pelo usuário;
- a automação só é injetada em `*.educacao.sp.gov.br`;
- páginas `gov.br` não recebem o script de automação;
- CPF, senha, código 2FA e cookies não são enviados à LLM;
- o botão Salvar só pode ser acionado após confirmação explícita;
- se aluno, horário, turma ou botão não forem identificados com segurança, o agente para e pede intervenção;
- não usa coordenadas fixas da tela.

## Fluxo Professor

```text
Agenda local
→ Sala do Futuro
→ Diário de Classe
→ Frequência
→ Lançamento
→ turma/disciplina/horários
→ C/F dos alunos
→ Salvar
→ confirmar “Alterações salvas”
→ Registro de aulas
→ horários
→ conteúdo
→ Salvar
→ confirmar “Registro salvo”
```

## Primeiro teste

1. Inicie o núcleo v0.8 no Termux.
2. Instale o APK gerado pelo workflow **Build Android APK**.
3. Abra o app e faça o login manualmente.
4. Navegue até Diário de Classe.
5. Use **Mapear** primeiro.
6. Confira turma, disciplina, horários e faltosos.
7. Só depois use **Preparar ação** e confirme.

A automação real ainda precisa ser validada contra o DOM da sua sessão autenticada antes de ser considerada pronta para uso diário.
