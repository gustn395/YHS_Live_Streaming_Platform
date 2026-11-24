package com.yhs.livestreaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * 애플리케이션 진입점.
 * <ul>
 *     <li>내장 Tomcat 실행: {@code bootRun}, {@code java -jar *.war}</li>
 *     <li>외부 Tomcat 배포: {@link SpringBootServletInitializer}가 WAR 배포 시 애플리케이션을 시작한다.</li>
 * </ul>
 */
@SpringBootApplication
public class LiveStreamingApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(LiveStreamingApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(LiveStreamingApplication.class);
    }
}
