// 실행 모듈: Spring Boot 진입점, 설정, 도메인 모듈 조립, WAR 패키징
plugins {
    id("yhs.java-conventions")
    war
    alias(libs.plugins.spring.boot)
}

base {
    // 산출물: build/libs/yhs-live-streaming-backend-<version>.war (실행 가능), -plain.war (외부 Tomcat 배포용)
    archivesName = rootProject.name
}

dependencies {
    // 공통 기반 + 도메인 모듈 조립
    implementation(project(":modules:global"))
    implementation(project(":modules:member"))
    implementation(project(":modules:broadcast"))
    implementation(project(":modules:streaming"))
    implementation(project(":modules:chat"))
    implementation(project(":modules:comment"))
    implementation(project(":modules:donation"))
    implementation(project(":modules:recording"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 통합 테스트: MockMvc 자동 구성, 인증 사용자 요청(spring-security-test). Boot 4는 테스트 지원이 기술별 스타터로 나뉘었다.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")

    // 외부 Tomcat에 배포할 때 컨테이너가 제공하는 런타임은 plain WAR에서 빠지도록 providedRuntime으로 둔다.
    // (실행 가능한 WAR에는 WEB-INF/lib-provided로 포함되어 java -jar 실행이 된다)
    providedRuntime(platform(libs.spring.boot.dependencies))
    providedRuntime("org.springframework.boot:spring-boot-starter-tomcat-runtime")
}

// WAR 모듈이므로 쓰지 않는 -plain.jar는 만들지 않는다.
tasks.named<Jar>("jar") {
    enabled = false
}
