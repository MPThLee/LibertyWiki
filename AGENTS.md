# AGENTS.md — AtlasWiki 독립 구현 개발 에이전트 규약

> 적용: 이 저장소의 Kotlin 백엔드, TypeScript 웹 클라이언트, 확장 SDK, 파서, 운영도구, 설계문서 및 API 스키마 전체. 이 문서는 개발 자동화와 리뷰어에게 동일하게 적용됩니다. 최신 승인된 설계가 우선하며 변경은 ADR/PR에서 추적합니다.

## 0. 프로젝트의 절대 불변 조건 (MUST)

1. **독립 구현 / AGPL:** 프로젝트 신규 코드의 라이선스는 `AGPL-3.0-only`입니다. LICENSE·SPDX·소스 제공·SBOM·서드파티 라이선스 검토 없이 배포해서는 안 됩니다.
2. **MediaWiki 코드 재사용 전면 금지:** MediaWiki 코어, Parsoid, MediaWiki 번들 및 서드파티 확장, 스킨의 PHP/JS/CSS/SQL/테스트 구현 코드를 복사·변환·번역·포팅·링크·내장하지 않습니다. `Copy from upstream`, 소스 붙여넣기 후 AI 코딩, 유사 로직을 문자 그대로 옮기는 작업을 거부합니다. 기능 설명·공개 API 계약·관찰한 동작·데이터 문법만 근거로 독자적으로 구현합니다.
3. **MediaWiki 기능 호환:** 최초 기준선 `MediaWiki 1.46.2`, 코어와 기본 배포 확장/스킨 기능 및 활성화된 API, Wikitext·템플릿·ParserFunctions·Scribunto Lua·시각 편집 왕복 변환까지 GA 전 검증합니다. 일부 구현을 전체 호환이라고 광고하면 안 됩니다.
4. **API 계열은 두 개:** `MediaWiki Compatible` = `/w/api.php` + `/w/rest.php/*`, `Native RESTful` = `/api/v1/*`. 기존 MediaWiki REST API가 Native RESTful이라는 주장은 틀립니다. 모든 도메인 기능·확장 기능에는 Native REST operation이 있어야 하며, 기준 MediaWiki에서 원본 API가 있는 경우 정확한 Compat 경로도 제공해야 합니다.
5. **향후 새 MediaWiki API:** 추가·변경·삭제되는 API를 지원할 때 **같은 기능 변경에서 양 API 계열의 route/문서/계약 테스트/권한/side effect 검증**을 모두 업데이트합니다. 한쪽만 구현한 기능은 `INCOMPLETE`; GA/버전 호환 선언을 금지합니다.
6. **기능 동작의 단일 원천:** 외부 API 어댑터는 `ApplicationService`를 호출합니다. API/controller 안에 비즈니스 로직, SQL 조합, 권한 우회, 스토리지 직접 쓰기를 두지 않습니다.
7. **저장소:** `LOCAL` + `S3_COMPATIBLE` 파일 저장 및 HTTP 서빙을 기본 구현합니다. 파일 원본·oldversion·thumbnail·visibility·해시·Range·이전/복구를 일관되게 지원합니다.
8. **확장 기능 필수:** extension manifest/SPI/hook/ParserFunction/특수 페이지/UI slot/보안 sandbox/버전 호환성/두 API 계열 노출을 설계합니다. 원본 PHP extension을 로드하는 기능은 제공하지 않습니다.
9. **관리자가 공개 정책 결정:** 읽기/편집/회원가입/업로드/파일 보기/익명 행위/보호·차단·그룹 권한을 MediaWiki 수준으로 설정할 수 있어야 합니다. 모든 UI/API/storage/search/cache에서 같은 정책을 적용합니다.
10. **영어 Wikipedia 규모는 목표이지 성능 사실이 아님:** 1억 페이지/20억 revision 설계용 시험 프로파일을 사용하며 실제 측정 전까지 달성했다고 표시하지 않습니다.
11. **웹만 개발:** 반응형 브라우저 UI를 구현합니다. iOS/Android 네이티브 앱은 범위 밖입니다.

## 1. 읽어야 할 문서 순서

`source/00_README.md` → `source/01_PRODUCT_PLAN.md` → `source/02_REQUIREMENTS.md` → `source/14_DUAL_API_CONTRACT.md` → `source/10_COMPATIBILITY.md` → `source/04_ARCHITECTURE.md` → `source/09_RUNTIME_ADR.md` → 해당 기능별 설계/CSV. 소스 트리의 경로가 다르면 프로젝트 루트의 README에서 참조되는 정확한 문서를 찾습니다.

## 2. 기본 기술 및 코딩 규칙

- 백엔드: Kotlin + Spring Boot **JVM**(정식 지원 LTS JDK) 우선, Gradle Kotlin DSL, PostgreSQL, Redis, OpenSearch, S3-compatible API. GraalVM Native Image는 독립 worker PoC에만 승인 후 도입합니다. Kotlin/Native+Ktor를 본체에 전환할 때는 별도 ADR과 성능·확장 시험이 필요합니다.
- 프런트엔드: TypeScript, React와 접근성 중심의 반응형 UI. UI는 Native RESTful API를 기본 호출하되 호환 API의 기능도 동일한 도메인에 매핑되어야 합니다.
- 패키지 경계 예: `domain.*`, `application.*`, `adapter.api.compat.action`, `adapter.api.compat.rest`, `adapter.api.native`, `adapter.persistence`, `adapter.storage.local`, `adapter.storage.s3`, `extension.spi`, `parser.*`.
- 클래스/메서드 이름은 역할을 드러내고, 외부 DTO는 도메인 엔티티를 그대로 노출하지 않습니다. API 가족 간 response DTO 공유 금지.
- Kotlin `data class`와 sealed error/result types 사용. null 반환보다 명확한 `Result`/예외/오류 코드 사용. UTC 보존·ISO 8601 변환, Unicode 정규화·title parser 유닛테스트 필수.
- JPA가 모든 고볼륨 작업에 적합하다고 가정하지 않습니다. 대규모 리비전 이력·검색·백필은 SQL/JDBC batch/streaming·파티셔닝·실측 계획을 따릅니다.
- revision은 append-only, 과거 revision 직접 수정/삭제 금지(가시성 은폐와 물리적 정리는 별개). 사용자 권한 변경은 동일 시점 이후 두 API에 일관되게 적용합니다.
- 네트워크/DB/스토리지 I/O에는 timeout, backpressure, retry budget, idempotency, 감사와 trace ID를 적용합니다. 무제한 전체스캔과 대형 파일 전체 메모리 로드는 금지합니다.

## 3. 기능 추가 PR 작업 순서 (반드시 따름)

1. **기능 지정:** `FR-*` 및 `CAP-*`/`EXT-*` ID를 정하고 출처 버전·기능 가시성·관리 권한을 기술합니다.
2. **기존 API inventory 확인:** 기준 MediaWiki의 실제 `api.php` module 및 `rest.php` path 존재 여부·파라미터·권한·오류·상태코드를 **블랙박스/공식 문서**로 확인합니다.
3. **두 API 매핑 등록:** `specs/api_operation_registry.csv` 및 해당 기능의 `specs/openapi.yaml`(Native) 갱신. 기존 원본에 API가 없으면 `compat=UI_ONLY`, `reason`을 기록합니다.
4. **도메인 구현:** `ApplicationService`에서 유효성/권한/transaction/outbox/수정이력을 처리하고 두 공개 API adapter는 변환만 합니다.
5. **테스트 구현:** 원본 MediaWiki differential contract, Native RESTful schema, cross-surface ACL, state/side-effect parity, parser roundtrip(해당 시), Local/S3 matrix(해당 시) 중 관련 항목을 반드시 작성합니다.
6. **문서 및 기록:** 요구사항/ADR/버전 프로파일/AGENTS.md 수정이 필요한지 검토하고 변경 근거와 출처를 PR에 적습니다.
7. **출시 게이트:** 양 API 노출/테스트가 완료되기 전에 기능을 GA/호환 완료라고 표시하지 않습니다.

## 4. API 규약

### A. MediaWiki Compatible

- `/{scriptPath}/api.php`: 기존 Action API 전반. `action`, `query prop/list/meta/generator`, 토큰/CSRF, `format`, `formatversion`, `continue`, `maxlag`, error code/HTTP, 조건부 가시성을 기준 버전에 따라 **정확히** 유지.
- `/{scriptPath}/rest.php/*`: 기존 MediaWiki REST API 라우트 전반. HTTP methods/headers/status/content negotiation/cache semantics, 원본 route version 유지.
- 위 두 경로는 **한 API 계열**입니다. 기존 PHP 코드 실행은 필요하지 않으며 불가능하다고 명시합니다.
- 원본에 없는 API를 임의 생성해 기존 MediaWiki API라 부르지 않습니다. 기준 원본에 등록된 API의 누락은 GA 차단 사항입니다.

### B. Native RESTful

- `/api/v1/{resources}`: OpenAPI 3.1, 안정적인 operationId, JSON Schema, request/response, 인증권한, ETag/If-Match 및 멱등성 가이드.
- 기존 MW Action/REST에서 제공하는 모든 기능과 관리 UI-only 기능 및 신규 확장 기능을 제공합니다. 한 기능이 여러 리소스 operation으로 분해되어도 되지만 capability 매핑은 필수입니다.
- Native v1의 breaking change는 v2/지원중단 정책과 함께 별도 릴리스로 전환합니다. MW 호환 프로파일 변경을 Native major version으로 혼동하지 않습니다.

### C. 공동 원칙

- 서로 다른 DTO/오류 포맷은 허용되지만 **도메인 상태/인가/감사/이벤트 및 비공개 데이터 보호는 동일**해야 합니다.
- endpoint smoke test만으로 기능 완료 처리 불가. API 정상/오류/권한/동시성/마이그레이션/대용량 테스트가 통과해야 합니다.
- MediaWiki 신규 버전에서 API가 추가되면 두 API 계열 대응을 같은 PR·릴리스에 포함하고 차등시험을 작성합니다.

## 5. 코드 재사용·출처 검증 절차

- 원본 MediaWiki/Parsoid/확장·스킨 소스 URL, 파일, 코드블록을 코딩 에이전트 입력으로 복사하지 않습니다. 공개 API 문서 링크와 직접 관찰한 테스트 결과는 사용 가능합니다.
- 기존 오픈소스 라이브러리는 SPDX ID, 출처, 라이선스 호환 및 공급망 검토 후 사용합니다. MediaWiki 본체/확장의 원본 구현 코드를 대체 라이브러리라 칭하여 재사용하지 않습니다.
- 출처가 의심되는 구현/복제 요청은 작업을 중지하고 자체 구현용 명세·테스트 케이스 수집으로 변경합니다.

## 6. 확장 개발 규정

- 모든 확장은 서명/검증된 manifest, 권한 capability, 최소 `platformApi` version, 실행 timeout·memory/CPU budget을 정의합니다.
- isolation 기본값은 별도 RPC worker; 신뢰할 수 있는 내부 모듈의 in-process 실행은 승인된 보안 검토 후 허용합니다.
- parser tags/functions, content models, special pages, UI/editor slots, event handlers, scheduled jobs, storage providers 등록 SPI를 제공하며 각 확장 기능을 Native RESTful API로도 노출합니다.
- 기존 MediaWiki에 실재하는 해당 확장 API가 있으면 호환 계열에도 등록하고 API 계약 테스트를 만듭니다.
- DB/core 내부 객체에 직접 접근하거나 임의 JS/CSS를 관리 승인 없이 공개 렌더 경로에 주입하면 안 됩니다.

## 7. 테스트 및 실행 명령 (프로젝트 실제 Gradle 태스크를 기준으로 확인)

실제 빌드 스크립트에 태스크가 생성되어 있다는 전제에서 다음 명령을 우선 사용합니다. 태스크가 없으면 임의로 통과 처리하지 않고 태스크를 정의하거나 문서를 갱신합니다.

```bash
./gradlew cleanTest bootTest
./gradlew test
./gradlew check
```

CI에서 별도 작업으로 `:contract:compatActionTest`, `:contract:compatRestTest`, `:contract:nativeRestTest`, `:contract:crossSurfaceParityTest`, `:parser:compatibilityTest`, `:storage:matrixTest`, `:extensions:integrationTest`, `:load:profileTest`를 구현할 것을 요구합니다. **현재 이는 제안 태스크 이름이지 존재가 확인된 태스크는 아닙니다.**

테스트 실패를 무시하거나 fixture를 조작하여 테스트를 통과시키지 않습니다. baseline 차이가 발생하면 먼저 기준 서버 구성·응답·시각과 권한을 확인합니다.

## 8. PR 검토 체크리스트

- [ ] 요구 ID / featureId / source MediaWiki version 확인
- [ ] MediaWiki source implementation을 복제·포팅하지 않았고 외부 라이선스 검토 통과
- [ ] MediaWiki Compatible API (Action 및/또는 MediaWiki REST 중 실재하는 것) 계약 유지
- [ ] Native RESTful `/api/v1` operation·OpenAPI 계약 등록
- [ ] 향후 버전 추가 API에도 dual API mapping 동기화
- [ ] 두 API의 ACL/비공개 여부·side effect·감사·에러·동시성 테스트 완료
- [ ] Wikitext/parser/Lua/DOM roundtrip 회귀시험 해당 시 완료
- [ ] 로컬/S3 저장과 서빙·이관 시험 해당 시 완료
- [ ] 확장 서명/격리/권한·비활성화·버전 시험 해당 시 완료
- [ ] SBOM·취약점·라이선스·AGPL 소스 제공 점검
- [ ] 문서·인벤토리·추적성/릴리스 게이트 갱신

## 9. 작업 시 지켜야 할 커뮤니케이션

- 작업 완료라고 말하기 전에 **실제로 수정한 파일, 검증한 테스트, 통과하지 못한 테스트, 남은 호환성 gap**을 구체적으로 보고합니다.
- 요구가 충돌하면 기능/원본 API 정밀 호환, AGPL/원본 코드 재사용 금지, API 양계열 동시 제공, 보안/데이터 무결성의 우선순위를 유지합니다.
- 원본 MediaWiki와 관찰된 동작이 불명확하면 임의 응답을 만들어 구현 완료라고 주장하지 않습니다. `compat_gap`와 검증 대상을 기록합니다.
