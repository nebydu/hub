# codex-gate.profile — hub 도메인 delta (monitoring-harness plugin 주입값)
#
# 이 파일은 hub가 monitoring-harness 플러그인의 공통 codex-gate 골격에 주입하는
# 도메인 delta다. 실행 로직(골격)은 플러그인이 보유하며 여기에는 복제하지 않는다.
# 플러그인은 이 파일을 convention 경로(${CLAUDE_PROJECT_DIR}/.claude/codex-gate.profile)에서
# 자동 발견하여 로드한다(별도 설정 불필요 — userConfig/per-user config 의존 없음).
#
# 동등성 기준: 이 값들은 기존 .claude/hooks/codex-gate.sh 동작을 그대로 재현한다.

# ── 트리거 경로 (hub Java/Spring 비즈니스 코드) ───────────────────────────
CODEX_GATE_TRIGGER_GLOBS=( "src/main/*" "src/test/*" "pom.xml" )

# ── 스킵 경로 (트리거보다 우선; 비코드 산출물) ────────────────────────────
CODEX_GATE_SKIP_GLOBS=( ".claude/*" "docs/*" "analysis/*" )

# ── 코드 변경 없음일 때 안내 메시지 ───────────────────────────────────────
CODEX_GATE_SKIP_MSG="[codex-gate] SKIP: src/main, src/test, pom.xml 변경이 없어 Codex 검증을 건너뜁니다."

# ── Codex 리뷰 프롬프트 (hub 도메인 전체) ─────────────────────────────────
CODEX_GATE_PROMPT="hub(Java/Spring Boot) 코드 변경 리뷰. 통합본 v0.9(../monitoring-meta/docs/통합본_v0_9.md)가 전체 제품/아키텍처 최상위 기준이다. 다음을 read-only로만 검토하고 codex-schema.json 형식의 JSON으로만 응답하라: (1) 통합본 v0.9 기준 전체 제품/아키텍처 방향 위반 (2) diff 자체로 입증되는 위상 위반(예: Phase 0 회귀를 유발하는 Phase 1 동작 선반영). 단, '이 변경이 Phase 0 유지인지 Phase 1+ 선반영인지'의 의도 분류는 handoff가 결정하며 이 gate 입력에 handoff가 없으므로, 분류가 모호하다거나 위상 분류 확인이 필요하다는 것 자체를 fail 사유로 삼지 마라 (3) Phase 0 데모 spec ../monitoring-meta/docs/phase0-snapshot/monitoring-demo-message-spec-v0.2.1.md 회귀 (4) envelope/kafka-payloads 메시징 계약 위반 (5) 통합본 v0.9 §7.2 β 구조의 메인 BE(Auth/Job/Approval/Knox/Validation/Agent State)와 분리 대상(rule-engine/Script Result/Alert·Incident/Notification/Metric Ingest) 사이 모듈 경계 위반 (6) 버그·회귀 가능성 (7) 테스트 누락. 참고: handoff 작업 spec 정합성과 '작업 위상 의도 분류'는 이 gate가 아니라 analyzer/spec-guardian이 담당하므로 여기서 검사하지 않는다(이 gate 입력에는 handoff가 포함되지 않음). 따라서 fail(verdict)은 diff로 입증되는 회귀/계약위반/버그/테스트누락에 한정하고, handoff 부재로 판단 불가한 위상 분류 확인 요청은 fail이 아니라 무시한다. 빌드 검증 기준은 'mvn package'(테스트 포함), 'mvn -DskipTests package'(빌드만). 위상 주의: 통합본의 Phase 1+ 목표를 Phase 0 코드에 무조건 강제하지 말 것. envelope.md는 Phase 1 목표이고 hub 코드는 Phase 0 위상이므로, envelope consumer측 동작(중복검사/trace복원) 미구현은 위반이 아니다."

# ── escalation 임계: hub 현행과 동일 ──────────────────────────────────────
CODEX_GATE_FAIL_LIMIT=3
CODEX_GATE_PARSE_FAIL_LIMIT=2
