# Cadastro de instalações push

Este contrato associa uma instalação Android ao perfil autenticado. O Notify persiste o cadastro no MongoDB sem consultar o Firebase durante essas chamadas. As rotas funcionam com `NOTIFY_PUSH_ENABLED=false`, sem credenciais do Firebase.

## Collection `push_installations`

| Campo | Tipo no MongoDB | Uso |
| --- | --- | --- |
| `_id` | ObjectId | ID interno retornado ao mobile; preservado em atualizações e reativações. |
| `profile_id` | Inteiro ou null | Perfil do subject do JWT; null após a desvinculação. |
| `installation_id` | String | FID informado pelo aplicativo, único na collection. |
| `status` | String | `ACTIVE`, `REVOKED`, `INVALID` ou `STALE`. |
| `created_at` | Date | Primeira criação do cadastro; preservada nas atualizações. |
| `updated_at` | Date | Última alteração do cadastro. |
| `last_seen_at` | Date | Último cadastro ou sincronização autenticada recebida pela API. |
| `disabled_at` | Date ou null | Data de desativação; null no cadastro ativo. |
| `disabled_reason` | String ou null | Nesta etapa, `LOGOUT` após desvinculação e null após cadastro. |

O campo `platform` não é utilizado. Datas e estados são definidos pelo servidor. `last_seen_at` não comprova que o aplicativo continua instalado. O cadastro e o logout gravam `ACTIVE` e `REVOKED`. O envio grava `INVALID` quando o Firebase retorna `UNREGISTERED`, com motivo `FCM_UNREGISTERED`; nesse caso o perfil é preservado. `STALE` está reservado para uma política futura de inatividade. Ainda não existe limpeza automática de instalações antigas.

Prepare os índices diretamente pelo `mongosh`, na base configurada em `MONGO_DB`. O Notify não declara esses índices na entidade e está com `auto-index-creation=false`. O índice único é necessário para impedir cadastros duplicados durante requisições concorrentes.

| Índice exigido | Campos | Único |
| --- | --- | --- |
| `uq_push_installation_id` | `installation_id` crescente | Sim. |
| `idx_push_installation_profile_status` | `profile_id` e `status` crescentes | Não. |

Os comandos abaixo criam as collections ausentes e os índices, sem inserir documentos fictícios ou apagar dados. Mantêm também os nomes e definições dos índices de `notifications`, anteriormente declarados na API. Se já existirem com as mesmas definições, não são recriados. Duplicações existentes ou definições conflitantes causam erro e precisam ser verificadas antes de prosseguir.

```javascript
db = db.getSiblingDB("NOME_DA_BASE_CONFIGURADA_EM_MONGO_DB");

db.push_installations.createIndex(
  { installation_id: 1 },
  { name: "uq_push_installation_id", unique: true }
);
db.push_installations.createIndex(
  { profile_id: 1, status: 1 },
  { name: "idx_push_installation_profile_status" }
);
db.notifications.createIndex(
  { profile_id: 1, type: 1, source_id: 1, alert_key: 1 },
  { name: "uq_notification_alert", unique: true }
);
db.notifications.createIndex(
  { profile_id: 1, created_at: -1 },
  { name: "idx_notification_profile_created" }
);
```

Se houver validação de documentos na collection, o esquema deve permitir `profile_id=null`, os campos de desativação nulos e os estados `ACTIVE`, `REVOKED`, `INVALID` e `STALE`. Os endpoints não armazenam a credencial privada do Firebase.

## Cadastrar ou sincronizar

```http
PUT /api/push/installations
Authorization: Bearer <JWT_DO_AUTH>
Content-Type: application/json

{"installationId":"FID_REGISTRADO_NO_FCM"}
```

Resposta `200 OK`, tanto na criação quanto na atualização:

```json
{"id":"670c00000000000000000001","status":"ACTIVE"}
```

`installationId` é o único campo usado no corpo. O perfil é obtido exclusivamente do JWT, mesmo que o cliente envie outro `profileId`. O ID interno da resposta é diferente do FID e deve ser guardado para a desvinculação. O corpo não precisa de plataforma, estado ou datas.

A validação local aceita de 1 a 256 caracteres ASCII: letras, números, `_` e `-`, sem espaços. Ela apenas protege o contrato de entrada: não confirma a existência do FID no FCM, seu registro para receber mensagens ou a permissão de notificações do Android. Na integração real, o cliente deve enviar o FID fornecido pelo registro bem-sucedido no FCM.

O cadastro usa `findAndModify` com upsert pelo FID. A mesma operação associa o perfil, ativa o cadastro, atualiza `updated_at` e `last_seen_at` e limpa os dados de desativação. `created_at` é preenchido somente na inserção. Uma colisão de índice causada por inserções concorrentes é tratada com uma tentativa de atualização do mesmo FID sem nova inserção. Falhas que não possam ser recuperadas são propagadas.

Repetir o FID mantém o mesmo documento. Outro FID gera outro documento, mesmo para o mesmo perfil. Cadastrar um FID com o JWT de outro perfil transfere o vínculo para o perfil atual.

## Desvincular no logout

```http
DELETE /api/push/installations/670c00000000000000000001
Authorization: Bearer <JWT_DO_AUTH>
```

Resposta `204 No Content`, sem corpo. A atualização filtra simultaneamente o ID interno e o perfil autenticado. Somente um documento ainda associado a esse perfil recebe `profile_id=null`, `status=REVOKED`, `disabled_reason=LOGOUT`, `disabled_at` e `updated_at` com a data do servidor. O FID, `created_at` e `last_seen_at` são preservados.

Um ID válido sem cadastro, já desvinculado ou associado a outro perfil também retorna `204`, sem alterar o banco. Isso permite repetir a chamada e impede que um logout do perfil antigo desative o vínculo transferido a outro perfil. Repetições não alteram a data da primeira desvinculação. Um PUT posterior com o mesmo FID reativa o documento e preserva seu ID interno.

## Erros

| Situação | Resposta |
| --- | --- |
| Sem JWT, token inválido ou subject sem perfil válido | `401`. |
| FID ausente, vazio, com caracteres inválidos ou maior que 256 caracteres | `400`, `Firebase Installation ID invalido`. |
| ID interno inválido no DELETE | `400`, `Id do cadastro de instalacao invalido`. |
| Corpo ausente, JSON inválido ou estrutura incompatível | `400`, `Corpo da requisicao invalido`. |

Os erros de entrada seguem o formato existente `timestamp`, `status`, `error` e `message`. As mensagens não repetem o FID nem o corpo recebido.

## Roteiro de teste manual da API

Use um ambiente de teste com MongoDB e JWTs válidos do Auth. Pode manter `NOTIFY_PUSH_ENABLED=false` e desabilitar o job com `NOTIFY_SCHEDULER_ENABLED=false` para testar apenas o cadastro. Como não há envio nesta etapa, `fid-de-teste` pode ser usado como identificador sintaticamente válido durante os testes do contrato.

1. Faça o PUT com `{"installationId":"fid-de-teste"}` e o JWT do perfil A. Guarde o `id` retornado.
2. Consulte `db.push_installations.find({ installation_id: "fid-de-teste" })`. Confira perfil A, estado ativo, datas e dados de desativação nulos.
3. Repita o PUT. Deve continuar existindo apenas um documento, com o mesmo ID e `created_at`; `last_seen_at` e `updated_at` devem avançar.
4. Cadastre `fid-de-teste-2` com o perfil A. Deve existir um segundo documento, com outro ID.
5. Cadastre `fid-de-teste` com o JWT do perfil B. O primeiro documento deve manter ID e criação, e passar para o perfil B.
6. Faça DELETE desse ID com o JWT do perfil A. A resposta deve ser `204`, preservando o vínculo ativo do perfil B.
7. Faça DELETE com o JWT do perfil B. Confira perfil nulo, estado `REVOKED`, motivo `LOGOUT` e data de desativação.
8. Repita o DELETE com B. A resposta continua `204`, preservando a data da primeira desvinculação.
9. Repita o PUT com B. Confira mesmo ID e criação, estado ativo e dados de desativação nulos.
10. Teste corpo inválido e ausência de autenticação, conforme a tabela de erros.

Os testes automatizados verificam o contrato HTTP com JWT, validação, mapeamento BSON, ausência de índices declarados nas entidades, filtros de propriedade, opções de atualização atômica e recuperação de uma colisão simulada de inserção. O driver MongoDB é simulado; prepare os índices e use o roteiro acima para conferir o comportamento no banco real.

## Integração Android futura

O mobile só deve cadastrar quando tiver FID registrado no FCM e uma sessão autenticada. Deve guardar o ID interno retornado, repetir o cadastro na sincronização e reenviá-lo após mudança do FID. Falhas de cadastro podem ser repetidas sem gerar documentos duplicados e não devem impedir o login.

No logout, deve chamar o DELETE antes de descartar o JWT. Uma falha de rede não confirma a desvinculação remota. O cliente deve cancelar sincronizações de cadastro da sessão antiga para evitar reativá-la depois do logout ou da troca de usuário.

Quando o FID mudar, o novo cadastro não identifica automaticamente qual instalação antiga foi substituída. O registro antigo pode ser invalidado se um envio retornar `UNREGISTERED`; a expiração por inatividade continua sendo uma etapa futura.

O envio busca os cadastros ativos do perfil e confere o vínculo atual antes de cada tentativa. Essa conferência não cancela mensagens já em trânsito: o Android deve conferir `profileId` contra a sessão atual antes de exibir o conteúdo. O contrato e os limites estão em [Envio de push](push-delivery.md). A implementação Android continua pendente.
