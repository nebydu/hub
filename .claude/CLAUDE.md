# .claude/CLAUDE.md — hub 자동화 오케스트레이션 규칙

이 파일은 hub repo의 `.claude/` sub-agent 자동화가 따르는 운영 규칙이다.
hub 루트의 `CLAUDE.md` / `AGENTS.md`(Claude Code ↔ Codex 이중 에이전트 규칙)와 별개로, **sub-agent 파이프라인 동작**을 규정한다.

## 0. 언어 규칙
- 답변 / 문서 / 주석은 **한국어**.
- 변수 / 함수 / 클래스 / 파일명 등 식별자는 **영어**(Java 표준 컨벤션).

## 1. 단계 구분 경고 (가장 중요)
- **envelope spec(`../monitoring-meta/docs/envelope.md`)이 monitoring-meta에 박혔지만, hub 코드는 여전히 Phase 0 데모 spec(v0.2.1) 단계에 있다.**
- "envelope.md가 정의됐다 ≠ hub 코드가 envelope을 따른다." 이 자동화는 **이 단계 차이를 인지한 채로** 작동한다.
- envelope 헤더 발행(x-message-id/x-message-version/x-source/x-trace-id)은 Phase 0 spec v0.2.1에 이미 포함된 동작이므로 정상이다. 반면 envelope.md의 Phase 1 consumer측 동작(x-message-id 중복 검사, x-trace-id trace 복원 등)이 hub에 없는 것도 **Phase 0 단계에서는 정상**이며 위반이 아니다.
- **운영 원칙**: "통합본 우선 + Phase 분류 + 데모 회귀 방지". 통합본을 방향 판단의 최상위 기준으로 두되, 통합본의 Phase 1+ 목표를 현재 Phase 0 코드에 무조건 강제하지 않는다(불필요한 fail 방지). 작업마다 Phase 0 유지인지 Phase 1+ 선반영인지 먼저 분류한다.

## 2. ground truth 우선순위
> 방향 판단의 최상위 기준은 통합본이고, 데모 spec은 Phase 0 회귀 방지 가드로 역할을 축소한다.
1. **통합본** (`../monitoring-meta/docs/master-design.md`) — 전체 제품 요구·아키텍처·모듈 경계·Phase 방향의 최상위 판단 기준
2. **작업 spec** (`../monitoring-meta/handoff/<work-id>/<work-id>-hub.md`) — 이번 작업에서 hub가 구현할 구체 입력
3. **코드** (현재 hub의 실제 동작·제약의 사실)
4. **데모 spec v0.2.1** (`../monitoring-meta/docs/phase0-snapshot/monitoring-demo-message-spec-v0.2.1.md`) — Phase 0 회귀 방지 가드. 통합본과 충돌 시 현재 Phase에서 어떻게 적용할지 판단
5. **envelope + kafka-payloads** (`../monitoring-meta/docs/`) — 메시징 세부 규약(Phase 1+ 도달 목표)

> **근거(provenance)**: 셋업 원 브리프의 초기 우선순위는 "코드 → 데모 spec v0.2.1 → 통합본"이었으나, 이 repo가 커밋 `9b7288f`(2026-05-29)로 통합본 중심 재조정을 단행했다. 형제 repo `script-agent`도 폴리레포 오케스트레이션 일관성을 위해 같은 순서로 정렬했다 — **사용자 명시 승인(2026-05-29)**. 원 브리프 acceptance 기준을 의도적으로 supersede한 것이며, 데모 spec v0.2.1은 버린 것이 아니라 #4 Phase 0 회귀 가드로 유지된다.

## 3. 작업 입력 형식
- 작업 spec은 **`../monitoring-meta/handoff/<work-id>/<work-id>-hub.md`** 한 곳에서만 받는다.
- 다른 위치(채팅 임의 지시, 다른 디렉터리 파일)에서 작업 spec을 받아 파이프라인을 시작하지 않는다.
- **work-id 바인딩 계약**: 파이프라인 시작 시 `<work-id>`를 **명시적으로 확정**하고, analyzer → implementer → tester → reviewer/spec-guardian 모든 호출에 **동일한 work-id를 전달**한다. work-id가 불명확하면 대화 맥락으로 추론하지 말고 멈춰 사람에게 확인한다. (plugin Stop hook(Codex 게이트)은 git diff 기반 경량 게이트라 work-id를 받지 않으며 handoff 일관성을 검사하지 않는다 — handoff 검사는 analyzer/spec-guardian 책임.)

## 4. 금지 사항
- **단계 점프 금지**: analyzer 산출물 없이 implementer로 가는 등 표준 호출 순서를 건너뛰지 않는다.
- **monitoring-meta는 read-only**: `../monitoring-meta/`의 통합본(master-design.md), envelope.md, kafka-payloads.md를 hub repo에서 직접 수정하지 않는다.
- **hub AGENTS.md 자동 갱신 금지**: 셋업 이후 사람이 수동 처리한다.

## 5. 표준 호출 순서와 재시도 한도
```
analyzer → (proposal-review 체크포인트) → implementer → tester → (병렬) reviewer + spec-guardian → (필요시) refactorer → Stop 시 Codex
```
- analyzer 산출물에 **사람 결정이 필요한 미결정 사안**이 있으면 즉시 멈추고 **사람을 호출**한다. implementer를 호출하지 않는다.
- **proposal-review 체크포인트(implementer 전 1회)**: analyzer 산출물을 구현 제안으로 정리해 harness plugin의 proposal-review runner로 **1회** 검토한다. 이 검토는 **메인 세션이 직접 수행**한다(서브에이전트 아님). proposal은 **stdin**으로 전달하고, 결과는 `--out analysis/<work-id>/proposal-review.json`으로 저장한다.
  - verdict가 `approve`가 아니거나 `confidence: low` / `missing_context` 배열이 비어있지 않음(non-empty) / degraded(repo에 `.claude/proposal-review.profile` 없음)면 **implementer를 호출하지 않고 멈춰 사람에게 보고**한다.
  - **자동 수렴 loop 금지**: `revise`를 자동 반영해 재호출하지 않는다. `revise`/`block`은 사람이 중재한다.
  - 이 체크포인트는 기존 on-demand decision-review command(`/proposal-review`)를 handoff 파이프라인 표준 순서에 **1회 흡수**한 것이다. **Stop hook(codex-gate, §9)과는 합치지 않는다** — proposal-review는 변경 전 합의, codex-gate는 변경 후 게이트로 분리 유지(§4 scope·harness `docs/decisions/proposal-review-scope.md` 경계).
- **implementer 재시도는 작업 spec id 단위로 최대 3회.** 초과 시 사람 escalation.
- **reviewer / spec-guardian은 병렬 호출**하며, **둘 다 통과(critical 0)해야** 다음 단계로 넘어간다.
- **refactorer는 reviewer가 구조 개선을 권고한 경우에만** 호출하고, 행위 보존을 reviewer + tester가 확인한다.

## 6. sub-agent 결과 보고 스키마 (공통)
모든 sub-agent는 본문 끝에 아래 JSON을 출력하고, 그 뒤에 **"외부 surface"** 섹션(hub 외부 — monitoring-meta / script-agent / infra 파급 이슈 분류)을 둔다.
```json
{
  "status": "ok | blocked | failed",
  "outputs": ["생성/수정한 파일 경로"],
  "findings": ["발견 사항"],
  "blockers": ["사람 결정이 필요하거나 다음 단계를 막는 항목"],
  "next_action": "다음에 할 일 한 줄"
}
```

## 7. sub-agent 역할 / Write 권한 요약
| agent | Write 권한 | 핵심 |
|---|---|---|
| analyzer | `analysis/` | 종합 분석, 미결정 사안 발견 시 사람 호출 게이트 |
| implementer | `src/main/**`, `src/test/**`, `pom.xml`, 리소스 | 코드 구현, 재시도 3회 한도 |
| tester | `src/test/**`만 | 회귀 1차 책임, 프로덕션 코드 수정 금지 |
| reviewer | 없음 | 모듈 경계(§7.2 β) critical / 강결합 warning, 보고서만 |
| spec-guardian | 없음 | 단계 분류 + envelope 헤더 규약, 보고서만 |
| refactorer | `src/main/**`, `src/test/**` | 행위 보존 리팩터링, reviewer 권고 시에만 |

## 8. hub 핵심 불변식 (회귀 시 critical)
> `.claude`(codex-gate.profile·agents/*.md)와 통합본·envelope·코드에 흩어진 hub 불변식을 한곳에 모은 일급 참조다. 각 항목은 **출처를 명시**하며 회귀 시 reviewer / spec-guardian / Stop 게이트가 critical로 잡는다. 기준은 **Phase 0 현재 hub가 실제로 보장하는 동작**이다(Phase 1+ 목표는 §2 우선순위 #5).

### 8.1 envelope 헤더 4종 발행 (메시징 계약)
hub가 **발행하는** 메시지에 envelope 헤더 4종을 첨부한다. 현 Phase 0에서 hub가 발행하는 토픽은 **command-topic 하나**(`producer/CommandPublisher`)이며, 헤더 키 문자열의 단일 진실 지점은 `messaging/EnvelopeHeaders`다.
- **x-message-id**: UUIDv4 string, 메시지마다 새로 발급, 필수.
- **x-message-version**: 문자열 `"1"` 고정(정수 아님), payload major 호환 깨짐 시에만 증가, 필수.
- **x-source**: hub는 `"monitoring-be"` 고정(kebab-case), 필수.
- **x-trace-id**: null/blank이면 **헤더 자체를 생략**(빈 값 헤더 생성은 위반), 값 있으면 포함, 선택. 데모는 발행만·소비측 검사 없음(ADR #15).

출처: `producer/CommandPublisher.java:37-47,70-76`, `agents/spec-guardian.md`(critical 룰), envelope.md.
계약 정의상 헤더 4종 대상은 6 공통 토픽(command / result-topic-job / result-topic-log / audit / alert / notification)이며 spec-guardian이 회귀 가드로 검사한다. heartbeats-topic·metrics-topic은 OTLP 예외(envelope 4종 미적용 — warning만, critical 아님).

### 8.2 command-topic 메시지 키 = target_agent_id
command-topic 발행 시 Kafka 메시지 키 = `target_agent_id`(Agent 단위 ordering 보장). 통합본 6.8.2 키 정책.
출처: `producer/CommandPublisher.java:25,66`, `config/KafkaConfig.java:187`, envelope.md:101, 통합본 6.8.2.
주의: result-topic-job/log의 키는 `agent_id`로 **별개**다 — 이 토픽들은 Agent가 발행하고 hub는 소비만 하므로 hub 발행 불변식에 포함되지 않는다(통합본 §6.9 토픽표 line 667-668).

### 8.3 valid_until 계산
`valid_until = issued + (next - issued) * 0.9` (spec §5.1.3). nextFireAt이 null(트리거 마지막 fire)이면 데모 기본값 +60초.
출처: `scheduler/ScheduleTriggerJob.java:101,107-114`, `domain/command/Command.java:17-18`.

### 8.4 §7.2 β 모듈 경계 (구조 불변식)
통합본 §7.2 (β) "모듈러 모놀리스 + 메시지 처리 분리" 기준. **메인 BE**(Auth / Job / Approval / Knox / Validation / Agent State / Heartbeat Consumer / Audit Consumer / BE Query API)와 **분리 대상**(rule-engine × N / Script Result / Alert·Incident / Notification / Metric Ingest) 사이를 토픽(메시지)을 우회해 직접 결합(직접 DB 접근·직접 클래스 호출)하면 **critical**. 분리 대상 간 통신은 토픽 기반만 허용. 현재 단일 Spring Boot 모놀리스에서는 "패키지 수준 분리 가능성 유지"로 해석한다.
출처: `agents/reviewer.md`(강제 룰), 통합본 §7.2 line 1985-1987.

### 8.5 hub 메시지 surface (참고)
- **발행**: command-topic (`CommandPublisher`, 유일).
- **소비**: audit-topic / result-topic-job / result-topic-log / heartbeats-topic.
출처: hub `src/main/java` grep(@KafkaListener / KafkaTemplate).

### [확인 필요] (출처 불명·모호 — 추측으로 채우지 않음)
- hub가 향후 audit/alert/notification을 **직접 발행**하게 될 때 8.1 헤더 규약 적용 여부 — 현 Phase 0 코드엔 해당 producer 없음(command-topic만 발행). 계약 정의상 6 토픽 대상이나 hub 발행 코드는 미존재.
- `x-source` 알려진 값 집합 불일치 — spec-guardian은 `script-agent / monitoring-be / otel-collector` 3개, 통합본 line 1654는 `+infra-agent / +rule-engine` 포함 5개. hub 고정값 `monitoring-be`는 영향 없으나 집합 정의 SoT 확정 필요.
- 데모 spec v0.2.1의 추가 동작 불변식(Agent의 `target_agent_id` 불일치 시 무시, dispatcher 발행 순서 등) 중 일급 불변식으로 승격할 항목 — 데모 spec 원문 재확인 후 선별 필요.

## 9. Stop hook (Codex 게이트)
- Stop hook은 `settings.json`의 `enabledPlugins`에 켜진 **`harness@monitoring` 플러그인**이 제공한다. `settings.json`에는 더 이상 Stop hook 블록도, native `hooks/codex-gate.sh`도 없다(plugin 전환 때 삭제). PreToolUse 쓰기 가드(native `hooks/pre-write-guard.sh`)도 plugin으로 위임하며 삭제했고, hub consumer 델타는 `.claude/write-guard.profile`로 옮겼다 — 그 결과 `settings.json`에는 `hooks` 블록 자체가 없다.
- **동작 구조**: plugin Stop hook이 hub의 도메인 delta 파일 `.claude/codex-gate.profile`을 convention 경로(`${CLAUDE_PROJECT_DIR}/.claude/codex-gate.profile`)에서 자동 로드해, plugin이 보유한 공통 게이트 골격(`codex-gate-core.sh`)을 실행한다. 실행 로직은 plugin에 있고, hub별 값(트리거/스킵 대상·리뷰 프롬프트·escalation 임계)만 profile이 주입한다.
- **발화/스킵 대상**: `.claude/codex-gate.profile`의 `CODEX_GATE_TRIGGER_GLOBS`(트리거)·`CODEX_GATE_SKIP_GLOBS`(스킵)가 단일 진실이다. 트리거 경로(hub Java/Spring 비즈니스 코드 + `pom.xml`) 변경 시 Codex(`codex exec --sandbox read-only`) 검토를 호출하고, 스킵 경로(비코드 산출물)만 변경되면 건너뛴다. 이 문서는 glob 리터럴을 복제하지 않는다 — profile을 보라.
- **안전장치**: `stop_hook_active` 무한루프 가드, profile의 `CODEX_GATE_FAIL_LIMIT`(FAIL 누적 한도) 초과 시 강제 통과, `CODEX_GATE_PARSE_FAIL_LIMIT`(파싱 연속 실패 한도) 초과 시 강제 통과(모두 escalation 로그 기록).
- **상태/로그 위치**: 런타임 상태·로그(`codex-gate.log`, `.codex-gate-state`, `.codex-gate-issues.txt`, `.codex-last-message.json`, `codex-gate-escalation.log`)는 plugin data 디렉터리 `~/.claude/plugins/data/harness-monitoring/<repo>/`에 기록되며 hub repo 밖이라 추적되지 않는다. `.gitignore`의 `.claude/.codex-gate-*` 항목은 plugin 전환 전 native hook용 legacy 가드다(무해).
