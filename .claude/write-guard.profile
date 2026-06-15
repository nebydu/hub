# write-guard.profile — hub consumer 델타 (monitoring-harness plugin PreToolUse 가드)
#
# 이 파일의 "존재" 자체가 opt-in 신호다. 없으면 플러그인 가드(write-guard-entry.sh)가
# hub를 비소비자로 보고 exit 0(무가드 통과)하므로, 이 파일을 지우면 보호 공백이 생긴다.
#
# 차단은 플러그인 공통 골격(write-guard-core.sh)의 유도 규칙으로 충분하다(exit 2 정본):
#   - 자기 docs/ + 모든 형제 repo(monitoring-meta / script-agent / infra…) + ground truth
#     교차쓰기를 호출 전에 차단
#   - 자기 repo 코드와 .claude/ 는 허용(자동화 설정은 사람이 관리 가능해야 함)
# 따라서 repo별 추가 차단 경로 지정은 불필요하다(형제는 유도 규칙 ③이 이미 전부 커버).
#
# 추가로 좁힐 경로가 생기면 아래에 지정한다(add-only — 유도 규칙을 약화할 수는 없음):
# WRITE_GUARD_BLOCK_PATHS=( "some/extra/path" )
