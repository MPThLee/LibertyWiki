# Domain Docs

이 문서의 경로는 저장소 루트 기준입니다.
문서 구성은 단일 컨텍스트(single-context)입니다.

## 탐색 전 읽기

루트 AGENTS.md의 순서에 따라 기존 설계 기준선을 읽습니다.

1. `wiki_design/source/00_README.md`
2. `wiki_design/source/01_PRODUCT_PLAN.md`
3. `wiki_design/source/02_REQUIREMENTS.md`
4. `wiki_design/source/14_DUAL_API_CONTRACT.md`
5. `wiki_design/source/10_COMPATIBILITY.md`
6. `wiki_design/source/04_ARCHITECTURE.md`
7. `wiki_design/source/09_RUNTIME_ADR.md`
8. 해당 기능의 설계문서 및 `wiki_design/specs/` 계약·카탈로그

추가로 루트 `GLOSSARY.md`와 작업 영역에 관련된 `docs/adr/` 결정을 읽습니다.
향후 `GLOSSARY-MAP.md`가 생기면 그 지도를 통해 관련 용어집을 선택합니다.
용어집·ADR 디렉터리가 아직 없으면 그대로 진행하고,
용어·결정이 정리될 때 domain-modeling 스킬로 생성합니다.

## 기존 결정과 새 결정

기존 ADR은 `wiki_design/source/04_ARCHITECTURE.md`와
`wiki_design/source/09_RUNTIME_ADR.md`에 있습니다.
기존 결정은 보존하며, 새 ADR은 `docs/adr/`에 기존 ID와 겹치지 않게 기록합니다.
기존 ADR을 대체할 때는 대상과 변경 근거를 명시합니다.
기존 결정과 충돌하는 제안은 충돌을 드러내고 근거를 설명합니다.

## 용어와 계약

용어집이 있으면 이슈·설계·테스트에서 그 용어를 사용합니다.
미정 용어는 관련 설계문서를 참고하고 필요한 결정을 domain-modeling 대상으로 기록합니다.
설계문서 사이의 충돌은 루트 AGENTS.md의 불변 조건과 최신 승인 설계에 따라 다룹니다.
관찰되지 않은 원본 동작은 compat_gap과 검증 대상으로 기록합니다.

API 매핑은 `wiki_design/specs/api_operation_registry.csv`,
Native 계약은 `wiki_design/specs/openapi.yaml`을 참조합니다.
설계 초안과 검증된 구현·테스트 결과를 구분합니다.
검증 결과·작업 로그·중간 조사 파일은 저장소에 추가하지 않습니다. 필요한 설계 결론은 설계문서나 ADR에 반영하고, 실행 결과는 완료 응답·커밋 본문 또는 외부 CI artifact에 보고합니다.
