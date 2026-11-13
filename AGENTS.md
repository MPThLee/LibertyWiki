# AGENTS.md — LibertyWiki 독립 구현 개발 에이전트 규약

> 적용: 이 저장소의 Kotlin 백엔드, TypeScript 웹 클라이언트, 확장 SDK, 파서, 운영도구, 설계문서 및 API 스키마 전체. 이 문서는 개발 자동화와 리뷰어에게 동일하게 적용됩니다. 최신 승인된 설계가 우선하며 변경은 ADR/PR에서 추적합니다.

## 0. 프로젝트의 절대 불변 조건 (MUST)

1. **독립 구현 / AGPL:** 프로젝트 신규 코드의 라이선스는 `AGPL-3.0-only`입니다. LICENSE·SPDX·소스 제공·SBOM·서드파티 라이선스 검토 없이 배포해서는 안 됩니다.
2. **MediaWiki 구현 코드 재사용 금지:** MediaWiki 코어, Parsoid, MediaWiki 번들 및 서드파티 확장, 스킨의 PHP/JS/CSS/SQL/테스트 구현 코드를 복사·변환·번역·포팅·링크·내장하지 않습니다. 기능 설명·공개 API 계약·관찰한 동작·데이터 문법을 근거로 독자적으로 구현합니다. 사용자가 재사용을 승인한 `librewiki/liberty-skin`의 코드와 리소스에는 [ADR-0205](docs/adr/0205-product-skins.md)의 예외를 적용합니다.
   사용자 승인에 따라 ParserTests의 fixture 데이터는 출처·버전·해시·라이선스·원래 고지를 확인해 검증 자료로 사용할 수 있습니다. 이 허용은 파서·테스트 실행기·정규화기·훅의 구현 코드 재사용을 허용하지 않습니다. [ADR-0204](docs/adr/0204-parser-test-data-boundary.md)를 따릅니다.
3. **MediaWiki 기능 호환:** 최초 기준선 `MediaWiki 1.46.2`의 코어와 기본 배포 확장 기능 및 활성화된 API, Wikitext·템플릿·ParserFunctions·Scribunto Lua·시각 편집 왕복 변환까지 GA 전 검증합니다. 제품 스킨 범위와 색상 모드는 [ADR-0205](docs/adr/0205-product-skins.md)를 따릅니다. 일부 구현을 전체 호환이라고 광고하면 안 됩니다.
4. **API 계열은 두 개:** `MediaWiki Compatible` = `/w/api.php` + `/w/rest.php/*`, `Native RESTful` = `/api/v1/*`. 기존 MediaWiki REST API가 Native RESTful이라는 주장은 틀립니다. 모든 도메인 기능·확장 기능에는 Native REST operation이 있어야 하며, 기준 MediaWiki에서 원본 API가 있는 경우 정확한 Compat 경로도 제공해야 합니다.
5. **향후 새 MediaWiki API:** 추가·변경·삭제되는 API를 지원할 때 **같은 기능 변경에서 양 API 계열의 route/문서/계약 테스트/권한/side effect 검증**을 모두 업데이트합니다. 한쪽만 구현한 기능은 `INCOMPLETE`; GA/버전 호환 선언을 금지합니다.
6. **기능 동작의 단일 원천:** 외부 API 어댑터는 `ApplicationService`를 호출합니다. API/controller 안에 비즈니스 로직, SQL 조합, 권한 우회, 스토리지 직접 쓰기를 두지 않습니다.
7. **저장소:** `LOCAL` + `S3_COMPATIBLE` 파일 저장 및 HTTP 서빙을 기본 구현합니다. 파일 원본·oldversion·thumbnail·visibility·해시·Range·이전/복구를 일관되게 지원합니다.
8. **확장 기능 필수:** extension manifest/SPI/hook/ParserFunction/특수 페이지/UI slot/보안 sandbox/버전 호환성/두 API 계열 노출을 설계합니다. 원본 PHP extension을 로드하는 기능은 제공하지 않습니다.
9. **관리자가 공개 정책 결정:** 읽기/편집/회원가입/업로드/파일 보기/익명 행위/보호·차단·그룹 권한을 MediaWiki 수준으로 설정할 수 있어야 합니다. 모든 UI/API/storage/search/cache에서 같은 정책을 적용합니다.
10. **영어 Wikipedia 규모는 목표이지 성능 사실이 아님:** 1억 페이지/20억 revision 설계용 시험 프로파일을 사용하며 실제 측정 전까지 달성했다고 표시하지 않습니다.
11. **웹만 개발:** 반응형 브라우저 UI를 구현합니다. iOS/Android 네이티브 앱은 범위 밖입니다.
12. **다중 DB:** 인스턴스마다 MariaDB/PostgreSQL/SQL Server/Oracle 중 하나를 선택하며 코어와 모든 기본 제공·외부 확장이 네 DB를 지원해야 합니다. 개발 기준일 2026-11-08의 하한은 각각 11.8/17/2022/19c이며 미검증 상위 버전을 자동 지원 선언하지 않습니다. [ADR-0201](docs/adr/0201-relational-database-portability.md)을 따릅니다.
13. **확장 경계:** 기본 배포 확장은 각각 manifest·버전·수명주기를 갖는 LibertyWiki 확장으로 구현하고 외부 확장과 같은 공개 SPI를 사용합니다. LOCAL/S3 저장·서빙은 내장 기능입니다. 확장 제거와 데이터 영구 삭제를 분리합니다.
14. **실행환경 독립:** LibertyWiki 실행물·이미지는 ARM64/x86-64를 지원합니다. DBMS/OpenSearch/Redis 등 외부 인프라의 CPU·OS·배포 방식은 프로젝트 실행환경과 독립적입니다. 특정 오케스트레이터를 프로젝트에서 도입·요구하지 않으며 사용자가 선택합니다.
15. **출시와 이전:** 1.0.0은 일반 문서 10만을 포함한 전체 문서 20만·등록 사용자 1만·DAU 100 프로파일로 검증합니다. ENWIKI 인증과 특정 위키 이전 승인은 별도입니다. 리브레 위키의 모든 고유 기능을 구현 범위로 자동 편입하지 않습니다.

## 1. 읽어야 할 문서 순서

`source/00_README.md` → `source/01_PRODUCT_PLAN.md` → `source/02_REQUIREMENTS.md` → `source/14_DUAL_API_CONTRACT.md` → `source/10_COMPATIBILITY.md` → `source/04_ARCHITECTURE.md` → `source/09_RUNTIME_ADR.md` → 해당 기능별 설계/CSV. 소스 트리의 경로가 다르면 프로젝트 루트의 README에서 참조되는 정확한 문서를 찾습니다.

## 2. 기본 기술 및 코딩 규칙

- 백엔드: Kotlin + Spring Boot **JVM**(정식 지원 LTS JDK) 우선, Gradle Kotlin DSL, DB별 persistence adapter(MariaDB/PostgreSQL/SQL Server/Oracle), Redis, OpenSearch, S3-compatible API. GraalVM Native Image는 독립 worker PoC에만 승인 후 도입합니다. Kotlin/Native+Ktor를 본체에 전환할 때는 별도 ADR과 성능·확장 시험이 필요합니다.
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

- §0.2의 재사용 경계를 코드 조사와 에이전트 입력에도 적용합니다. 공개 API 문서 링크와 직접 관찰한 테스트 결과는 사용 가능합니다.
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

## 10. 작업 진행, 커밋, Merge Request 및 이슈 업데이트

- 작업은 포크(fork)해서 진행하며, 브랜치 이름은 GitFlow 구분 규칙(`feature/*`, `bugfix/*`, `release/*`, `hotfix/*`, `support/*`)을 따릅니다.
- 작업을 시작할 때 `wip` 라벨을 붙이고, 작업이 종료될 때 `wip` 라벨을 삭제합니다.
- 작업 변경과 관련 검증을 마친 뒤, 완료 보고 전에 해당 작업의 변경사항을 반드시 Git 커밋으로 남깁니다. 기존 사용자 변경이나 다른 작업의 변경은 분리하고 이번 작업의 변경만 스테이징합니다.
- 머지(merge)는 진행하지 않으며, 포크 원격에 브랜치를 푸시한 뒤 `gh` 클라이언트(`gh pr create`)로 Merge Request를 생성합니다.
- 커밋 메시지의 제목과 본문은 모두 영어로 작성합니다.
- 제목은 `<type>: <summary>` 형식으로 작업 분류와 구체적인 작업 요약을 적습니다. 분류는 `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`, `build`, `ci`, `revert` 중 변경 목적에 맞게 선택합니다.
- 본문에는 주요 변경사항과 변경 이유, 검증한 명령 및 결과, 실행하지 못한 검증과 남은 gap을 상세히 기록합니다. 해당하지 않는 항목은 그 이유를 명시합니다.
- 본문에 `Model: <model name>`을 반드시 포함하여 실제 작업 모델을 기록합니다. 여러 모델이 작업했다면 각 모델과 담당 작업을 적습니다. 모델명은 `Model: GPT-6 (Codex)`처럼 실행 환경에서 확인 가능한 명칭으로 간결하게 적으며, 확인할 수 없는 세부 버전에 대한 부연 설명은 생략합니다.
- 작업에 연결된 GitHub 이슈가 있으면 작업 시작 시 [작업 시작 절차](docs/agents/issue-tracker.md#작업-시작)를, 커밋 및 Merge Request 생성 후 완료 보고 전에 [이슈 완료 절차](docs/agents/issue-tracker.md#작업-완료)를 따릅니다.
- 완료 보고에는 커밋 해시, Merge Request 링크와 관련 이슈의 업데이트 결과를 포함합니다. 커밋, Merge Request 생성이나 이슈 업데이트가 실패하면 원인과 남은 상태를 보고합니다.

예시:

```text
docs: require completion commits with model attribution

Require agents to commit task changes after validation and before reporting completion.
Define English commit subjects with a change type and summary, and require
detailed bodies covering changes, rationale, validation, and remaining gaps.

Validation: git diff --check passed. Runtime tests were not run because only
agent instructions changed. No compatibility behavior changed.

Model: GPT-6 (Codex)
```

## 11. 저장소 보관 범위

- 저장소에는 설계문서·API/데이터/시험 요구 계약, 소스코드와 필요한 개발 설정만 보관합니다.
- 검증 결과·실행 로그·품질 보고서·작업 중간파일·생성된 통합본/문서 사본·빈 결과 manifest를 추가하지 않습니다. 임시 작업은 저장소 밖의 임시 디렉터리에서 수행하고 테스트/CI 산출물은 외부 artifact 저장소를 사용합니다.
- 요구사항·합격 기준·계약과 실제 실행 결과를 구별합니다. 설계 파일에 실행 결과나 파일 해시/변경 목록을 누적하지 않습니다. 실제 테스트 데이터와 재현 가능한 테스트 소스는 소스코드의 일부로 관리할 수 있습니다.
- 검증한 명령·결과·미실행 항목은 완료 응답과 커밋 본문에 보고합니다. 별도 검증 기록 파일을 생성하지 않습니다.
- 이 원칙은 과거 커밋에도 적용하며, 불필요 산출물을 정리할 때 해당 경로를 이력에서도 제거합니다.

## 12. 서브에이전트 위임과 모델 선택

1. **작업 분담.** 독립적으로 진행할 수 있는 코드 조사, 구현, 테스트, 리뷰가 있으면 서브에이전트에 위임하고 주 에이전트는 병행 가능한 작업을 진행합니다. 짧은 단일 작업이나 앞 단계의 결과에 의존하는 작업은 직접 처리하거나 의존 순서대로 진행합니다.
2. **모델 선택.** 위임할 때 작업 유형과 난이도에 맞춰 GPT-6.1 Sol(`gpt-6.1-sol`) 또는 GPT-6.1 Terra(`gpt-6.1-terra`)를 지정합니다. 요구사항·문서 조사, 계약 대조, 독립 리뷰에는 Sol을 우선합니다. 코드 구현·리팩터링, 복잡한 버그 진단, 테스트 작성에는 Terra를 우선합니다.
3. **추론 강도.** 모델과 별도로 `low`, `medium`, `high` 중 하나를 지정합니다. 절차와 판정 기준이 명확한 조사나 단순 수정에는 `low`, 일반 구현이나 여러 파일을 대조하는 검토에는 `medium`, 원인이 불명확하거나 API 호환성·보안·데이터 무결성에 영향을 주는 판단에는 `high`를 사용합니다.
4. **위임 범위.** 목표, 관련 저장소 규약과 설계 문서, 담당 파일, 기대 결과와 검증 기준을 전달합니다. 병렬 편집은 담당 파일이 겹치지 않도록 배정합니다. 서브에이전트는 변경 파일, 검증 결과와 남은 문제를 주 에이전트에게 보고합니다.
5. **통합 검증.** 주 에이전트는 결과를 검토·통합하고, 변경 간 연동을 검증한 뒤 §10에 따라 모델과 담당 작업을 기록하고 커밋합니다.

## Agent skills

### Issue tracker

이슈·작업 명세는 GitHub Issues로 관리합니다. 이슈 조회·생성·갱신 전에 `docs/agents/issue-tracker.md`를 읽습니다.

### Triage labels

기본 5개 triage 역할을 사용합니다. 분류·상태 변경 전에 `docs/agents/triage-labels.md`를 읽습니다.

### Domain docs

단일 컨텍스트(single-context)입니다. 코드베이스 탐색·설계 전에 `docs/agents/domain.md`를 읽습니다.
