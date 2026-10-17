# Sugestões de lojas próximas

## Regras

O Core encontra perfis residenciais ativos com endereço geocodificado e lojas próximas cujos perfis e empresas estão ativos. O Notify consulta `GET /internal/notifications/nearby-stores?radiusKm=5&page=0&size=100` com `X-Notify-Token`. Cada candidato contém `profileId` e uma lista de lojas com `storeId`, `name` e `distanceKm`. Não há consulta de GPS nem geocodificação no Notify.

O raio padrão é 5 km, incluindo o limite. O intervalo padrão é 72 horas entre sugestões de qualquer tipo de loja para o mesmo perfil. O relógio é o configurado na aplicação; o próximo horário permitido é persistido como `Instant`. O agendamento diário pode postergar o aviso até a execução seguinte, mas não antecipa o intervalo. Repetir o job não libera outra sugestão.

A seleção prioriza a loja nova pendente mais próxima que ainda não tenha sido apresentada no ciclo atual. Sem novidades elegíveis, escolhe a loja não apresentada mais próxima. Distâncias iguais são desempatadas pelo ID da loja. Lojas ausentes da resposta atual, fora do raio ou com dados inválidos não são selecionadas.

Ao esgotar as lojas disponíveis na consulta atual, o ciclo avança e limpa o conjunto de lojas apresentadas. A primeira escolha evita a última loja do ciclo anterior quando existe outra opção. Com uma única loja disponível, ela pode reaparecer após o intervalo mínimo. Uma loja removida/inativada não bloqueia o fechamento do ciclo; uma loja que passar a ser elegível pode entrar no ciclo atual. Com distâncias fixas A < B < C, os ciclos normais serão A, B, C / A, B, C: não há alternância artificial da primeira loja.

## Descoberta e prioridade de novidades

Antes das sugestões, o Notify consulta `/internal/notifications/new-stores` com `from`, `to`, `radiusKm`, `page` e `size`. Cada loja traz `storeId`, `name`, `createdDate` e `profileIds`. Cada par perfil/loja é persistido como uma pendência, inclusive durante o intervalo sem poder sugerir.

Na primeira execução, a descoberta começa na data atual no fuso configurado. Lojas antigas continuam elegíveis para a rotação normal. Após a primeira janela concluída, o progresso é persistido. A próxima execução consulta novamente a última data concluída para capturar lojas cadastradas depois do job anterior. Em indisponibilidades, recupera as datas restantes em janelas de até 31 dias, respeitando o contrato do Core. O progresso só avança depois de persistir todas as páginas da janela; uma falha permite repetir sem recriar pendências já existentes. Uma página vazia com `hasNext=true` não encerra nenhuma das consultas.

O cadastro da pendência usa somente `$setOnInsert` e o `_id` composto por perfil e loja. Consultas repetidas não reabrem sugestões já apresentadas. Uma descoberta sobreposta de loja já apresentada no ciclo atual também é consumida. Pendências são revalidadas pela lista atual de lojas próximas antes da seleção; não se envia o conteúdo antigo apenas porque existe uma pendência. Sem elegibilidade atual, ela fica pendente para uma futura consulta. Não há expiração automática por idade nesta versão.

Exemplo: dia 1, loja A; dia 2, loja D recém-cadastrada fica pendente; dia 4, D é sugerida como novidade e entra no conjunto apresentado; dia 7, a rotação segue com B. Ambos os tipos usam o mesmo limite de frequência.

## Persistência e concorrência

| Collection | Campos principais | Finalidade |
| --- | --- | --- |
| `nearby_store_rotations` | `_id` inteiro do perfil, `version`, `cycle`, `shown_store_ids`, `last_store_id`, `next_eligible_at` | Rotação e frequência compartilhadas. |
| `new_store_suggestions` | `_id` string `perfil:loja`, `profile_id`, `store_id`, `store_created_date` ISO, `discovered_at`, `presented_at` | Novidade pendente quando `presented_at` é null; mantém o registro após apresentação para não reabrir a pendência. |
| `new_store_discovery_state` | `_id` igual a `new-stores`, `scanned_through` ISO `YYYY-MM-DD` | Data concluída, atualizada com `$max` para não retroceder entre execuções. |

Uma transação por perfil confirma a atualização da rotação, a inserção do aviso em `notifications` e a apresentação da pendência. `@Version` protege a rotação contra gravações concorrentes, inclusive após reiniciar a aplicação. Conflitos ou falhas abortam a transação; o perfil pode ser tentado novamente numa execução posterior. Uma falha por perfil não impede os demais, mas o fluxo e a execução geral terminam com resultado de falha.

O envio de push ocorre depois do commit e mantém os limites existentes: não há recuperação persistida de push. O intervalo e o ciclo consideram o aviso gravado, não o recebimento, a exibição ou a leitura. Não se altera o formato público do histórico; são acrescentados `NEARBY_STORE` e `NEW_NEARBY_STORE`, com `sourceId` contendo o ID da loja. A chave é `nearby-store:cycle:<numero>`, em conjunto com perfil, tipo e origem no índice único já existente. A versão da rotação coordena ambos os tipos.

Não remova a rotação ou os registros de novidades como rotina de limpeza: isso pode reiniciar a frequência e reabrir divulgações. A exclusão de dados de um perfil deve ser coordenada com o ciclo de vida desse perfil.

## Preparar o MongoDB

Este fluxo exige um replica set ou cluster com suporte a transações, também quando `NOTIFY_PUSH_ENABLED=false`. Mongo standalone pode atender aos fluxos antigos, mas não à gravação transacional de lojas. Para executar somente os fluxos antigos, use `NOTIFY_NEARBY_STORES_ENABLED=false`.

Prepare as collections antes de habilitar o fluxo, mantendo a criação automática de índices desabilitada. Execute na base de `MONGO_DB`, com uma conta de administração apropriada:

```javascript
db = db.getSiblingDB("NOME_DA_BASE_CONFIGURADA_EM_MONGO_DB");

for (const name of ["nearby_store_rotations", "new_store_suggestions", "new_store_discovery_state"]) {
  if (!db.getCollectionNames().includes(name)) {
    db.createCollection(name);
  }
}

db.new_store_suggestions.createIndex(
  { profile_id: 1, presented_at: 1 },
  { name: "idx_new_store_suggestion_profile_presented" }
);
```

O índice `_id` de cada collection já garante a unicidade dos estados e pares. Preserve também os índices existentes de `notifications` e `push_installations` descritos em [Cadastro de instalações](push-installations.md). Os comandos acima não apagam documentos e não alteram validações existentes; se houver schema validator, ele precisa permitir os campos e tipos descritos. A aplicação não faz alterações administrativas no banco ao iniciar.

## Validação integrada

Use uma base isolada com suporte a transações, os índices preparados e Core/Auth acessíveis. O teste de envio ao aparelho exige a implementação Android.

1. Cadastre um perfil residencial e lojas geocodificadas dentro e fora de 5 km. Execute o job e confira um único aviso da loja elegível mais próxima.
2. Repita o job antes de 72 horas: não deve haver outro aviso de loja para o perfil.
3. Cadastre uma loja nova próxima e execute a descoberta durante o intervalo. Confira sua pendência sem criar outro aviso.
4. Após o intervalo, confira a prioridade da novidade, sua apresentação e sua inclusão em `shown_store_ids`.
5. Continue até o novo ciclo. Confira a limpeza do conjunto e a prevenção de repetir a última loja quando houver alternativa.
6. Inative uma loja pendente ou altere o endereço do perfil no Core. Confira que somente candidatos atualmente elegíveis são sugeridos.
7. Em ambiente controlado, interrompa uma gravação ou execute dois jobs concorrentes. Confirme que não existe avanço confirmado de ciclo sem o aviso correspondente nem dois avisos no intervalo.
8. Indisponibilize produtos, eventos e lojas separadamente. Confirme que os outros fluxos continuam e que `notify.generation.flow.duration` identifica o fluxo com falha.

Os testes automatizados usam o padrão do repositório com dependências simuladas, incluindo o driver e a sessão Mongo. Eles não substituem a verificação das transações, consultas geográficas do Core e entrega Firebase em ambiente real.
