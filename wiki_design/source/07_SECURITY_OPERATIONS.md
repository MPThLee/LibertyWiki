# 07. 비기능·보안·운영 설계서

## 1. 제안 워크로드 및 측정 기준

1차 성능 가정은 **DAU 1,000명, 10만 문서 이하, 1TB 이하 첨부, 100명 동시활성 사용자**입니다. 데이터 규모, 편집 비율, 템플릿 복잡도에 따라 결과가 크게 달라집니다. 아래 목표는 제품 기획상의 **초기 수용 목표**이며 실제 측정 결과가 아닙니다.

| NFR ID | 목표(중간 규모 제안) | 시험 방식 | 우선도 |
|---|---|---|---|
| NFR-PERF-001 | 캐시 적중 페이지 조회 p95 ≤ 1.0초 | k6 50~150 VU, 15분 혼합 부하 | P0 |
| NFR-PERF-002 | 비캐시 페이지 조회 p95 ≤ 2.0초 | 캐시 무효화, 고비용 템플릿 별도 통계 | P0 |
| NFR-PERF-003 | 페이지 revision 저장 p95 ≤ 2.0초 | 동시 저장·충돌·권한 검증 포함 | P0 |
| NFR-PERF-004 | 검색 API p95 ≤ 1.5초 | 상위/희귀 쿼리+1만/10만 문서 조합 | P1 |
| NFR-PERF-005 | 검색 반영 지연 p95 ≤ 60초 | revision→OpenSearch indexed timestamp | P1 |
| NFR-PERF-006 | 저장 API 정상 구간 5xx·timeout < 0.3% | 관측/부하/장애시험 | P0 |
| NFR-AVAIL-001 | 월 가용성 목표 99.9% | 합성 헬스 체크·SLO 계산 | P1 |
| NFR-DR-001 | RPO ≤ 1시간 | PITR + 객체 버전 복원 실험 | P0 |
| NFR-DR-002 | RTO ≤ 4시간 | 분리된 검증 환경에서 실제 복구 | P0 |
| NFR-SEC-001 | 권한 우회 0건, OWASP 주요 위험 대응 | SAST/DAST/권한 네거티브 테스트 | P0 |
| NFR-SEC-002 | 공개/비공개 파일 직접 URL 일관성 | URL·CDN·객체 경로 접근 검사 | P0 |
| NFR-SEC-003 | 모든 변경 API 인증/인가/CSRF 정책 적용 | 자동 API 침투 테스트 | P0 |
| NFR-A11Y-001 | 주요 경로 WCAG 2.2 AA | axe + NVDA/VoiceOver 수동 점검 | P1 |
| NFR-I18N-001 | ko/en/ja + 1개 RTL 언어 UI | 날짜/시간/복수형/IME/RTL 테스트 | P1 |
| NFR-MAINT-001 | 1회 배포를 위한 schema 변경 롤백 계획 보유 | 사전 백업·마이그레이션 리허설 | P0 |
| NFR-OPS-001 | 모든 업무 요청 traceId 추적 가능 | 로그·트레이스·Outbox 연결 검사 | P0 |

**성능 시험 워크로드 예시:** 읽기 75%, 검색 15%, 편집/관리 10%. 4KiB, 100KiB, 1MiB 페이지를 혼합하고 템플릿 전개량 1/10/100개 프로파일을 나눕니다. 더 큰 100,000 DAU 환경은 별도 규모 시험을 진행해야 합니다.

## 2. 보안 아키텍처

### 2.1 인증·인가

- 사용자 세션 쿠키는 Secure, HttpOnly, SameSite를 지정하며 state-changing 브라우저 API에는 CSRF 방어를 적용합니다.
- 외부 API 토큰은 권한 범위(scope), 유효기간, 발급·폐기자 추적을 기본 제공합니다.
- RBAC에 문서 보호·차단·위키 공개 정책·다중 위키·자원 컨텍스트를 합성하는 정책 엔진을 둡니다.
- 관리자 MFA, 최소 권한, 인프라 운영 권한과 위키 관리자 권한 분리를 적용합니다.
- `suppressrevision`, 검사관 데이터, 사용자 IP 등 민감정보는 별도 권한과 감사 로그/보존기간을 적용합니다.

### 2.2 입력 공격면

| 공격면 | 대응 설계 |
|---|---|
| Wikitext/HTML | AST→HTML whitelist sanitizer, 위험 프로토콜/이벤트 핸들러 삭제, CSP |
| 템플릿/Lua | recursion budget, CPU/메모리 제한, IO 금지, 런타임 격리 |
| 파일 업로드 | MIME 실제 내용 확인, allowlist, AV 옵션, 격리 상태, SVG/PDF 스크립트 검증 |
| 정규식·사용자 필터 | 실행 시간·백트래킹 한도, 정책 변경 감사 |
| URL/리다이렉트 | 허용 scheme, SSRF 방지, 내부 주소 접근 차단 |
| 외부 검색 | 사용자별 검색 결과 권한 필터·본문 요약/스니펫 검증 |
| 캐시/CDN | 비공개 내용 공용 캐시 금지, 적절한 Cache-Control/Vary, signed media delivery |
| 관리자·배치 | 2인 승인 옵션, dry-run, 감사 로그, 대상 수 상한 |

### 2.3 보안 정책

- 기본 비밀정보는 KMS/시크릿 매니저 또는 운영 비밀 파일에 보관하고 Git/이미지에 포함하지 않습니다.
- DB/TLS 종단, 객체스토리지 전송, 감사 로그 전송에 암호화를 적용합니다.
- 파일 바이너리에 대한 무조건적인 글로벌 `mc mirror --remove` 형태의 삭제 동기화를 백업 설계로 채택하지 않습니다.
- 관리자 UI에는 비밀값과 개인정보를 마스킹하고 다운로드·열람도 감사 대상으로 관리합니다.
- SBOM, 종속성 CVE 검사를 CI 단계에 포함하며, 파서/렌더 라이브러리에는 별도 fuzz test를 적용합니다.

## 3. 백업·복구 및 객체 스토리지 일관성

백업 대상은 PostgreSQL WAL/PITR, S3 객체 원본·버전·메타데이터, 구성 및 확장 manifest, 감사·이관 로그입니다. **검색 인덱스는 파생 상태이므로 재생성을 지원**합니다. Redis 세션·캐시는 재생 가능한 것과 보존이 필요한 항목을 구분합니다.

### 3.1 저장 안전성 원칙

1. 객체 버저닝을 지원하는 저장소에서는 반드시 활성화하고 보존/삭제 보호·오프사이트 복제를 적용합니다. 저장소 제품별 versioning/locking 지원 수준은 별도 검증이 필요합니다.
2. DB 단독 백업 또는 객체 단독 백업을 성공한 전체 위키 백업으로 표시하지 않습니다.
3. 논리 백업에는 파일 inventory와 해시 manifest를 포함합니다.
4. 소스에 누락된 객체가 있다고 목적지 객체를 자동 삭제하는 미러링은 일반적인 백업 방식으로 허용하지 않습니다. 삭제 반영은 별도 2인 승인 또는 보존 정책으로 통제합니다.
5. 주기적 무작위 파일 복구/전체 DR 훈련을 수행하고 checksum mismatch를 알립니다.

### 3.2 백업 runbook

`정합 시점 식별 → DB snapshot/WAL 위치 기록 → S3 version/inventory snapshot → Config/Manifest export → 교차 해시 검증 → offsite immutable copy → 복구 훈련 → 결과 승인`

### 3.3 실패 시 복구 규칙

- 검색 서버 실패: 문서 읽기/편집 서비스 지속, 검색만 폴백.
- S3 실패: 파일 조회·업로드만 제한, 문서 본문 CRUD 지속(본문 내부 파일은 표시 실패 가능).
- Redis 실패: DB 기반 세션/제한된 로컬 캐시 폴백 또는 안전한 오류; 인증 우회 금지.
- DB primary 실패: 쓰기 중지, 읽기 replica·장애 조치 정책 별도.
- Outbox 축적: 재시도, 작업 순서, DLQ, 관리자 수동 재처리 가능.

## 4. 관측성 및 권장 메트릭

| Metric / SLI | 구분 | 목적 |
|---|---|---|
| `wiki_http_requests_total`, `wiki_http_request_duration_seconds` | Counter/Histogram | HTTP 오류·지연 SLO |
| `wiki_revision_commits_total`, `wiki_edit_conflicts_total` | Counter | 편집 성공·충돌율 |
| `wiki_parser_duration_seconds`, `wiki_parser_failures_total` | Histogram/Counter | 문서 렌더·파서 병목 |
| `wiki_template_expansions_total`, `wiki_template_recursion_limit_total` | Counter | 비정상 템플릿/의존 폭증 |
| `wiki_outbox_pending_events`, `wiki_outbox_oldest_age_seconds` | Gauge | 비동기 지연·정체 |
| `wiki_search_index_lag_seconds`, `wiki_search_errors_total` | Gauge/Counter | 검색 최신성/가용성 |
| `wiki_object_store_requests_total`, `wiki_object_store_integrity_errors_total` | Counter | 객체 저장 실패·무결성 |
| `wiki_file_thumbnail_jobs_total` | Counter | 파생 이미지 처리 성공/오류 |
| `wiki_auth_failures_total`, `wiki_permission_denials_total` | Counter | 보안 이벤트/오탐 추세 |
| `wiki_backup_last_success_timestamp_seconds` | Gauge | 백업 성공 신선도 |
| `wiki_restore_drill_last_success_timestamp_seconds` | Gauge | 복구 검증 |

권장 로깅 필드: `timestamp, level, service, trace_id, request_id, wiki_id, actor_id(masked), action, page_id, revision_id, result, error_code, duration_ms`. 문서 본문·패스워드·토큰·민감 IP는 기본 수집하지 않습니다.

## 5. CI/CD와 운영 환경

### 5.1 최소 환경

- local: PostgreSQL + Redis + Garage 또는 MinIO 호환 저장소 + 선택 OpenSearch + mock mail.
- test/CI: 실제 DB·S3 호환 API Testcontainers + parser fixture + contract test.
- staging: 운영 동일 버전/확장/구성의 최소 1개 독립 복제 환경.
- prod: 버전 고정 이미지, schema migration 관리, 비밀정보 외부 주입, Prometheus/Grafana 로그 및 추적 연동.

### 5.2 필수 배포 게이트

`lint/static analysis → unit → parser golden tests → DB & S3 integration → OpenAPI contract → FE E2E → dependency+license+container scan → staging migration rehearsal → canary → production smoke/metrics`

### 5.3 용량 증대 기준

- wiki-api의 요청 지연·CPU·DB connection pool 포화가 지속되면 인스턴스 수평 확장을 검토합니다.
- parser worker의 작업 대기 시간이 목표를 넘으면 전용 worker pool을 확장합니다.
- OpenSearch 샤드·메모리 요구량은 문서 수와 검색 필드·언어 분석에 따라 별도 산정합니다.
- S3 저장소 오류율·p99 지연·복제 지연·버저닝 상태를 상시 감시합니다.

## 6. 개인 정보 및 법적 운영 체크

- 계정 정보, IP 기록, 관리자 식별 정보에 대한 수집 목적/보유 기간/접근 권한을 분리합니다.
- 삭제 및 RevisionDelete/CheckUser 상당 기능은 법령·사업 정책을 정한 뒤 활성화합니다.
- 데이터 이관에는 기존 개인정보 처리 근거와 사용자 통지를 확인합니다.
- 가져오는 문서·이미지·스킨·코드는 각각 저작권·라이선스·저작자표시·재배포 의무를 확인합니다.

## 7. 잔여 위험

- 기능 동등성은 검증 대상 자료·코어 버전·확장 활성 설정에 의존합니다.
- 파서/편집기 및 Lua 호환 구현은 예상보다 큰 개발비를 요구할 수 있습니다.
- 공개 웹과 비공개 위키의 캐시/객체 정책은 동시에 기본 적용하기 어렵습니다. 설치 프로파일로 분리합니다.
- CheckUser/가젯은 기본 배포에 포함되더라도 고위험 기능입니다. 출시 전 독립 보안·법무 승인을 요구합니다.


## 11. v1.0 보안·라이선스·파일 관리 운영 통제

| 정책 | 규격 |
|---|---|
| 라이선스 | AGPL-3.0-only / LICENSE·SPDX·source link·NOTICE·배포 스크립트·제3자 의존성 고지; 법률 검토 게이트 |
| 개발 독립성 | MediaWiki·Parsoid·번들 코드 및 CSS/JS·소스 주석·SQL의 복제/포팅/AI 요약-코드변환 금지 |
| 익명/비공개 | 익명 읽기/쓰기/계정 생성/파일 다운로드/메타데이터 노출 각각 관리자 설정. 인증 실패는 보호 파일 object key까지 숨김 |
| 파일 로컬 | 업로드 tmp와 publish 분리, symlink/traversal 방지, noexecute, 파일서버 direct public mount 지양 |
| 파일 S3 | IAM least privilege, endpoint TLS, credentials secret manager, private bucket default, 짧은 presign 및 세션 권한 연계 |
| 확장 | signed manifest, capabilities, site-admin 승인, sandbox egress/DB/filesystem policy, request budget |
| 출처 추적 | SBOM(SPDX/CycloneDX), dependency checks, 코드 리뷰에 외부 출처·허가 흔적 기록 |
| Wikipedia 규모 | DoS 차단, 무한 transclusion/expensive parser functions, hot article storm, mass invalidation, S3 outage 대응 |

**AGPL 의무 관련:** 원본 AGPL-3.0 제13조의 네트워크 이용자 대응 소스 제공 조항을 준수하도록 UI에서 소스 보기 경로를 제공합니다. 구체적인 적용 주체·수정물·번들 DLL/JAR 링크 관계와 제3자 라이선스 결합은 배포 전 전문 검토가 필요합니다. 운영상 기본 정책은 이용자에게 해당 버전의 재현 가능한 소스·빌드 지시사항을 제공하는 방향입니다.
