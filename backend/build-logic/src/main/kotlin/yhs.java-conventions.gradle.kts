// 모든 백엔드 모듈(app, modules/*)에 적용하는 공통 Java 규칙
// 사용: plugins { id("yhs.java-conventions") }
plugins {
    java
}

group = "com.yhs.livestreaming"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        // 컴파일·테스트는 JDK 17로 한다. Gradle 자체는 JDK 17 이상(25 포함)으로 실행해도 된다.
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// 라이브러리 버전은 Spring Boot BOM으로 맞춘다. (버전 카탈로그 gradle/libs.versions.toml)
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val springBootBom = dependencies.platform(libs.findLibrary("spring-boot-dependencies").get())

dependencies {
    "implementation"(springBootBom)
    "annotationProcessor"(springBootBom)
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
