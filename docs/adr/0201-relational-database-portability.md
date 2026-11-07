---
status: accepted
date: 2026-11-08
---
# 네 DBMS의 동일한 제품 기능 지원

하나의 설치에서 DBMS 한 종류를 선택하며 MariaDB, PostgreSQL, Microsoft SQL Server, Oracle Database 모두를 1.0.0부터 지원한다. 코어와 기본 제공·외부 확장 모두 같은 기능·권한·데이터 의미를 보장해야 하므로, PostgreSQL 전용 ADR-002를 이 결정으로 대체한다. 모듈형 모놀리스·공통 ApplicationService·단일 트랜잭션과 outbox 원칙은 유지한다.

지원 하한은 개발 기준일 2026-11-08에 PostgreSQL 17, SQL Server 2022, MariaDB 11.8, Oracle 19c로 고정한다. MariaDB·Oracle은 직전 장기지원 계열을 적용한다. 새 메이저 출시로 하한을 자동 변경하지 않으며 실제 지원 버전·에디션·패치는 검증한 지원표로 관리한다. 하한 이상의 미검증 버전을 자동 지원한다고 선언하지 않는다.

공통 도메인과 persistence 계약 아래 DB별 타입·스키마 변경·잠금·ID·제목 고유성·작업 획득·백업 복구를 검증한다. 외부 확장도 네 DBMS 지원 의무가 있으며 DB 내부 객체를 직접 사용하는 대신 플랫폼 persistence capability를 사용한다. 예시 PostgreSQL DDL을 다른 엔진의 운영 스키마로 간주하지 않는다. 같은 기능이더라도 ENWIKI 규모 인증은 DB별 별도 실측이다.

근거: 사용자 인터뷰 Q10–11, Q14, Q19, Q21–22. 외부 DB 서버의 CPU·OS·배포 방식은 LibertyWiki 실행환경 요구와 독립적이다.
