#!/usr/bin/env bash
# pre-write-guard.sh — hub PreToolUse 게이트 (Git Bash 전용)
# 목적: ground truth/문서/형제 repo로의 쓰기를 호출 "전"에 차단한다(전 sub-agent 공통).
#   - permissions.deny(Write/Edit)는 docs/**·../monitoring-meta/ 루트는 막지만,
#     additionalDirectories에 올린 ../monitoring-meta/docs·handoff 하위는 write-grant가 deny를
#     무력화해 뚫린다(2026-05-29 dry-run 확인). 이 hook이 그 구멍을 닫는다.
#   - .claude는 의도적으로 보호 대상에서 제외한다(자동화 설정은 사람이 관리 가능해야 하며,
#     permissions.deny로 막으면 설정 관리까지 하드락되는 문제가 있었다 — 2026-05-29 결정).
#     hook은 Write/Edit/NotebookEdit 툴만 막는다.
# 판정 대상 경로(절대경로 정규화 후 하위 포함):
#   1) <repo>/docs   2) <repo>/../monitoring-meta   3) <repo>/../script-agent
# JSON 파싱 = python (이 환경에 jq 미설치). 출력 = PreToolUse permissionDecision JSON.
set -euo pipefail

INPUT="$(cat)"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

# stdin(JSON 페이로드)은 먼저 고정한 뒤 python으로 넘긴다. REPO_ROOT는 argv로 전달.
printf "%s" "$INPUT" | python -c '
import sys, json, os
# Windows python 기본 stdout(cp949)에서 한글/em-dash 크래시 방지
sys.stdout.reconfigure(encoding="utf-8")

def norm(p):
    return os.path.normcase(os.path.abspath(p))

repo = sys.argv[1]
forbidden = [
    norm(os.path.join(repo, "docs")),
    norm(os.path.join(repo, "..", "monitoring-meta")),
    norm(os.path.join(repo, "..", "script-agent")),
]

try:
    data = json.loads(sys.stdin.read())
except Exception:
    # 파싱 실패 시 이 게이트는 판단하지 않는다(차단하지 않음)
    sys.exit(0)

ti = data.get("tool_input") or {}
fp = ti.get("file_path") or ti.get("notebook_path") or ti.get("path") or ""
if not fp:
    sys.exit(0)

target = norm(fp)

def under(child, parent):
    return child == parent or child.startswith(parent + os.sep)

for root in forbidden:
    if under(target, root):
        reason = "쓰기 금지 경로입니다(ground truth/문서/형제 repo 보호 — pre-write-guard): " + fp
        print(json.dumps({
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": reason,
            }
        }, ensure_ascii=False))
        sys.exit(0)

# 허용: 아무 것도 출력하지 않고 통과
sys.exit(0)
' "$REPO_ROOT"
