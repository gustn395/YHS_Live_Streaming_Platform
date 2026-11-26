package com.yhs.livestreaming.member.app.session;

import com.yhs.livestreaming.global.security.jwt.AccessTokenRoles;
import com.yhs.livestreaming.member.domain.MemberRole;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Access Token {@code role} Claim 허용 값 = {@link MemberRole} 이름(MB-D012). */
@Component
public class MemberAccessTokenRoles implements AccessTokenRoles {

    @Override
    public Set<String> allowedRoles() {
        return Arrays.stream(MemberRole.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
