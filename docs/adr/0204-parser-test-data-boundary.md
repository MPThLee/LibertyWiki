---
status: accepted
date: 2026-11-08
---
# ParserTests 데이터 사용과 독립 구현의 경계

사용자 지시에 따라 MediaWiki 1.46.2에 대응하는 코어·번들 확장·Parsoid의 ParserTests를 외부 테스트 데이터셋으로 사용할 수 있다. 입력·기대 결과·준비 문서·설정·실행 모드·기대 메타데이터를 검증 자료로 취급하며 출처·버전·파일 해시·개별 라이선스와 원래 고지를 확인·유지한다. 이 허용은 MediaWiki/Parsoid 파서·실행기·정규화기·훅 구현의 복사·번역·포팅을 허용하지 않는다. LibertyWiki 신규 구현은 AGPL-3.0-only이며 외부 데이터의 라이선스를 임의로 변경하지 않는다.

PoC 대표 위험군을 실행 전에 고정하고 의미·원문 보존과 왕복 시험 100%, 데이터 손실 0건, 악성 입력의 자원 제한을 합격 조건으로 한다. 전체 코퍼스의 실패·미지원·skip·disabled 및 모드별 적용 여부를 별도 보고하며 이를 통과로 합산하지 않는다. PoC 합격은 전체 API·파서·확장 호환 완료와 구별한다.

근거: 사용자 Q16, Q18. 기존 10_COMPATIBILITY·13_LICENSE_CLEANROOM의 테스트 코드 금지는 유지하되 fixture 데이터 허용을 명시한다. 실제 코퍼스 파일의 출처·라이선스·체크섬과 수량은 아직 조사 전이다.

공식 형식: https://www.mediawiki.org/wiki/Manual:Parser_tests
