# LibertyWiki

MediaWiki 1.46.2의 코어·기본 배포 확장·스킨 기능을 독립 구현하는 AGPL-3.0-only 위키 프로젝트입니다. 현재 저장소는 **승인된 설계와 계약 초안 단계**이며 실행 엔진이나 호환성 시험 완료를 뜻하지 않습니다.

현재 설계는 [문서 안내](wiki_design/source/00_README.md)에서 시작합니다. [제품 기획](wiki_design/source/01_PRODUCT_PLAN.md), [요구사항](wiki_design/source/02_REQUIREMENTS.md), [두 API 계약](wiki_design/source/14_DUAL_API_CONTRACT.md), [아키텍처](wiki_design/source/04_ARCHITECTURE.md), [결정 기록](docs/adr/), [용어집](GLOSSARY.md)을 순서대로 참고합니다.

2026-11-08에 확정한 [1.0 검증 프로파일](wiki_design/specs/release_profile_1_0.json)은 일반 문서 10만을 포함한 전체 문서 20만·등록 사용자 1만·DAU 100을 목표로 합니다. 리브레 위키는 이전 검증 대상이며 사이트의 모든 고유 기능이 구현 범위는 아닙니다. ENWIKI 인증과 실제 위키 전환 승인은 제품 출시 승인과 별도입니다.

LibertyWiki 실행물과 Docker 이미지는 ARM64·x86-64를 지원하도록 개발합니다. 외부 DBMS·OpenSearch·Redis 등 인프라의 실행환경은 프로젝트 실행환경과 독립적이며, 오케스트레이션은 사용자가 선택합니다.

저장소에는 설계문서·계약, 이슈트래커, 소스코드와 필요한 개발 설정만 보관합니다. 검증 결과·실행 로그·중간파일·생성된 통합본은 저장소에 추가하지 않습니다. 현재 결정은 `wiki_design/source/`, `wiki_design/specs/`, `docs/adr/`를 따릅니다.
