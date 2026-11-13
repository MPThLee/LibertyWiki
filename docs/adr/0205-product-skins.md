---
status: accepted
date: 2026-11-13
---

# 1.0 제품 스킨과 색상 모드

1.0 제품 스킨은 리브레 위키의 **Liberty**와 LibertyWiki가 자체 설계한 **여백** 두 종류로 확정한다. 이 결정은 [ADR-0202](0202-extension-and-release-boundaries.md)의 MediaWiki 기본 배포 스킨 범위를 대체한다. 두 스킨 모두 라이트·다크 모드를 제공하고, 사용자 선택과 사이트 기본값은 [제품 스킨 설계](../../wiki_design/source/03_EXTENSION_PARITY.md#3-제품-스킨-2종)를 따른다.

사용자의 명시적 승인에 따라 [`librewiki/liberty-skin`](https://github.com/librewiki/liberty-skin)의 코드와 리소스를 LibertyWiki 웹 스킨에 재사용할 수 있다. 도입할 리비전과 각 파일·리소스의 출처, 라이선스 및 고지를 확인하고 [공급망 정책](../../wiki_design/source/13_LICENSE_CLEANROOM.md)에 따라 기록한다. 이 예외는 해당 스킨에 적용한다. 제품 웹 스킨에 필요한 부분을 통합하며, MediaWiki PHP 스킨 런타임은 제품 실행 범위에서 제외한다.
