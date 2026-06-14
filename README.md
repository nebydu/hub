# hub — 모니터링 솔루션 데모 BE

Agent와의 양방향 통신 허브 + Thymeleaf 단일 페이지 콘솔을 갖춘
**데모 walking skeleton 완성본**.

spec v0.2.1의 데모 범위(§0 위상 / §1~§5 토픽·도메인·페이로드 / §4.2 단일
페이지 UI / ADR #16 #17 등)를 모두 코드로 풀어놓은 reference implementation.
영속 저장소·인증·LEGO+WebSocket·SQL_JOB 등은 본개발 영역 (§6 / §7 ADR 후보).

## 데모 BE 전체 범위

- **수신 측 (Agent → BE)** 네 토픽 처리:
  - `audit-topic` — AGENT_STARTED → AgentRegistry 등록, AGENT_STOPPED → OFFLINE 마킹,
    JOB_EXECUTED → audit ring buffer 적재
  - `result-topic-job` / `result-topic-log` — JobResult(SCRIPT_JOB / LOG_JOB) ring buffer 적재
    (T4-2 분리, 단일 멀티토픽 listener)
  - `heartbeats-topic` — OTLP protobuf 디코드 (spec §5.4.2 / ADR #2), HeartbeatLatestMap 갱신 +
    AgentRegistry.lastSeen 갱신
- **송신 측 (BE → Agent)**: Quartz 스케줄러 + `command-topic` producer
  - cron 트리거마다 `valid_until` = "다음 트리거 예정 시각의 90% 지점" 계산 (§5.1.3)
  - Trigger misfire = `MISFIRE_INSTRUCTION_DO_NOTHING` (§5.1 + ADR #17)
  - spec §2.2 envelope 헤더 4종(x-message-id/version/source/trace-id) 첨부
  - spec §2.3 메시지 키 = `target_agent_id`
- **Thymeleaf 단일 페이지 UI** — `GET /` 한 페이지에 모든 in-memory state 노출:
  - 등록 Agent 목록 (ONLINE/OFFLINE + last_seen + heartbeat last_seen)
  - Schedule 등록 폼 (§4.2 단일 폼 — job_type 선택으로 SCRIPT_JOB/LOG_JOB 분기)
  - 등록된 Schedule 목록
  - 최근 명령 / 작업 결과 / 감사 이벤트 3종 패널
- **REST API**(외부 자동화용):
  - `POST /schedules` — Schedule 등록 (JSON)
  - `GET /schedules` — 등록된 스케줄 목록
  - `GET /commands` — 최근 발행 commands 50개
  - `POST /ui/schedules` — UI 폼 submit (redirect → `/`)
  - `GET /health` → `OK`
- 영속 저장소 없음 (재시작 시 모든 상태 휘발, Quartz도 RAMJobStore)
- 인증/인가 없음

## 본개발 진입 시 다뤄지는 영역 (ADR 후보 — spec §7)

영속 저장소(PG/OpenSearch, ADR #12), 인증/인가(JWT+Knox, #7),
Schema Registry(#1), Heartbeat protobuf(#2), 화면 LEGO+WebSocket(#8),
SQL_JOB(#9), Alert/Incident, 시계열 메트릭 등.

자세한 메시지 스키마는 [`monitoring-demo-message-spec-v0.2.1.md`](../monitoring-meta/docs/phase0-snapshot/monitoring-demo-message-spec-v0.2.1.md) 참조.

## 사전 조건

- JDK 21 (이 모듈은 `pom.xml`에 명시한 Java 21로 빌드)
- Maven 3.9+
- 부모 워크스페이스 `../infra/`의 docker-compose가 떠 있어야 한다.
  Kafka broker(`localhost:9092`)와 OTel Collector를 함께 띄운다.

  ```powershell
  docker compose -f ../infra/docker-compose.yml up -d
  ```

  토픽(`command-topic`, `result-topic-job`, `result-topic-log`, `audit-topic`,
  `heartbeats-topic`)은 `kafka-init` one-shot 컨테이너가 자동 생성한다.

## 데이터스토어 연결 (phase1-050)

PG / OpenSearch / Redis / MinIO 4종의 **클라이언트 연결 설정**을 갖춘다(연결 설정 +
Gated 연결 smoke까지만 — 도메인 영속 repository/엔티티/DDL/트랜잭션은 아직 없으며
Phase 0 in-memory 흐름은 불변). 엔드포인트/credential은 `application.yml`에서
`${ENV:default}` 패턴으로 주입되며, credential은 infra `.env`와 동일 변수명
(`POSTGRES_USER`/`POSTGRES_PASSWORD`/`REDIS_PASSWORD`/`MINIO_ROOT_USER`/
`MINIO_ROOT_PASSWORD`)을 읽는다.

인프라 미기동 상태에서도 hub는 부팅한다(PG는 Hikari `initialization-fail-timeout: -1`,
OpenSearch/MinIO 빈은 객체 생성만 하고 eager ping/connect 안 함). 따라서 **기본
`mvn test`는 연결 smoke를 skip**한다. 실제 인프라 연결 검증은 `SMOKE_INFRA=1`
환경변수로 Gate를 열어 별도 실행한다:

```powershell
# 인프라가 기동된 상태에서만 (../infra docker-compose up -d)
$env:SMOKE_INFRA=1; ./mvnw -Dtest=DatastoreConnectionSmokeTest test
```

`SMOKE_INFRA`가 없으면 smoke 테스트는 비활성화되고 기존 단위/슬라이스 테스트만 돈다.

## 빌드

```powershell
mvn -DskipTests package
```

테스트 포함 빌드:

```powershell
mvn package
```

## 실행

```powershell
mvn spring-boot:run
```

기본 포트 `8080`. `GET /health` → `OK` 가 첫 동작 확인 신호.
브라우저로 `http://localhost:8080/`을 열면 데모 콘솔 UI가 뜬다.

### 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `HUB_KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka brokers. infra의 host listener 포트. |
| `HUB_PG_URL` | `jdbc:postgresql://localhost:5432/appdb` | PostgreSQL JDBC URL. |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | (빈값) / (빈값) / `appdb` | PG credential·DB명 (infra `.env`). |
| `HUB_OPENSEARCH_ENDPOINT` | `http://localhost:9200` | OpenSearch HTTP endpoint (인증 없음). |
| `HUB_REDIS_HOST` / `HUB_REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / (빈값) | Redis 접속 (infra는 password 필수). |
| `HUB_MINIO_ENDPOINT` / `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` / `HUB_MINIO_BUCKET` | `http://localhost:9000` / (빈값) / (빈값) / `app-objects` | MinIO 접속·버킷. |
| `SMOKE_INFRA` | (미설정) | `1`이면 데이터스토어 연결 smoke 활성화. 미설정 시 skip. |

`application.yml`의 ring buffer 크기와 heartbeat timeout도 환경변수로 override
가능 (`HUB_AUDIT_RINGBUFFERSIZE` 등 Spring relaxed binding 규칙):

| 설정 | 기본값 | spec 출처 |
|---|---|---|
| `hub.audit.ring-buffer-size`   | 200 | §4.3 audit-topic ring |
| `hub.job.ring-buffer-size`     | 100 | §4.3 result-topic-job/log ring |
| `hub.command.ring-buffer-size` |  50 | §4.3 command-topic ring |
| `hub.agent.heartbeat-timeout-seconds` | 30 | §3.2 OFFLINE 판정 기준값 |

## 종단 검증 시나리오

1. infra docker-compose 기동 (위 사전 조건).
2. 본 모듈에서 `mvn spring-boot:run`.
3. 다른 터미널에서 `curl http://localhost:8080/health` → `OK` 확인.
4. 또 다른 터미널에서 `../script-agent`를 빌드/실행:

   ```powershell
   cd ../script-agent
   go run ./cmd/agent
   ```

   hub 콘솔에 다음과 같은 로그가 나타나야 한다:

   ```
   AGENT_STARTED received: agent_id=<uuid> hostname=<host> os=<goos>/<goarch> agent_version=0.1.0
   ```

5. script-agent에 `Ctrl+C` → hub 콘솔에:

   ```
   AGENT_STOPPED received: agent_id=<uuid> reason=interrupt
   ```

6. script-agent가 살아있는 동안 hub 콘솔에는 10초 간격(spec §5.4.1)으로
   heartbeat이 silently 누적된다 — 로그 레벨 DEBUG에서만 보이지만,
   `HeartbeatLatestMap`과 `AgentRegistry.lastSeen`이 갱신되고 있다.

7. Schedule 등록 → commands 발행 → script-agent 실행 → result-topic-job/log 수신의
   종단 시연:

   **브라우저(권장)**: `http://localhost:8080/`에서 등록 폼으로 1분 간격
   SCRIPT_JOB 등록. 같은 페이지에서 매 분 새 command와 결과가 패널에 누적되는
   걸 확인.

   **REST**:

   ```powershell
   curl -X POST http://localhost:8080/schedules `
        -H "Content-Type: application/json" `
        -d '{
          "job_type": "SCRIPT_JOB",
          "target_agent_id": "<위 AGENT_STARTED 로그의 agent_id>",
          "cron": "0 * * * * ?",
          "spec": {
            "script_path": "echo",
            "args": ["hello from hub"],
            "timeout_seconds": 5,
            "output_cap_bytes": 4096
          }
        }'
   ```

   매 분 hub 콘솔에:

   ```
   COMMAND sent: execution_id=<uuid> target_agent=<uuid> job_type=SCRIPT_JOB valid_until=<+54s> ...
   JOB_RESULT received: execution_id=<uuid> agent_id=<uuid> job_type=SCRIPT_JOB status=SUCCESS
   JOB_EXECUTED received: ...
   ```

## 패키지 구조

```
com.monitoring.hub
├── HubApplication            # 부팅 엔트리포인트
├── config                    # AppProperties, KafkaConfig
├── domain
│   ├── audit                 # AuditEvent + Actor/Target + enum
│   ├── job                   # JobResult + ScriptResult/LogResult + JobType/JobStatus
│   │                         # + JobDefinition + ScheduleDefinition
│   ├── command               # Command (command-topic 페이로드)
│   ├── heartbeat             # HeartbeatState
│   └── agent                 # AgentInfo + AgentState
├── store                     # AuditRingBuffer, JobResultRingBuffer, CommandRingBuffer,
│                             # HeartbeatLatestMap, AgentRegistry, JobRegistry,
│                             # ScheduleRegistry (in-memory)
├── ingest
│   ├── audit                 # AuditConsumer (@KafkaListener)
│   ├── jobresult             # JobResultConsumer
│   └── heartbeat             # HeartbeatConsumer + HeartbeatOtlpDecoder (OTLP protobuf 디코드)
├── producer                  # CommandPublisher (KafkaTemplate + envelope 헤더)
├── scheduler                 # ScheduleService (Quartz 등록), ScheduleTriggerJob
├── api                       # HealthController, ScheduleController,
│                             # CommandHistoryController, ScheduleRegistrationRequest
└── web                       # UiController (Thymeleaf), ScheduleFormRequest

src/main/resources/templates/
└── index.html                # 데모 단일 페이지 — 모든 in-memory state 노출
```

본개발은 PG/OpenSearch 영속 + JWT/Knox 인증 + LEGO+WebSocket 화면으로 전환
(spec §7 ADR 후보 리스트).

## 라이선스 / 메타

데모 walking skeleton. 단발 시연이 아니라 본개발의 reference implementation을
의도한다.
