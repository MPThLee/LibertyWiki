# 12. 신규 Extension 플랫폼 상세 설계

## 1. 적용 범위 및 비호환성

MediaWiki의 **확장 가능한 제품 기능**을 재구현하되 MediaWiki PHP 확장 바이너리/소스/ABI를 로드하지 않습니다. 신규 확장은 AtlasWiki(가칭)의 문서화된 SDK/SPI로 작성합니다. 배포 기본 확장의 기능은 오픈소스 코드를 복제하지 않고 독립 모듈로 구현합니다. AGPL 라이선스와 별개로 신규 확장물의 배포·실행 라이선스 정책은 법률검토 및 운영자 정책으로 관리합니다.

## 2. manifest(안)

```yaml
id: org.example.demo
version: 1.0.0
platformApi: '>=1.0 <2.0'
execution: isolated-rpc
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
- 확장 내부 DB migration은 네임스페이스/트랜잭션/rollback plan과 함께 관리하며 core schema 직접 수정은 금지합니다.
- UI 가젯/사용자 스크립트는 임의 JS 실행으로 인한 보안위험을 분리하고, MediaWiki 외부 Gadget JS와의 완전 코드 호환은 **금지된 원본 코드 재사용 정책과 구분되는 별도 호환 요구**로 관리합니다.

## 5. 버전·라이프사이클

`DISCOVERED → VALIDATED → APPROVED → INSTALLED → ENABLED → QUIESCING → DISABLED → REMOVED`.

Extension API의 semantic version과 호환 프로파일을 분리합니다. 미지원 SPI 변경은 충돌로 처리하고 유지 중인 확장 기능/API를 무통지로 제거하지 않습니다. 새 MediaWiki 번들 확장 API가 등장하면 공통 도메인/MediaWiki Compatible/Native RESTful 구현과 API register/test fixture를 **하나의 필수 deliverable**로 추가합니다.

## 6. 테스트

설치/비활성화/업그레이드/다운그레이드 정책, 권한차단, 100% dual API mapping, 멀웨어 스캔, 강제 timeout, 인가 누락, 재진입/replay, ParserFunction 확장과 Lua 호출, 새 스킨 slot/관리 UI 렌더 결과를 검증합니다.
