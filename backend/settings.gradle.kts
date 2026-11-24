// 백엔드 멀티 모듈 루트 설정
// - 공통 빌드 규칙(convention plugin)은 build-logic에 둔다.
// - 도메인 모듈은 modules/<domain>에 있다. (README의 모듈 규칙 참고)
pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 저장소는 이 파일에서만 선언한다. 모듈 build 파일에서 repositories {}를 쓰면 빌드가 실패한다.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "yhs-live-streaming-backend"

include(":app")

include(
    ":modules:member",
    ":modules:broadcast",
    ":modules:streaming",
    ":modules:chat",
    ":modules:comment",
    ":modules:donation",
    ":modules:recording",
)
