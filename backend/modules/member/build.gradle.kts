// 도메인 모듈: 회원: 회원가입, 로그인, OTP, OAuth, 회원 정보
// 의존성은 구현할 때 필요한 것만 추가한다. (backend/README.md 6장 "의존성 도입 계획")
plugins {
    id("yhs.domain-module-conventions")
}

dependencies {
    // 공통 기반: 오류 코드·공개 경로 등록·JPA 공통 설정(G-D001, G-D010)
    implementation(project(":modules:global"))
    // 회원가입 API Spec 인터페이스의 OpenAPI 어노테이션(G-D008)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    // 로그인 세션·Refresh Token·OTP·요청 제한 저장소(MB-D014~D019)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // OTP 메일 SMTP 발송(MB-D016)
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Redis 어댑터 테스트: Testcontainers Redis(GenericContainer, G-D009). SMTP 어댑터 테스트: Testcontainers Mailpit
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    // 영속성 테스트: Testcontainers PostgreSQL + Flyway 마이그레이션(G-D009)
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("org.postgresql:postgresql")
}
