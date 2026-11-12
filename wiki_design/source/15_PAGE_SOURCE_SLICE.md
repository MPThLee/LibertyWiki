# 15. 내부 시험용 문서 생성 및 현재 원문 조회

적용 범위는 FR-PAGE-001/002, FR-NS-003 및 CAP-PAGE-CREATE, CAP-PAGE-READ, CAP-PAGE-READ-TITLE이다. 영어 첫 글자 대문자 규칙을 사용하는 일반 이름공간(0)의 Wikitext를 제목으로 생성하고 제목·ID로 현재 revision을 조회한다. 넘겨주기 지시문도 입력한 원문 그대로 반환한다. 렌더링, 편집, 삭제, 보호, 이름공간 설정과 세션 인증은 후속 범위다.

## 제목 및 상태

1. 제목을 NFC로 정규화한 후 ASCII 공백과 밑줄을 하나의 공백으로 합치고 양끝에서 제거한다. 첫 코드포인트를 Unicode 대문자로 변환한 후 NFC로 정규화한다. 이후 글자의 대소문자는 보존한다.
2. 빈 값, `.`, `..`, `~~~` 포함 값, 제어문자, 퍼센트 인코딩·HTML 문자 참조 패턴, URL 경로에 영향을 주는 문자, 괄호 구문 `[]{}<>`, 파이프, 콜론을 거부한다. 정규화한 키는 UTF-8 255바이트 이하다. 콜론, 슬래시, 지역별 대소문자 및 다른 이름공간은 compat_gap이다.
   HTTP 경로에서 세미콜론은 `%3B`로 인코딩한다.
3. 공통 PageApplicationService에서 먼저 read 권한을 확인한다. 생성에는 edit과 create 권한도 필요하다. 읽기 권한이 없는 경우 기존 제목과 없는 제목에 동일한 거부 응답을 반환한다. 내부 시험용 운영자 Bearer 자격증명과 설정 가능한 익명 정책을 사용한다. 초기값은 익명 조회 허용, 익명 편집·생성 거부다.
4. PostgreSQL에서 `(namespace_id, normalized_title)`의 고유 제약으로 동시 요청을 직렬화한다. 같은 트랜잭션에서 문서, 부모가 없는 첫 불변 revision, 성공 감사 이벤트, 발행 대기 outbox 이벤트를 저장한다. 원문은 입력한 문자열 그대로 저장하고 SHA-256은 UTF-8 바이트에서 계산한다. 출처 요청 ID를 감사와 outbox에서 공통으로 사용한다.
5. 실패 시 새 문서·revision·감사·outbox가 남지 않는다. 네트워크 응답을 잃은 생성 요청을 같은 제목으로 재시도하면 충돌한다. 현재 원문을 다시 조회해 결과를 확인할 수 있다. Idempotency-Key 응답 재생 및 outbox 배달은 후속 범위다.

## 공개 계약

| 기능 | MediaWiki Action | MediaWiki REST | Native |
|---|---|---|---|
| 생성 | 폼 POST `action=edit&createonly=1`, `title`, `text`, `token`, 선택 `summary`. 성공은 `edit.result=Success`, `newrevid`. 중복은 `articleexists`, 잘못된 토큰은 `badtoken` | JSON POST `/v1/page`, 문자열 `title`, `source`, `comment`, 선택 `content_model=wikitext`. 성공은 201 및 Location, 중복은 409. 기준 서버는 `comment: null` 요청에 400을 반환한다 | POST `/api/v1/pages`, 201 및 ID Location, 409 충돌, 422 유효성 오류 |
| 현재 원문 | `action=query&prop=revisions`에 단일 `titles` 또는 `pageids`, `rvslots=main&rvprop=ids%7Ctimestamp%7Ccontent%7Ccontentmodel` 및 `format=json&formatversion=2`. 없는 문서는 `missing`, 원문은 `slots.main.content` | GET `/v1/page/{title}`, 200 및 `source`와 `latest`, 301 제목 정규화, 404 문서 없음 | GET `/api/v1/pages/by-title/{title}` 또는 `/api/v1/pages/{pageId}`, 200 현재 revision과 원문, 404 없음 |
| 제목 확인 | `action=query&prop=info`에 단일 `titles` 또는 `pageids`. 제목 변경은 `query.normalized` | 같은 제목 조회 route | 같은 제목 조회 operation |
| 생성 토큰 | `action=query&meta=tokens&type=csrf`. 내부 시험 프로필에서는 비세션 토큰 `+\\` 사용 | 익명 요청은 Action에서 얻은 토큰 필요. 내부 운영자 Bearer는 헤더 인증 | 운영자 Bearer 또는 허용된 익명 정책 |

출처: [Action Edit](https://www.mediawiki.org/wiki/API:Edit), [Revisions](https://www.mediawiki.org/wiki/API:Revisions), [Info](https://www.mediawiki.org/wiki/API:Info), [Tokens](https://www.mediawiki.org/wiki/API:Tokens), [REST API Reference](https://www.mediawiki.org/wiki/API:REST_API/Reference).

## 시험 계약과 compat_gap

- PostgreSQL 17에서 공개 HTTP 경계로 세 생성 경로의 성공, 제목 및 ID 조회, 입력 오류, 없는 문서, 익명 정책 거부, 동시 중복 생성을 시험한다. 문서·revision·감사·outbox의 식별자 및 행 수를 비교한다. 런타임이 제공하는 Native 성공·오류 응답을 OpenAPI 스키마와 대조한다. 웹은 같은 Native 계약을 사용하며 HTML이 포함된 원문도 텍스트로 표시한다.
- 버전을 확인한 MediaWiki 1.46.2 시험 인스턴스에서 Action과 REST의 생성·조회·누락·중복 계약 중 비교 가능한 필드를 검증한다. 사이트 설정에서 파생된 ID·시각·라이선스 값과 감사 형식은 직접 일치 대상으로 삼지 않는다.
- compat_gap: MediaWiki의 쿠키·로그인/OAuth, 세션별 CSRF, 정확한 권한·오류 우선순위 및 모든 REST 오류 본문, HTTP 캐시·조건부 요청, 콘텐츠 필터 및 저장 전 치환, 자동 편집 요약, 로케일·이름공간·전체 제목 문법, 복수 제목·revision 조회 옵션, 전체 Action 전송/출력 변형, REST PUT 생성은 별도 계약과 차등 검증이 필요하다.
- MariaDB 11.8, SQL Server 2022, Oracle 19c의 고유성·트랜잭션·마이그레이션 및 동시성 검증과 Wikitext 렌더링은 후속 범위다. 이 범위의 구현 상태는 INCOMPLETE이며 GA 출시 게이트를 충족하려면 위 차이를 해소해야 한다.
