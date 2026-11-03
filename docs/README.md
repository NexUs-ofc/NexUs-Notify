# Documentação e revisão do Notify

Revisão iniciada em **02/11/2026** sobre o Notify `5105295`, com consulta aos projetos locais Core (`ff6c69d`) e Mobile (`e058c8e`). Na preparação desta branch, as referências do remoto foram atualizadas e a documentação foi reconciliada com a `main` em `15e6295`.

## Documentação

- [Configuração e execução](../README.md).
- [Cadastro de instalações push](push-installations.md).
- [Envio de push](push-delivery.md).
- [Lojas próximas](nearby-stores.md).
- [Painel do administrador](painel-admin.md).
- [Containers e Kubernetes](containers.md).

## Escopo e resultado

A análise original examinou os arquivos de produção, configurações, dependências, documentação e testes relacionados do Notify, além das integrações com Core e Mobile. As referências desses dois projetos foram procuradas de forma transversal; suas funcionalidades independentes não foram auditadas integralmente.

As conclusões são baseadas em código disponível localmente. Não houve consulta a registros de tráfego de produção, banco real ou envio de push. “Chamado” neste relatório significa que há um caminho de execução identificado no código, e não que houve tráfego confirmado no ambiente publicado.

A hipótese inicial se confirmou parcialmente: o Mobile não tem consumo de histórico encontrado nas telas, mas usa o Notify para cadastrar e desvincular instalações push. O banco também sustenta deduplicação, destinatários e estado das sugestões de lojas.

### Atualização da base antes da publicação

A `main` recebeu alterações depois do checkout usado na revisão:

- `0052c89` removeu o controle de leitura, incluindo `Notification.markAsRead`, `read_at` no domínio, os campos de leitura da resposta, o endpoint PATCH e suas dependências exclusivas.
- Outras alterações integradas atualizaram testes, contratos da API, limite de requisições, containers e Kubernetes. A readiness do manifesto atual usa o health público.

A retirada isolada de `Notification.markAsRead` e os ajustes de fixtures feitos durante esta revisão já foram absorvidos pela remoção mais ampla do controle de leitura na `main`. Eles não são reaplicados nesta branch. **O diff final contém somente documentação**, preservando integralmente o código da base `15e6295`.

## Endpoints atuais do aplicativo

As três rotas exigem JWT do Auth e resolvem o perfil pelo subject do token.

| Método e rota | Uso encontrado | Decisão nesta branch |
| --- | --- | --- |
| `GET /api/notifications?page=0&size=20` | Declarado em `NotifyAPI.listar` e encapsulado no `NotificationRepository` do Mobile. Não foi encontrado quem instancie esse repository nas telas ou nos ViewModels. | Preservar a rota. Continua candidata a uma decisão futura de retirada do contrato. |
| `PUT /api/push/installations` | `CerisApplication` → `PushNotifications` → job de sincronização → `PushSynchronizer.synchronize` → `PushInstallationRepository.registrarImediato`. | Manter: registra o dispositivo usado pelo push. |
| `DELETE /api/push/installations/{id}` | `PushNotifications` → `PushSynchronizer.revoke` → `PushInstallationRepository.desvincularImediato`. Usado no fluxo de logout, mudança de sessão e desativação do push. | Manter: desfaz o vínculo com a conta. |

O cadastro depende de URL do Notify configurada, sessão com perfil e permissão de notificações. O Mobile já possui recebimento de mensagens de dados, verificação da sessão, deduplicação e publicação dos tipos suportados.

`PATCH /api/notifications/{id}/read` já foi retirado da implementação integrada na `main`; `NotificationResponse` não inclui `readAt` nem `read`. As declarações antigas encontradas no cliente Mobile não equivalem a chamadas em execução.

Não foi encontrado o Core chamando o histórico do Notify. Buscas adicionais nos projetos locais Auth, IA e Web também não identificaram chamadas a essa rota. Scripts, painéis ou versões antigas fora dos checkouts são possibilidades não verificadas; nenhum consumidor externo concreto foi identificado.

Evidências: [NotificationController](../src/main/java/com/example/nexusnotify/controller/NotificationController.java), [NotificationResponse](../src/main/java/com/example/nexusnotify/controller/dto/NotificationResponse.java), [NotifyAPI do Mobile](../../../mobile/Ceris-Mobile/app/src/main/java/com/example/ceris/api/NotifyAPI.kt), [NotificationRepository do Mobile](../../../mobile/Ceris-Mobile/app/src/main/java/com/example/ceris/repository/NotificationRepository.kt), [PushSynchronizer](../../../mobile/Ceris-Mobile/app/src/main/java/com/example/ceris/notification/PushSynchronizer.kt) e [CerisApplication](../../../mobile/Ceris-Mobile/app/src/main/java/com/example/ceris/CerisApplication.kt).

## Operação e documentação da API

| Rotas | Responsabilidade | Decisão |
| --- | --- | --- |
| `GET /admin/session` e `GET /admin/metrics` | Contrato administrativo, protegido por `X-Admin-Key`. | Preservar. Ausência de chamada no Mobile não comprova desuso operacional. |
| `GET /actuator/health` | Health público, incluindo a verificação do MongoDB. É o endereço usado pela readiness atual. | Preservar. O health não comprova entrega pelo Firebase nem consulta o Core. |
| `GET /actuator/metrics`, `/actuator/metrics/{name}` e `/actuator/prometheus` | Inspeção e exportação de métricas, protegidas pela chave administrativa. | Preservar; consumidores externos não foram verificados. |
| Swagger e `/v3/api-docs` | Documentação e inspeção manual dos contratos. | Preservar. Não precisam de consumidor no Mobile. |

A aplicação não oferece endpoint público de geração ou disparo manual de notificações. A geração automática entra por `NotificationScheduler`; `MetricsSampler` tem um agendamento separado para o painel.

## Integração com o Core

O fluxo identificado é **agendador do Notify → Core → regras → MongoDB → Firebase → Mobile**. O Core fornece candidatos; não foi encontrado chamando a API do Notify.

| GET no Core | Consumidor no Notify |
| --- | --- |
| `/internal/notifications/products` | `ProductNotificationGenerationService` → `CoreClient.products`. |
| `/internal/notifications/events` | `EventNotificationGenerationService` → `CoreClient.events`. |
| `/internal/notifications/shopping-lists` | `ShoppingListNotificationGenerationService` → `CoreClient.shoppingLists`. |
| `/internal/notifications/new-stores` | Descoberta de novidades em `NearbyStoreNotificationGenerationService` → `CoreClient.newStores`. |
| `/internal/notifications/nearby-stores` | Seleção de lojas elegíveis em `NearbyStoreNotificationGenerationService` → `CoreClient.nearbyStores`. |

As consultas são paginadas e enviam `X-Notify-Token`. O agendamento padrão é diário às 11h em `America/Sao_Paulo`. As duas consultas de lojas dependem desse fluxo estar habilitado. Firebase só envia quando o push está habilitado e há destinatários ativos. O JWT do Auth é validado localmente, sem chamada HTTP do Notify ao Auth.

Evidências: [CoreClient](../src/main/java/com/example/nexusnotify/infra/core/CoreClient.java), [NotificationGenerationService](../src/main/java/com/example/nexusnotify/service/NotificationGenerationService.java), [NotificationScheduler](../src/main/java/com/example/nexusnotify/infra/scheduler/NotificationScheduler.java) e [NotificationCandidateController do Core](../../../core/NexUs-Core/src/main/java/com/example/nexuscore/controller/NotificationCandidateController.java).

## Por que o banco permanece necessário

| Collection | Responsabilidade |
| --- | --- |
| `notifications` | Persiste o aviso antes do envio e impede a recriação do mesmo alerta pela chave única. Seu `_id` integra o payload usado pelo Mobile para evitar exibição duplicada. |
| `push_installations` | Guarda FID, perfil, estado, revogação e invalidação; localiza destinatários e permite conferir o vínculo antes de enviar. |
| `nearby_store_rotations` | Mantém ciclo, lojas já apresentadas, última sugestão e próxima data elegível. |
| `new_store_suggestions` | Mantém novidades pendentes e apresentadas, evitando reabrir a mesma pendência. |
| `new_store_discovery_state` | Mantém o progresso da descoberta e permite recuperar intervalos após interrupção. |

`NotificationCreationService` despacha somente depois que `NotificationWriter` insere um aviso novo. Avisos existentes não são reenviados. Lojas salvam rotação, aviso e consumo da pendência em transação MongoDB.

O índice `uq_notification_alert` continua necessário. O índice `idx_notification_profile_created` ainda atende à rota de histórico existente. Retirar o banco ou esses mecanismos mudaria deduplicação, frequência, recuperação e vínculo de dispositivos. `NOTIFY_PUSH_ENABLED=false` não cria um modo sem banco: a geração continua persistindo avisos.

Evidências: [NotificationCreationService](../src/main/java/com/example/nexusnotify/service/NotificationCreationService.java), [NotificationWriter](../src/main/java/com/example/nexusnotify/service/NotificationWriter.java), [PushDispatchService](../src/main/java/com/example/nexusnotify/service/PushDispatchService.java) e [NearbyStoreSuggestionService](../src/main/java/com/example/nexusnotify/service/NearbyStoreSuggestionService.java).

## Recomendações que permanecem fora desta branch

| Item | Avaliação |
| --- | --- |
| API GET de histórico e suas dependências exclusivas | Sem consumidor encontrado no Mobile. É candidata a uma retirada futura de contrato; a geração e a persistência não dependem desse controller. |
| `SchedulerProperties` | Sem consumidor direto identificado, mas o binding tipado participa da rejeição de configurações inválidas. Manter na limpeza mínima. |
| `@EnableMethodSecurity`, dois `@EnableScheduling` e ramo Hikari/Postgres das métricas | Simplificações opcionais de baixo ganho, que ampliariam a verificação de segurança, agendamento e observabilidade. Não foram alteradas. |
| MongoDB, cadastro push, JWT, geração, regras, Firebase e administração | Responsabilidades identificadas; preservar. Não há dependência principal do `pom.xml` cuja retirada seja justificada pela ausência de tela de histórico. |
| Dados antigos, índices e `PushInstallationStatus.STALE` | Não houve inspeção de dados ou validadores externos. Nenhuma migração ou exclusão foi realizada. |

No Core local, `CandidatePageResponse` não tem referência encontrada fora da declaração. `ProductCandidateResponse` é usado apenas por `StockMapper.toNotificationCandidate`, sem chamada encontrada desse método. No Mobile local, o repository de histórico e os métodos assíncronos de instalações não têm consumidores de produção encontrados; o push usa os métodos síncronos. Esses projetos não foram editados.

## Achados funcionais separados da limpeza

- O Notify gera `SHOPPING_LIST_REMINDER`, mas os parsers `PushMessage` e `NotificationLink` do Mobile local não aceitam esse tipo. O lembrete é descartado antes da exibição. Isso é uma incompatibilidade de contrato, não evidência para retirar o fluxo de listas.
- O detalhe de evento do Mobile tenta `GET /api/events/{id}`, ausente no Core local analisado. A rota interna de candidatos existe e atende a outra responsabilidade.
- A divergência das probes observada no checkout inicial não permanece no manifesto atual: a readiness usa `GET /actuator/health`, público. Essa correção veio da `main` e não é uma alteração desta branch.

O envio continua sem fila persistida ou recuperação automática de tentativas perdidas. Uma falha entre salvar e enviar pode perder o push; repetir a geração não o reenvia. Esse comportamento não foi alterado.

Evidências: [ShoppingListReminderRule](../src/main/java/com/example/nexusnotify/service/rule/ShoppingListReminderRule.java), [PushMessage](../../../mobile/Ceris-Mobile/app/src/main/java/com/example/ceris/notification/PushMessage.kt), [NotificationLink](../../../mobile/Ceris-Mobile/app/src/main/java/com/example/ceris/notification/NotificationLink.kt), [NotificationSourceAPI](../../../mobile/Ceris-Mobile/app/src/main/java/com/example/ceris/api/NotificationSourceAPI.kt) e [deployment.yaml](../k8s/deployment.yaml).

## Conteúdo final e validação

Esta branch corrige o README da raiz e os guias de push para refletir a integração existente. Também remove exemplos do PATCH de leitura e os campos de resposta que já foram retirados na `main`. Este arquivo reúne o inventário e as decisões da revisão.

O diff final em relação a `15e6295` altera somente `README.md`, `docs/README.md`, `docs/push-delivery.md` e `docs/push-installations.md`. Código, testes, dependências, configurações e implantação são os da base atualizada. Core e Mobile permanecem sem edições.

Na etapa anterior, ainda sobre `5105295`, a compilação, o empacotamento e o Checkstyle passaram. Foram executados 282 testes: 279 passaram e três falharam na inicialização do cliente HTTP do Firebase por falha de conexão local do Java. Os testes das três classes de fixtures ajustadas naquela etapa e o teste de atualização atômica passaram. Uma verificação Java independente reproduziu a falha em `Selector.open()`. Esses resultados pertencem à etapa anterior e não são apresentados como uma execução da suíte da nova base.

Como o diff final é exclusivamente documental, a conferência desta branch verifica referências, exemplos e ausência de mudanças na implementação. Links entre os projetos irmãos refletem a organização local `NEXUS_DEV`; exigem esses checkouts ao lado do Notify.
