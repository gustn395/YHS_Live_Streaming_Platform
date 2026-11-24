// 도메인 모듈(modules/<domain>) 공통 규칙
// 사용: plugins { id("yhs.domain-module-conventions") }
plugins {
    id("yhs.java-conventions")
    `java-library`
}

base {
    // WAR의 WEB-INF/lib에서 다른 라이브러리와 이름이 겹치지 않게 한다. 예: yhs-live-streaming-member-0.0.1-SNAPSHOT.jar
    archivesName = "yhs-live-streaming-${project.name}"
}
