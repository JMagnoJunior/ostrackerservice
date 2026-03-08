# Backend Technical Specification - US-12 Dashboard de Monitoramento por Status

## 1. Overview
- Contexto atual:
  - O backend ja possui endpoints administrativos para conferencia (`/admin/orders/conference`) e fila de ligacao (`/admin/orders/call-queue`).
  - O dominio `Order` possui `status`, `finishedAt`, `clientName`, `clientPhone`, `technicalSummary`, `finalValue` e `hashAccess`.
  - O controle de acesso atual permite `SECRETARIA` e `SUPERUSUARIO` para conferencia, mas outros endpoints administrativos ainda podem ser acessados por `TECNICO` via regra geral.
  - Nao existem campos de agendamento (`scheduledAt`) nem status `DESCARTADA` no enum atual.
- Objetivo:
  - Criar contrato de backend para dashboard administrativo com filtros essenciais de operacao:
    - `ATRASADOS`
    - `SEM_AGENDAMENTO`
    - `PROXIMOS_DESCARTES`
  - Expor visao geral de volumes por status e lista paginada filtravel para mobile/frontend.
- Escopo:
  - Novos endpoints read-only para dashboard.
  - Regras de classificacao por filtro com base no estado atual do modelo.
  - Restricao explicita de autorizacao para secretaria/superusuario.
- Fora de escopo:
  - Alteracoes de fluxo de agendamento, entrega, descarte manual ou purge.
  - Alteracao de status de OS pelo dashboard.

## 2. Main Business Rules
- Regra `Sem Agendamento`: OS com status `FINALIZADA` ou `AGUARDANDO_AGENDAMENTO`.
- Regra `Atrasados`: subconjunto de `Sem Agendamento` com `finishedAt <= now - overdueHours` (padrao 24h).
- Regra `Proximos Descartes`: OS com `status != ENTREGUE` e janela de idade entre `discardWarningDays` e `discardDeadlineDays` (padrao 90-119 dias desde `finishedAt`).
- Regra `Volumes por Status`: dashboard deve retornar contagem por `OrderStatus` para visao geral da operacao.
- Regra `Ordenacao de Prioridade`: listas filtradas devem priorizar OS mais antigas (`finishedAt ASC`, desempate por `id ASC`).
- Regra `Autorizacao do Dashboard`: endpoints de monitoramento aceitam apenas `ROLE_SECRETARIA` e `ROLE_SUPERUSUARIO`.

### Integration Contract (Backend -> Mobile/Frontend)

#### Endpoint 1: Resumo do Dashboard
- **Metodo/rota**: `GET /admin/orders/monitoring/summary`
- **Auth**: `Authorization: Bearer <jwt>`
- **Permissoes**: `SECRETARIA` ou `SUPERUSUARIO`
- **Query params**:
  - `referenceAt` (opcional, ISO-8601 datetime): usado para congelar calculo de janelas no frontend e em testes; default `OffsetDateTime.now()`.
- **Response 200**:
```json
{
  "generatedAt": "2026-03-08T11:20:00Z",
  "counters": {
    "atrasados": 12,
    "semAgendamento": 37,
    "proximosDescartes": 5
  },
  "statusVolumes": [
    { "status": "AGUARDANDO_CONFERENCIA", "count": 8 },
    { "status": "FINALIZADA", "count": 21 },
    { "status": "AGUARDANDO_AGENDAMENTO", "count": 16 },
    { "status": "AGENDADA_PRESENCIAL", "count": 4 },
    { "status": "AGENDADA_DELIVERY", "count": 2 },
    { "status": "ENTREGUE", "count": 15 }
  ]
}
```

#### Endpoint 2: Lista Paginada por Filtro
- **Metodo/rota**: `GET /admin/orders/monitoring`
- **Auth**: `Authorization: Bearer <jwt>`
- **Permissoes**: `SECRETARIA` ou `SUPERUSUARIO`
- **Query params**:
  - `filter` (obrigatorio): `ATRASADOS | SEM_AGENDAMENTO | PROXIMOS_DESCARTES`
  - `page` (opcional, default `0`, minimo `0`)
  - `size` (opcional, default `50`, minimo `1`, maximo `200`)
  - `status` (opcional, repetivel): restringe a listagem para um ou mais `OrderStatus`
  - `referenceAt` (opcional, ISO-8601 datetime): mesmo comportamento do resumo
- **Response 200**:
```json
{
  "content": [
    {
      "id": "fcb2bc2f-d845-4a80-a9a7-40cbfc6dd309",
      "status": "FINALIZADA",
      "clientName": "Cliente Exemplo",
      "clientPhone": "5511999990000",
      "technicalSummary": "Troca de celula de carga",
      "finalValue": 320.50,
      "finishedAt": "2026-03-05T09:00:00Z",
      "inactiveHours": 74,
      "discardAt": "2026-07-03T09:00:00Z",
      "daysToDiscard": 117,
      "monitoringFilter": "SEM_AGENDAMENTO"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```
- **Observacoes de payload**:
  - `inactiveHours` e `daysToDiscard` sao campos calculados para priorizacao visual.
  - `discardAt` deve ser calculado como `finishedAt + discardDeadlineDays`.
  - Quando `filter=ATRASADOS`, `monitoringFilter` deve retornar `ATRASADOS` em todos os itens.

#### Contrato de Erros
- **401 Unauthorized**: token ausente, invalido ou expirado.
- **403 Forbidden**: token valido sem perfil permitido.
- **400 Bad Request**:
  - `filter` ausente/invalido.
  - `page < 0`.
  - `size` fora dos limites permitidos.
  - `referenceAt` com formato invalido.
- **Resposta de erro**: manter `ProblemDetail` padrao do Spring para consistencia com os demais endpoints protegidos.

## 3. Data Models
- Modelo atual utilizado:
  - Tabela `orders` ja contem os campos necessarios para o dashboard v1: `status`, `finished_at`, `client_name`, `client_phone`, `technical_summary`, `final_value`.
- Mudancas propostas:
  - Sem novas colunas para US-12.
  - Sem alteracao de enum de status para esta entrega.
  - Regras de `ATRASADOS` e `PROXIMOS_DESCARTES` serao derivadas de `status` + `finished_at`.
- Constraints e performance:
  - Reaproveitar indice existente `idx_orders_status_finished_at`.
  - Consultas de dashboard devem sempre incluir ordenacao por `finishedAt` para estabilidade.
- Migracao/backfill:
  - Nao aplicavel nesta entrega.

## 4. Affected or Created Classes

#### `MonitoringDashboardController`
- **Status**: `new`
- **Responsibility**: expor contratos HTTP de resumo e lista paginada do dashboard.
- **Method signatures**:
  - `fun getSummary(referenceAt: OffsetDateTime?): MonitoringSummaryDTO`
  - `fun listMonitoringOrders(filter: MonitoringFilter, page: Int, size: Int, status: List<OrderStatus>?, referenceAt: OffsetDateTime?): MonitoringPageResponseDTO<MonitoringOrderItemDTO>`
- **Dependencies**: `MonitoringDashboardService`

#### `MonitoringDashboardService`
- **Status**: `new`
- **Responsibility**: aplicar regras de classificacao (`ATRASADOS`, `SEM_AGENDAMENTO`, `PROXIMOS_DESCARTES`) e montar respostas para dashboard.
- **Method signatures**:
  - `fun getSummary(referenceAt: OffsetDateTime): MonitoringSummaryDTO`
  - `fun listByFilter(filter: MonitoringFilter, statuses: Set<OrderStatus>?, pageable: Pageable, referenceAt: OffsetDateTime): MonitoringPageResponseDTO<MonitoringOrderItemDTO>`
- **Dependencies**: `OrderRepository`, `MonitoringProperties`, `MeterRegistry`

#### `OrderRepository`
- **Status**: `modified`
- **Responsibility**: suportar consulta agregada de volumes por status e filtros dinamicos para monitoramento.
- **Method signatures**:
  - `fun countGroupedByStatus(): List<OrderStatusCountProjection>` (new)
  - `fun findAll(spec: Specification<Order>, pageable: Pageable): Page<Order>` (new capability via `JpaSpecificationExecutor`)
- **Dependencies**: `Spring Data JPA`

#### `MonitoringProperties`
- **Status**: `new`
- **Responsibility**: centralizar limites de classificacao e pagina para o dashboard.
- **Method signatures**:
  - `data class MonitoringProperties(val overdueHours: Long, val discardWarningDays: Long, val discardDeadlineDays: Long, val maxPageSize: Int)`
- **Dependencies**: `@ConfigurationProperties(prefix = "app.monitoring")`

#### `SecurityConfig`
- **Status**: `modified`
- **Responsibility**: restringir rota de monitoramento a `SECRETARIA` e `SUPERUSUARIO`.
- **Method signatures**:
  - `fun securityFilterChain(http: HttpSecurity): SecurityFilterChain` (modified mapping rules)
- **Dependencies**: Spring Security

## 5. Configuration
- Novas propriedades de runtime:
  - `app.monitoring.overdue-hours=24`
  - `app.monitoring.discard-warning-days=90`
  - `app.monitoring.discard-deadline-days=120`
  - `app.monitoring.max-page-size=200`
- Compatibilidade:
  - Defaults mantem comportamento esperado de negocio sem exigir rollout coordenado.
  - Alteracao de thresholds pode ser feita por ambiente sem mudanca de codigo.

## 6. Tests
- **Unitarios (service)**:
  - Classificacao correta de `SEM_AGENDAMENTO`.
  - Limiar exato de `ATRASADOS` (24h exato entra no filtro).
  - Janela de `PROXIMOS_DESCARTES` (dia 90 entra, dia 120 sai).
  - Intersecao entre `filter` e `status` adicional.
  - Calculo de `daysToDiscard`, `discardAt` e ordenacao deterministica.
- **Integrados (controller + banco + seguranca)**:
  - `GET /admin/orders/monitoring/summary` retorna contadores coerentes com massa de dados.
  - `GET /admin/orders/monitoring` respeita `page/size`, ordenacao e `totalElements`.
  - `401` sem token, `403` para `TECNICO`, `200` para `SECRETARIA`/`SUPERUSUARIO`.
  - `400` para `filter` invalido, `size` fora de faixa e `referenceAt` invalido.
- **Criticos de regressao**:
  - Endpoint legado `/admin/orders/call-queue` continua funcional.
  - Endpoints de conferencia (`/admin/orders/conference/**`) mantem comportamento atual.
  - Sem impacto em criacao tecnica de OS (`/api/orders/finalizations`).
