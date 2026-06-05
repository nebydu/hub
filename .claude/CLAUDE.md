# .claude/CLAUDE.md — hub 자동화 오케스트레이션 규칙

이 파일은 hub repo의 `.claude/` sub-agent 자동화가 따르는 운영 규칙이다.
hub 루트의 `CLAUDE.md` / `AGENTS.md`(Claude Code ↔ Codex 이중 에이전트 규칙)와 별개로, **sub-agent 파이프라인 동작**을 규정한다.

## 0. 언어 규칙
- 답변 / 문서 / 주석은 **한국어**.
- 변수 / 함수 / 클래스 / 파일명 등 식별자는 **영어**(Java 표준 컨벤션).

## 1. 위상 구분 경고 (가장 중요)
- **envelope spec(`../monitoring-meta/docs/envelope.md`)이 monitoring-meta에 박혔지만, hub 코드는 여전히 Phase 0 데모 spec(v0.2.1) 위상에 있다.**
- "envelope.md가 정의됐다 ≠ hub 코드가 envelope을 따른다." 이 자동화는 **이 위상 차이를 인지한 채로** 작동한다.
- envelope 헤더 발행(x-message-id/x-message-version/x-source/x-trace-id)은 Phase 0 spec v0.2.1에 이미 포함된 동작이므로 정상이다. 반면 envelope.md의 Phase 1 consumer측 동작(x-message-id 중복 검사, x-trace-id trace 복원 등)이 hub에 없는 것도 **Phase 0 위상에서는 정상**이며 위반이 아니다.
- **운영 원칙**: "통합본 우선 + Phase 분류 + 데모 회귀 방지". 통합본 v0.9를 방향 판단의 최상위 기준으로 두되, 통합본의 Phase 1+ 목표를 현재 Phase 0 코드에 무조건 강제하지 않는다(불필요한 fail 방지). 작업마다 Phase 0 유지인지 Phase 1+ 선반영인지 먼저 분류한다.

## 2. ground truth 우선순위
> 방향 판단의 최상위 기준은 통합본이고, 데모 spec은 Phase 0 회귀 방지 가드로 역할을 축소한다.
1. **통합본 v0.9** (`../monitoring-meta/docs/통합본_v0_9.md`) — 전체 제품 요구·아키텍처·모듈 경계·Phase 방향의 최상위 판단 기준
2. **작업 spec** (`../monitoring-meta/handoff/<work-id>-hub.md`) — 이번 작업에서 hub가 구현할 구체 입력
3. **코드** (현재 hub의 실제 동작·제약의 사실)
4. **데모 spec v0.2.1** (`../monitoring-meta/docs/phase0-snapshot/monitoring-demo-message-spec-v0.2.1.md`) — Phase 0 회귀 방지 가드. 통합본과 충돌 시 현재 Phase에서 어떻게 적용할지 판단
5. **envelope + kafka-payloads** (`../monitoring-meta/docs/`) — 메시징 세부 규약(Phase 1+ 도달 목표)

> **근거(provenance)**: 셋업 원 브리프의 초기 우선순위는 "코드 → 데모 spec v0.2.1 → 통합본"이었으나, 이 repo가 커밋 `9b7288f`(2026-05-29)로 통합본 중심 재조정을 단행했다. 형제 repo `script-agent`도 폴리레포 오케스트레이션 일관성을 위해 같은 순서로 정렬했다 — **사용자 명시 승인(2026-05-29)**. 원 브리프 acceptance 기준을 의도적으로 supersede한 것이며, 데모 spec v0.2.1은 버린 것이 아니라 #4 Phase 0 회귀 가드로 유지된다.

## 3. 작업 입력 형식
- 작업 spec은 **`../monitoring-meta/handoff/<work-id>-hub.md`** 한 곳에서만 받는다.
- 다른 위치(채팅 임의 지시, 다른 디렉터리 파일)에서 작업 spec을 받아 파이프라인을 시작하지 않는다.
- **work-id 바인딩 계약**: 파이프라인 시작 시 `<work-id>`를 **명시적으로 확정**하고, analyzer → implementer → tester → reviewer/spec-guardian 모든 호출에 **동일한 work-id를 전달**한다. work-id가 불명확하면 대화 맥락으로 추론하지 말고 멈춰 사람에게 확인한다. (Stop hook `codex-gate.sh`는 git diff 기반 경량 게이트라 work-id를 받지 않으며 handoff 정합성을 검사하지 않는다 — handoff 검사는 analyzer/spec-guardian 책임.)

## 4. 금지 사항
- **단계 점프 금지**: analyzer 산출물 없이 implementer로 가는 등 표준 호출 순서를 건너뛰지 않는다.
- **monitoring-meta는 read-only**: `../monitoring-meta/`의 통합본 v0.9, envelope.md, kafka-payloads.md를 hub repo에서 직접 수정하지 않는다.
- **hub AGENTS.md 자동 갱신 금지**: 셋업 이후 사람이 수동 처리한다.

## 5. 표준 호출 순서와 재시도 한도
```
analyzer → implementer → tester → (병렬) reviewer + spec-guardian → (필요시) refactorer → Stop 시 Codex
```
- analyzer 산출물에 **사람 결정이 필요한 미결정 사안**이 있으면 즉시 멈추고 **사람을 호출**한다. implementer를 호출하지 않는다.
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
| analyzer | `docs/`, `analysis/` | 종합 분석, 미결정 사안 발견 시 사람 호출 게이트 |
| implementer | `src/main/**`, `src/test/**`, `pom.xml`, 리소스 | 코드 구현, 재시도 3회 한도 |
| tester | `src/test/**`만 | 회귀 1차 책임, 프로덕션 코드 수정 금지 |
| reviewer | 없음 | 모듈 경계(§7.2 β) critical / 강결합 warning, 보고서만 |
| spec-guardian | 없음 | 위상 분류 + envelope 헤더 규약, 보고서만 |
| refactorer | `src/main/**`, `src/test/**` | 행위 보존 리팩터링, reviewer 권고 시에만 |

## 8. Stop hook (Codex 게이트)
- `hooks/codex-gate.sh`가 Stop 이벤트에서 작동한다.
- **실행 방식(Windows 주의)**: `settings.json`은 exec form으로 `.claude/hooks/git-bash.cmd` shim을 호출한다. 이 shim은 `%ProgramFiles%\Git\bin\bash.exe`를 동적으로 expand해 실행한다. 표준 Git for Windows 설치(기본 경로)에서 그대로 동작하며, 비표준 설치는 shim 한 줄만 수정한다. **PATH 의존 없음** — Windows PATH상 `bash`가 WSL(`C:\Windows\System32\bash.exe`)로 먼저 잡혀도 영향받지 않는다.
- **발화 대상**: `src/main/**`, `src/test/**`, `pom.xml` 변경 시 Codex(`codex exec --sandbox read-only`) 검토 호출.
- **스킵 대상**: `.claude/**`, `docs/**`, `analysis/**` 등 비코드 산출물만 변경된 경우.
- **안전장치**: `stop_hook_active` 무한루프 가드, FAIL 3회 초과 시 강제 통과, 파싱 2회 연속 실패 시 강제 통과(모두 escalation 로그 기록).
- 상태 파일(`.codex-gate-*`, `.codex-last-message.json` 등)은 `.gitignore`에 등록되어 추적되지 않는다.
