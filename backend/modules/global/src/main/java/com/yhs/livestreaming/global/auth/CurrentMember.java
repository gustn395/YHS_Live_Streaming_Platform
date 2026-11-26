package com.yhs.livestreaming.global.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 인자에 현재 인증 주체({@link AuthenticatedMember})를 주입한다. 인증 주체가 없으면 401이다(G-D006).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentMember {
}
