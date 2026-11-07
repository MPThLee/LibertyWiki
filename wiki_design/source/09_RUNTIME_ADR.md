# 09. 런타임·프레임워크 기술 검토 및 ADR-101

## 1. 결정 기준

본 프로젝트는 **동적 확장 시스템**, 장기간 살아있는 웹 서버, 대규모 페이지·리비전 스트리밍, 고부하 파서/파일 처리, AGPL 배포가 핵심입니다. 단순 Hello World 처리속도나 시작 시간보다 **기능 호환성, 개발 생산성, 안정적인 확장 로딩, 메모리 비용 및 라이선스**가 중요합니다. 비교는 현재 소프트웨어 생태계 기반의 질적 평가이며 공정한 벤치마크 측정 결과가 아닙니다.

## 2. 주요 후보 비교

| 구분 | Kotlin / Spring Boot **JVM** | Kotlin / Spring Boot **GraalVM Native Image** | Kotlin / **Ktor JVM** | Kotlin/Native / **Ktor Native** |
|---|---|---|---|---|
| 처리량·지속부하 | JIT 워밍업 이후 대규모 서버에서 높은 성능 기대, GC 설정 필수 | 실제 벤치마크 필요; Native가 JVM보다 반드시 빠른 것은 아님 | 가벼운 구조 및 코루틴 처리, 튜닝·벤치마크 필요 | 네이티브 런타임 효율은 코드/메모리 모델에 종속, 실제 비교 데이터 부족 |
| 시작 지연 | JVM 시작·DI 초기화 비용, CDS/CRaC 등 별도 최적화 가능 | 일반적으로 짧은 시작 시간, 소규모 worker에서 유리 | Spring보다 초기화 구조 단순화 가능 | VM이 없어 시작구조 단순; 초기화 부담 작게 설계 가능 |
| 상주 메모리 | JVM heap + metaspace + GC, 고정 메모리 최소치 부담 | 상대적으로 작게 설계 가능하지만 성능/구성에 따라 달라짐 | JVM 기본 메모리 비용 존재 | JVM heap 없음. 코루틴·Allocator·GC 측정 필요 |
| 런타임 확장 | **JAR classloader + 별도 워커 RPC 가능**. 장기 운영 툴링 풍부 | **closed-world/AOT**로 빌드 후 신규 클래스 로딩·reflection이 제약; 새 플러그인은 별도 프로세스/WASM 권장 | 동적 JAR 또는 RPC 가능하나 SDK/보안 설계 자체 구현 부담 | 런타임 코드 로딩 모델이 JVM과 달라 **외부 RPC/WASM 확장 중심** |
| 개발 난도 | 팀 친화적, DI·JPA/JDBC·테스트·운영 도구 풍부 | AOT hints, reflection, native image build/test CI가 복잡 | 명시적 구성 필요, 대규모 프로젝트 관례 설계 | JVM 전용 라이브러리 사용 불가, 서버/생태계 이슈를 별도 해결 |
| 안정성·운영 | 풍부한 운영 경험, observability, 진단·프로파일링 | 빌드별 호환성·JNI·resource metadata 제약 및 디버깅 공수 | JVM 수준 안정성에 프레임워크별 운영 도구 차이 | Native 서버 모드의 기능 범위를 출시 당시 재평가해야 함 |
| 상용배포·AGPL 연계 | 배포 JDK(Temurin 등)의 실제 라이선스·빌드 provenance 확인 | **GraalVM CE: GPLv2+Classpath exception**; **Oracle GraalVM: GFTC**, 재배포 조건 등 제품별 확인 필수 | Ktor/라이브러리·JDK 각각 SBOM 검증 | Kotlin/Native toolchain·Ktor·네이티브 링크 라이브러리 각각 라이선스 확인 |
| 종합 | **권장 1순위** | 선택적 worker PoC | 대안 서버 후보 | 실험적 대안; 주력 서버로 즉시 선정하지 않음 |

성능 수치·메모리 절감률·개발비 배수는 아직 측정하지 않았으므로 임의 수치를 넣지 않습니다.

## 3. 권장 기술 결정

**ADR-101: 주력 API/도메인 서버는 Kotlin + Spring Boot JVM**으로 구성합니다. OpenJDK LTS, PostgreSQL JDBC, Redis, 검색 시스템, OpenTelemetry, 백그라운드 워커를 사용하며 확장은 기본적으로 독립 프로세스의 권한 제한 RPC로 격리합니다. 내부 신뢰 모듈의 JVM SPI는 별도 서명/승인/운영정책 하에만 허용합니다.

**GraalVM Native Image**는 짧은 수명·작은 메모리의 이미지 변환 worker 또는 별도 사이드카 등에 PoC를 실시하되, 운영자가 업그레이드 시 즉시 플러그인을 설치하는 서버 본체에는 우선 적용하지 않습니다. Native Image의 closed-world analysis로 인해 동적 확장과 상충하기 때문입니다.

**Ktor JVM**은 Spring Boot 대비 API throughput/메모리에서 이점이 실측되거나 팀의 유지보수 생산성이 높을 경우 대체 검토합니다. **Kotlin/Native + Ktor**는 별도 `prototype/native-server` 트랙에서 정합성/운영성을 검증하며 Spring Boot의 모든 기능을 그대로 대체할 수 있다고 가정하지 않습니다.

## 4. PoC 테스트 계획

- 동일한 `PageQuery`, `RevisionCommit`, `WikitextParse`, `UploadStream` 계약을 각 후보의 최소 서버에서 구현합니다.
- 동일한 JVM/Native 측정 환경, CPU/RAM/SSD, TLS reverse proxy, 연결 풀, 응답 크기 및 부하 도구를 사용합니다.
- **성능:** steady-state RPS, p50/p95/p99, CPU/core, heap/RSS, GC pause, cold start, 성능 변동성.
- **안정성:** 24~72시간 soak, DB failover, 연속 빌드·재시작, 확장 설치/교체, 메모리 누수, thread/coroutine exhaustion.
- **개발성:** 기능별 구현시간·보수 난이도·테스트/빌드시간·AOT 특수 설정·CI 러너 요구량.
- **라이선스:** JDK 배포물, GraalVM 배포물, Gradle 플러그인, Ktor, 네이티브 링크 라이브러리, 컨테이너 이미지 등 재배포 적합성 표.

판정은 정량적 벤치마크와 개발자 경험 데이터로 ADR을 업데이트하되 **완전 API·위키텍스트 호환 및 동적 확장** 요구를 희생하지 않습니다.

## 5. 라이선스 유의사항

- **자체 코드 AGPL-3.0-only**와 런타임/컴파일러 자체의 라이선스는 별개입니다. 연결·배포 형태별 법적 결합 효과를 개별 확인합니다.
- Oracle GraalVM GFTC는 일반적 프로덕션 사용이 허용될 수 있어도 **재배포 시 무료 제공 조건** 등 별도 제한이 있을 수 있으므로, 그 조건을 만족하지 못하는 상용 이미지 배포에 무조건 적합하다고 판단하지 않습니다.
- GraalVM Community Edition GPLv2 Classpath Exception 배포 형태와 Apache/MIT/BSD 등 서드파티 라이브러리의 출처/NOTICE를 관리합니다.

## 6. 참고 근거

- https://docs.spring.io/spring-boot/reference/packaging/native-image/advanced-topics.html
- https://www.graalvm.org/latest/reference-manual/native-image/
- https://www.graalvm.org/faq/
- https://ktor.io/docs/server-native.html
- https://www.gnu.org/licenses/agpl-3.0.en.html
