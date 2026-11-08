# Issue tracker: GitHub Issues

이슈와 작업 명세의 단일 원천은 [LibertyWiki GitHub Issues](https://github.com/fregmented/LibertyWiki/issues)입니다. 이슈 번호와 URL로 항목을 식별합니다. 제품 설계와 API 계약은 저장소의 기존 문서를 참조합니다.

## 스킬에서 사용하는 작업

- “publish to the issue tracker”: 기능 명세 또는 구현 작업을 GitHub 이슈로 작성하고 관련 이슈와 설계 문서를 링크합니다.
- “fetch the relevant ticket”: 이슈 번호 또는 URL로 본문, 상태, 라벨, 담당자와 댓글을 확인합니다.
- 기능 이슈에는 관련 FR 및 CAP/EXT ID, 출처 버전, 수용 기준, API 매핑·권한·부작용 검증, 미검증 항목을 기록합니다. 문서·도구 설정 작업은 제품 기능 ID가 적용되지 않는 이유를 기록합니다.
- triage 역할은 `docs/agents/triage-labels.md`를 따릅니다. 진행 상황과 결정은 이슈 본문 또는 댓글에 기록합니다.

## Wayfinder

- 탐색 지도는 GitHub 이슈에 `Notes`, `Decisions-so-far`, `Fog`를 기록하고 하위 이슈를 링크합니다.
- 하위 이슈의 본문에 `Type: research`, `prototype`, `grilling`, `task` 중 하나와 의존 이슈 번호를 기록합니다.
- 실행 상태는 열린 미배정 이슈를 `open`, 열린 담당 이슈를 `claimed`, 닫힌 이슈를 `resolved`로 취급합니다. 이는 triage 역할과 별개입니다.
- 열린 미배정 이슈 중 모든 의존 이슈가 닫힌 가장 낮은 번호를 선택하고 작업 전에 담당자를 지정합니다.
- 해결 시 이슈에 `Answer`를 기록하고 닫은 뒤, 지도 이슈의 `Decisions-so-far`에 요약과 이슈 링크를 추가합니다.
