# 12. 신규 Extension 플랫폼 상세 설계

## 1. 적용 범위 및 비호환성

MediaWiki의 **확장 가능한 제품 기능**을 재구현하되 MediaWiki PHP 확장 바이너리/소스/ABI를 로드하지 않습니다. 신규 확장은 LibertyWiki의 문서화된 SDK/SPI로 작성합니다. 배포 기본 확장은 각각 manifest·버전·수명주기를 갖는 LibertyWiki 확장으로 독립 구현합니다. 외부 확장과 동일한 공개 SPI를 사용하고 코어 내부 접근 특권을 두지 않습니다. LOCAL/S3 저장·서빙은 내장 예외입니다. AGPL 라이선스와 별개로 신규 확장물의 배포·실행 라이선스 정책은 법률검토 및 운영자 정책으로 관리합니다.

## 2. manifest(안)

```yaml
id: org.example.demo
version: 1.0.0
platformApi: '>=1.0 <2.0'
execution: isolated-rpc
requiredDatabaseSupport: [MARIADB, POSTGRESQL, SQL_SERVER, ORACLE]
entrypoint: ./demo-worker
permissions:
  - pages.read
  - pages.write
  - events.subscribe
capabilities:
  - id: demo.article.decorate
    kind: page-transformation
    nativeRest: {method: POST, path: /api/v1/extensions/demo/decorate}
    mediaWikiCompatible:
      declared: false  # 원본 기준에 해당 endpoint가 없다면 거짓 경로를 추가하지 않음
    hooks: [page.render.before]
  - id: demo.parse.tag
    kind: parser-tag
    parserTags: [demo]
signature: 'detached-signature-ref'
```

**각 확장의 외부 사용자 기능은 Native REST operation을 등록해야 합니다.** MediaWiki Compatible operation은 원본 기준에 실제 존재하는 경우만 대응하며, 설치한 확장이 새로운 compat route를 제시하는 경우에도 해당 API가 표준 MediaWiki 전체에서 존재하는 것으로 표시하면 안 됩니다.

## 3. API·이벤트·파서·UI 통합 지점

| SPI/Hook | 확장 가능 기능 | 규칙 |
|---|---|---|
| `PageReadHook`, `PreSaveHook`, `PostCommitEvent` | 읽기변형/편집검증/감사 이벤트 | 선행 쓰기/권한 우회 불가; postcommit 이벤트 재전송 허용 |
| `ParserTag`, `ParserFunction`, `ContentModel` | WikiText 확장 태그, 함수, 콘텐츠 모델 | 순서·우선순위·expansion budget 명시 |
| `SpecialPageProvider`, `ThemeSlot`, `EditorAction` | 관리자 특수 페이지, 테마, 편집기 버튼 | 별도 ACL 및 CSP 적용 |
| `ApiCapabilityProvider` | **MediaWiki Compatible + Native RESTful** | 기능-엔드포인트-테스트 ID 매핑 필수 |
| `NotificationProvider`, `JobHandler` | 알림/배치/비동기 인덱싱 | idempotency 및 retry/deadletter 계약 |
| `StorageProvider` | 별도 객체 저장소 확장 | 기본 Local/S3 동작·보안 계약 훼손 금지 |

## 4. 격리·보안·호환성

- 제3자 확장은 **프로세스/컨테이너 또는 WASM 격리**를 기본으로 합니다. filesystem/network/DB 직접 접근을 허용하지 않으며 capability token만 노출합니다.
- 관리자는 manifest 서명·publisher provenance·SBOM·필요권한을 검토한 뒤 사이트별 설치/활성화/비활성화를 수행합니다.
- RPC에는 request ID, deadline, budget, permission context, API version이 포함됩니다. 워커 crash는 핵심 페이지 쓰기 트랜잭션을 손상시키지 않습니다.
- 확장 내부 DB migration은 플랫폼 persistence capability가 네임스페이스·트랜잭션·DB별 재개/보상/복구 계획과 함께 관리하며 core schema 직접 수정은 금지합니다. DDL 전체가 트랜잭션 롤백된다고 가정하지 않습니다.
- UI 가젯/사용자 스크립트는 임의 JS 실행으로 인한 보안위험을 분리하고, MediaWiki 외부 Gadget JS와의 완전 코드 호환은 **금지된 원본 코드 재사용 정책과 구분되는 별도 호환 요구**로 관리합니다.

## 5. 버전·라이프사이클

`DISCOVERED → VALIDATED → APPROVED → INSTALLED → ENABLED → QUIESCING → DISABLED → REMOVED`.

Extension API의 semantic version과 호환 프로파일을 분리합니다. 미지원 SPI 변경은 충돌로 처리하고 유지 중인 확장 기능/API를 무통지로 제거하지 않습니다. 새 MediaWiki 번들 확장 API가 등장하면 공통 도메인/MediaWiki Compatible/Native RESTful 구현과 API register/test fixture를 **하나의 필수 deliverable**로 추가합니다.

## 6. 테스트

설치/비활성화/업그레이드/다운그레이드 정책, 권한차단, 100% dual API mapping, 멀웨어 스캔, 강제 timeout, 인가 누락, 재진입/replay, ParserFunction 확장과 Lua 호출, 새 스킨 slot/관리 UI 렌더 결과를 검증합니다.

## 7. DB 지원과 제거 후 보존

기본 제공·외부 확장 모두 MariaDB/PostgreSQL/SQL Server/Oracle 지원이 필수이며 일부 DB만 지원하는 확장은 설치 승인 대상이 아닙니다. manifest 선언뿐 아니라 동일 기능·권한·데이터 의미·업그레이드·실패 복구의 네 DB 계약 시험 증거를 검증합니다. 외부 확장과 기본 확장 모두 공통 persistence capability를 사용하며 vendor SQL이나 DB 연결을 직접 열어 이 계약을 우회하지 않습니다.

QUIESCING에서는 신규 호출·작업을 중단하고 진행 중 작업의 종료/재개 상태를 기록합니다. DISABLED/REMOVED는 hook·route·worker 자원을 해제하지만 설정·저장 데이터·문서 원문을 보존합니다. 재설치 복구와 버전 호환을 검증하며 영구 삭제는 권한·영향 미리보기·명시적 관리자 요청·감사·복구/보존 정책이 있는 별도 Native 관리 operation으로 설계합니다. 해당 operation의 상세 route/schema/Compat 원본 존재 여부는 아직 inventory 및 계약 상세화 대상입니다.

해석할 확장이 없으면 원문을 opaque node로 보존하고 경고합니다. 시각 편집은 해당 부분을 손상시키는 저장을 차단하며, 보존을 검증한 원문 편집 경로를 제공합니다. 과거 렌더를 재사용할 때도 현재 ACL·visibility·sanitizer 계약을 적용합니다. 확장 제거를 데이터 삭제나 호환 완료로 표시하지 않습니다.
