# Documentação e testes do Notify

## Contrato da API

A documentação descreve consulta de notificações, cadastro e revogação de instalações e rotas administrativas. Os parâmetros de paginação possuem seus limites documentados; installationId descreve o Firebase Installation ID e seu formato.

O histórico e as instalações usam BearerAuth. As rotas administrativas usam X-Admin-Key. O contrato registra os erros de entrada, autenticação, limite de requisições e falhas internas sem atribuir a todos o mesmo formato de corpo.

Os testes acessam /v3/api-docs, verificam descrições e respostas e resolvem referências aos modelos.

## Tempo controlado no teste de limite

O teste HTTP usa o limitador real com um relógio monotônico controlado apenas na configuração de teste. Confere 60 segundos de espera, redução para 59 após 1,1 segundo e liberação da cota ao completar 60 segundos. Nenhum tempo de espera real ou alteração do limitador de produção é necessário.

## Firebase

Os testes de configuração geram uma chave RSA fictícia e inicializam o SDK real, verificando projeto, credenciais, variáveis de configuração e encerramento do FirebaseApp.

Essa verificação não envia mensagens e não comprova a entrega em um aparelho. O envio real depende das credenciais do projeto e de uma instalação válida do aplicativo.

### Execução pelo aplicativo desktop no Windows

Foi reproduzido o erro Unable to establish loopback connection na criação do seletor de rede do Java, tanto no JDK 17 quanto no JDK 26. A inicialização falhava antes de se comunicar com o Firebase.

O teste de abertura do seletor funcionou ao definir jdk.net.unixdomain.tmpdir para um diretório fora de AppData, como a pasta .cache/nexus-test-sockets dentro do perfil do usuário. A mesma opção deve chegar à JVM dos testes; pode ser definida em JAVA_TOOL_OPTIONS apenas no terminal da execução.

Esse ajuste é local ao ambiente Windows do aplicativo desktop. Não é necessário modificar o SDK, simular FirebaseMessaging ou desabilitar os testes. A seleção do diretório está descrita no [código do OpenJDK](https://github.com/openjdk/jdk17u/blob/master/src/java.base/windows/classes/sun/nio/ch/UnixDomainSocketsUtil.java).

Exemplo no PowerShell, com JAVA_HOME já apontando para um JDK 17:

```powershell
$socketDirectory = Join-Path $env:USERPROFILE '.cache/nexus-test-sockets'
New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir="' + $socketDirectory + '"'
.\mvnw.cmd verify checkstyle:check '-Dmongodb.test.uri=mongodb://localhost:27017'
```

## Persistência real

MongoPersistenceTest cria um banco com identificador aleatório e remove esse banco no encerramento. Verifica:

- Persistência e deduplicação de notificações.
- Paginação e isolamento do histórico por perfil.
- Reassociação de uma instalação sem duplicação.
- Proteção contra invalidação por uma versão anterior do vínculo.
- Revogação restrita ao proprietário atual.
- Deduplicação de sugestões e avanço monotônico do cursor de descoberta.

Os índices únicos necessários são criados no banco de teste. Nenhum banco da organização é usado.

Execute com JDK 17 e MongoDB de teste:

```text
./mvnw verify checkstyle:check -Dmongodb.test.uri=mongodb://localhost:27017
```

O CI fornece MongoDB 8.2 e passa a propriedade de conexão. Sem ela, os testes que precisam de MongoDB são ignorados; os demais continuam executando.

## Validação de envio real

Para verificar a entrega fora dos testes automatizados, configure FIREBASE_PROJECT_ID e FIREBASE_CREDENTIALS_BASE64 com uma conta de serviço do mesmo projeto e habilite NOTIFY_PUSH_ENABLED. Cadastre um Firebase Installation ID válido do aplicativo usando um JWT do perfil de teste.

Com o Core acessível e os agendamentos habilitados, crie um cenário elegível para uma regra de notificação e confirme o histórico, o resultado do envio e o recebimento no aparelho. O token de integração entre Core e Notify deve coincidir nos dois serviços.

A política de aceitação do JWT até sua expiração permanece como decisão de escopo.

## Resultado em 02/11/2026

verify e checkstyle:check concluídos com JDK 17.0.20.1 e MongoDB 8.2 local: 290 testes aprovados, zero falhas, zero erros e zero ignorados. O pacote JAR foi gerado.

Os 12 testes de configuração do Firebase passaram com o SDK real e credenciais fictícias geradas pelos testes. As três falhas anteriores de inicialização foram resolvidas no ambiente de execução pelo diretório de sockets fora de AppData. O código de produção do Firebase permaneceu igual.

O teste de Retry-After passou com tempo controlado, e os quatro testes de MongoPersistenceTest foram executados em um MongoDB real. O teste de recebimento em aparelho exige o cenário descrito acima.
