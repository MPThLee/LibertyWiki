# 08. 개발 로드맵·검증계획·요구사항 추적성

## 1. 개발 일정 — 범위와 게이트 중심으로 산정

**기존 v0.9의 단기간 추정치는 전체 MediaWiki API·Wikitext 정밀 호환과 영어 위키백과급 성능 시험을 포함하지 않았으므로 신규 제품의 총기간 추정에 사용하지 않습니다.** 공수가 확정되지 않은 현 단계에서 달력상 완료일을 제시하지 않으며 아래의 개발 트랙과 게이트를 기준으로 측정 후 산정합니다.

| 단계 | 우선 진행 범위 | 완료 게이트 |
|---|---|---|
| T0 기준선 | 1.46.2 코어/확장 활성 상태 확인, API 전체 inventory, 다중 DB/CPU/저장소 지원표, AGPL·fixture 출처/라이선스, 고정 테스트 코퍼스 | G0 — 출처 기록·검증 하니스 |
| T1 위험 검증 | Wikitext/템플릿/Lua/시각 편집 roundtrip 및 Local/S3 정합성, 확장 SDK PoC | G1 — 파서·스토리지 최소 적합성 |
| T2 코어 개발 | 페이지·리비전·보호·검색·파일·웹 UI·두 API 어댑터·권한·감사 | G2 — 내부 MVP·양계열 smoke |
| T3 기본 배포 기능 | 번들 34종·스킨 5종, Wikitext 고급·편집기·관리 기능, extension capability 노출 | G3 — 기능·확장 시험 |
| T4 전체 호환 | 기준 MediaWiki Action/REST 원본 계약 전수 + Native REST 기능 전수 대응 + 데이터 이전 | G4 — API·파서·인가·부작용 전수 |
| 1.0 인수 | RELEASE_1_0의 기능·성능·복구·보안·접근성 | G5 — 제품 1.0 출시 승인 |
| T5 후속 규모 인증 | ENWIKI 분할/캐시/미디어/검색/재색인/장애/백업 | G-ENWIKI — DB별 별도 실측 인증 |
| 사이트 이전 | 리브레 위키의 실제 데이터·추가 의존성·URL·계정·전환 검증 | G-MIGRATION — 제품 GA와 별도 승인, 쓰기 동결 최대 24시간/읽기 유지 |

**모든 단계는 병렬 개발될 수 있지만, 기능 완료는 두 API 계열과 Wikitext·스토리지 등의 해당 계약을 동시에 충족한 경우에만 인정합니다.** 팀 편성은 제품 책임자, 호환성/파서 팀, Kotlin 백엔드·API 팀, 웹/편집기 팀, 확장/스토리지 팀, QA/SRE/보안·법무 지원 등 역량 기준으로 구성하고, 인력과 예산 확정 후 작업량을 재추정합니다.

## 2. 상세 개발 WBS

| WBS ID | 작업 | 의존 | 대표 산출물 | P |
|---|---|---|---|---|
| WBS-01 | 기준 MediaWiki 릴리스·번들 목록 고정 | - | compatibility baseline | P0 |
| WBS-02 | 권한/제목/네임스페이스 분석 | 01 | 엔터티·권한 정책 | P0 |
| WBS-03 | 승인된 ParserTests 데이터 출처/조건 고정·독립 실행기 | 01 | Golden test suite | P0 |
| WBS-04 | Parser+CST/AST PoC | 03 | parser prototype | P0 |
| WBS-05 | Page/Revision/Slot/Blob DB | 02 | SQL migration/aggregate | P0 |
| WBS-06 | 편집 트랜잭션/충돌/diff/revert | 05 | RevisionService API | P0 |
| WBS-07 | Link graph/Category/Template dependency | 04,06 | async dependency index | P0 |
| WBS-08 | File/Version/S3/Thumbnail | 05 | file service + worker | P0 |
| WBS-09 | Auth/session/RBAC/Page protection | 02,05 | authz tests | P0 |
| WBS-10 | UI 읽기/편집/검색/이력/특수페이지 | 06,09 | web application | P0 |
| WBS-11 | Search/Outbox/Recent changes/Watchlist | 06,07 | index worker | P0 |
| WBS-12 | Wikitext/WikiEditor/TemplateData | 04,10 | editor fixtures | P1 |
| WBS-13 | VisualEditor/Cite/ParserFunctions/Math | 12 | editor extension suites | P1 |
| WBS-14 | Lua/Scribunto/advanced parser extensions | 04,13 | sandbox + test suite | P1 |
| WBS-15 | AbuseFilter/TitleBlacklist/CheckUser 등 | 09,11 | security extension suites | P1/P2 |
| WBS-16 | 미디어·코드·스킨 번들 기능 | 08,10 | extension parity suite | P1 |
| WBS-17 | MW Action+MediaWiki REST 호환 어댑터 및 Native RESTful API의 **양계열 동시 구현** | 06,12 | 양계열 계약+cross-surface parity fixtures | P0/P1 |
| WBS-18 | XML/DB/File 이관·delta·검증 | 05,08 | migration pipeline | P1 |
| WBS-19 | 백업/모니터링/배포/DR | 05,08,11 | runbooks, dashboards | P0/P1 |
| WBS-20 | 전체 통합/성능/보안/접근성 검증 | 12~19 | GA test report | P1 |

## 3. 요구사항 추적성 매트릭스 (대표 단위)

| Business Requirement | Functional Requirement | 설계 컴포넌트 | 검증 ID | 릴리스 |
|---|---|---|---|---|
| BR-001 문서 작성·보존 | FR-PAGE-001, FR-EDIT-001/004 | Page/Revision | TC-EDIT-01 | P0 |
| BR-002 편집 충돌 방지 | FR-EDIT-002 | Revision concurrency | TC-EDIT-02 | P0 |
| BR-003 이력·되돌리기 | FR-EDIT-005/006 | Diff/Revert | TC-REV-01 | P0 |
| BR-004 문서 네임스페이스 | FR-NS-001/003 | TitleService | TC-NS-01 | P0 |
| BR-005 위키텍스트 렌더 | FR-PARSE-001/003/004 | Parser+Template | TC-PARSE-01/02 | P0/P1 |
| BR-006 안전한 파일 관리 | FR-FILE-001/002/007 | File/S3 gateway | TC-FILE-01/02/03 | P0/P1 |
| BR-007 인가·관리 추적 | FR-AUTH-002/010, FR-ADMIN-001 | Authz/Audit | TC-AUTH-01/TC-AUD-01 | P0 |
| BR-008 검색 가능한 위키 | FR-SEARCH-001/003/005 | Search/Outbox | TC-SEARCH-01/02 | P0 |
| BR-009 기본 편집기·인용 | EXT-004, EXT-028, EXT-033/034 | Editor/Parser | TC-EXT-04/28/33/34 | P1 |
| BR-010 안전한 기존 위키 이전 | FR-API-008, INT-MW-01 | Migration | TC-MIG-01 | P1 |

전체 FR 카탈로그와 EXT 34개를 추적 단위로 관리합니다. 2026-11-08의 추가 공통 요구까지 SRS·feature catalog·backlog·traceability를 함께 갱신합니다. 카탈로그의 모든 FR에는 수용 기준이 존재하나 이 문서의 대표 추적성 표에 모든 test ID가 개별 나열되어 있지는 않습니다. 구현 백로그 관리 도구에서 전체 1:1 혹은 1:N 매핑을 생성하여 유지하는 것이 필수입니다.

## 4. 테스트 레이어별 계약

| 레이어 | 도구 예시 | 최소 구성 |
|---|---|---|
| Unit | JUnit5/Kotest, MockK | TitleNormalizer, AuthzPolicy, Diff, Outbox idempotency |
| Parser golden | 버전 고정 ParserTests 데이터 + 독립 실행기 | wt→HTML, wt→wt, html→wt, template/extension tag |
| Integration | 네 DBMS/LOCAL/S3 연결 시험, Redis/OpenSearch 장애 시험 | 실 데이터 쓰기/실패 주입/롤백 |
| Contract | OpenAPI validator, JSON/XML fixtures | Native REST 계약 + MW Action·REST 완전 호환 + cross-surface ACL/state parity |
| E2E | Playwright | 읽기·검색·편집·충돌·파일·권한/로그 |
| Security | ZAP, dependency scan, fuzz | XSS, CSRF, SSRF, ACL bypass, 악성 PDF/SVG |
| Load | k6/Gatling | 읽기/검색/편집 + 고비용 템플릿 |
| Disaster | PITR + S3 복구 리허설 | DB/파일 정합성·RTO/RPO 증거 |
| Accessibility | axe, NVDA/VoiceOver | 주요 UI WCAG 2.2 AA |

## 5. 품질 게이트

### Gate G1 — 파서 적합성

- 문서·템플릿·파서 함수·인용·수식·Lua fixture별 테스트를 분리합니다.
- `source → HTML → source`는 정확한 원문 동일성 검사와 의미론적 비교를 함께 수행합니다.
- 점수는 테스트 집합 크기와 실제 위키 콘텐츠 대표성을 명시해야 의미가 있습니다. PoC의 선정 위험군은 실행 전 고정하고 의미·원문 보존/왕복 시험 100% 통과, 데이터 손실 0건, 악성 입력 자원 제한을 합격 조건으로 합니다. 전체 코퍼스와 PoC의 분모·모드를 별도 보고하며 skip/disabled/미지원은 통과가 아닙니다.

### Gate G-MIGRATION — 특정 위키 이관 적합성

- 페이지·리비전·파일 건수와 비공개·삭제 이력의 범위를 분명하게 지정합니다.
- 원본과 신규 파일의 실제 SHA-256이 100% 일치해야 합니다.
- 예외에는 미수집 파일, 깨진 원본, 권한 거부, 호환성 지원 밖의 extension 태그를 구분합니다.
- 재실행·중단 후 재개·잘못된 부분 되돌리기를 테스트합니다.

### Gate G3 — 번들 기능 완료

- 34종 확장과 5개 레이아웃에 **각각** 수용시험 기록이 있어야 합니다. 미완료 항목은 Gap ID로 추적하며 필수 기능 gap이 남으면 제품 GA를 승인하지 않습니다.
- 각 기본 제공 확장은 독립 manifest·버전·수명주기·공개 SPI를 갖고 네 DBMS에서 개별 증거를 제출합니다.
- CheckUser/가젯처럼 고위험 기능은 독립 보안·법무 승인이 선행되어야 합니다.

### Gate G5 — 제품 1.0 GA

권한 우회 0건, critical/high 취약점 미해결 0건(예외 승인 불가 원칙), 모든 필수 기능 수용시험 통과, RELEASE_1_0 프로파일의 네 DBMS·두 앱 CPU·LOCAL/S3 검증, RPO 1시간/RTO 4시간 목표, 실제 파일 SHA 정합성, 승인된 API p95 및 접근성 시험을 확인합니다. ENWIKI 인증과 실제 위키 전환은 별도 게이트입니다.

## 6. Gap & Risk Register

| ID | 항목 | 영향 | 대응 | 승인 게이트 |
|---|---|---|---|---|
| GAP-001 | 완전한 Wikitext round-trip | 매우 큼 | CST/opaque node, 문서군별 fixture, 중간 편집 UI 경고 | G1 |
| GAP-002 | 모든 API/버전 완전 호환 | 매우 큼 | 전수 API inventory와 단계별 구현 현황 공개; GA 필수 경로 누락 금지 | G0/G4 |
| GAP-003 | MediaWiki PHP 확장 재사용 불가 | 큼 | 기능 단위 신규 플러그인 모델, ABI 미호환 명시 | G0 |
| GAP-004 | Lua MW frame API 차이 | 큼 | 라이브러리·버전 고정, 샌드박스 Golden tests | G1/G4 |
| GAP-005 | 민감 CheckUser 데이터 처리 | 매우 큼 | 개인정보 영향 검토, 별도 보존 기간, 관리자 분리 | G4 |
| GAP-006 | 객체 스토리지 대량 삭제·유실 | 매우 큼 | 버저닝/오프사이트 immutable 백업, 삭제 미러 차단 | G3/G5 |
| GAP-007 | 다국어/RTL/IME 편집 차이 | 중간 | OS·브라우저별 matrix | G4 |
| GAP-008 | 템플릿 대량 갱신 시 큐 폭증 | 큼 | bounded queue/예산/작업 병합 | G3 |
| GAP-009 | GPL 등 코드 재사용 라이선스 | 매우 큼 | 구성요소별 SBOM·법무 검토·재작성 원칙 | G0 |
| GAP-010 | 원본 덤프의 파일·oldimage 누락 | 큼 | dump+DB+객체 inventory 교차검증 | G2 |

## 7. 최종 승인 기준과 인수물

필수 산출물은 PRD, SRS/EXT catalog, architecture decision, logical/physical data schema, API contract, UI IA, security/operations runbook, parser & API compatibility fixtures, migration verification report, requirement-to-test traceability 및 Release Gap List입니다. 현재 패키지는 **기획·설계 초안**으로 테스트 결과와 실제 구현의 검증 보고서는 생성되지 않았습니다.

## 8. 기준 출처

- 번들 목록: https://www.mediawiki.org/wiki/Bundled_extensions_and_skins
- 파서 QA: https://www.mediawiki.org/wiki/Parsing/QA
- API: https://www.mediawiki.org/wiki/API
- MediaWiki 확장 개발: https://www.mediawiki.org/wiki/Manual:Developing_extensions


## 11. v1.0 출시 재정의 및 현실적 일정 위험

기존 v0.9의 단순 MVP/번들 구현 추정은 **완전 API·Wikitext 호환 및 영어 위키백과급 부하 검증을 포함하지 않은 수치**였으므로 더 이상 총개발 일정이나 완료 판단에 사용하지 않습니다. 인력과 재정·대상 라이브러리·실제 원본 위키의 파서 코퍼스가 정해지지 않은 상태에서 신뢰성 있는 총개월 수를 산정할 수 없습니다. 여러 팀이 병렬 개발할 수 있는 트랙 중심으로 계획합니다.

현재 단계·게이트 ID는 절 1을 단일 기준으로 사용합니다. 과거 T0~T6 표와 중복 G2/G4 의미는 폐기하며 `G5`는 제품 1.0, `G-ENWIKI`는 후속 규모 인증, `G-MIGRATION`은 특정 위키의 실제 전환입니다.

### 필수 테스트 패키지

1. `contract-action`: baseline MediaWiki 동일 입력의 request/response/side effect differential, JSON/XML/continue/errors/permissions.
2. `contract-rest`: content negotiation, HTTP statuses, HTML endpoints, conditional caching.
3. `parser-fixtures`: Wikitext→DOM, AST, dependencies, template transclusion, Lua, parser functions, empty/invalid inputs.
4. `roundtrip-fixtures`: 선택적 직렬화, 비편집 요소 보존, 중첩/태그, 비라틴 유니코드, 소스 편집↔시각 편집.
5. `storage-matrix`: local/S3 × upload/publish/range/cache/private/migration/object version/failed multipart.
6. `extensions-matrix`: enable/disable, permission scopes, plugin crash, resource exhaustion, upgrade/rollback, hot reload policy.
7. `capacity-enwiki`: 1억 pages/20억 revisions 수준 데이터 생성·증분 변경·파서 fan-out·index rebuild·cold start, 인프라 구성과 측정치 기록.
8. `license-provenance`: 허용/금지 라이선스, 업스트림 코드 복사 여부, AGPL source offer, SPDX 파일 헤더.

### 테스트 실패 처리

- baseline과 차이가 있으면 **모호한 부분일수록 원본 동작을 기계적으로 증명**한 뒤 요구·fixture·코드를 수정합니다.
- `TODO`, `NotImplemented`, `Unsupported`, silent fallback을 GA 호환 API에 남기지 않습니다.
- 외부 API 차이·파서 데이터 손실은 단순 경고가 아니라 release-blocking defect입니다.
- 규모 시험은 개발자가 임의로 pass 처리하지 않고 부하·프로파일·측정 장비·데이터셋·시각을 남깁니다.

## 12. v1.1 — 이중 공개 API 게이트

- **GA 및 후속 버전 출시**는 `MediaWiki Compatible`과 `Native RESTful` 두 API 계열의 완성 여부를 함께 확인합니다. `/w/api.php`와 `/w/rest.php`는 하나의 호환 API 계열에 속합니다.
- MediaWiki 버전의 릴리스 노트/실제 route inventory 변경 사항을 `api_operation_registry.csv`에 등록하지 않으면 CI 실패로 처리합니다.
- 새 API/기능마다 `compat_contract`, `native_contract`, `cross_surface_side_effects`, `cross_surface_acl` 네 종류 테스트가 있어야 합니다. 기준에 실제 해당 API가 존재하지 않는 경우 `N/A` 사유를 기록합니다.
- `Native RESTful`의 기능 단위 coverage는 기존 Action/REST가 제공하는 모든 도메인 기능과 UI-only 관리 기능, 번들 확장 기능을 포함합니다.
- 양 계열의 실제 직렬화가 달라도 데이터 정합성·권한·감사·알림·검색 이벤트가 불일치하면 버그입니다.
- 신규 Native v2가 필요할 경우 v1 지원기간, breaking change 목록, OpenAPI generated clients를 별도로 검증합니다.

## 13. 미확정 입력과 증거 수집 순서

1. DB 하한(11.8/17/2022/19c)은 승인되었지만 버전·에디션·패치별 실제 지원표는 비어 있습니다. 앱 CPU별 연결/동시성/Unicode/빈 문자열/DDL 재개/복구와 모든 확장의 공통 persistence 계약을 검증합니다. 외부 서비스의 CPU/OS를 앱과 결합하지 않습니다.
2. `parser_corpus_manifest.json`의 출처·라이선스·버전·해시·설정·기대 출력/메타데이터·실행 모드를 수집하고 대표 위험군을 선정합니다. 원본 실행기·정규화기·훅 구현은 가져오지 않습니다. 원본 기대값을 현재 구현 출력으로 바꿔 통과시키지 않습니다.
3. 실제 revision 수·미디어 용량·동시접속·QPS·장비와 템플릿/Lua 자원 분포를 조사합니다. DAU 100으로 동시접속이나 RPS를 추정해 합격 조건을 채우지 않습니다. 최소 사양·예산·총기간은 위험 검증 이후 재산정합니다.
4. 리브레 위키의 현재 코어/확장 설정·데이터 접근·미디어/삭제 이력 범위는 아직 확인되지 않았습니다. 공개 페이지 직접 조회가 403이었으므로 과거 인덱스 통계를 현재 실행 환경의 증거로 사용하지 않습니다. 실제 이전 리허설 전 권한 있는 일관 스냅샷을 확보하고 source/target 쓰기 authority·전환 후 신규 쓰기 보존/복구를 검증합니다.
5. 현재 저장소에는 실행 엔진·Gradle wrapper·테스트 태스크가 없습니다. 문서/CSV/JSON/YAML 일관성 검사는 구현·차등시험·부하시험 통과와 구별하며 모든 실행 게이트는 NOT_RUN으로 남깁니다.
