# 15. 내부 시험용 문서 생성·원문 조회·편집·이력

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

## 리비전 이력과 이전 원문

일반 이름공간의 Wikitext 문서에서 `FR-EDIT-004`의 이력 조회(`CAP-REVISION-HISTORY`)와 `FR-PAGE-008`의 리비전 ID 기반 원문 조회(`CAP-REVISION-READ`)를 제공한다. 문서의 현재 포인터가 바뀐 뒤에도 UUID 또는 MediaWiki 호환 숫자 ID로 동일한 리비전에 접근한다. 최초 리비전의 부모는 Native에서 `null`, Action에서 `0`이며, 후속 리비전은 저장 당시 직전 리비전을 가리킨다. 원문은 해당 리비전의 main 슬롯에서 읽고, 넘겨주기 지시문과 줄바꿈을 저장된 그대로 반환한다.

`PageApplicationService`에서 공통 읽기 정책을 적용한 뒤 문서 이력과 단일 리비전을 조회한다. 읽기 금지 시 기존 문서·리비전과 존재하지 않는 ID에 동일한 접근 거부를 반환하며, 제목·원문·요약·작성자·해시·페이지네이션 정보가 응답에 드러나지 않아야 한다. 익명과 운영자 Bearer에는 현재 원문 조회와 같은 정책을 적용한다. 두 API 계열의 읽기 응답과 오류에 `Cache-Control: no-store`를 적용한다. 내부 시험에는 공개 리비전과 사이트 전체 읽기 정책이 포함된다. 문서·리비전 단위 가시성을 추가할 때에는 소속 문서에 대한 읽기 권한과 본문·요약·작성자별 가시성을 공통 서비스에서 판정한다. 숨긴 필드에서 유도한 크기·해시·델타와 탐색 링크에도 동일한 공개 정책을 적용한다.

이력은 저장한 UTC 시각의 역순으로 정렬한다. 같은 시각이면 숫자 리비전 ID의 역순으로 정렬한다. 각 페이지는 크기 제한을 적용하고, 정렬 경계와 소속 문서를 함께 검사한다. 다음 페이지에서 이전에 본 항목을 다시 반환하지 않으며, 정렬 경계보다 최신인 리비전이 추가되어도 더 오래된 페이지로 이어서 탐색한다. PostgreSQL 17 구현은 요청 시작 시 읽은 현재 포인터를 리비전 범위의 상한으로 사용한다. 문서 ID, 시각, 숫자 리비전 ID로 탐색하며 이력 조회 결과에 원문 전체를 포함하지 않는다. 단일 리비전은 고유 ID로 조회한다. 읽기는 현재 포인터·리비전·성공 감사·outbox를 변경하지 않는다.

### Native RESTful과 웹

| 요청 | 성공 | 오류 |
|---|---|---|
| `GET /api/v1/pages/{pageId}/revisions` | 200 및 `revisions` 배열. 각 항목은 `id`, `parentRevisionId`, UTC `createdAt`, `summary`를 포함한다. `limit`은 1–50(기본 20)이다. `nextCursor`는 다음 페이지가 있을 때 마지막 항목의 UUID, 없을 때 `null`이다. `cursor`로 전달한 리비전은 다음 페이지에서 제외한다. | 잘못된 `limit`은 400 `INVALID_LIMIT`. 잘못된 UUID, 없는 리비전 또는 다른 문서의 `cursor`는 400 `INVALID_CURSOR`. 문서 없음은 404 `PAGE_NOT_FOUND`, 잘못된 문서 UUID는 422 `INVALID_PAGE_ID`. |
| `GET /api/v1/revisions/{revisionId}` | 200 및 `id`, `parentRevisionId`, `createdAt`, `summary`, `pageId`, `normalizedTitle`, `source`, `contentModel`(`wikitext`). | 리비전 없음은 404 `REVISION_NOT_FOUND`, 잘못된 UUID는 422 `INVALID_REVISION_ID`. |

두 요청에 유효하지 않은 Bearer를 제출하면 401을 반환한다. 읽기 권한이 없으면 403을 반환한다. 자세한 스키마는 [Native OpenAPI](../specs/openapi.yaml)를 따른다.

웹의 문서 화면에서 이력 버튼으로 첫 20건을 조회하고 `nextCursor`가 있을 때 더 오래된 항목을 요청한다. 이력 항목을 선택하면 해당 UUID의 원문을 텍스트로 표시하며 현재 편집 중인 원문과 요약을 유지한다. Native 원문 URL은 리비전 UUID에 고정된다. 렌더링된 과거 판의 공유 URL과 MediaWiki `oldid` 및 `Special:PermanentLink` 링크 보존은 후속 범위다. [MediaWiki History](https://www.mediawiki.org/wiki/Help:History), [MediaWiki PermanentLink](https://www.mediawiki.org/wiki/Help:PermanentLink).

### MediaWiki Compatible 인벤토리

| 표면 | 공개 계약 근거 |
|---|---|
| Action 이력 | `GET /w/api.php?action=query&prop=revisions`에 단일 `titles` 또는 `pageids`와 `rvlimit` 등 열거 옵션을 지정한다. `rvdir=older`가 기본이며 `rvdir=newer`는 오래된 항목부터 열거한다. `rvprop`은 메타데이터·원문·슬롯의 반환 필드를 지정한다. 응답에 `continue`가 있으면 같은 조회에 `continue`와 `rvcontinue` 값을 다시 전달한다. 공식 문서는 읽기 권한을 요구하며 접근 거부(`rvaccessdenied`), 복수 문서와 열거 옵션의 조합, `revids`와 열거 옵션의 조합(`rvrevids`)에 대한 오류를 기술한다. [Revisions](https://www.mediawiki.org/wiki/API:Revisions), [Continue](https://www.mediawiki.org/wiki/API:Continue). |
| Action 이전 원문 | 같은 module에서 양의 숫자 `revids`로 리비전을 지정한다. `rvprop=ids%7Ctimestamp%7Ccomment%7Ccontent%7Ccontentmodel`, `rvslots=main`, `format=json&formatversion=2` 요청의 원문 위치는 `query.pages[].revisions[].slots.main.content`이다. 공식 문서의 숨김 표시에는 `texthidden`, `commenthidden`, `userhidden`이 있으며 가능한 오류 목록에는 `rvnosuchrevid`가 있다. 직접 ID 조회에서 누락된 ID를 표현하는 방식과 숨김 권한별 응답은 기준 서버 차등 검증 대상이다. [Revisions](https://www.mediawiki.org/wiki/API:Revisions), [RevisionDelete](https://www.mediawiki.org/wiki/Help:RevisionDelete). |
| REST 이력 | `GET /w/rest.php/v1/page/{title}/history`는 기본 최신순으로 최대 20건씩 반환한다. `older_than` 또는 `newer_than`에 숫자 리비전 ID를 지정하면 해당 ID보다 오래되거나 새로운 구간을 반환한다. 응답에는 `revisions`, `latest`, 선택 `older`·`newer`가 있다. `filter`는 `reverted`, `anonymous`, `bot`, `minor` 중 하나다. 둘 이상의 방향을 지정하거나 매개변수가 잘못되면 문서화된 상태는 400이고, 문서나 리비전이 없으면 404이다. [REST Get page history](https://www.mediawiki.org/wiki/API:REST_API/Reference#Get_page_history), [공개 응답 예](https://en.wikipedia.org/w/rest.php/v1/page/Earth/history). |
| REST 이전 원문 | `GET /w/rest.php/v1/revision/{id}`는 성공 시 200 JSON으로 리비전과 `source`를 반환한다. 문서화된 누락 상태는 404이다. 공개 응답은 `content_model`, `page`의 `id`·`key`·`title`, `license`, `timestamp`, `user`, `comment`, `size`, `delta`, `minor`를 포함한다. 메타데이터 조회용 `/v1/revision/{id}/bare`와 HTML 경로는 별도 계약이다. [REST Get revision source](https://www.mediawiki.org/wiki/API:REST_API/Reference#Get_revision_source), [공개 응답 예](https://en.wikipedia.org/w/rest.php/v1/revision/764138197). |

목표 버전은 [MediaWiki 1.46.2](https://www.mediawiki.org/wiki/Release_notes/1.46)다. 내부 시험 Action은 열거 옵션을 생략하면 현재 포인터의 1건을 반환한다. 명시적 `rvlimit`은 1–50건의 메타데이터를 반환한다. `continue=||` 및 `rvcontinue=yyyyMMddHHmmss|revid`에서 UTC 타임스탬프와 ID는 다음 페이지의 첫 항목을 가리킨다. 토큰의 타임스탬프와 ID가 요청 문서의 리비전과 일치해야 한다. 원문은 단일 `revids` 또는 `rvlimit=1`에서 요청한다. 존재하지 않는 `revids`는 HTTP 200의 `query.badrevids`에 ID와 `missing`으로 표현한다.

내부 시험 REST 이력은 정규화된 제목을 사용하고, 정규화가 필요한 요청에 301을 반환한다. 이동 방향은 `older_than` 또는 `newer_than` 한 가지를 지정한다. 앵커를 제외한 구간을 반환하고 결과가 비어도 `latest`를 제공한다. 존재하지 않는 리비전 원문은 404 `rest-nonexistent-revision`, 요청 문서에 속하지 않는 이력 앵커는 404 `rest-nonexistent-title-revision`이다. 최초 리비전의 `delta`는 `null`이며 후속 리비전에서는 부모와의 UTF-8 바이트 크기 차이를 반환한다.

## 시험 계약과 compat_gap

- PostgreSQL 17에서 공개 HTTP 경계로 세 생성 경로의 성공, 제목 및 ID 조회, 입력 오류, 없는 문서, 익명 정책 거부, 동시 중복 생성을 시험한다. 문서·revision·감사·outbox의 식별자 및 행 수를 비교한다. 런타임이 제공하는 Native 성공·오류 응답을 OpenAPI 스키마와 대조한다. 웹은 같은 Native 계약을 사용하며 HTML이 포함된 원문도 텍스트로 표시한다.
- 버전을 확인한 MediaWiki 1.46.2 시험 인스턴스에서 Action과 REST의 생성·조회·누락·중복 계약 중 비교 가능한 필드를 검증한다. 사이트 설정에서 파생된 ID·시각·라이선스 값과 감사 형식은 직접 일치 대상으로 삼지 않는다.
- 편집 시험은 세 경로 각각에서 정상 저장, 이전 revision 보존, 부모와 현재 포인터, 권한과 유효성 오류, 같은 기준 ID로 경쟁하는 요청 중 한 건만 성공하는지 확인한다. 성공 감사·outbox에는 문서·revision·요청 ID가 일치해야 하며 거부된 요청으로 저장 상태가 바뀌면 안 된다. Native 응답은 OpenAPI와 대조하고, 웹은 충돌 후 편집 원문 보존을 확인한다. MediaWiki 차등 시험은 정상 업데이트와 충돌 등 기준 인스턴스에서 관찰할 수 있는 계약을 비교한다.
- 이력 시험은 생성과 연속 편집 후 세 공개 경로에서 같은 리비전 순서와 지원하는 부모 관계, 원문 및 요약을 확인한다. 페이지 경계, 마지막 페이지, 새 편집 후 계속 조회, 누락 ID, 잘못되거나 다른 문서에 속하는 페이지네이션 기준, 읽기 거부 및 운영자 조회를 시험한다. 거부 응답에 원문·제목·요약·링크가 없어야 하며, 조회 전후 문서 현재 포인터와 리비전·감사·outbox가 같아야 한다. Native 성공·오류 응답은 OpenAPI와 비교한다. 웹은 더 오래된 이력, 읽기 전용 이전 원문과 편집 입력 보존을 확인한다. 차등 시험은 1.46.2에서 관찰한 Action 계속 토큰·숫자 ID 조회와 REST 이력 링크·단일 리비전 원문·상태를 비교한다.
- compat_gap: MediaWiki의 쿠키·로그인/OAuth, 세션별 CSRF, 정확한 권한·오류 우선순위 및 모든 REST 오류 본문, HTTP 캐시·조건부 요청, 콘텐츠 필터 및 저장 전 치환, 자동 편집 요약, 로케일·이름공간·전체 제목 문법, 복수 제목·revision 조회 옵션, 전체 Action 전송/출력 변형, REST PUT 생성은 별도 계약과 차등 검증이 필요하다.
- 편집 compat_gap에는 오래된 기준의 자동 병합과 REST 충돌 diff·번역 메시지, 동일 원문 저장 시 Action `nochange` 및 REST 응답·부작용, 편집 요약 `comment: null`, 기준 ID를 생략한 Action 편집, 부분 편집과 타임스탬프·소규모 편집·주시 목록 옵션이 포함된다. 보호, 차단, 행위 필터, 미리보기와 Native ETag 조건부 저장은 제품 요구사항으로 추적한다.
- 이력 compat_gap에는 가져온 리비전의 시각·ID 경계 검증, 다중 ID·다중 원문·슬롯·필터와 전체 메타데이터, Action의 오래된 순 정렬·포맷·계속 토큰 변형, REST 필터·캐시·조건부 요청, 전체 제목 정규화·넘겨주기, 이전 ID 이관, 삭제·필드별 숨김·억제 권한과 오류 표현이 포함된다. 시험 환경 작성자는 익명/운영자 두 역할로 표현한다. 렌더링·diff·메타데이터 전용 REST 경로와 이전 URL 호환은 별도 기능으로 추적한다.
- 첫 구현은 PostgreSQL 17을 사용한다. MariaDB 11.8, SQL Server 2022, Oracle 19c에서 스키마 이행, 동시 편집 직렬화, 이전 revision 보존, 실패 롤백 및 감사·outbox 원자성을 검증해야 한다. 구현 상태는 `INCOMPLETE`이며 GA 출시 게이트에는 이 검증, 호환성 차이 해소와 Wikitext 렌더링이 포함된다.
- 이력과 이전 원문의 DB 검증은 위 세 DBMS에서도 기존 문서·리비전의 ID 매핑 보존, 안정적인 정렬과 경계 조회, 동시 편집 중 페이지 탐색, UTC 및 Unicode 원문 보존, 읽기 권한과 상태 동등성, 제한된 범위 조회 계획을 요구한다. [ADR-0201](../../docs/adr/0201-relational-database-portability.md).

편집 작업의 범위는 [설계 #4](https://github.com/fregmented/LibertyWiki/issues/4), [구현 #5](https://github.com/fregmented/LibertyWiki/issues/5), [기능 검증 #6](https://github.com/fregmented/LibertyWiki/issues/6)에서 추적한다.

이력과 이전 원문 작업은 [설계 #7](https://github.com/fregmented/LibertyWiki/issues/7), [구현 #8](https://github.com/fregmented/LibertyWiki/issues/8), [기능 검증 #9](https://github.com/fregmented/LibertyWiki/issues/9)에서 추적한다.
