package com.yhs.livestreaming.global.auth;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** {@link CurrentMember} 인자 리졸버를 Spring MVC에 등록한다. */
@Configuration(proxyBeanMethods = false)
public class GlobalAuthWebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentMemberArgumentResolver());
    }
}
