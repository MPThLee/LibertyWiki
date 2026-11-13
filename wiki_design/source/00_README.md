# LibertyWiki — MediaWiki 1.46.2 호환 독립 구현 프로젝트

**상태:** 설계 기준선(구현·시험 이전) / **작성일:** 2026-11-07 / **언어:** 한국어 / **라이선스:** AGPL-3.0-only(프로젝트 결정) / **범위:** 코어 + 1.46.2 기본 배포 확장 34종·스킨 5종

**승인 결정 반영일:** 2026-11-08. 현재 승인 기준은 이 `source/` 문서, `../specs/` 계약 초안과 [ADR](../../docs/adr/)입니다. 검증 결과·실행 로그·중간파일·생성된 통합본은 Git에 보관하지 않습니다.

## 단일한 제품 약속

MediaWiki 1.46.2의 코어와 해당 배포판에 동봉되는 기본 확장의 **기능, MediaWiki Compatible API·Native RESTful API 동시 제공 및 Wikitext 호환성을 합격 조건으로 하는 순수 독립 구현**입니다. **기존 MediaWiki/Parsoid/확장/스킨 코드의 복사·번역·포팅·내장·링킹·변형 재사용을 금지**합니다. API/Wikitext 호환은 소스 호환 또는 PHP 확장 ABI 호환을 뜻하지 않습니다. 기존 PHP 확장은 그대로 설치해 실행하지 않습니다. 이 프로젝트 자체가 제공하는 문서화된 확장 SPI/API에 의해 작성된 **신규 확장**을 설치·실행합니다.

## 결정 사항

| 주제 | 결정 |
|---|---|
| 라이선스 | AGPL-3.0-only, SaaS·자가호스팅 모두 소스 제공·고지 체계 적용. 제3자 의존성 라이선스 검토 필수 |
| 구현 독립성 | MediaWiki와 번들 확장은 **기능 설명·프로토콜·공개 동작**을 참조하지만 원본 코드는 전면 재사용 금지 |
| 호환성 기준 | MediaWiki 1.46.2 구성별 Action API(`api.php`)/MediaWiki REST API(`rest.php`)와 Wikitext+ParserFunctions+Scribunto+roundtrip의 동작 일치; 동시에 모든 도메인 기능에 Native RESTful API 제공 |
| 위키 공개 정책 | 공개/비공개, 익명 읽기·편집·계정 생성·업로드 등 MediaWiki 수준의 관리자 설정 제공 |
| 플랫폼 | 웹 UI만, 반응형 데스크톱/모바일 브라우저. 네이티브 앱은 범위 밖 |
| 규모 | 영어 위키백과급 문서·이력·검색·트래픽을 지원하는 **수평 확장 가능한 아키텍처** 및 검증 프로파일 |
| 파일 | LOCAL 및 S3-compatible 저장·서빙을 모두 내장, 인스턴스별 선택·전환·혼합 가능하도록 설계 |
| 확장 | 서명된 manifest, capability 권한 모델, lifecycle, event/command/render/api hooks, 격리 실행, 버전 호환; 확장 기능도 양계열 API 제공 |
| 런타임 권고 | Kotlin + Spring Boot JVM(LTS JDK), Native Image는 선택 worker, Kotlin/Native+Ktor는 PoC 후보 |
| 제품 목적 | 현재 운영 위키의 문법·역사·확장문법을 보존한 자체 엔진 이전. 리브레 위키 전체 기능 복제를 범위로 삼지 않음 |
| 1.0 검증 규모 | 일반 문서 10만 포함 전체 문서 20만, 등록 사용자 1만, DAU 100. revision·미디어·QPS·장비는 조사·PoC에서 고정 |
| DB 지원 | MariaDB 11.8 / PostgreSQL 17 / SQL Server 2022 / Oracle 19c를 하한으로 고정, 코어·모든 확장 지원. 실제 버전·에디션·패치 검증 필수 |
| 배포 경계 | LibertyWiki 실행물·Docker 이미지 ARM64/x86-64. 외부 인프라 실행환경과 독립. 오케스트레이션은 사용자 선택 |
| 승인 구분 | 1.0 제품 출시 / ENWIKI 규모 인증 / 리브레 위키 실제 이전 승인 별도 |
| 파서 위험 검증 | 기준 버전의 코어·번들·Parsoid ParserTests 데이터를 고정하고 실행기·엔진은 독립 작성 |

## 문서 맵

| 파일 | 목적 |
|---|---|
| `01_PRODUCT_PLAN.md` | 목표·범위·성공 기준 및 비목표 |
| `02_REQUIREMENTS.md` | 98개 기초 코어 요구사항 + 호환·보안·확장·저장 요구사항 |
| `03_EXTENSION_PARITY.md` | 34개 번들 확장·5종 스킨 상세 검증 |
| `04_ARCHITECTURE.md` | 시스템 분해, 비동기 처리, 수평확장 설계 |
| `05_DATA_DESIGN.md` | 개념 데이터 모델, 분할·보관·이관 설계 |
| `06_API_AND_UI.md` | 기존 API 완전 호환을 위한 어댑터/검증 계약, UI/권한 |
| `07_SECURITY_OPERATIONS.md` | AGPL 운영, 위키 보안, 공급망, 파일 접근 |
| `08_ROADMAP_QA.md` | 파서/API 계약 시험, Wikipedia급 규모 게이트 |
| `09_RUNTIME_ADR.md` | JVM / GraalVM Native / Kotlin Native·Ktor 비교 및 결론 |
| `10_COMPATIBILITY.md` | 정확한 API/Wikitext 호환 범위·다단계 시험 전략 |
| `11_STORAGE_DESIGN.md` | LOCAL+S3 내장 저장/서빙·전환 설계 |
| `12_EXTENSION_PLATFORM.md` | 런타임 확장 프레임워크 및 SPI |
| `13_LICENSE_CLEANROOM.md` | AGPL·클린룸 개발·의존성 정책 |
| `14_DUAL_API_CONTRACT.md` | **두 API 계열 동시 제공·지속 동기화·버전별 증분 업데이트** |
| `15_PAGE_SOURCE_SLICE.md` | PostgreSQL 내부 시험용 문서 생성·원문 조회·편집·이력·비교·취소 계약과 호환성 범위 |
| `16_USER_REQUIREMENT_TRACEABILITY.md` | 사용자 제안·구현 이슈·승인 결정에서 PRD·SRS로 이어지는 요구 근거와 이전 수용 기준 |
| `../../docs/adr/` | 기존 결정을 대체하는 2026-11-08 승인 결정 |
| `../specs/release_profile_1_0.json` | 1.0 규모·DB·CPU·성능·운영·이전 요구 프로파일(미실행) |
| `../AGENTS.md` | 개발 에이전트용 구속력 있는 작업 규칙 |
| `../specs/*` | CSV·YAML·SQL 설계 초안과 테스트 매트릭스. `validation_requirements.csv`는 NFR/INT, `release_scope`는 1.0 제품·후속 공통 제품·후속 인증·선택 확장을 구분 |

## 검증·주의

- 현재 문서는 **설계안**입니다. Wikipedia급 부하가 실제로 달성되었거나 API 및 Wikitext가 이미 완전 호환된다는 뜻이 아닙니다.
- 베이스라인은 특정 버전, 구성 및 활성 확장에 종속됩니다. 정밀 호환성은 **해당 구성의 외부 관찰 가능 동작**으로 테스트합니다.
- 스킨은 기능·화면 구조를 독립 구현하고 상표/외관 리소스는 복제하지 않습니다.
- 기존 배포판에 기본 포함된 확장과 기본 활성화된 확장을 구분합니다.
- 주요 문서는 개발 전제·의사결정용이고 YAML/SQL은 모든 API·DDL이 구현 완료된 산출물이 아닙니다.

## 주요 근거

- https://www.mediawiki.org/wiki/Download
- https://www.mediawiki.org/wiki/Bundled_extensions_and_skins
- https://www.mediawiki.org/wiki/API:Action_API
- https://www.mediawiki.org/wiki/API:REST_API
- https://www.mediawiki.org/wiki/Parsoid/API
- https://www.mediawiki.org/wiki/Manual:Extension_registration
- https://www.gnu.org/licenses/agpl-3.0.en.html
