// 공통 기반 모듈: 공통 예외·오류 응답(ProblemDetail), 보안 기본 구성, 공통 인증 계약, OpenAPI 공통 설정
// 결정: docs/policy.md 3장(G-D). 구조와 현재 상태: docs/global/module.md
plugins {
    id("yhs.domain-module-conventions")
}

dependencies {
    // 다른 모듈이 오류 코드·인증 계약(@CurrentMember 등)과 함께 쓰는 타입이라 api로 노출한다.
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-validation")
    // DB 공통 설정(G-D010): JPA 감사 기능과 생성·수정 시각 기반 클래스(BaseTimeEntity)를 도메인 모듈이 함께 쓴다.
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    // 단위 테스트(외부 인프라 없음, G-D009): standalone MockMvc는 spring-test(spring-boot-starter-test)에 있다.
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
}
