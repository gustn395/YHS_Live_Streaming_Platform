/**
 * 공통 기반: 공통 예외·오류 응답(ProblemDetail), 보안 기본 구성, 공통 인증 계약, OpenAPI 공통 설정.
 *
 * <p>도메인 모듈과 달리 계층 패키지(in·app·domain·out) 대신 기능별 패키지를 쓴다:
 * {@code error}, {@code security}, {@code auth}, {@code openapi}. 결정은 docs/policy.md 3장(G-D),
 * 구조와 현재 상태는 docs/global/module.md를 따른다.
 */
package com.yhs.livestreaming.global;
