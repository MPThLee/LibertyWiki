# 16. 사용자 요구사항 출처와 범위

이 문서는 [PRD](01_PRODUCT_PLAN.md)와 [SRS](02_REQUIREMENTS.md)의 요구를 역사적 제안, 구현 이슈, 승인된 운영 결정에 연결합니다. [리브레 위키의 리버티 엔진 요구사항](https://librewiki.net/wiki/리브레_위키:LibertyEngine/리버티_엔진_요구사항)은 공개 출처이며, 아래 줄 번호는 2024-12-03 검색 제안까지 포함된 사용자 제공 `libertywiki_user_req.wikitext.txt`의 해당 절과 댓글을 가리킵니다. 과거 제안과 댓글에는 서로 다른 의견이 있으며 운영 설정과 현재 사용 여부는 별도로 확인합니다.

새 기능의 개발 착수는 내부 MVP 및 기본 호환성·확장 기능 테스트 이후입니다. 이미 승인된 1.46.2 기준 기능에는 기존 단계와 게이트를 적용합니다. 1.0 제품 승인과 리브레 위키 전환 승인의 관계는 [ADR-0202](../../docs/adr/0202-extension-and-release-boundaries.md)를 따릅니다.

## 2026-11-12 사용자 범위 결정

| 결정 출처 | 적용 범위 |
|---|---|
| Q1 관리·편집 편의 승인 | 통합 관리 및 특수 페이지 탐색, 편집 중 표시, 롤백 확인, 최근 변경의 차이 미리보기, 저장 전 차이 탭, 재로그인 후 편집 이어가기, 간편 이미지 업로드, 원본 편집기의 시각적 표와 단축 입력을 후속 공통 제품에 포함합니다. FR-UX-001–009. |
| Q2 사이트 기능의 후속 확장 승인 | 지도·외부 영상, 사용자프로필, 스포일러·투표, 본문 내 토론, 분류·하위 문서 넘겨주기, 상위 링크 숨김, URL 변수, 오프라인 HTML, 렌더링 검색·선택적 원문 색인을 후속 선택 확장으로 개발합니다. FR-ADDON-001–012. |
| Q3 민감 기능의 운영 근거 조건 | CheckUser 지속 북마크·가림 처리된 공개 로그와 사용자 문서 소유자의 보호·삭제·복구는 리브레 위키 운영 근거가 확인될 때 이전 범위를 심사합니다. 기존 FR-ADMIN-008, FR-POLICY-002, FR-PAGE-003/004/005에 연결합니다. |

[SRS §11](02_REQUIREMENTS.md#11-후속-공통-제품과-선택-확장)에 동작과 수용 기준을 명시합니다. 선택 확장의 리브레 위키 전환 필수 여부는 실제 운영 의존성과 이전 수용 기준으로 따로 판정합니다.

## 첨부 원문과 기존 요구의 연결

표의 FR은 관련 요구입니다. 후보 동작에 연결된 FR은 의존성 또는 경계입니다. **기준**은 현재 제품 계약, **보완**은 기존 계약의 명세나 시험 보완, **후보**는 범위 결정이 남은 추가 동작, **이전 후보**는 현재 사이트 의존성 확인 대상, **충돌**은 기존 승인 계약으로 판정한 요청입니다.

| 원문 줄 | 요구 | 관련 FR·확장 | PRD·SRS 대조와 판정 |
|---|---|---|---|
| 7–9 | 대체 마크업 편집 | FR-EDIT-001, FR-EDIT-011, FR-PARSE-007, FR-COMP-004, EXT-033, FR-UX-009 | 후속 공통 제품. 편집기에서 대체 입력을 표준 Wikitext로 변환하며 미수정 원문과 시각 편집 왕복을 보존합니다. |
| 11–12 | 관리 기능 유지 | FR-SITE-002, FR-PAGE-004, FR-PAGE-005, FR-AUTH-002, FR-AUTH-003, FR-AUTH-010, FR-ADMIN-001, FR-POLICY-001, FR-POLICY-002 | 기준. 코어·번들 관리 기능과 실제 권한을 검증합니다. 사이트별 역할·추가 절차는 이전 인벤토리 대상입니다. |
| 14–23 | 관리 패널과 파일 수정 없는 설정 | FR-SITE-001, FR-SITE-002, FR-SITE-004, FR-SITE-005, FR-AUTH-005, FR-OPS-004, FR-POLICY-001, FR-UX-001 | 초기·사용자 설정과 역할별 관리는 기준입니다. 설정 요약 홈, 분류 탐색과 추가 웹 설정은 후속 공통 제품으로 137–142행과 통합합니다. |
| 25–26 | MariaDB·PostgreSQL·SQLite | FR-DBMS-001, FR-DBMS-002 | MariaDB·PostgreSQL은 기준. SQLite 제품 지원은 [ADR-0201](../../docs/adr/0201-relational-database-portability.md)의 네 DBMS 결정과 충돌합니다. [#9](https://github.com/fregmented/LibertyWiki/issues/9)의 SQLite는 비교 서버 구성입니다. |
| 28–29 | 다중계정 비교, 감사, 북마크, 공개 로그 | FR-ADMIN-008, FR-POLICY-002, FR-DUALAPI-006, EXT-003 | 복수 계정·IP 비교와 조사·추가 조회 감사는 CheckUser 기준입니다. 지속 북마크와 가림 처리된 공개 로그는 이전에 필요하다는 운영 근거가 확인될 때 검토합니다. [Special:Investigate](https://www.mediawiki.org/wiki/Help:Special_Investigate) |
| 31–33 | 외부 영상의 HTML5 삽입·모바일 재생 | FR-FILE-006, FR-PARSE-002, FR-A11Y-002, FR-EXT-002, FR-ADDON-002 | 후속 선택 확장. 기존 동영상 썸네일과 브라우저 재생을 구분합니다. 승인된 제공자·형식, 모바일 재생과 실패 상태를 검증합니다. |
| 38–40 | 완전한 시각 편집 | FR-EDIT-011, FR-PARSE-007, FR-COMP-004, EXT-033 | 기준. 대표 왕복 PoC와 GA의 번들 기능 전수를 구분합니다. 원본·시각 모드에서 표, 인용, 틀, 미수정 원문을 검증합니다. |
| 42–47 | 문서 내 CSS, 공통 CSS | FR-SITE-004, FR-API-005, FR-PARSE-002, FR-EXT-002, EXT-029 | 기준과 충돌. 권한 있는 사이트 스타일과 범위가 제한된 TemplateStyles는 기준입니다. 임의 CSS 주입은 격리 규칙과 충돌하며, 별도 문법은 실사용이 확인될 때 안전한 변환 또는 확장으로 검토합니다. 178–179행과 함께 봅니다. |
| 49–53 | 사용자 토론에서 본문으로 복귀 | FR-NS-001, FR-TALK-001 | 원문에서 해결로 표시했습니다. 사용자 문서↔사용자 토론의 양방향 이동은 일반 토론 회귀 기준에 포함합니다. |
| 55–59 | 리브레맵스, 유니폼 틀, 대진표·가계도 | FR-PARSE-003, FR-PARSE-008, FR-API-008, FR-EXT-003, FR-ADDON-001 | 지도 삽입은 후속 선택 확장입니다. 유니폼·대진표·가계도 틀과 모듈은 콘텐츠로 이관합니다. 표준 문법으로 처리되지 않는 include가 실제로 쓰이면 이전에서 검증합니다. |
| 61–64 | 클릭 가능한 이미지와 HTML 링크 | FR-LINK-001, FR-PARSE-001, FR-COMP-003, EXT-011 | 보완. 표준 파일 `link=`의 내부·외부·빈 대상을 검증합니다. 임의 HTML과 별도 중첩 링크 문법은 보안·문법 경계를 적용합니다. [이미지 링크](https://www.mediawiki.org/wiki/Help:Images#Link_behavior) |
| 66–70 | 외부 이미지 삽입과 후속 반대 | FR-PARSE-002, FR-COMP-003, FR-POLICY-001 | 충돌이 있는 제안. 호환 프로파일의 설정에 따른 외부 이미지와 허용 도메인을 시험합니다. 원문의 새 문법과 사이트에서 허용할 호스트는 별도 이전 조사 대상입니다. [외부 이미지 설정](https://www.mediawiki.org/wiki/Manual:$wgAllowExternalImagesFrom) |
| 72–73 | 사용자 문서에서 기여·프로필 열기 | FR-AUTH-002, FR-AUTH-003, FR-AUTH-004, FR-ADMIN-001, FR-API-008, FR-ADDON-003 | 기여 탐색과 권한별 공개 기록은 기준입니다. 사용자프로필과 공개 필드 집계는 후속 선택 확장으로 개발하며 기존 사이트 URL은 필요할 때 이전에서 검증합니다. |
| 75–78 | Enter로 줄바꿈 | FR-EDIT-001, FR-PARSE-001, FR-COMP-003, EXT-022, EXT-034, FR-UX-009 | 원문의 기본 개행 의미 변경은 충돌합니다. 기준의 줄바꿈 도구와 Poem을 검증하며 Enter 단축 입력은 7–9행의 후속 편집기에 연결합니다. |
| 80–82 | 사용자 문서의 소유자 권한 | FR-PAGE-003, FR-PAGE-004, FR-PAGE-005, FR-PAGE-007, FR-AUTH-002, FR-POLICY-002 | 기존 그룹·보호 계약은 기준입니다. 소유자의 권한 확대는 리브레 위키 이전에 필요하다는 운영 근거가 확인될 때 검토합니다. 루트 문서 이동에 반대하는 원문과 하위 문서 이동·넘겨주기 예외를 함께 확인합니다. |
| 84–86 | 해시태그로 분류 | FR-EDIT-001, FR-LINK-004, FR-API-006, FR-UX-009 | 기존 분류와 가젯 플랫폼은 기준입니다. 해시태그에서 표준 분류 링크로 변환하는 입력은 7–9행의 후속 편집기에 연결합니다. |
| 88–90 | 분류 넘겨주기 시 자동 재분류 | FR-LINK-003, FR-LINK-004, FR-COMP-003, FR-ADDON-007 | 기준 분류 멤버십의 자동 치환은 호환 계약과 충돌합니다. 목적 분류로의 치환을 명시적으로 활성화한 편집 흐름을 후속 확장으로 제공합니다. [분류 동작](https://www.mediawiki.org/wiki/Help:Categories#Redirecting_a_category) |
| 92–94 | 구조화된 투표 | FR-TALK-002, FR-EXT-001, FR-EXT-003, FR-API-008, FR-ADDON-005 | 구조화된 투표 상태·집계는 후속 선택 확장입니다. 기존 서명 투표는 원문·이력 보존 범위입니다. |
| 96–97 | 서버에서 수식 변환 | FR-PARSE-002, FR-COMP-003, EXT-015 | 기준. TeX 입력을 격리 환경에서 MathML/SVG 등 프로파일별 결과로 변환하고 브라우저에서 접근 가능한 표현을 검증합니다. 특정 LaTeX 실행기 선택은 구현 설계 사항입니다. [Math 렌더 모드](https://www.mediawiki.org/wiki/Extension:Math/en) |
| 99–101 | 스포일러 공개 확인 | FR-PARSE-003, FR-EXT-003, FR-A11Y-001, FR-ADDON-004 | 후속 선택 확장. 틀의 의미와 접근 가능한 공개 조작을 검증합니다. 표시 상태와 읽기 권한을 각각 확인합니다. |
| 103–108 | 필수 번들 제공과 PHP 확장 포팅 | FR-EXT-001, FR-EXT-002, FR-EXT-003, FR-LIC-002, FR-DUALAPI-005 | 번들 독립 구현은 기준. PHP 코드 변환·원본 확장 실행은 독립 구현 및 SPI 규약과 충돌합니다. |
| 110–114 | 본문에서 진행 중인 토론 발견·표시 | FR-TALK-001, FR-TALK-002, FR-WATCH-003, EXT-008, FR-ADDON-006 | 토론 연결·답글·구독은 기준입니다. 본문 내 표시·알림은 후속 선택 확장으로 제공하며 연결 토론의 읽기 정책을 적용합니다. |
| 116–122 | 편집 중 표시와 편집 병합 | FR-EDIT-002, FR-EDIT-009, FR-UX-002 | 충돌 감지와 병합 보조는 기준입니다. 기준 프로파일에서 겹치지 않는 변경의 자동 병합 조건을 검증합니다. 후속 공통 제품은 편집 중임을 알리는 UI입니다. [편집 충돌](https://www.mediawiki.org/wiki/Help:Edit_conflict) |
| 124–126 | 최근 변경에서 롤백 확인 | FR-EDIT-006, FR-AUTH-005, FR-WATCH-002, FR-UX-003 | 기준 프로파일의 확인 설정은 호환 기준입니다. 최근 변경에서의 확인·취소와 개인 설정을 후속 공통 UI 수용 기준에도 연결합니다. [롤백 설정](https://meta.wikimedia.org/wiki/WMDE_Technical_Wishes/Rollback) |
| 128–131 | 상위 넘겨주기에서 하위 대상 추론 | FR-PAGE-007, FR-LINK-003, FR-DUALAPI-006, FR-ADDON-008 | 후속 선택 확장. 명시적 하위 문서·넘겨주기를 우선하고 목적지 부재·순환·권한을 검증합니다. 169–170행의 일반 넘겨주기와 적용 조건이 다릅니다. |
| 133–135 | 최근 변경의 실제 변경점 미리보기 | FR-WATCH-002, FR-EDIT-005, FR-EDIT-007, FR-UX-004 | 후속 공통 제품. 기존 요약·필터·리비전 diff를 활용하고 두 리비전의 가시성을 확인합니다. |
| 137–142 | 특수 페이지 아이콘·타일·목록·권한 필터 | FR-SITE-002, FR-POLICY-002, FR-A11Y-001, FR-UX-001 | 특수 페이지 목록은 기준입니다. 타일·목록 전환과 권한 필터는 14–23행과 같은 후속 공통 관리 화면에서 검증합니다. |
| 144–153 | 표 약식 문법과 병합·속성 입력 | FR-PARSE-001, FR-COMP-003, FR-EDIT-011, EXT-034, FR-UX-009 | 표·병합·허용 속성의 기준 문법은 제품 범위입니다. 표 약식 입력은 표준 Wikitext를 생성하는 7–9행 후속 편집기에 연결합니다. |
| 155–158 | 각주·취소선 단축 입력 | FR-EDIT-001, FR-PARSE-001, FR-PARSE-007, EXT-004, EXT-034, FR-UX-009 | 각주·취소선의 기준 표현은 제품 범위입니다. 단축 입력은 표준 Wikitext를 생성하는 7–9행 후속 편집기에 연결합니다. |
| 160–161 | 편집 화면의 저장 전 차이 | FR-EDIT-001, FR-EDIT-005, FR-UX-005 | 저장 전 차이 보기는 기준입니다. 원문·미리보기와 전환하는 차이 탭은 후속 공통 UI입니다. 이력의 두 저장판 비교와 충돌 시 차이는 별도 시나리오입니다. [편집 도움말](https://www.mediawiki.org/wiki/Help:Editing_pages/en#Show_changes) |
| 163–164 | 유머성 취소선만 숨기기 | FR-PARSE-001, FR-EXT-003, FR-AUTH-005 | 취소선 숨기기 기능이 채택될 때 적용되는 조건부 후보입니다. 이미 폐기됐음을 나타내는 일반 취소선 의미를 보존합니다. |
| 166–167 | 문서별 상위 링크 숨김 | FR-PAGE-007, FR-NS-002, FR-COMP-003, FR-ADDON-009 | 기준 프로파일의 이름공간 설정과 상위 페이지 존재 여부를 시험합니다. 개별 문서의 상위 링크 숨김은 후속 선택 확장입니다. [하위 문서](https://www.mediawiki.org/wiki/Help:Subpages/en) |
| 169–170 | 원문 넘겨주기 문법 | FR-LINK-003, FR-PAGE-002, FR-COMP-003 | 기존 일반 `#REDIRECT`와 중복입니다. 원문에 추가로 쓰이는 별도 문법이 확인되면 이전 인벤토리에 등록합니다. |
| 172–173 | URL 변수의 파서 노출 | FR-PARSE-002, FR-PARSE-005, FR-EXT-002, FR-EXT-003, FR-ADDON-010 | 후속 선택 확장. 필요한 입력만 허용하며 파서·캐시 문맥에 가시성과 변수를 반영하고 민감 쿼리 노출을 시험합니다. |
| 175–176 | 단일 HTML 오프라인 묶음과 XML | FR-PAGE-010, FR-LINK-004, FR-PARSE-006, FR-FILE-007, FR-API-008, FR-ADDON-011 | XML은 기준입니다. 선택·하위·분류·링크·틀을 묶은 최신판 단일 HTML은 후속 선택 확장이며 링크·파일과 내보낼 시점의 권한을 검증합니다. |
| 178–179 | CSS 배경의 내부 이미지 | FR-PARSE-002, FR-FILE-007, FR-EXT-002, EXT-029 | 보완. CSS 경로별 스코프와 URL 허용 정책을 검증합니다. `File:`·`Media:`를 CSS URL 안에서 해석하는 새 규칙은 이전 후보입니다. [TemplateStyles](https://www.mediawiki.org/wiki/Help:TemplateStyles/en) |
| 181–182 | 파일의 class와 링크 동시 지정 | FR-LINK-001, FR-COMP-003, FR-FILE-005 | `class=`와 `link=` 각각은 기준입니다. 동시 지정은 고정 프로파일의 DOM과 클릭 대상을 확인하고 차이는 `compat_gap`으로 추적합니다. 별도 개선은 프로파일 확인 이후 판정합니다. |
| 184–185 | 세션 만료 시 저장 중지와 로그인 | FR-AUTH-001, FR-EDIT-003, FR-EDIT-009, FR-DUALAPI-006, FR-UX-006 | 인증된 편집의 만료 시 저장 중지는 기준입니다. 재로그인 뒤 초안을 이어가는 후속 공통 UI에서 저장을 다시 명시적으로 요청하고 권한과 기준판을 검사합니다. |
| 187–188 | 문서 ID 링크와 특정 판 링크 | FR-PAGE-002, FR-PAGE-008, FR-API-008, FR-RELEASE-001 | 기준과 이전. 현재 문서는 제목 변경 이후에도 문서 ID로, 특정 판은 후속 편집 이후에도 리비전 ID로 엽니다. 기존 사이트 URL·숫자 ID 연결은 이전에서 검증합니다. |
| 190–191 | 간편 이미지 업로드 | FR-FILE-001, FR-EDIT-011, FR-FILE-003, EXT-033, EXT-034, FR-UX-007 | 기준 구성의 편집기 업로드·삽입 흐름을 검증합니다. 이미지 선택부터 본문 삽입까지 이어지는 간편 흐름은 후속 공통 UI에서도 검증합니다. [업로드 대화상자](https://www.mediawiki.org/wiki/Upload_dialog/en) |
| 193–194 | 소도구와 HotCat 사례 | FR-API-006, FR-LINK-004, FR-EXT-002, EXT-010 | 가젯 플랫폼은 기준. 리브레 위키의 개별 가젯·분류 작업은 이전 의존성 조사 대상이며 승인된 UI 슬롯과 API에서 독립 구현합니다. |
| 196–197 | 원본 모드에서 시각적으로 표 편집 | FR-EDIT-001, FR-EDIT-011, FR-PARSE-007, EXT-034, FR-UX-008 | 표 삽입과 시각 모드 편집은 기준입니다. 원본 모드에서 표를 시각적으로 고치고 주변 Wikitext를 보존하는 동작은 후속 공통 제품입니다. |
| 199–200 | 렌더링 텍스트만 기본 색인, 선택적 원문 색인 | FR-SEARCH-001, FR-SEARCH-002, FR-SEARCH-003, FR-PARSE-006, FR-DUALAPI-006, FR-ADDON-012 | 후속 선택 확장. 확장이 활성인 프로파일의 렌더 텍스트 기본 검색과 별도 원문 색인을 제공하며 고정 Compat 검색 동작을 보존합니다. OpenSearch는 기존 색인 인프라를 사용합니다. [기본 검색 동작](https://www.mediawiki.org/wiki/Help:Searching) |

## 승인된 설계·지침의 요구 근거

| 승인 근거 | 관련 FR과 적용 경계 |
|---|---|
| [AGENTS §0·§3–6](../../AGENTS.md), [PRD §3](01_PRODUCT_PLAN.md#3-호환성-범위와-완료의-정의), [두 API 계약](14_DUAL_API_CONTRACT.md) | FR-COMP-001–004, FR-DUALAPI-001–012, FR-LIC-001/002. 고정 기준의 UI·API·Wikitext 동작과 독립 구현, 두 API의 권한·부작용 계약을 검증합니다. |
| [ADR-0201](../../docs/adr/0201-relational-database-portability.md) | FR-DBMS-001/002. 인터뷰 Q10–11, Q14, Q19, Q21–22의 DB 선택·지원 하한과 동등성. |
| [ADR-0202](../../docs/adr/0202-extension-and-release-boundaries.md), [PRD §9](01_PRODUCT_PLAN.md#9-제품-출시와-실제-이전의-분리) | FR-EXT-001–003, FR-STORE-001–004, FR-RELEASE-001, FR-API-008, FR-SITE-005, FR-PAGE-008, FR-AUTH-001/008, FR-ADMIN-007. 인터뷰 Q2–8, Q12–13, Q15–17, Q20, Q25–26의 독립 확장·저장소·1.0 프로파일과 별도 이전 승인. |
| [ADR-0205](../../docs/adr/0205-product-skins.md) | FR-API-005, FR-LIC-002. 제품 스킨 Liberty·여백, 두 스킨의 라이트·다크 모드, Liberty 코드·리소스 재사용 승인. |
| [ADR-0203](../../docs/adr/0203-runtime-and-infrastructure-independence.md) | FR-RUNTIME-001, FR-OPS-001/003/004. 앱 ARM64/x86-64와 독립 인프라 실행환경 및 사용자 선택 오케스트레이션. |
| [ADR-0204](../../docs/adr/0204-parser-test-data-boundary.md), [호환 설계](10_COMPATIBILITY.md) | FR-PARSERDATA-001, FR-PARSE-001/007/008, FR-COMP-003/004. 승인된 fixture 데이터의 출처·설정과 독립 실행기, 손실 없는 왕복 검증. |

## GitHub 이슈와 제품 계약

이슈는 구현 단위입니다. 한 이슈의 합격 기준이 해당 FR의 일부만 다루면 나머지는 SRS의 해당 단계와 제품 게이트에 연결합니다. 전체 구현·시험 상태는 [GitHub Issues](https://github.com/fregmented/LibertyWiki/issues)와 호환 계약에서 확인합니다.

| 이슈 원문 | 작업 범위 | 관련 FR |
|---|---|---|
| [#1](https://github.com/fregmented/LibertyWiki/issues/1) · [#2](https://github.com/fregmented/LibertyWiki/issues/2) · [#3](https://github.com/fregmented/LibertyWiki/issues/3) | 문서 생성·현재 원문·제목 정규화 | FR-PAGE-001, FR-PAGE-002, FR-NS-003 |
| [#4](https://github.com/fregmented/LibertyWiki/issues/4) · [#5](https://github.com/fregmented/LibertyWiki/issues/5) · [#6](https://github.com/fregmented/LibertyWiki/issues/6) | 원문 편집·저장 검사·충돌 | FR-EDIT-001, FR-EDIT-002, FR-EDIT-003 |
| [#7](https://github.com/fregmented/LibertyWiki/issues/7) · [#8](https://github.com/fregmented/LibertyWiki/issues/8) · [#9](https://github.com/fregmented/LibertyWiki/issues/9) | 리비전 이력·과거 원문 | FR-EDIT-004, FR-PAGE-008 |
| [#10](https://github.com/fregmented/LibertyWiki/issues/10) | 저장판끼리 차이 | FR-EDIT-005 |
| [#11](https://github.com/fregmented/LibertyWiki/issues/11) | 편집 충돌 시 게시된 차이 | FR-EDIT-002 |
| [#12](https://github.com/fregmented/LibertyWiki/issues/12) · [#13](https://github.com/fregmented/LibertyWiki/issues/13) | 최신·과거 단일 편집 취소 | FR-EDIT-006 |
| [#14](https://github.com/fregmented/LibertyWiki/issues/14) | 세션과 편집 작성자 | FR-AUTH-001, FR-API-003 |
| [#15](https://github.com/fregmented/LibertyWiki/issues/15) | 기존 문서 편집 보호 | FR-PAGE-005, FR-ADMIN-001 |
| [#16](https://github.com/fregmented/LibertyWiki/issues/16) | 연속 편집 롤백 | FR-EDIT-006 |
| [#17](https://github.com/fregmented/LibertyWiki/issues/17) | 기본 원문 렌더링 | FR-PARSE-001, FR-PARSE-002 |
| [#18](https://github.com/fregmented/LibertyWiki/issues/18) | 초안 렌더링 미리보기 | FR-EDIT-001, FR-PARSE-002 |
| [#19](https://github.com/fregmented/LibertyWiki/issues/19) | 최근 변경의 생성·편집 | FR-WATCH-002 |
| [#20](https://github.com/fregmented/LibertyWiki/issues/20) | 최근 변경에서 공개 기여 열기 | FR-AUTH-004 |
| [#21](https://github.com/fregmented/LibertyWiki/issues/21) | 문서 주시와 개인 목록 | FR-WATCH-001 |
| [#22](https://github.com/fregmented/LibertyWiki/issues/22) | 주시 문서의 최근 변경 | FR-WATCH-001, FR-WATCH-002 |
| [#23](https://github.com/fregmented/LibertyWiki/issues/23) | 제목 접두어 검색 | FR-SEARCH-001 |
| [#24](https://github.com/fregmented/LibertyWiki/issues/24) | 제목·본문 검색, 제목 폴백 | FR-SEARCH-001, FR-SEARCH-003, FR-SEARCH-005 |
| [#25](https://github.com/fregmented/LibertyWiki/issues/25) | 일반 문서 링크와 없는 대상 표시 | FR-LINK-001 |
| [#26](https://github.com/fregmented/LibertyWiki/issues/26) | 공개 역링크 | FR-LINK-002 |
| [#27](https://github.com/fregmented/LibertyWiki/issues/27) | 일반 문서 넘겨주기 | FR-LINK-003, FR-PAGE-002 |
| [#28](https://github.com/fregmented/LibertyWiki/issues/28) | 일반 문서 이동과 이전 제목 넘겨주기 | FR-PAGE-003, FR-ADMIN-001 |
| [#29](https://github.com/fregmented/LibertyWiki/issues/29) | PNG와 두 저장 백엔드 | FR-FILE-001, FR-FILE-007, FR-STORE-001, FR-STORE-002, FR-STORE-003 |
| [#30](https://github.com/fregmented/LibertyWiki/issues/30) | 기준 서버와 API 인벤토리 | FR-COMP-001, FR-COMP-002, FR-DUALAPI-009, FR-DUALAPI-010 |
| [#31](https://github.com/fregmented/LibertyWiki/issues/31) | ParserTests와 독립 실행기 | FR-PARSERDATA-001, FR-PARSE-001, FR-PARSE-002 |
| [#32](https://github.com/fregmented/LibertyWiki/issues/32) | 이름공간·제목 정규화 | FR-NS-001, FR-NS-003, FR-PAGE-001, FR-PAGE-002 |
| [#33](https://github.com/fregmented/LibertyWiki/issues/33) | 사이트 초기 설정과 익명 정책 | FR-SITE-001, FR-POLICY-001, FR-DUALAPI-006 |
| [#34](https://github.com/fregmented/LibertyWiki/issues/34) | 점검 모드 | FR-SITE-005, FR-DUALAPI-006, FR-DUALAPI-008 |
| [#35](https://github.com/fregmented/LibertyWiki/issues/35) · [#36](https://github.com/fregmented/LibertyWiki/issues/36) | 로컬 가입·비밀번호 재설정 | FR-AUTH-001, FR-API-003 |
| [#37](https://github.com/fregmented/LibertyWiki/issues/37) | 그룹 권한 부여·회수 | FR-AUTH-002, FR-AUTH-010, FR-POLICY-002, FR-ADMIN-001 |
| [#38](https://github.com/fregmented/LibertyWiki/issues/38) | 계정·IP 차단 | FR-AUTH-003, FR-EDIT-003, FR-ADMIN-001 |
| [#39](https://github.com/fregmented/LibertyWiki/issues/39) | 이동·생성 보호 | FR-PAGE-005, FR-EDIT-003, FR-ADMIN-001 |
| [#40](https://github.com/fregmented/LibertyWiki/issues/40) | 문서 삭제·복구 | FR-PAGE-004, FR-ADMIN-001 |
| [#41](https://github.com/fregmented/LibertyWiki/issues/41) | 토론 연결과 서명 | FR-TALK-001, FR-PARSE-001 |
| [#42](https://github.com/fregmented/LibertyWiki/issues/42) | 표·목록·외부 링크·주석 | FR-PARSE-001, FR-PARSE-002 |
| [#43](https://github.com/fregmented/LibertyWiki/issues/43) | 렌더링한 과거판 고정 링크 | FR-PAGE-008, FR-PARSE-002 |
| [#44](https://github.com/fregmented/LibertyWiki/issues/44) | 틀 포함과 치환 | FR-PARSE-003, FR-PARSE-006, FR-COMP-003 |
| [#45](https://github.com/fregmented/LibertyWiki/issues/45) | 틀 변경 후 재렌더·재색인 | FR-PARSE-006, FR-SEARCH-003 |
| [#46](https://github.com/fregmented/LibertyWiki/issues/46) | 표준 분류 편집·탐색 | FR-LINK-004 |
| [#47](https://github.com/fregmented/LibertyWiki/issues/47) | 소규모 편집·최근 변경 필터 | FR-EDIT-001, FR-EDIT-004, FR-WATCH-002 |
| [#48](https://github.com/fregmented/LibertyWiki/issues/48) | 공개 문서 메타데이터 | FR-PAGE-006 |
| [#49](https://github.com/fregmented/LibertyWiki/issues/49) | 일반 관리 감사 로그 | FR-ADMIN-001 |
| [#50](https://github.com/fregmented/LibertyWiki/issues/50) | 파일 교체와 이전 원본 | FR-FILE-002, FR-FILE-007, FR-STORE-001, FR-STORE-002, FR-STORE-003 |
| [#51](https://github.com/fregmented/LibertyWiki/issues/51) | 업무 큐 재시도 | FR-OPS-002 |
| [#52](https://github.com/fregmented/LibertyWiki/issues/52) | PNG 썸네일과 본문 표시 | FR-FILE-005, FR-FILE-007, FR-STORE-003, FR-PARSE-006 |
| [#53](https://github.com/fregmented/LibertyWiki/issues/53) | 파일 설명·라이선스·사용처 | FR-FILE-003, FR-LINK-006 |
| [#54](https://github.com/fregmented/LibertyWiki/issues/54) | 요청 ID와 서비스 상태 | FR-OPS-003 |
| [#55](https://github.com/fregmented/LibertyWiki/issues/55) | 백업과 격리 복구 | FR-OPS-001 |
| [#56](https://github.com/fregmented/LibertyWiki/issues/56) | 한도와 멱등 재시도 | FR-API-003, FR-DUALAPI-008, FR-DUALAPI-012 |
| [#57](https://github.com/fregmented/LibertyWiki/issues/57) | 언어·IME·RTL | FR-I18N-001, FR-WEB-001 |
| [#58](https://github.com/fregmented/LibertyWiki/issues/58) | 접근성·모바일 | FR-A11Y-001, FR-A11Y-002, FR-WEB-001 |
| [#59](https://github.com/fregmented/LibertyWiki/issues/59) | 서명된 예제 확장·격리·비활성화 | FR-EXT-001, FR-EXT-002, FR-EXT-003, FR-DUALAPI-005 |
| [#60](https://github.com/fregmented/LibertyWiki/issues/60) | Lua 위험 검증 | FR-PARSE-008, FR-PARSERDATA-001 |
| [#61](https://github.com/fregmented/LibertyWiki/issues/61) | Wikitext·편집 DOM 왕복 위험 검증 | FR-PARSE-007, FR-COMP-004, FR-PARSERDATA-001 |
| [#62](https://github.com/fregmented/LibertyWiki/issues/62) | 내부 MVP 통합 판정 | FR-DUALAPI-006, FR-DUALAPI-008, FR-DUALAPI-010, FR-PARSERDATA-001, FR-LIC-001, FR-LIC-002 |

이슈 범위보다 넓은 수용 기준을 다음과 같이 추적합니다.

- **편집과 이력.** [#10](https://github.com/fregmented/LibertyWiki/issues/10)은 저장된 판끼리 비교하고, [#11](https://github.com/fregmented/LibertyWiki/issues/11)은 충돌 시 게시된 차이를 표시하며 자동 병합을 `compat_gap`으로 둡니다. [#18](https://github.com/fregmented/LibertyWiki/issues/18)의 렌더링 미리보기에는 저장 전 원문 차이가 빠져 있습니다. FR-EDIT-001/002/005의 각 비교 기준과 무부작용을 따로 검증합니다. FR-EDIT-006의 웹 확인, FR-AUTH-001의 만료 후 편집 복구는 [#16](https://github.com/fregmented/LibertyWiki/issues/16)·[#14](https://github.com/fregmented/LibertyWiki/issues/14)의 기본 동작에 추가로 검증합니다.
- **문서·기여·최근 변경.** FR-PAGE-003의 토론·하위 문서 동반 이동, FR-LINK-002의 없는 대상·고아 문서 목록, FR-AUTH-004의 이름공간 필터·로그 범위, FR-WATCH-002의 편집·로그 통합 피드는 첫 이슈 범위를 넘어섭니다. 관련 기준에는 [#28](https://github.com/fregmented/LibertyWiki/issues/28), [#26](https://github.com/fregmented/LibertyWiki/issues/26), [#20](https://github.com/fregmented/LibertyWiki/issues/20), [#19](https://github.com/fregmented/LibertyWiki/issues/19)·[#47](https://github.com/fregmented/LibertyWiki/issues/47)·[#49](https://github.com/fregmented/LibertyWiki/issues/49)를 연결합니다. 기준 프로파일의 권한·필터·페이지네이션을 포함해 확인합니다.
- **기준 기능과 단계.** FR-EDIT-003의 저장 전 검증과 FR-ADMIN-003/EXT-001의 행위 필터 기능에는 서로 다른 단계 표기가 있습니다. 기본 입력·권한 검증, 활성 필터의 호출과 효과를 각각 추적합니다. FR-API-002의 P1 Action 어댑터 항목과 별개로 [#30](https://github.com/fregmented/LibertyWiki/issues/30)·[#62](https://github.com/fregmented/LibertyWiki/issues/62)의 P0 통합 범위에는 핵심 Action·REST 검증이 포함됩니다. FR-PARSE-007/008과 EXT-033/024의 전체 기능 수용은 [#60](https://github.com/fregmented/LibertyWiki/issues/60)·[#61](https://github.com/fregmented/LibertyWiki/issues/61)의 대표 위험군 시험과 구분합니다.
- **직접 구현 이슈가 필요한 기준.** 특수 페이지 목록, XML 내보내기, CheckUser, 시각 편집, Gadgets의 기능 계약은 인접한 MVP 이슈 또는 시험 하니스로는 충족 범위를 확인할 수 없습니다. 각 FR·EXT의 동작·API·권한·설정별 수용 기준으로 검증합니다. PRD의 베타 가젯 범위와 FR-API-006/EXT-010의 GA 기준은 개별 기능 명세에서 확인할 항목입니다.

## 이전 조건의 수용 기준

이전 승인에서 추가 동작을 요구할 때는 실제 활성 확장·설정, 대표 문서나 관리 작업, 기존 URL·ID, 데이터·권한·로그의 예를 근거로 남깁니다. 필요한 동작과 이관 방식을 운영자가 승인한 뒤 리허설에서 검증합니다. 미확인 항목에는 조사할 데이터와 관찰할 동작을 명시합니다.

- 문법·틀·모듈·가젯을 승인 범위대로 이관하거나 독립 구현한 확장으로 재현합니다. 현행 및 과거 리비전의 원문과 의미, 실패 및 권한 거부 시 동작을 비교합니다.
- 기존 문서 URL과 숫자 ID는 현재 문서나 정확한 과거 리비전에 연결합니다. 비공개·삭제된 대상은 제목·위치·본문·색인·캐시에 노출되지 않는지 검사합니다.
- 권한, 민감 로그, 외부 삽입, 검색 설정은 각각 실제 운영 구성을 고정하고 정상·거부·비활성화·복구를 검증합니다. 원본에 해당 공개 API가 있는 확장은 두 API 계약을 함께 검증합니다. 신규 도메인 기능의 Native 계약은 [두 API 계약](14_DUAL_API_CONTRACT.md)을 따릅니다.
- CheckUser 추가 기능은 현재 검사관 절차와 공개 범위, 필요한 보존 기간을 확인하고 운영 근거가 있는 경우에만 심사합니다. 공개 로그의 재식별과 로그 접근권을 시험 대상으로 삼습니다. 사용자 문서 위임의 운영 근거가 확인되면 보호·삭제·복구·이동 각각의 대상과 소유권 경계를 정하고, 관리자 권한으로의 상승과 다른 사용자 문서 접근을 검증합니다.
- 완료 판정은 [G-MIGRATION](08_ROADMAP_QA.md#gate-g-migration--특정-위키-이관-적합성)과 [이전 데이터 설계](05_DATA_DESIGN.md)의 범위, 데이터 일치, 읽기 유지·쓰기 동결, 재인증·복구 조건을 충족해야 합니다.
