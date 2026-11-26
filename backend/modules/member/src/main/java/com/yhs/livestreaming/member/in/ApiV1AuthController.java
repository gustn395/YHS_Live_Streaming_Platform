package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.global.error.BusinessException;
import com.yhs.livestreaming.global.security.csrf.CsrfTokenRenewer;
import com.yhs.livestreaming.member.app.MemberErrorCode;
import com.yhs.livestreaming.member.app.SignupCommand;
import com.yhs.livestreaming.member.app.SignupResult;
import com.yhs.livestreaming.member.app.SignupService;
import com.yhs.livestreaming.member.app.limit.RateLimitScope;
import com.yhs.livestreaming.member.app.limit.RequestRateLimiter;
import com.yhs.livestreaming.member.app.login.CompletedLogin;
import com.yhs.livestreaming.member.app.otp.LoginChallenge;
import com.yhs.livestreaming.member.app.login.LoginCompletionService;
import com.yhs.livestreaming.member.app.login.LoginService;
import com.yhs.livestreaming.member.app.otp.OtpResent;
import com.yhs.livestreaming.member.app.otp.OtpService;
import com.yhs.livestreaming.member.in.dto.LoginRequest;
import com.yhs.livestreaming.member.in.dto.LoginSuccessResponse;
import com.yhs.livestreaming.member.in.dto.OtpRequiredResponse;
import com.yhs.livestreaming.member.in.dto.OtpVerifyRequest;
import com.yhs.livestreaming.member.in.dto.SignupRequest;
import com.yhs.livestreaming.member.in.dto.SignupResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API(MB-W04~W08). 상태를 바꾸는 요청은 모두 CSRF 토큰이 필요하다(G-D014). IP 요청 제한을 서비스 호출보다 먼저 건다
 * (MB-D018, 클라이언트 IP는 {@code getRemoteAddr()}만).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class ApiV1AuthController implements ApiV1AuthSpec {

    private final SignupService signupService;
    private final LoginService loginService;
    private final OtpService otpService;
    private final LoginCompletionService loginCompletionService;
    private final RequestRateLimiter rateLimiter;
    private final AuthCookieWriter cookies;
    private final CsrfTokenRenewer csrfTokenRenewer;

    ApiV1AuthController(SignupService signupService, LoginService loginService, OtpService otpService,
                        LoginCompletionService loginCompletionService, RequestRateLimiter rateLimiter,
                        AuthCookieWriter cookies, CsrfTokenRenewer csrfTokenRenewer) {
        this.signupService = signupService;
        this.loginService = loginService;
        this.otpService = otpService;
        this.loginCompletionService = loginCompletionService;
        this.rateLimiter = rateLimiter;
        this.cookies = cookies;
        this.csrfTokenRenewer = csrfTokenRenewer;
    }

    @Override
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request,
                                                 HttpServletRequest httpRequest) {
        rateLimiter.acquire(RateLimitScope.SIGNUP, ClientIp.of(httpRequest));
        SignupResult result = signupService.signup(
                new SignupCommand(request.username(), request.password(), request.email(), request.nickname()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SignupResponse(result.memberId(), result.accountStatus()));
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<OtpRequiredResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse httpResponse) {
        rateLimiter.acquire(RateLimitScope.LOGIN, ClientIp.of(httpRequest));
        LoginChallenge challenge = loginService.login(request.username(), request.password());
        cookies.writeChallenge(httpResponse, challenge.challengeId(), challenge.challengeExpiresAt());
        return ResponseEntity.ok(OtpRequiredResponse.until(challenge.otpExpiresAt()));
    }

    @Override
    @PostMapping("/otp/resend")
    public ResponseEntity<OtpRequiredResponse> resendOtp(
            @CookieValue(name = MemberAuthCookies.OTP_CHALLENGE, required = false) String challengeId,
            HttpServletRequest httpRequest) {
        rateLimiter.acquire(RateLimitScope.OTP, ClientIp.of(httpRequest));
        OtpResent resent = otpService.resend(requireChallenge(challengeId));
        return ResponseEntity.ok(OtpRequiredResponse.until(resent.otpExpiresAt()));
    }

    @Override
    @PostMapping("/otp/verify")
    public ResponseEntity<LoginSuccessResponse> verifyOtp(
            @CookieValue(name = MemberAuthCookies.OTP_CHALLENGE, required = false) String challengeId,
            @Valid @RequestBody OtpVerifyRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        rateLimiter.acquire(RateLimitScope.OTP, ClientIp.of(httpRequest));
        long memberId = otpService.verify(requireChallenge(challengeId), request.code());
        // Challenge는 위에서 소비됐다. 쓸모없어진 쿠키를 먼저 지우고, 아래에서 실패하면 인증 쿠키 없이 다시 로그인하게 한다.
        cookies.clearChallenge(httpResponse);
        CompletedLogin login = loginCompletionService.complete(memberId);
        cookies.writeAccessToken(httpResponse, login.accessToken().value(), login.accessToken().expiresAt());
        cookies.writeRefreshToken(httpResponse, login.refreshToken(), login.sessionExpiresAt());
        // 최종 인증 성공 = 인증 상태 변경: CSRF 토큰을 새로 만든다(G-D014). 1차 인증에서는 하지 않는다.
        csrfTokenRenewer.renew(httpRequest, httpResponse);
        return ResponseEntity.ok(new LoginSuccessResponse(login.memberId(), login.role(),
                login.accessToken().expiresAt()));
    }

    private static String requireChallenge(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            MemberErrorCode code = MemberErrorCode.OTP_CHALLENGE_INVALID;
            throw new BusinessException(code, code.detail());
        }
        return challengeId;
    }
}
