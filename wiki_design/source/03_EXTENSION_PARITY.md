# 03. MediaWiki 기본 배포 확장 기능 동등성 설계

## 1. 번들 해석 및 버전 고정

공식 `Bundled extensions and skins` 목록을 기능 인벤토리로 사용했습니다. **이 문서에서는 MediaWiki 1.46.2 기준 34개 확장과 5종 스킨을 구현 범위 판단 대상으로 설정합니다.** 기능이 배포판에 포함되었다는 것은 기본적으로 서버에서 활성화되어 있다는 뜻이 아닙니다. 관리 UI에서 활성화 여부와 설정을 별도로 모델링합니다.

공식 현재 목록에 나타나는 **CodeMirror는 1.47+** 항목이므로 1.46.2 범위에서는 제외하고 P2 후속 항목으로 관리합니다. 그 외 `CirrusSearch`, `Semantic MediaWiki`, `Translate`, `MobileFrontend`, `AWS` 등은 이 목록의 기본 번들로 가정하지 않으며 선택 확장으로 분류합니다.

**동등성 수준:** F = 기능·사용자 동작, C = Wikitext/데이터, A = 기존 외부 API입니다. **F/C/A 모두 GA 필수**이며 표의 P0/P1/P2는 구현 선후 관계만 표시합니다. 일부 확장에 API가 없으면 해당 프로파일은 N/A로 기록합니다.

## 2. 기본 번들 34종 기능 대응 매트릭스

| EXT ID | 확장 | 요구 기능 | 구현 전략 | 수용시험 핵심 | 단계 |
|---|---|---|---|---|---|
| EXT-001 | **AbuseFilter** | 변수 기반 편집 필터·경고·차단·태그·로그 | Filter DSL→AST 검증 후 저장 트랜잭션 전 평가; rule execution budget | 경고/차단/태그, 오탐, 시간 초과, 규칙 비공개 테스트 | P1 |
| EXT-002 | **CategoryTree** | 카테고리 트리 동적 탐색 | 카테고리 그래프 조회 + 지연 로딩 + 순환 방지 | 상하위·수천 노드 로딩/순환 차단 | P1 |
| EXT-003 | **CheckUser** | 계정·IP 연관 조사 및 감사 | 별도 PII 저장소·검사관 전용 권한·목적 제한·복수 계정/IP 비교·로그 | 조사 및 양방향 추가 조회별 감사, 검사와 로그의 별도 권한, 비인가 사용자 차단 및 보존기간 | P2 |
| EXT-004 | **Cite** | `<ref>`·`<references>` 인용/각주 | 파서 인용 AST, 재사용/명명된 각주와 문헌 목록 | 동일 각주 재사용·중첩·미완성 태그 | P1 |
| EXT-005 | **CiteThisPage** | 문서 인용 형식·고정 리비전 표기 | 문서 metadata→인용 서지정보 생성 | 인용 문자열에 정확한 영구 링크·수정일 표기 | P1 |
| EXT-006 | **CodeEditor** | CSS/JS/JSON 코드 편집 IDE | 구문 하이라이트·lint·자동완성·탭 지원 | 키보드/IME/에러 표시 일치 | P1 |
| EXT-007 | **ConfirmEdit** | CAPTCHA/봇 방지 챌린지 | 행위 위험 평가 + pluggable CAPTCHA | 회원가입/대량 외부링크/오답/접근성 대체 | P1 |
| EXT-008 | **DiscussionTools** | 구조화 토론 답글·구독 | 토론 토픽 토큰 식별, 쓰기 뒤 별도 event | 원문 토론과 UI 답글/서명 의미 일치 | P1 |
| EXT-009 | **Echo** | 사용자 메시지/알림·읽음 상태 | 중앙 Notification 서비스 + 사용자 채널 설정 | 멘션/되돌림/구독 알림/읽음 상태 | P1 |
| EXT-010 | **Gadgets** | 사용자 또는 관리자 정의 UI 가젯 | 가젯 매니페스트·권한·스코프·CSP/샌드박스 | 임의 코드의 권한 상승·XSS 차단 | P2 |
| EXT-011 | **ImageMap** | 이미지 클릭 영역·좌표 링크 | 이미지맵 파서 AST·안전한 렌더러 | 다각형/사각형/원과 모바일 터치 영역 | P1 |
| EXT-012 | **InputBox** | 페이지 생성·검색 입력 위젯 | 제한된 폼 선언과 액션 리졸버 | 페이지 생성·검색 이동·입력값 필터 | P1 |
| EXT-013 | **Linter** | 문법 오류 진단/문서 관리 | 파서 진단 수집 + 특수 목록 | 구문 오류 위치/심각도/수정 후 갱신 | P1 |
| EXT-014 | **LoginNotify** | 신규 기기·의심 로그인 통지 | 새 IP/기기 감지 + 개인정보 최소 수집 | 오인 통지 억제·계정별 설정 | P1 |
| EXT-015 | **Math** | TeX 수식 렌더링 | 허용된 TeX subset → MathML/SVG sandbox | 프로파일별 서버 변환 결과·브라우저 접근성, 무한 처리·악성 매크로 차단 | P1 |
| EXT-016 | **MultimediaViewer** | 이미지 확대·메타데이터 라이트박스 | 모달 뷰어·키보드/이전/다음·메타데이터 | 모바일/접근성/파일 권한 검증 | P1 |
| EXT-017 | **Nuke** | 사용자 일괄 생성 문서 삭제 | 범위 미리보기→승인→비동기 소프트 삭제 | 최대 건수 제한·부분실패/실행 로그 | P1 |
| EXT-018 | **OATHAuth** | OTP/TOTP 2단계 인증 | TOTP 검증·복구 코드·관리 정책 | OTP 회전/재사용 방지·백업코드 폐기 | P1 |
| EXT-019 | **PageImages** | 문서 대표 이미지 추출 | 문서 렌더 AST에서 대표 이미지 선정 | 이미지 변경 시 문서 썸네일 갱신 | P1 |
| EXT-020 | **ParserFunctions** | 조건문·식·문자/시간 함수 | Wikitext ParserFunctionRegistry | 활성 함수 전수와 문자열 함수의 구성 차이, 함수별 정상·오류·중첩·자원 제한 | P1 |
| EXT-021 | **PdfHandler** | PDF 파일 미리보기/페이지 썸네일 | 별도 PDF 렌더 worker 제한된 샌드박스 | 큰 PDF/암호화/손상 파일 fallback | P1 |
| EXT-022 | **Poem** | 시와 줄바꿈 태그 렌더링 | `<poem>` parser plugin | 빈 줄·공백·마크업 속성 보존 | P1 |
| EXT-023 | **Replace Text** | 대량 본문 정규식 치환 | dry-run diff + batch mutation + undo 지점 | 선택/전후 diff/실패 재개/권한 검사 | P1 |
| EXT-024 | **Scribunto** | Lua 모듈 실행·`#invoke` | 버전 고정 Lua VM sandbox + 모듈 인터페이스 | CPU/메모리/라이브러리 접근 제한·모듈 100개 fixture | P1 |
| EXT-025 | **SecureLinkFixer** | 위험한/구식 외부 링크 자동 보안 보정 | 링크 정규화/검증과 대상 규칙 적용 | unsafe redirect·scheme 처리/정상 링크 보존 | P1 |
| EXT-026 | **SpamBlacklist** | 스팸 URL 패턴 목록 검사 | 최적화된 Regex/Trie, 허용 목록 우선순위 | 악성 URL 차단/예외/패턴 타임아웃 | P1 |
| EXT-027 | **SyntaxHighlight** | 문서 코드블록 구문 강조 | highlight worker 또는 클라이언트 렌더 | 언어 자동 감지/코드 인젝션 차단 | P1 |
| EXT-028 | **TemplateData** | 템플릿 변수 메타데이터 | TemplateData JSON 검증·에디터 dialog 동기화 | 필수·별칭·타입·기본값 폼/검증 | P1 |
| EXT-029 | **TemplateStyles** | 템플릿 scoped CSS | CSS 파서·허용 속성·스타일 스코핑 | 스코프 격리, CSS 경로별 허용 URL과 차단 URL 및 비공개 이미지 접근 | P1 |
| EXT-030 | **TextExtracts** | 문서 텍스트 요약·추출 API | 렌더 AST→plain text→요약길이 정책 | 테이블/각주/템플릿 제거 정책 및 접근권한 | P1 |
| EXT-031 | **Thanks** | 기여 감사 버튼·알림 | 기여 revision→감사 event 발행 | 중복 감사 방지·알림 수신 | P1 |
| EXT-032 | **TitleBlacklist** | 금지 문서/계정 이름 패턴 | TitlePolicy 엔진·관리자 테스트 UI | 금지 제목 생성·이동·사용자 등록 거부 | P1 |
| EXT-033 | **VisualEditor** | WYSIWYG 시각편집 | ProseMirror 계열 editor + AST serialization adapter | 링크·표·인용·틀 왕복, 소스 변환 경고, 기준 구성의 파일 업로드·삽입과 권한 거부 | P1 |
| EXT-034 | **WikiEditor** | 확장 도구모음 기반 위키텍스트 편집 | 코드 편집기 도구모음/위키 문법 삽입 | 툴바·키보드·미리보기, 기준 구성의 파일 업로드·삽입, 줄바꿈·표 삽입 | P0 |

## 3. 번들 스킨 5종 대응 범위

스킨은 원본 소스/CSS를 복사하지 않고 접근성·기능·네비게이션·URL·사용자 상호작용의 동등성을 검증하는 **독립 레이아웃 프로파일**로 재구현합니다. Theme Adapter는 네비게이션·목차·사이드바·콘텐츠·편집 액션 슬롯을 제공하며, 정확한 픽셀/브랜드 복제는 목표가 아닙니다.

| SKIN ID | 원본 스킨 | 신규 레이아웃 기능 | 검증 |
|---|---|---|---|
| SKIN-01 | MinervaNeue | 모바일용 내비게이션·목차·가독성 | 360/768/1440px, 키보드, 편집 진입, 목차, 테마 스냅샷 |
| SKIN-02 | MonoBook | 클래식 2열 내비게이션과 사이드바 | 360/768/1440px, 키보드, 편집 진입, 목차, 테마 스냅샷 |
| SKIN-03 | Timeless | 반응형 문서 레이아웃 | 360/768/1440px, 키보드, 편집 진입, 목차, 테마 스냅샷 |
| SKIN-04 | Vector | 전통적인 위키 내비게이션 | 360/768/1440px, 키보드, 편집 진입, 목차, 테마 스냅샷 |
| SKIN-05 | Vector 2022 | 향상된 목차·읽기 설정·탐색 | 360/768/1440px, 키보드, 편집 진입, 목차, 테마 스냅샷 |

## 4. 기본 번들에 포함되지 않는 선택 확장 후보

아래 후보의 P1/P2 표시는 기존 검토 우선순위이며 1.0 필수 범위에 자동 포함되지 않습니다. 도입 시 LibertyWiki 확장으로 범위를 별도 등록합니다. 기본 문서 검색은 코어 계약이며 OpenSearch 연결 자체는 외부 인프라 adapter입니다. S3 저장·서빙만 아래 표에서 명시한 내장 예외입니다.

| 구분 | 기능 | 대응 |
|---|---|---|
| 후속 배포 차이 | CodeMirror (공식 1.47+) | P2, 구문 편집 고도화 후보. 1.46.2 기본 범위에는 포함하지 않음 |
| 고급 검색 | CirrusSearch/Elastica | 제품 P1 OpenSearch 기능은 자체 검색 모듈로 구현; 기본 번들 외 CirrusSearch API는 해당 확장이 활성화된 baseline에 속하지 않는 한 범위 밖 |
| 구조화 데이터 | Semantic MediaWiki | P2 외부 플러그인. 코어 설계에 속성 확장 슬롯만 확보 |
| 번역 워크플로 | Translate/ContentTranslation | P2 다국어 프로젝트, 기본 i18n과 구분 |
| 파일 저장소 어댑터 | AWS | MediaWiki 확장의 동작 복제 대신 S3 호환 저장소를 신규 제품 기본 지원 |
| 모바일 API/프런트 | MobileFrontend | 신규 제품 반응형 웹 내장, 모바일 전용 API/클라이언트 호환은 별도 |
| 인증 연동 | PluggableAuth/LDAP | P1 OIDC/SAML 어댑터, 외부 LDAP 직접 연계는 선택 |

## 5. 복잡도·보안·허가 리스크

- **VisualEditor + Parsoid/Wikitext:** 템플릿/확장 태그의 원문 보존이 가장 큰 기술 위험입니다. 편집 가능한 노드와 opaque token을 구분합니다. 100~500개 대표 문서에 대해 HTML 비교/왕복 테스트를 운영합니다.
- **Scribunto:** Lua VM의 버전/표준 라이브러리/샌드박스/MW frame API 일치 범위를 명시해야 합니다. 단순 Lua 실행 지원은 MediaWiki Scribunto 완전 호환이 아닙니다.
- **CheckUser:** IP·기기 정보에 대한 별도 권한과 보유기간이 필요하며 법적 검토 결과 없이는 출시하지 않습니다.
- **Gadgets/CodeEditor:** 임의 사용자 JavaScript 실행을 기본 허용하지 않습니다. 안전한 manifest 기반 관리 도구로 시작하고 JS 호환은 별도입니다.
- **AbuseFilter/TitleBlacklist/SpamBlacklist:** 정규식 성능 제한, 과도한 차단 복구, 관리자 감사가 필수입니다.
- **Cite/Math/PDF/SyntaxHighlight:** 렌더링 워커는 서버와 격리하고 악성 입력·파일·CPU 소모 시험을 수행합니다.

## 6. 출처·주의

- 공식 목록: https://www.mediawiki.org/wiki/Bundled_extensions_and_skins
- WikiEditor: https://www.mediawiki.org/wiki/Extension:WikiEditor
- VisualEditor: https://www.mediawiki.org/wiki/Extension:VisualEditor
- Scribunto: https://www.mediawiki.org/wiki/Extension:Scribunto
- AbuseFilter: https://www.mediawiki.org/wiki/Extension:AbuseFilter

기본 제공 34종은 각각 manifest·버전·설치/활성/비활성/제거 수명주기를 갖는 LibertyWiki 확장으로 독립 구현합니다. 외부 확장과 같은 공개 SPI 및 플랫폼 persistence capability를 사용하며, 코어 내부 접근 특권을 두지 않습니다. 기본 제공·외부 확장 모두 MariaDB/PostgreSQL/SQL Server/Oracle에서 기능·권한·데이터 정합성 시험을 통과해야 합니다. LOCAL/S3 저장·서빙은 내장 예외입니다. 리브레 위키의 추가 확장·스킨 전체는 이 목록에 자동 편입하지 않습니다.


## 7. 확장 기능 API 및 파서 기능의 추가 필수 조건

- 번들 확장별 `action=query` module/권한/에러/parameter와 `rest.php` 등록 path를 실제 baseline에서 동적으로 인벤토리화합니다.
- **AbuseFilter/CheckUser/DiscussionTools/Scribunto/VisualEditor/Gadgets**는 API, 사용자 권한, 에러 형식, 페이지 의존성까지 비교하며 단순히 비슷한 화면만 구현하면 완료가 아닙니다.
- Scribunto는 Lua 모듈 호출 결과뿐 아니라 Lua sandbox, `mw.*`의 외부에서 관찰되는 동작, `#invoke` 오류 문자열의 호환성을 검증합니다. Lua 런타임은 별도 검토를 거쳐 호환 라이선스의 새 구현을 사용합니다.
- VisualEditor는 기존 자바스크립트 코드를 사용하지 않으며 신규 웹 편집기를 구현합니다. 생성 DOM의 의미/편집 대상 영역·선택적 직렬화 결과를 fixture로 비교합니다.
- 익명/비공개 위키에서는 Echo, CheckUser, Gadgets, Recent Changes, 파일 접근에 동일한 비밀유지/권한 정책을 적용합니다.
- 기본 번들은 배포에 포함된다는 의미이며 **설치 직후 모든 확장이 자동 실행되는 것은 아닙니다.** admin extension registry가 설정을 관리합니다.
- 34종 목록 외 CodeMirror는 MediaWiki 1.47+ 배포에 추가된 항목이며 기준 1.46.2 정식 호환 범위 밖입니다.

## 8. 각 확장의 이중 공개 API 등록

기본 번들 EXT-001 ~ EXT-034 및 신규 플러그인의 **기능/운영 관리 작업은 모두 Native RESTful operation으로 명세**합니다. 각 기능에 MediaWiki 코어 또는 기존 확장의 Action/REST 외부 API가 실제 존재하면 **MediaWiki Compatible adapter도 별도 등록하여 정밀 호환**합니다. baseline에 없는 MediaWiki API 경로를 억지로 만들어 거짓 호환성을 주장해서는 안 됩니다. 기존 해당 기능이 API로 제공되지 않고 UI에서만 실행 가능한 경우에는 `compatType=UI_ONLY`, `compatRoute=N/A`, `nativeRoute=필수`로 등록합니다.

- 예: AbuseFilter 룰 검사 API → `api.php?action=abusefiltercheckmatch`와 신규 Native 검사 endpoint를 모두 유지합니다(기준 버전에서 실제 존재/활성 확인 후 등록).
- 예: CheckUser 민감 조회 → 대응하는 기존 API가 있으면 호환 계약을 유지하고 신규 Native에도 엄격한 권한·감사를 적용합니다.
- 예: 확장 설정·사용자 스크립트·가젯 → 외부 API 노출 여부는 인벤토리로 확인하되 신규 Native 관리 API는 반드시 구현합니다.
- 향후 기본 번들 추가/교체 시 `EXT-*` 목록·compat profile·native registry·테스트·관리 UI를 같은 변경에서 갱신합니다.
