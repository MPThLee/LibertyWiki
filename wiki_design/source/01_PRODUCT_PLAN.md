# 01. 제품 기획서 (PRD) — v1.1

## 1. 제품 정의

**LibertyWiki**는 MediaWiki 1.46.2 코어 및 기본 배포 확장 34종·스킨 5종이 제공하는 외부 기능을 Kotlin 기반 독립 소프트웨어로 재구현하는 AGPL 위키 플랫폼입니다. UI, 위키텍스트 렌더링, 기존 API 클라이언트 호출, 파일 저장·서빙, 운영자 정책, 신규 확장 프레임워크에 걸쳐 **기능 및 인터페이스 호환성을** 추구합니다. 구현은 기존 MediaWiki/확장 소스와 완전히 분리하며 기존 PHP 코드를 사용하지 않습니다.

| 항목 | 구속력 있는 결정 |
|---|---|
| 정식 라이선스 | AGPL-3.0-only (프로젝트·웹 UI·재배포에 라이선스 고지, Corresponding Source 제공 경로) |
| 코드 조달 | MediaWiki/Parsoid/기본 확장/스킨의 코드, 패치, 데이터 기반 파생 코드를 사용하지 않음 |
| 정밀 호환 | MediaWiki 1.46.2 고정, 해당 구성의 Action API, REST API, Wikitext와 활성 번들 기능의 외부 관찰 동작을 동등화 |
| 이중 공개 API | **MediaWiki Compatible** (`/w/api.php`, `/w/rest.php/*`)와 **Native RESTful** (`/api/v1/*`)을 모든 도메인 기능·확장 기능에 함께 제공 |
| 버전 진화 | MediaWiki 후속 버전에서 새 API/기능을 도입하면 두 API 계열을 같은 릴리스에 추가하고 양계열의 계약·권한·부작용 테스트 통과를 필수화 |
| 위키 정책 | 공개·비공개·혼합 읽기 정책, 익명 편집·등록·차단·보호의 관리자 수준 설정 |
| 사용자 클라이언트 | 브라우저 웹만; 반응형 모바일 화면은 제공, iOS/Android 앱은 제공하지 않음 |
| 인프라 | 단일 노드부터 영어 위키백과급 scale-out 구조, 프로파일별 배포 용량 및 SLA 조정 |
| 파일 저장 | 로컬 FS와 S3 호환, 파일 서빙 게이트웨이, 썸네일/버전/권한/정합성 감시 내장 |
| 확장 | 표준 manifest/SPI, hook/API/특수페이지/파서함수/화면 슬롯, 격리 및 업그레이드 지원 |
| 백엔드 | Kotlin/Spring Boot JVM 주력. GraalVM Native / Ktor Kotlin/Native는 PoC 후 역할 배정 |

## 2. 사용자 및 사용 환경

익명 방문자, 로그인 사용자, 편집자, 검토자, 관리자, 검사관(CheckUser), 권한 관리자, 인터페이스 관리자, 봇, 외부 API 클라이언트, 확장 개발자, 사이트·인프라 운영자를 모두 지원합니다. 사이트 설치자는 기본 권한 정책을 선택할 수 있고, 이름공간별·행위별·사용자 그룹별 규칙을 수정할 수 있습니다. 정책 변경은 웹 UI뿐 아니라 API·캐시·검색·파일 게이트웨이에 일관되게 적용합니다.

## 3. 호환성 범위와 '완료'의 정의

**API 호환:** MediaWiki Compatible 계열은 기준 버전에 포함된 코어·활성 번들 확장이 노출하는 Action API 및 MediaWiki REST API 메서드·경로·쿼리·본문·인증·응답·오류·continuation·부작용·권한 동작을 인벤토리화하고 차이가 없는 것을 목표로 합니다. 기능을 P0/P1/P2로 단계 구현하더라도 **GA 시 전체 범위에서 미지원 API가 있어서는 안 됩니다**. API 변경은 MediaWiki baseline upgrade 프로젝트로 관리합니다.

**Native RESTful 동등성:** 호환 API로 가능한 사용자 기능과 관리 기능, 확장 기능은 별도 `/api/v1/*` RESTful 엔드포인트로 제공하며 동일 도메인 서비스·인가·부작용·데이터 정합성을 공유합니다. Action API/기존 REST API의 각 세부 프로토콜 파라미터를 Native API에서 그대로 복제할 필요는 없으나, 기능 손실은 금지합니다. 새 MediaWiki 버전의 API 추가도 이 정책이 적용됩니다.

**Native RESTful 동등성:** 호환 API로 가능한 사용자 기능과 관리 기능, 확장 기능은 별도 `/api/v1/*` RESTful 엔드포인트로 제공하며 동일 도메인 서비스·인가·부작용·데이터 정합성을 공유합니다. Action API/기존 REST API의 각 세부 프로토콜 파라미터를 Native API에서 그대로 복제할 필요는 없으나, 기능 손실은 금지합니다. 새 MediaWiki 버전의 API 추가도 이 정책이 적용됩니다.

**위키텍스트 호환:** 문법뿐 아니라 제목 정규화, 이름공간, 매직 워드, 템플릿·파서 함수·Lua `#invoke`, 인용/수식/파일/표/목록 등 번들 확장 문법, 비정상 입력, 치환, 직렬화와 HTML 왕복 편집까지 비교합니다. 결과는 canonical DOM·의미·전개 순서 및 원본 보존을 검증하며, 출력에서 원본 플랫폼이 보장하지 않는 byte-for-byte HTML 일치만 별도로 구분합니다.

**확장 호환:** 신규 Extension SDK를 지원하는 것을 뜻합니다. **기존 PHP extension ABI의 실행/호환을 보장하지 않습니다.** 기본 번들 기능은 모두 독립 구현하여 신규 플랫폼의 번들 모듈로 공급합니다. 외부 MediaWiki 확장 코드는 가져오지 않습니다.

**원본 콘텐츠 호환:** XML/SQL export 이력·파일·사용자·로그·템플릿·모듈·미디어 링크를 누락 없이 복원 가능한 데이터 모델을 제공합니다. 민감정보 및 계정 credential의 이전은 별도 권한과 법령을 준수합니다.

## 4. 주요 사용자 여정

1. 독자: 공개/비공개 정책 적용→검색/링크 탐색→문서·확장 콘텐츠·파일 보기.
2. 편집자: `wikitext` 또는 VisualEditor→미리보기→기존과 동일한 렌더 결과 확인→충돌 검사→불변 revision 기록.
3. 봇: MediaWiki Compatible API의 동일 URL/토큰/continuation/에러 코드로 조회·편집하거나 Native RESTful API를 사용하여 동일 결과를 얻습니다.
4. 관리자: 사이트 오픈/비공개 전환·익명 편집 허용/거부·그룹별 권한 변경·문서 보호·감사.
5. 확장 개발자: manifest 작성→capability 선언→서명/검증→설치→후크 등록→진단→업그레이드/비활성화.
6. 파일 관리자: 스토리지 모드 선택→로컬/S3 저장→미디어 게이트웨이 제공→무손실 마이그레이션·백업 복원.
7. SRE: 통계/캐시 히트율/큐 처리량 관리→샤드/인덱스 확장→재해복구 훈련.

## 5. 출시 단계와 허용 범위

| 단계 | 제공 기능 | 외부 호환 요구 | 출시 의미 |
|---|---|---|---|
| P0 내부 MVP | 제목/리비전/읽기/편집/검색/파일/권한/로컬+S3/확장 SDK 기반 | 핵심 Action API와 Wikitext의 정식 conformance suite 초기 통과 | 내부 시험 전용, MediaWiki drop-in 선언 금지 |
| P1 공개 베타 | 기본 번들 기능 대다수, Parsoid-style roundtrip, Lua/가젯, 관리자 정책, API 엔드포인트 완성 | API/파서 gap register 0을 목표로 수렴 | 호환성 미완료 사항을 공개 표시 |
| P2 호환 GA | 코어 및 기본 배포 전체 기능/API/Wikitext conformance 완료, 외부 확장 SDK 검증, 규모/보안/DR 통과 | 미지원 필수 항목 0; 보안·데이터 정합성 치명 결함 0 | 해당 baseline에 대해 호환 제품으로 배포 가능 |
| P3 | MediaWiki 새 버전 지원, 성능 개선, 선택적 서드파티 extension API 에뮬레이션 | 버전별 호환성 프로파일 | 기존 GA 준수 유지 |

P2는 **나중에 해도 되는 선택 기능**이 아니라 GA 출시의 필수 관문입니다. 상호 연동의 위험이 높은 기능은 구현 순서를 늦출 수 있어도 요구에서 삭제할 수 없습니다.

## 6. 영어 위키백과급 용량

영어 위키백과의 2026-10 관측치는 대략 전체 페이지 6,640만 건, 누적 편집 12.8억 회, 일반 문서 725만 건 수준입니다. 누적 편집 수는 영구적으로 보존된 현재 리비전 수와 동일하다고 가정하지 않습니다. 역사 덤프의 2025-06 비압축 용량은 약 31TB로 보고되어 있습니다. 이는 **설계 입력**이지 제품 성능 달성 증거가 아닙니다.

| 규모 프로파일 | 구성 목표(설계값) | 평가 게이트 |
|---|---|---|
| SMALL | 10만 페이지, 300만 revision, 로컬 단일 노드 배포 | 로컬 저장/백업, p95 응답 |
| LARGE | 1,000만 페이지, 2억 revision, 수백만 미디어 객체 | DB partition, API 스케일, 재색인 |
| ENWIKI | **1억 페이지, 20억 revision**, 템플릿 고부하/대규모 검색/독립 미디어 스케일 | 실제 영문 위키 데이터 계열 재현, 수평 확장 및 장애 복구 시험 |

읽기 QPS·CDN 요청량·미디어 총바이트는 측정값으로 정해야 하며, ENWIKI 프로파일은 단계별 **부하 재현과 SLO 합격 후에만** 달성으로 표기합니다. 런타임 개발 방식만으로 Wikipedia급 부하를 보장할 수 없습니다.

## 7. 제품 KPI와 게이트

- API: MediaWiki Compatible 기준 API 인벤토리 100% 명세 및 통과; 동일 기능별 Native RESTful 매핑 100% 등록, 양쪽의 권한·부작용 동등성 테스트 통과, 승인되지 않은 gap 0.
- Wikitext: 승인된 규격의 정상·오류·중첩·Lua·DOM roundtrip fixture 100% 통과, 우회·정보손실 0.
- 데이터: 동의한 전체 페이지·revision·파일 건수와 콘텐츠 해시 100% 일치, 지정 범위 고아 데이터 0.
- 보안: 권한 우회·비공개 페이지/파일 유출·XSS·RCE·cross-tenant leak 중대 결함 0.
- 운영: 데이터 노드 장애/검색 장애/스토리지 장애/재색인/스토리지 전환/복구 실측 통과.
- 재배포: AGPL 고지·소스 제공 기능·SBOM·의존성 라이선스 및 코드를 재사용하지 않았다는 provenance 점검.

## 8. 비목표와 명시적 구별

- **iOS/Android 네이티브 앱은 제공하지 않습니다.** 그러나 모바일 웹 반응형 UI는 제공합니다.
- 기존 MediaWiki PHP extension/skin을 수정 없이 실행하는 것은 목표가 아닙니다. **신규 확장 개발 기능은 필수**입니다.
- Wikipedia에서 별도로 운영하는 Wikimedia Commons/Wikidata/로그인 공동체 등 다중 사이트 서비스를 그대로 복제하지 않으나 외부 연동 API 지원 여지는 둡니다.
- 브랜드·원본 CSS·HTML 템플릿·아이콘·특수한 레이아웃 코드를 복제하지 않습니다. 호환이 요구되는 데이터 계약과 사용성 중심으로 새 UI를 구현합니다.

## 9. 참고 링크

- https://www.mediawiki.org/wiki/Download
- https://www.mediawiki.org/wiki/Bundled_extensions_and_skins
- https://www.mediawiki.org/wiki/API:Action_API
- https://www.mediawiki.org/wiki/API:REST_API
- https://meta.wikimedia.org/wiki/Data_dumps/Dumps_sizes_and_growth
- https://en.wikiscan.org/
