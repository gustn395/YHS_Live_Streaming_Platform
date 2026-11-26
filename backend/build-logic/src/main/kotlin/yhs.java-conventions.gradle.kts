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

// Spring 버전 일치 검사(docs/policy.md G-D012).
// BOM은 권장 버전일 뿐이라, 다른 라이브러리가 더 높은 Boot·Framework 모듈을 요구하면 Gradle 충돌 해결로 버전이 섞인다.
// 섞이면 자동 설정이 깨지므로(예: springdoc 3.2.1 → spring-boot-webmvc 4.2.0) 실행 클래스패스를 해석할 때 빌드를 멈춘다.
val springBootVersion = libs.findVersion("spring-boot").get().requiredVersion
configurations.matching { it.name == "runtimeClasspath" || it.name == "testRuntimeClasspath" }.configureEach {
    val configurationName = name
    incoming.afterResolve {
        val modules = resolutionResult.allComponents
            .mapNotNull { it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier }
        val bootMismatch = modules
            .filter { it.group == "org.springframework.boot" && it.version != springBootVersion }
            .map { it.displayName }
        val frameworkVersions = modules
            .filter { it.group == "org.springframework" }
            .groupBy({ it.version }, { it.module })
        if (bootMismatch.isNotEmpty() || frameworkVersions.size > 1) {
            throw GradleException(
                "Spring 버전 불일치($configurationName): Boot는 $springBootVersion 이어야 한다. " +
                    "다른 Boot 모듈=$bootMismatch, Framework 버전별 모듈=$frameworkVersions. " +
                    "더 높은 Spring 버전을 끌어오는 의존성을 확인한다(G-D012)."
            )
        }
    }
}
