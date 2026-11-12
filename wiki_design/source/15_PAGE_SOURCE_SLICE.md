# 15. 내부 시험용 문서 생성·현재 원문 조회·원문 편집

영어 첫 글자 대문자 규칙을 사용하는 일반 이름공간(0)의 Wikitext를 제목으로 생성하고, 제목·ID로 현재 revision을 조회하고, 현재 revision을 기준으로 기존 문서의 원문 전체를 편집한다. 넘겨주기 지시문도 입력한 원문 그대로 반환한다. 생성과 조회는 FR-PAGE-001/002, FR-NS-003 및 CAP-PAGE-CREATE, CAP-PAGE-READ, CAP-PAGE-READ-TITLE에 해당한다. 편집은 FR-EDIT-001/002/003 및 CAP-REVISION-COMMIT의 일부를 제공한다. 렌더링, 삭제, 보호, 이름공간 설정과 세션 인증은 후속 범위다.

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
| 쓰기 토큰 | `action=query&meta=tokens&type=csrf`. 내부 시험 프로필에서는 비세션 토큰 `+\\` 사용 | 익명 요청은 Action에서 얻은 토큰 필요. 내부 운영자 Bearer는 헤더 인증 | 운영자 Bearer 또는 허용된 익명 정책 |

출처: [Action Edit](https://www.mediawiki.org/wiki/API:Edit), [Revisions](https://www.mediawiki.org/wiki/API:Revisions), [Info](https://www.mediawiki.org/wiki/API:Info), [Tokens](https://www.mediawiki.org/wiki/API:Tokens), [REST API Reference](https://www.mediawiki.org/wiki/API:REST_API/Reference).

## 기존 문서 편집

`PageApplicationService`는 read와 edit 권한을 문서 조회 전에 확인한다. 익명 편집에는 `WIKI_ANONYMOUS_READ`와 `WIKI_ANONYMOUS_EDIT`가 모두 필요하다. 원문은 유효한 Unicode로 UTF-8 1,048,576바이트 이하이며, 빈 원문도 허용한다. 변경 요약은 유효한 Unicode 500코드포인트 이하이며, 요약이 없으면 빈 문자열을 저장한다. 세 API 경로는 같은 입력 검증과 쓰기 정책을 적용한다.

클라이언트는 현재 원문을 조회할 때 받은 기준 revision ID를 저장 요청에 포함한다. 기준 ID가 해당 문서의 현재 revision과 다르면 충돌로 종료한다. PostgreSQL 쓰기 트랜잭션은 현재 revision 포인터가 기준 ID인 경우에만 갱신하는 낙관적 동시성 제어를 사용한다. 경쟁 요청이 먼저 저장하면 충돌로 종료한다. 저장에 성공하면 직전 revision을 부모로 둔 불변 revision이 추가된다. 입력 원문이 현재 원문과 같아도 유효한 기준 ID로 저장하면 새 revision이 추가된다.

새 revision에는 작성자, UTC 시각, 변경 요약과 UTF-8 원문의 SHA-256을 저장한다. `revision.committed` 성공 감사와 outbox 이벤트를 동일 트랜잭션으로 기록하고 문서·revision·요청 ID를 연결한다. 실패나 충돌 시 이전 revision과 현재 포인터를 보존하고 성공 감사와 outbox를 추가하지 않는다. 응답을 받지 못한 클라이언트가 같은 기준 ID로 재전송하면 첫 저장이 커밋된 경우 충돌한다. 현재 원문을 다시 조회하여 결과를 확인할 수 있다.

| 경로 | 내부 시험 계약 |
|---|---|
| Native `POST /api/v1/pages/{pageId}/revisions` | JSON `baseRevisionId`(UUID), `source`, 선택 `summary`. 성공은 201과 `CurrentSourcePage`(새 `currentRevisionId`, 원문, 새 revision 시각). 없는 문서는 404 `PAGE_NOT_FOUND`, 오래된 기준은 409 `EDIT_CONFLICT`. JSON 오류는 400, 필드 유효성 오류는 422. |
| MediaWiki Action 폼 `POST /w/api.php` | `action=edit`, `title` 또는 숫자 `pageid`, 양의 정수 `baserevid`, `text`, `token`, 선택 `summary`와 `nocreate`. JSON formatversion 2를 사용한다. 성공은 `edit.result=Success`와 `oldrevid`, `newrevid`. 없는 제목은 `missingtitle`, 없는 페이지 ID는 `nosuchpageid`, 충돌은 `editconflict`를 `error.code`로 반환하며 HTTP 상태는 200이다. `createonly=1` 생성 경로는 위 계약을 따른다. |
| MediaWiki REST `PUT /w/rest.php/v1/page/{title}` | JSON `latest.id`(양의 정수), `source`, 문자열 `comment`, 선택 `content_model=wikitext`와 익명 요청용 `token`. 성공은 200 및 `source`와 새 `latest`. 없는 문서는 404 `apierror-missingtitle`, 오래된 기준은 409 `editconflict`이다. |

공식 Action 문서는 `edit`의 POST, 읽기·쓰기 권한, CSRF 토큰, 제목·페이지 ID, 기준 revision 및 `nocreate` 계약을 설명한다. REST 문서는 `PUT /v1/page/{title}`의 현재 revision 기반 업데이트, 200 응답, 충돌 시 409, 쿠키 인증의 CSRF 요구를 설명한다. 기준 revision을 생략한 REST 요청은 원본에서 생성 경로로 쓰일 수 있다. [Action Edit](https://www.mediawiki.org/wiki/API:Edit), [REST Update page](https://www.mediawiki.org/wiki/API:REST_API/Reference#Update_page).

웹에서는 조회한 문서의 UUID와 당시 `currentRevisionId`로 Native 편집을 제출한다. 충돌 시 편집 중인 원문과 요약을 유지하고 최신 문서를 확인할 수 있게 한다. 브라우저 편집 입력에서 줄바꿈은 LF로 정규화된다. API는 요청 본문의 줄바꿈을 보존한다. 자동 재시도와 자동 병합은 후속 범위다.

## 시험 계약과 compat_gap

- PostgreSQL 17에서 공개 HTTP 경계로 세 생성 경로의 성공, 제목 및 ID 조회, 입력 오류, 없는 문서, 익명 정책 거부, 동시 중복 생성을 시험한다. 문서·revision·감사·outbox의 식별자 및 행 수를 비교한다. 런타임이 제공하는 Native 성공·오류 응답을 OpenAPI 스키마와 대조한다. 웹은 같은 Native 계약을 사용하며 HTML이 포함된 원문도 텍스트로 표시한다.
- 버전을 확인한 MediaWiki 1.46.2 시험 인스턴스에서 Action과 REST의 생성·조회·누락·중복 계약 중 비교 가능한 필드를 검증한다. 사이트 설정에서 파생된 ID·시각·라이선스 값과 감사 형식은 직접 일치 대상으로 삼지 않는다.
- 편집 시험은 세 경로 각각에서 정상 저장, 이전 revision 보존, 부모와 현재 포인터, 권한과 유효성 오류, 같은 기준 ID로 경쟁하는 요청 중 한 건만 성공하는지 확인한다. 성공 감사·outbox에는 문서·revision·요청 ID가 일치해야 하며 거부된 요청으로 저장 상태가 바뀌면 안 된다. Native 응답은 OpenAPI와 대조하고, 웹은 충돌 후 편집 원문 보존을 확인한다. MediaWiki 차등 시험은 정상 업데이트와 충돌 등 기준 인스턴스에서 관찰할 수 있는 계약을 비교한다.
- compat_gap: MediaWiki의 쿠키·로그인/OAuth, 세션별 CSRF, 정확한 권한·오류 우선순위 및 모든 REST 오류 본문, HTTP 캐시·조건부 요청, 콘텐츠 필터 및 저장 전 치환, 자동 편집 요약, 로케일·이름공간·전체 제목 문법, 복수 제목·revision 조회 옵션, 전체 Action 전송/출력 변형, REST PUT 생성은 별도 계약과 차등 검증이 필요하다.
- 편집 compat_gap에는 오래된 기준의 자동 병합과 REST 충돌 diff·번역 메시지, 동일 원문 저장 시 Action `nochange` 및 REST 응답·부작용, 편집 요약 `comment: null`, 기준 ID를 생략한 Action 편집, 부분 편집과 타임스탬프·소규모 편집·주시 목록 옵션이 포함된다. 보호, 차단, 행위 필터, 미리보기와 Native ETag 조건부 저장은 제품 요구사항으로 추적한다.
- 첫 구현은 PostgreSQL 17을 사용한다. MariaDB 11.8, SQL Server 2022, Oracle 19c에서 스키마 이행, 동시 편집 직렬화, 이전 revision 보존, 실패 롤백 및 감사·outbox 원자성을 검증해야 한다. 구현 상태는 `INCOMPLETE`이며 GA 출시 게이트에는 이 검증, 호환성 차이 해소와 Wikitext 렌더링이 포함된다.

편집 작업의 범위는 [설계 #4](https://github.com/fregmented/LibertyWiki/issues/4), [구현 #5](https://github.com/fregmented/LibertyWiki/issues/5), [기능 검증 #6](https://github.com/fregmented/LibertyWiki/issues/6)에서 추적한다.
