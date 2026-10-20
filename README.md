# NexUs-Notify

API de notificações do NexUs. Consulta candidatos no Core, aplica as regras de produtos, eventos e lojas próximas e salva os avisos no MongoDB. Com o push habilitado, tenta enviar os novos avisos pelo Firebase para as instalações ativas do perfil. O mobile consulta o histórico e registra a leitura pela API.

## Execução local

Requisitos: Java 17 ou superior, MongoDB e NexUs-Core disponível para a geração dos avisos. O fluxo de lojas exige MongoDB com suporte a transações (replica set ou cluster), mesmo com o push desabilitado. O Maven Wrapper está incluído no repositório.

Para a primeira configuração, copie o modelo na raiz do projeto. Se você já possui `.env.properties`, ajuste os valores nesse arquivo:

```powershell
Copy-Item .env.example .env.properties
```

Preencha `MONGO_URI` e `MONGO_DB` com a base das collections `notifications` e `push_installations`. Prepare as collections e os índices previamente pelo `mongosh`, conforme [Cadastro de instalações push](docs/push-installations.md). A criação automática de índices no Notify está desabilitada. Configure `NOTIFY_CORE_TOKEN` com o mesmo token da integração no Core e `JWT_SECRET_BASE64` com a mesma chave usada pelo Auth e pelo Core. A chave JWT deve representar pelo menos 32 bytes após a decodificação Base64.

Para lojas, prepare também `nearby_store_rotations`, `new_store_suggestions` e `new_store_discovery_state`, conforme [Lojas próximas](docs/nearby-stores.md). Para executar somente produtos e eventos, configure `NOTIFY_NEARBY_STORES_ENABLED=false`.

Os arquivos `.env` e `.env.properties` são carregados da raiz do projeto e estão no `.gitignore`. Ambos usam o formato `CHAVE=VALOR`, sem aspas envolvendo os valores. Se os dois existirem, `.env.properties` tem prioridade. Variáveis de ambiente do processo têm prioridade sobre esses arquivos.

```powershell
.\mvnw.cmd spring-boot:run
```

Em Linux ou macOS:

```sh
./mvnw spring-boot:run
```

## Configuração

| Variável | Padrão | Uso |
| --- | --- | --- |
| `MONGO_URI` | Obrigatória | Conexão com o MongoDB. |
| `MONGO_DB` | Obrigatória | Nome da base com as collections `notifications` e `push_installations`. |
| `NOTIFY_PORT` | `8082` | Porta HTTP. |
| `CORE_BASE_URL` | `http://localhost:8080` | Endereço do Core. |
| `NOTIFY_CORE_TOKEN` | Vazio | Token enviado no header `X-Notify-Token` nas consultas ao Core. |
| `CORE_CONNECT_TIMEOUT` | `2s` | Limite para estabelecer a conexão com o Core. |
| `CORE_READ_TIMEOUT` | `5s` | Limite de espera da resposta do Core. |
| `JWT_ISSUER` | `nexus-auth` | Emissor esperado no JWT. Deve corresponder ao Auth. |
| `JWT_SECRET_BASE64` | Obrigatória | Chave de validação dos tokens do Auth. |
| `TRACING_ACESS` | Vazio | Chave fixa de monitoramento, enviada no parâmetro `key` das rotas de métricas. Sem configuração, essas rotas retornam `503`. |
| `CORS_ALLOWED_ORIGINS` | Vazio | Origens de navegador permitidas, separadas por vírgula. |
| `NOTIFY_SCHEDULER_ENABLED` | `true` | Habilita a geração automática. |
| `NOTIFY_SCHEDULER_CRON` | `0 0 11 * * *` | Horário de execução, com seis campos. |
| `NOTIFY_NEARBY_STORES_ENABLED` | `true` | Habilita descoberta de lojas novas e sugestões por endereço cadastrado. |
| `NOTIFY_NEARBY_STORES_RADIUS_KM` | `5` | Raio em km, maior que zero e até 100, enviado ao Core. |
| `NOTIFY_NEARBY_STORES_INTERVAL_DAYS` | `3` | Intervalo mínimo entre sugestões de loja; aceita valores a partir de 3 dias. |
| `NOTIFY_PUSH_ENABLED` | `false` | Inicializa o Firebase Admin e habilita o envio dos novos avisos quando `true`. |
| `FIREBASE_PROJECT_ID` | Vazio | ID do projeto Firebase; obrigatório quando o push estiver habilitado. |
| `FIREBASE_CREDENTIALS_BASE64` | Vazio | JSON da conta de serviço codificado em Base64; obrigatório quando o push estiver habilitado. |

O tamanho do lote de consulta ao Core é 100. O fuso e as antecedências estão em `application.yml`, em `notify.notification`.

## Configuração do Firebase para push

A API usa o Firebase Admin SDK Java. Com `NOTIFY_PUSH_ENABLED=false`, não inicializa o Firebase e não exige suas credenciais. Com `true`, registra uma instância `nexus-notify` e um cliente `FirebaseMessaging`, liberando a instância ao encerrar a aplicação.

Use o JSON privado da conta de serviço com o papel **Firebase Cloud Messaging API Admin**, no mesmo projeto configurado em `FIREBASE_PROJECT_ID`. A inicialização rejeita configurações incompletas, Base64 inválido, credenciais inválidas e projeto divergente, sem incluir o conteúdo da credencial nos erros de leitura. O `google-services.json` do Android é uma configuração do mobile e não serve como credencial do servidor.

### Preparar a credencial

Mantenha o JSON fora do repositório e do JAR. No PowerShell, substitua o caminho abaixo pelo caminho real do arquivo baixado:

```powershell
$taskFirebaseKeyPath = 'C:\caminho\credencial-notify.json'
[Convert]::ToBase64String([IO.File]::ReadAllBytes($taskFirebaseKeyPath)) | Set-Clipboard
```

O valor fica na área de transferência. Base64 apenas permite armazenar o JSON em uma linha; o resultado continua sendo um segredo. Acrescente estas linhas ao `.env.properties` local, substituindo os exemplos pelos valores do seu projeto:

```properties
NOTIFY_PUSH_ENABLED=true
FIREBASE_PROJECT_ID=seu-projeto-firebase
FIREBASE_CREDENTIALS_BASE64=COLE_O_VALOR_COPIADO
```

### Publicar no Discloud

Ao implantar pela integração com GitHub, cadastre as três variáveis na seção **Variáveis de Ambiente** do Discloud. A plataforma gera o `.env` na raiz da aplicação, que o Notify carrega como arquivo de propriedades. Para upload manual, forneça o `.env` na raiz do pacote de implantação privado, ao lado do JAR e do `discloud.config`, sem incluí-lo no Git ou no JAR. Preserve também as configurações de MongoDB, Auth e Core já exigidas pela API.

Referências: [Firebase Admin SDK](https://firebase.google.com/docs/admin/setup) e [variáveis na integração GitHub do Discloud](https://docs.discloud.com/en/api-and-integrations/github-integration).

### Limites desta etapa e integração futura com o mobile

Inicializar o SDK verifica localmente a credencial e o projeto; não faz uma chamada remota para confirmar se a chave foi revogada, se o papel IAM permite enviar ou se a API FCM V1 está habilitada. O health atual também não valida o Firebase. Essas verificações exigem um teste real quando o envio estiver implementado.

Após salvar um aviso novo, a API busca as instalações ativas do perfil, confere o vínculo atual e tenta enviar uma mensagem de dados pelo FID. Falhas de envio não removem o aviso do histórico nem interrompem os outros destinatários. Não há fila persistida nem recuperação de envios após falhas ou reinícios. O fluxo, os erros tratados e o contrato Android estão em [Envio de push](docs/push-delivery.md).

## Regras e agendamento

| Tipo | Dias de antecedência |
| --- | --- |
| Produto próximo do vencimento | 3, 1 e no dia do vencimento. |
| Evento próximo | 7, 3, 1 e no dia do evento. |

O job executa diariamente às 11h no fuso `America/Sao_Paulo`. Percorre as páginas do Core e gera os avisos previstos para a data da execução. O token da integração deve estar configurado para essas consultas.

Produtos, eventos e lojas têm serviços de geração independentes. O job tenta os três fluxos mesmo se algum falhar. Uma falha parcial mantém o resultado geral como falha, preservando os avisos já criados e identificando cada fluxo nos logs e nas métricas.

Lojas usam os endpoints internos `/internal/notifications/nearby-stores` e `/internal/notifications/new-stores` do Core, com `X-Notify-Token`. O raio padrão é 5 km do endereço cadastrado. O Notify permite uma sugestão de loja por perfil a cada 72 horas, prioriza novidades pendentes e, depois, escolhe a loja ainda não apresentada mais próxima. Ao esgotar as opções atuais, reinicia o ciclo e evita repetir a última loja quando houver alternativa. Consulte [Lojas próximas](docs/nearby-stores.md) para os ciclos, a persistência e a recuperação da descoberta.

O índice único de perfil, tipo, origem e chave do alerta protege a persistência contra duplicações. Cada antecedência de produto/evento tem sua própria chave. O agendamento precisa do serviço em execução; execuções perdidas de produtos/eventos ainda não são recuperadas automaticamente. A descoberta de lojas novas mantém uma data de progresso persistida e recupera o intervalo desde a última consulta concluída.

O envio é síncrono durante a geração, somente para avisos recém-inseridos e quando `NOTIFY_PUSH_ENABLED=true`. Repetir a geração não reenvia avisos já existentes, mesmo que uma tentativa anterior tenha falhado. Sem instalações ativas, o aviso permanece apenas no histórico. O Android ainda precisa implementar o recebimento e a exibição das mensagens de dados.

## Swagger / OpenAPI

Com a aplicação em execução, acesse `http://localhost:8082/swagger-ui.html` (ou a porta definida em `NOTIFY_PORT`). A especificação fica em `/v3/api-docs` (JSON) e `/v3/api-docs.yaml` (YAML).

A documentação é pública, seguindo o padrão do Core e do Auth. Para executar as rotas do aplicativo pelo Swagger, clique em **Authorize** e informe somente o JWT emitido pelo Auth, sem o prefixo `Bearer`. O Swagger adiciona o header `Authorization: Bearer <token>` às requisições; as rotas continuam exigindo autenticação.

O Notify usa Springdoc 3.1.1, compatível com Spring Boot 4, conforme a [documentação oficial](https://springdoc.org/).

## Endpoints do aplicativo

As rotas de notificações exigem `Authorization: Bearer <JWT_DO_AUTH>`. O perfil é obtido do subject do JWT.

| Método e rota | Resultado |
| --- | --- |
| `GET /api/notifications?page=0&size=20` | Lista do perfil autenticado, ordenada por criação decrescente e ID decrescente em caso de empate. |
| `PATCH /api/notifications/{id}/read` | Marca um aviso do perfil como lido, com resposta `204`. Repetições preservam a primeira leitura. |

`page` começa em zero e `size` aceita valores de 1 a 100. A resposta da lista contém `content` e `hasNext`, sem contagem total.

```json
{
  "content": [
    {
      "id": "670c00000000000000000001",
      "type": "PRODUCT_EXPIRING",
      "sourceId": "45",
      "title": "Produto próximo do vencimento",
      "message": "O produto Leite vence amanhã.",
      "createdAt": "2026-10-14T14:00:00Z",
      "readAt": null,
      "read": false
    }
  ],
  "hasNext": false
}
```

```sh
curl -H 'Authorization: Bearer <JWT_DO_AUTH>' 'http://localhost:8082/api/notifications?page=0&size=20'
curl -X PATCH -H 'Authorization: Bearer <JWT_DO_AUTH>' http://localhost:8082/api/notifications/670c00000000000000000001/read
```

Parâmetros ou IDs inválidos retornam `400`. Um aviso ausente ou pertencente a outro perfil retorna `404`. Falhas de autenticação retornam `401`.

## Cadastro de instalações push

As duas rotas exigem o JWT do Auth. O perfil é sempre obtido do token; o cadastro funciona mesmo com `NOTIFY_PUSH_ENABLED=false`, pois nesta etapa utiliza apenas o MongoDB.

| Método e rota | Resultado |
| --- | --- |
| `PUT /api/push/installations` | Cria ou atualiza o cadastro pelo `installationId`, com resposta `200` contendo `id` e `status`. |
| `DELETE /api/push/installations/{id}` | Desvincula o cadastro do perfil autenticado, com resposta `204`. Usa o ID interno retornado pelo PUT. |

Corpo do PUT:

```json
{"installationId": "FID_REGISTRADO_NO_FCM"}
```

O FID é único na collection `push_installations`. Chamadas repetidas preservam o ID e a data original de criação. Uma instalação pode mudar de perfil, e um perfil pode ter várias instalações. No logout, o cadastro recebe `profile_id=null`, `status=REVOKED` e `disabled_reason=LOGOUT`. O DELETE repetido, sem vínculo ou de outro perfil retorna `204` sem alterar o documento. Um ID interno inválido ou um FID inválido retorna `400`; ausência ou falha de autenticação retorna `401`.

Consulte [Cadastro de instalações push](docs/push-installations.md) para o esquema da collection, as regras de atualização, exemplos de requisição e um roteiro de teste manual. O envio pelo Firebase está descrito em [Envio de push](docs/push-delivery.md); a integração Android continua pendente.

## Health e métricas

O Actuator expõe apenas `health` e `metrics`.

| Rota | Acesso | Uso |
| --- | --- | --- |
| `GET /actuator/health` | Público | Status do serviço, incluindo a verificação do MongoDB. Detalhes e componentes ficam ocultos. |
| `GET /actuator/metrics` | Chave `TRACING_ACESS` no parâmetro `key` | Lista das métricas disponíveis. |
| `GET /actuator/metrics/{name}` | Chave `TRACING_ACESS` no parâmetro `key` | Valores e filtros de uma métrica. |

O health retorna `200` com status `UP` quando as verificações passam e `503` com status `DOWN` quando uma dependência verificada falha. Ele verifica o MongoDB, mas não realiza uma consulta ao Core. As métricas usam uma chave fixa, seguindo o mecanismo de monitoramento do Auth. Configure `TRACING_ACESS` no `.env.properties`, `.env` ou nas variáveis do Discloud e envie seu valor no parâmetro `key`. O nome da variável preserva a grafia usada pelo Auth.

A segurança das métricas usa uma cadeia própria, sem autenticação JWT ou papel `ADMIN`. Chave ausente ou incorreta retorna `401`; chave não configurada no servidor retorna `503`. A comparação usa `MessageDigest.isEqual`, e o filtro não registra a chave nos logs nem a devolve nas mensagens de erro. Um JWT não substitui a chave de monitoramento, e essa chave não autentica as rotas do aplicativo, que continuam exigindo JWT do Auth. Apenas `health` e `metrics` estão expostos pelo Actuator.

| Métrica | Etiqueta | Informação |
| --- | --- | --- |
| `notify.generation.duration` | `result=success` ou `failure` | Quantidade e duração das execuções, incluindo execuções sem candidatos e execuções com falha. |
| `notify.generation.flow.duration` | `flow=products`, `events` ou `stores`; `result=success` ou `failure` | Resultado e duração de cada fluxo, inclusive quando outro falha. |
| `notify.notifications.created` | `type=PRODUCT_EXPIRING`, `UPCOMING_EVENT`, `NEARBY_STORE` ou `NEW_NEARBY_STORE` | Avisos efetivamente inseridos. Inclui inserções anteriores a uma falha na mesma execução. |
| `notify.notifications.duplicates` | `type=PRODUCT_EXPIRING` ou `UPCOMING_EVENT` | Avisos que já existiam e foram ignorados. Intervalos sem sugestão de loja não contam como duplicação. |

No timer, `COUNT` representa as execuções concluídas com aquele resultado, `TOTAL_TIME` é o tempo acumulado em segundos e `MAX` é a maior duração na janela de medição. Candidatos fora das antecedências configuradas não entram no contador de duplicados.

```sh
curl http://localhost:8082/actuator/health
curl --get --data-urlencode 'key=<TRACING_ACESS>' http://localhost:8082/actuator/metrics
curl --get --data-urlencode 'key=<TRACING_ACESS>' --data-urlencode 'tag=result:failure' http://localhost:8082/actuator/metrics/notify.generation.duration
```

Os contadores de avisos aparecem após o primeiro registro correspondente. As métricas ficam na memória da instância e reiniciam com a aplicação. As etiquetas de negócio usam apenas tipo, fluxo e resultado; nenhum ID de perfil, produto, evento ou loja é usado como etiqueta. A etiqueta `application` identifica o serviço.

Os logs registram início, conclusão, quantidade criada, duração em milissegundos e falhas da geração. A stack trace auxilia o diagnóstico. Tokens e conteúdo dos avisos não são registrados deliberadamente nesses logs. Os resultados de envio de push também são registrados conforme [Envio de push](docs/push-delivery.md).

## Validação

```powershell
.\mvnw.cmd verify checkstyle:check
```

Em Linux ou macOS:

```sh
./mvnw verify checkstyle:check
```

Os testes usam Core, Mongo e Firebase simulados e desabilitam o job. Incluem seleção e ciclos de lojas, frequência compartilhada, recuperação da descoberta, configuração transacional, commit/abort de sessão Mongo simulada, contratos HTTP e isolamento dos fluxos. Para validar transações no banco real, prepare um replica set ou cluster e siga o roteiro de [Lojas próximas](docs/nearby-stores.md).
