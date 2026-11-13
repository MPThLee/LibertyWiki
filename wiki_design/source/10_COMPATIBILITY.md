# 10. MediaWiki 정밀 호환성·위키텍스트 검증 설계

## 1. 버전 프로파일

최초 기준은 **MediaWiki 1.46.2 + 기준 환경에 실제 활성화된 번들 확장 설정**입니다. 한 버전이라도 언어·설정·확장 활성화에 따라 API module, 파서 동작과 권한이 달라집니다. `compat-profile.yaml`에는 기준 MediaWiki 빌드·extensions·content language·namespace·rights·parser config·observed endpoints를 명시합니다. 미래 릴리스에서는 프로파일을 **새로 추가**하며 과거 규격을 무단 변경하지 않습니다.

## 2. MediaWiki Compatible API 검증

- **Action API**: `api.php` action/query/prop/list/meta/generator 전체, 등록 파라미터·기본값·리다이렉트·format/formatversion·XML/JSON·continuation·오류 코드·상태코드·권한·토큰·maxlag.
- **MediaWiki REST API**: `rest.php` 경로/HTTP method·content negotiation·리비전/HTML·conditional requests·로그인/쓰기·오류·캐싱.
- **핵심 원칙**: 단일 버전에서 실제 제공되는 모든 기존 엔드포인트/operation을 API inventory로 수집하고 환경 변수의 영향을 테스트합니다. 원본에 존재하지 않는 route를 임의로 호환 API라고 주장하지 않습니다.
- **플러그인**: 확장 활성화가 기존 API에 영향을 미치는 경우 module registration과 기능 노출을 결합 테스트합니다.

## 3. Wikitext·편집·파서 동등성

1. lexer + parser + transclusion expander + parser function registry + Lua sandbox + DOM renderer + selective serializer를 역할별로 분리합니다.
2. 파서 입력에 제목·언어·이름공간·page/revision context·template/module dependency version을 포함합니다.
3. 파서 fixtures: 표, 링크, 한글·RTL, nested parser functions, magic words, `nowiki`, template recursion, unclosed tags, syntax variants, file captions, Cite/Math/Poem/TemplateStyles/SyntaxHighlight, Lua frame/mw.*.
4. 편집 fixtures: 원본 Wikitext→DOM→비편집 부분 보존 직렬화 및 변경된 부분만 수정되는 roundtrip; DOM 안정성은 canonicalized DOM semantic tree, src-span/provenance로 비교.
5. 대형 템플릿 의존성 invalidation, Lua resource budget, cache poison, XSS escape, malicious styles를 검증합니다.
6. 사용자 승인에 따라 기준 코어·번들 확장·Parsoid의 ParserTests **fixture 데이터**를 사용합니다. 출처·라이선스·원래 고지·버전/해시·조건을 보존하며 **원본 파서·테스트 실행기·정규화기·훅 구현 코드는 복사하지 않습니다**. 실제 위키 코퍼스의 콘텐츠 라이선스·개인정보·서비스 약관은 별도로 검토합니다.

## 4. 차등 시험 프로토콜

- baseline 환경의 공개 동작을 HTTP/API/UI를 통해 시험하고 expected fixture를 결과로 기록합니다. 민감 자료/개인정보는 비식별 처리합니다.
- 비교 대상의 구성 변경은 fixture metadata에 남깁니다. 동일한 입력이 다르게 동작하면 원인을 `config`, `version`, `compat_gap`, `expected_nondeterminism`으로 분류합니다.
- **기존 API의 외부 observable fields·HTTP**는 엄밀 비교하고, 시간·nonce 등 비결정적 값만 정의된 정규화 규칙으로 비교합니다.
- **Wikitext**는 텍스트/DOM 구조·링크·분류·중첩·템플릿 의존·원문 보존을 비교하되, upstream이 보장하지 않는 internal HTML의 단순 속성 순서 등에만 한정된 정규화를 적용합니다.
- 편집·삭제·보호·파일 업로드는 read-only fixture가 아니라 **동일한 state transition·audit trail**까지 비교해야 합니다.

## 5. 두 공개 API의 호환성과 동등성

MediaWiki Compatible 계열과 Native RESTful 계열은 **동일한 기능 수**와 **같은 데이터/인가 결과**를 보장하지만, request/response wire format 자체까지 양 계열이 같을 필요는 없습니다. 기존 원본 규격과의 byte-level/field-level 비교는 **MediaWiki Compatible**에만 적용합니다. `specs/api_operation_registry.csv`를 두 계열의 매핑 장부로 삼고, 향후 MediaWiki 버전 업데이트에서도 그 장부를 반드시 함께 업데이트합니다.

## 6. GA 판정

- MediaWiki 1.46.2 구성의 Action/REST 경로 inventory 전수·매개변수·권한·오류 계약 시험 통과.
- 활성 기본 배포 확장의 사용자 기능·Wikitext·API 동작 전수 통과.
- 제품 UI는 [Liberty와 여백의 스킨 수용 기준](03_EXTENSION_PARITY.md#3-제품-스킨-2종)을 통과해야 합니다. 스킨 선택에 해당하는 코어 API는 기준 서버의 계약을 검증합니다.
- 기능별 Native RESTful 계약 등록률 100%, API 간 state/ACL/audit parity 불일치 0.
- 고정 데이터셋의 revision/files 해시·공개 ID/URL 의미 보존을 포함한 이관 기능 시험 통과. 특정 운영 위키의 실제 전환 승인은 별도 G-MIGRATION이며 그 사이트 전체 기능을 GA 범위로 자동 편입하지 않습니다.
- 향후 버전 지원 표기는 **그 버전의 API 이중 표면과 위키텍스트를 검증한 뒤**에만 합니다.

## 7. 참고 문서

- https://www.mediawiki.org/wiki/API:Action_API
- https://www.mediawiki.org/wiki/API:REST_API
- https://www.mediawiki.org/wiki/Parsoid
- https://www.mediawiki.org/wiki/Manual:Parser_tests
- https://www.mediawiki.org/wiki/Version_lifecycle

## 8. 코퍼스 고정과 비교 분모

실제 코어/번들/Parsoid 테스트 데이터에는 출처·revision·SHA-256·라이선스·고지·준비 문서·config/options·언어·확장 requirement·모드·비교 규칙을 함께 관리합니다. 빈 manifest나 별도 실행 결과 파일은 저장소에 추가하지 않습니다. legacy HTML과 Parsoid integrated/standalone 기대값, 기대 metadata와 manual selser의 편집/원문 결과를 구분합니다. 출력 정규화는 공식 형식과 관찰한 계약을 근거로 독립 구현하며, 권한·링크·분류·의미나 원문 손실을 숨기는 정규화를 허용하지 않습니다.

선택 PoC 위험군의 의미/왕복 시험 100%, 손실 0건과 자원 안전성을 요구합니다. 전체 코퍼스의 모드별 결과와 분모는 별도로 공개합니다. 확장 requirement 누락으로 건너뛴 파일, disabled, known failure, 미지원 또는 NOT_RUN을 PASS로 합산하지 않습니다. API·Lua·편집기·확장 coverage는 자료 인벤토리로 확인하며 ParserTests 통과만으로 전체 제품 호환을 선언하지 않습니다. 현재 자료 인벤토리와 실행 결과는 없습니다.

공식 데이터 형식: https://www.mediawiki.org/wiki/Manual:Parser_tests
