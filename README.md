# NexUs-Notify

API de notificações do NexUs. Consulta candidatos no Core, aplica as regras de produtos e eventos e salva os avisos no MongoDB. O mobile consulta o histórico e registra a leitura pela API.

## Execução local

Requisitos: Java 17 ou superior, MongoDB e NexUs-Core disponível para a geração dos avisos. O Maven Wrapper está incluído no repositório.

Para a primeira configuração, copie o modelo na raiz do projeto. Se você já possui `.env.properties`, ajuste os valores nesse arquivo:

```powershell
Copy-Item .env.example .env.properties
```

Preencha `MONGO_URI` e `MONGO_DB` com a base que contém a collection `notifications`, gerenciada pelo NexUs-DB. Configure `NOTIFY_CORE_TOKEN` com o mesmo token da integração no Core e `JWT_SECRET_BASE64` com a mesma chave usada pelo Auth e pelo Core. A chave JWT deve representar pelo menos 32 bytes após a decodificação Base64.

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
| `MONGO_DB` | Obrigatória | Nome da base com a collection `notifications`. |
| `NOTIFY_PORT` | `8082` | Porta HTTP. |
| `CORE_BASE_URL` | `http://localhost:8080` | Endereço do Core. |
| `NOTIFY_CORE_TOKEN` | Vazio | Token enviado no header `X-Notify-Token` nas consultas ao Core. |
| `CORE_CONNECT_TIMEOUT` | `2s` | Limite para estabelecer a conexão com o Core. |
| `CORE_READ_TIMEOUT` | `5s` | Limite de espera da resposta do Core. |
| `JWT_ISSUER` | `nexus-auth` | Emissor esperado no JWT. Deve corresponder ao Auth. |
| `JWT_SECRET_BASE64` | Obrigatória | Chave de validação dos tokens do Auth. |
| `CORS_ALLOWED_ORIGINS` | Vazio | Origens de navegador permitidas, separadas por vírgula. |
| `NOTIFY_SCHEDULER_ENABLED` | `true` | Habilita a geração automática. |
| `NOTIFY_SCHEDULER_CRON` | `0 0 11 * * *` | Horário de execução, com seis campos. |
| `NOTIFY_PUSH_ENABLED` | `false` | Inicializa o Firebase Admin quando `true`. |
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

Esta etapa prepara o cliente Firebase. O cadastro de instalações, os endpoints correspondentes e o processamento dos envios serão implementados depois. O mobile precisará registrar sua instalação no FCM, enviar o FID com o JWT do perfil, atualizar esse cadastro e desvinculá-lo no logout. Nenhum contrato novo para o mobile é exposto nesta etapa.

## Regras e agendamento

| Tipo | Dias de antecedência |
| --- | --- |
| Produto próximo do vencimento | 3, 1 e no dia do vencimento. |
| Evento próximo | 7, 3, 1 e no dia do evento. |

O job executa diariamente às 11h no fuso `America/Sao_Paulo`. Percorre as páginas do Core e gera os avisos previstos para a data da execução. O token da integração deve estar configurado para essas consultas.

O índice único de perfil, tipo, origem e chave do alerta protege a persistência contra duplicações. Cada antecedência tem sua própria chave. O agendamento precisa do serviço em execução; gerações perdidas durante uma indisponibilidade ainda não são recuperadas automaticamente.

O cliente Firebase pode ser configurado conforme a seção acima. O envio dos avisos às instalações ainda será implementado em uma task própria. Atualmente os avisos ficam disponíveis para consulta no aplicativo.

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

## Health e métricas

O Actuator expõe apenas `health` e `metrics`.

| Rota | Acesso | Uso |
| --- | --- | --- |
| `GET /actuator/health` | Público | Status do serviço, incluindo a verificação do MongoDB. Detalhes e componentes ficam ocultos. |
| `GET /actuator/metrics` | JWT com `profile_type=ADMIN` | Lista das métricas disponíveis. |
| `GET /actuator/metrics/{name}` | JWT com `profile_type=ADMIN` | Valores e filtros de uma métrica. |

O health retorna `200` com status `UP` quando as verificações passam e `503` com status `DOWN` quando uma dependência verificada falha. Ele verifica o MongoDB, mas não realiza uma consulta ao Core. O acesso às métricas sem token retorna `401`; um perfil sem a permissão `ADMIN` recebe `403`.

| Métrica | Etiqueta | Informação |
| --- | --- | --- |
| `notify.generation.duration` | `result=success` ou `failure` | Quantidade e duração das execuções, incluindo execuções sem candidatos e execuções com falha. |
| `notify.notifications.created` | `type=PRODUCT_EXPIRING` ou `UPCOMING_EVENT` | Avisos efetivamente inseridos. Inclui inserções anteriores a uma falha na mesma execução. |
| `notify.notifications.duplicates` | `type=PRODUCT_EXPIRING` ou `UPCOMING_EVENT` | Avisos que já existiam e foram ignorados. |

No timer, `COUNT` representa as execuções concluídas com aquele resultado, `TOTAL_TIME` é o tempo acumulado em segundos e `MAX` é a maior duração na janela de medição. Candidatos fora das antecedências configuradas não entram no contador de duplicados.

```sh
curl http://localhost:8082/actuator/health
curl -H 'Authorization: Bearer <JWT_ADMIN_DO_AUTH>' http://localhost:8082/actuator/metrics
curl -H 'Authorization: Bearer <JWT_ADMIN_DO_AUTH>' 'http://localhost:8082/actuator/metrics/notify.generation.duration?tag=result:failure'
```

Os contadores de avisos aparecem após o primeiro registro correspondente. As métricas ficam na memória da instância e reiniciam com a aplicação. As etiquetas de negócio usam apenas tipo e resultado; nenhum ID de perfil, produto ou evento é usado como etiqueta. A etiqueta `application` identifica o serviço.

Os logs registram início, conclusão, quantidade criada, duração em milissegundos e falhas da geração. A stack trace auxilia o diagnóstico. Tokens e conteúdo dos avisos não são registrados deliberadamente nesses logs. O acompanhamento de envios de push será adicionado com essa integração.

## Validação

```powershell
.\mvnw.cmd verify checkstyle:check
```

Em Linux ou macOS:

```sh
./mvnw verify checkstyle:check
```

Os testes usam Core e Mongo simulados e desabilitam o job. Para validar o ambiente real, configure as conexões, consulte o health e acompanhe os logs da execução diária junto das métricas.
