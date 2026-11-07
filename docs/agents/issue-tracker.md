# Issue tracker: Local Markdown

이 문서의 경로는 저장소 루트 기준입니다.

## 저장 위치

- 기능별 명세: `.scratch/<feature-slug>/spec.md`
- 구현 이슈: `.scratch/<feature-slug>/issues/<NN>-<slug>.md`
- 이슈 번호는 기능별 `01`부터 시작하며, 이슈마다 파일 하나를 사용합니다.
- triage 상태는 파일 상단의 `Status:`에 기록합니다.
  역할 문자열은 `docs/agents/triage-labels.md`를 따릅니다.
- 의견과 진행 이력은 하단 `## Comments`에 추가합니다.

## 스킬에서 사용하는 작업

- “publish to the issue tracker”: 해당 기능 디렉터리에 명세 또는 개별 이슈 파일을 작성합니다.
- “fetch the relevant ticket”: 전달받은 경로를 읽습니다.
  번호만 받으면 기능 디렉터리와 함께 식별하고, 중복 시 대상을 확인합니다.
- 기능 이슈에는 관련 FR 및 CAP/EXT ID, 출처 버전, 수용 기준,
  API 매핑·권한·부작용 검증, 미검증 항목을 기록합니다.
  문서·도구 설정 작업은 제품 기능 ID가 적용되지 않는 이유를 기록합니다.

## Wayfinder

- 탐색 지도: `.scratch/<effort>/map.md`
  (`Notes`, `Decisions-so-far`, `Fog`).
- 하위 이슈: `.scratch/<effort>/issues/<NN>-<slug>.md`.
- `Type:`은 `research`, `prototype`, `grilling`, `task` 중 하나입니다.
- Wayfinder 이슈의 실행 상태는 `Status: open`, `claimed`, `resolved`로 관리합니다.
  이는 triage 역할과 구분되는 탐색 워크플로입니다.
- 의존성은 `Blocked by: NN, NN`으로 기록합니다.
- 열린 미점유 이슈 중 모든 의존성이 resolved인 가장 낮은 번호를 선택합니다.
- 작업 전 claimed로 저장합니다.
- 해결 시 `## Answer`에 결과를 추가하고 resolved로 변경한 뒤,
  지도의 Decisions-so-far에 요약과 이슈 링크를 추가합니다.
