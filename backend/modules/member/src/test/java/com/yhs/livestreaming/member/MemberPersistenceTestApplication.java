package com.yhs.livestreaming.member;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * member 모듈의 슬라이스 테스트(@DataJpaTest)용 설정. 도메인 모듈에는 @SpringBootApplication이 없어서 테스트에만 둔다
 * (backend/README.md 5장). 엔티티·리포지토리 스캔 기준 패키지는 com.yhs.livestreaming.member다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class MemberPersistenceTestApplication {
}
