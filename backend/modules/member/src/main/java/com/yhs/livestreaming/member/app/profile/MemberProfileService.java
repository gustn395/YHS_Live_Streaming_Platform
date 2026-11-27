package com.yhs.livestreaming.member.app.profile;

import com.yhs.livestreaming.global.auth.AuthenticatedMember;
import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.error.CommonErrorCode;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.MemberRepository;
import com.yhs.livestreaming.member.app.session.LoginSessionStore;
import com.yhs.livestreaming.member.domain.AccountStatus;
import com.yhs.livestreaming.member.domain.Member;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 내 정보 조회·닉네임 수정(MB-W15, MB-D028). Access Token·세션이 유효해도 요청마다 DB에서 회원을 다시 확인한다.
 * <ul>
 *     <li>DISABLED: 이 요청의 로그인 세션을 폐기하고 403 {@code MEMBER_ACCOUNT_DISABLED}(Refresh와 같은 규칙, MB-D021).</li>
 *     <li>회원 없음·PENDING(정상 흐름에서는 생기지 않음): 세션을 폐기하고 401 {@code AUTH_UNAUTHENTICATED}.</li>
 *     <li>세션 폐기를 확인할 수 없으면(Redis 장애) 503으로 끝낸다(MB-D019).</li>
 * </ul>
 * 닉네임 형식(MB-D008)은 요청 DTO가 검증하고, 변경은 ACTIVE 조건이 붙은 UPDATE 한 문장으로 한다. UPDATE가 반영되면 그 시점에
 * ACTIVE였으므로 바로 뒤 상태가 바뀌어도 바뀐 정보를 돌려준다(이미 커밋한 변경을 403으로 알리지 않는다). 0행이면 상태를 확인해
 * 세션을 폐기하고, 그 사이 다시 ACTIVE가 됐으면(정지 해제 등) 한 번 더 시도한다. 인증 쿠키 삭제는 컨트롤러가 한다.
 */
@Service
public class MemberProfileService {

    static final String AUTHENTICATION_REQUIRED = "인증이 필요합니다.";

    private final MemberRepository members;
    private final LoginSessionStore sessions;
    private final Clock clock;

    MemberProfileService(MemberRepository members, LoginSessionStore sessions, Clock clock) {
        this.members = members;
        this.sessions = sessions;
        this.clock = clock;
    }

    public MemberProfile getMyProfile(AuthenticatedMember me) {
        return toProfile(requireActive(me));
    }

    public MemberProfile changeNickname(AuthenticatedMember me, String nickname) {
        if (members.updateNicknameOfActive(me.memberId(), nickname, clock.instant()) == 0) {
            requireActive(me); // ACTIVE가 아니면 세션을 폐기하고 여기서 끝난다
            if (members.updateNicknameOfActive(me.memberId(), nickname, clock.instant()) == 0) {
                requireActive(me); // 재시도 사이에 정지·삭제됐으면 같은 규칙(세션 폐기·403/401)을 따른다
                throw new IllegalStateException("닉네임 수정 중 회원 상태가 계속 바뀌었다");
            }
        }
        // 반영된 변경은 상태와 관계없이 돌려준다. 그 사이 회원이 삭제됐으면 상태 확인 규칙(401)을 따른다.
        Optional<Member> updated = members.findById(me.memberId());
        return updated.isPresent() ? toProfile(updated.get()) : toProfile(requireActive(me));
    }

    private Member requireActive(AuthenticatedMember me) {
        Optional<Member> found = members.findById(me.memberId());
        if (found.isEmpty() || found.get().getAccountStatus() == AccountStatus.PENDING) {
            sessions.revoke(me.sessionId(), me.memberId());
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED, AUTHENTICATION_REQUIRED);
        }
        Member member = found.get();
        if (member.getAccountStatus() == AccountStatus.DISABLED) {
            sessions.revoke(me.sessionId(), me.memberId());
            throw new BusinessException(MemberErrorCode.ACCOUNT_DISABLED, MemberErrorCode.ACCOUNT_DISABLED.detail());
        }
        return member;
    }

    private static MemberProfile toProfile(Member member) {
        return new MemberProfile(member.getId(), member.getAccountType(), member.getUsername(), member.getEmail(),
                member.isEmailVerified(), member.getNickname(), member.getRole(), member.getAccountStatus());
    }
}
