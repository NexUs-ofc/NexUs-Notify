# Envio de push pelo Firebase

## Fluxo e persistência

1. A geração salva um aviso novo em `notifications`, protegida pelo índice único de alerta.
2. Com `NOTIFY_PUSH_ENABLED=true`, o `PushDispatchService` busca as instalações `ACTIVE` desse perfil.
3. Antes de cada envio, confere o ID interno, FID, perfil, estado e `updated_at` consultados.
4. O `FirebasePushProvider` envia uma mensagem de dados pelo FID usando o Firebase Admin SDK.
5. Cada resultado é registrado nos logs. Um erro em um aparelho não interrompe os demais.

As collections continuam sendo apenas `notifications` e `push_installations`. Não há collection de entregas, novos campos no aviso, novos endpoints ou novos índices nesta etapa. Os índices necessários e seus comandos de preparação estão em [Cadastro de instalações](push-installations.md).

Com o push desabilitado, não há inicialização do Firebase nem consulta de instalações durante a geração. Os avisos continuam sendo salvos e consultados normalmente. Avisos já existentes não são enviados ao habilitar o push posteriormente. Sem instalações ativas, o aviso permanece apenas no histórico.

O envio é síncrono e aumenta a duração da geração conforme o número de instalações e o tempo de resposta do Firebase. As opções do SDK limitam conexão a 5 segundos e leitura/escrita a 10 segundos por operação. Isso não é um prazo total da geração; o SDK pode repetir requisições internamente.

## Resultados

| Resultado | Tratamento |
| --- | --- |
| Firebase aceitou | Log com ID interno do aviso, ID interno da instalação e ID da mensagem. Não confirma recebimento, exibição ou leitura. |
| `UNREGISTERED` | Tenta marcar a instalação como `INVALID`, com `disabled_reason=FCM_UNREGISTERED` e data de desativação. Preserva perfil e FID. |
| Outros códigos, incluindo `INVALID_ARGUMENT`, `SENDER_ID_MISMATCH`, autenticação, quota e indisponibilidade | Registra o código sem invalidar o cadastro. O erro pode estar na mensagem, projeto ou infraestrutura. |
| Exceção inesperada ou falha no Mongo durante o envio | Registra o tipo da exceção sem seu texto, preservando o aviso salvo. |

O `activeSnapshot` é somente um filtro de consulta, não um documento salvo. O filtro usado por `isCurrentActive` também é usado no `markInvalid`. A invalidação é uma atualização condicional: se o vínculo ou `updated_at` mudarem enquanto o Firebase responde, o cadastro atualizado não é sobrescrito pela resposta antiga. O método retorna se houve alteração.

`updated_at` é uma data BSON com precisão de milissegundos, não uma versão monotônica de sessão. Essa proteção não elimina todas as condições de corrida. Alterações na mesma precisão temporal podem não ser distinguíveis; uma troca após a conferência também pode ocorrer durante a chamada externa. Um logout não consegue cancelar uma mensagem já aceita pelo Firebase. A verificação da sessão no Android continua necessária.

Os logs desta implementação não incluem FID, título, corpo, credenciais ou o texto bruto das exceções. A leitura continua sendo registrada exclusivamente pelo endpoint de leitura.

## Limites aceitos nesta versão

Não há fila persistente, worker, agenda de novas tentativas ou recuperação após reinício. Se a API cair entre salvar e enviar, o aviso permanece no histórico e o push pode ser perdido. Falhas de envio não são recuperadas ao repetir a geração, pois o aviso já existe. Uma chamada aceita pelo Firebase antes de um erro de comunicação também pode resultar em exibição mesmo quando a API registra falha.

O cadastro `INVALID` é reativado pelo próximo PUT autenticado do mesmo FID. Não há detecção imediata de desinstalação nem rotina de expiração por inatividade; `STALE` segue reservado.

## Contrato Android futuro

Mensagem de dados, com todos os valores como strings:

```json
{
  "notificationId": "670c00000000000000000001",
  "profileId": "12",
  "type": "PRODUCT_EXPIRING",
  "sourceId": "45",
  "title": "Produto próximo do vencimento",
  "message": "O produto Leite vence amanhã."
}
```

Não há payload `notification` de exibição automática. O aplicativo deve implementar o recebimento da mensagem de dados, conferir a sessão e só então exibir o aviso. Deve descartar mensagens sem sessão ou com outro `profileId`, evitar duplicações por `notificationId` e consultar a API para obter o estado atualizado. Mensagens do mesmo perfil entre sessões diferentes não são diferenciadas por este contrato; se necessário, isso exigirá uma versão de vínculo compartilhada entre cliente e API.

O envio usa prioridade Android alta para avisos visíveis ao usuário e TTL de uma hora no FCM. O mobile deve exibir os avisos elegíveis prontamente; prioridade alta sem conteúdo visível pode ser rebaixada pelo FCM. Permissão Android, canal de notificação e execução em segundo plano precisam ser tratados e testados no mobile. O TTL não confirma entrega nem substitui a data de criação do histórico.

## Validação

Os testes automatizados usam MongoDB e Firebase simulados: conferem o payload serializado pelo SDK, destinatário FID, configuração condicional, classificação dos erros, isolamento de falhas, filtros BSON e envio somente após salvar um aviso novo. Não usam credenciais reais nem fazem envios externos.

Para o teste integrado, use uma base isolada com os índices preparados, Core e Auth acessíveis e um Android registrado no mesmo projeto Firebase:

1. Configure as credenciais conforme o README e habilite `NOTIFY_PUSH_ENABLED=true`.
2. Cadastre pelo PUT um FID real registrado no FCM com o JWT do perfil. `fid-de-teste` serve apenas para testar o cadastro com push desabilitado, não para confirmar recebimento.
3. Prepare no Core um produto ou evento desse perfil que satisfaça uma regra para a data atual no fuso configurado.
4. Em teste, configure `NOTIFY_SCHEDULER_CRON=0 * * * * *` e mantenha `NOTIFY_SCHEDULER_ENABLED=true`. O job executará a cada minuto. Restaure o cron de produção após o teste.
5. Confira o aviso no Mongo, o resultado nos logs e o recebimento Android. Um log de aceitação não prova que houve exibição.
6. Repita a geração: o mesmo aviso não deve ser inserido ou enviado outra vez.
7. Teste múltiplos aparelhos, ausência de aparelhos ativos, logout e troca de perfil. Valide também mensagens em trânsito no Android após o logout.

Sem o recebimento Android implementado, só é possível validar a aceitação no FCM; a exibição depende da próxima etapa do mobile.

Referências: [envio por FID](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/messaging/Message.Builder), [erros do FCM](https://firebase.google.com/docs/cloud-messaging/error-codes) e [prioridade Android](https://firebase.google.com/docs/cloud-messaging/android-message-priority).
