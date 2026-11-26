package com.yhs.livestreaming.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * GL-T007: {@code @CurrentMember}는 SecurityContext의 인증 주체를 그대로 넘기고, 인증 주체가 없으면 401로 이어지는
 * 인증 예외를 던진다(G-D006).
 */
class CurrentMemberArgumentResolverTest {

    private final CurrentMemberArgumentResolver resolver = new CurrentMemberArgumentResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsOnlyAnnotatedAuthenticatedMemberParameter() throws Exception {
        assertThat(resolver.supportsParameter(parameter("annotated"))).isTrue();
        assertThat(resolver.supportsParameter(parameter("notAnnotated"))).isFalse();
    }

    @Test
    void resolvesAuthenticatedMemberFromSecurityContext() throws Exception {
        AuthenticatedMember member = new AuthenticatedMember(42L, "USER", "session-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        Object resolved = resolver.resolveArgument(parameter("annotated"), null, null, null);

        assertThat(resolved).isSameAs(member);
    }

    @Test
    void rejectsAnonymousAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThatThrownBy(() -> resolver.resolveArgument(parameter("annotated"), null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void rejectsMissingAuthentication() throws Exception {
        assertThatThrownBy(() -> resolver.resolveArgument(parameter("annotated"), null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void rejectsPrincipalOfOtherType() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("plain-user", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThatThrownBy(() -> resolver.resolveArgument(parameter("annotated"), null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private static MethodParameter parameter(String methodName) throws NoSuchMethodException {
        Method method = Handlers.class.getDeclaredMethod(methodName, AuthenticatedMember.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private static final class Handlers {
        void annotated(@CurrentMember AuthenticatedMember member) {
        }

        void notAnnotated(AuthenticatedMember member) {
        }
    }
}
