package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.global.auth.AuthenticatedMember;
import com.yhs.livestreaming.global.auth.CurrentMember;
import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.error.CommonErrorCode;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.profile.MemberProfile;
import com.yhs.livestreaming.member.app.profile.MemberProfileService;
import com.yhs.livestreaming.member.in.dto.MemberProfileResponse;
import com.yhs.livestreaming.member.in.dto.NicknameUpdateRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 정보 API(MB-W15, MB-D028). 공개 경로가 아니므로 global 보안 체인이 Access 쿠키와 로그인 세션을 먼저 검증한다(G-D004,
 * G-D007). 계정 상태 때문에 세션을 폐기한 경우(401·403)에만 인증 쿠키를 지운다(Refresh와 같은 처리, MB-D020).
 */
@RestController
@RequestMapping("/api/v1/members")
public class ApiV1MemberController implements ApiV1MemberSpec {

    private final MemberProfileService profileService;
    private final AuthCookieWriter cookies;

    ApiV1MemberController(MemberProfileService profileService, AuthCookieWriter cookies) {
        this.profileService = profileService;
        this.cookies = cookies;
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<MemberProfileResponse> getMyProfile(@CurrentMember AuthenticatedMember me,
                                                              HttpServletResponse httpResponse) {
        return ResponseEntity.ok(MemberProfileResponse.from(
                clearingCookiesOnRevoke(httpResponse, () -> profileService.getMyProfile(me))));
    }

    @Override
    @PatchMapping("/me")
    public ResponseEntity<MemberProfileResponse> changeNickname(@CurrentMember AuthenticatedMember me,
                                                                @Valid @RequestBody NicknameUpdateRequest request,
                                                                HttpServletResponse httpResponse) {
        return ResponseEntity.ok(MemberProfileResponse.from(
                clearingCookiesOnRevoke(httpResponse, () -> profileService.changeNickname(me, request.nickname()))));
    }

    private MemberProfile clearingCookiesOnRevoke(HttpServletResponse httpResponse, Supplier<MemberProfile> call) {
        try {
            return call.get();
        } catch (BusinessException ex) {
            if (ex.errorCode() == MemberErrorCode.ACCOUNT_DISABLED || ex.errorCode() == CommonErrorCode.UNAUTHENTICATED) {
                cookies.clearTokens(httpResponse);
            }
            throw ex;
        }
    }
}
