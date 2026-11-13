# Triage Labels

GitHub 이슈에 사용하는 기본 triage 라벨입니다. 이슈에는 해당 역할 라벨을 하나만 적용합니다. 라벨이 없으면 저장소에 생성한 뒤 적용합니다.

| 역할 | 라벨 | 의미 |
|---|---|---|
| needs-triage | needs-triage | 유지관리자의 평가 필요 |
| needs-info | needs-info | 추가 정보 대기 |
| ready-for-agent | ready-for-agent | 명세가 준비되어 에이전트 구현 가능 |
| ready-for-human | ready-for-human | 사람의 구현 필요 |
| wontfix | wontfix | 진행하지 않기로 결정 |

스킬이 triage 역할을 지시하면 위 라벨을 사용합니다.
작업을 시작할 때는 `wip` 라벨을 추가로 붙이고, 작업이 종료될 때 `wip` 라벨을 삭제합니다.
Wayfinder 실행 상태는 `issue-tracker.md`의 별도 규칙을 따릅니다.
