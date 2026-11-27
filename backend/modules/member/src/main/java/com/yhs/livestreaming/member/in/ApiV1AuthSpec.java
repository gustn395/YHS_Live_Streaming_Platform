package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.global.openapi.OpenApiConfig;
import com.yhs.livestreaming.global.auth.AuthenticatedMember;
import com.yhs.livestreaming.member.in.dto.LoginRequest;
import com.yhs.livestreaming.member.in.dto.LoginSuccessResponse;
import com.yhs.livestreaming.member.in.dto.OtpRequiredResponse;
import com.yhs.livestreaming.member.in.dto.OtpVerifyRequest;
import com.yhs.livestreaming.member.in.dto.SignupRequest;
import com.yhs.livestreaming.member.in.dto.SignupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 인증 API의 Swagger Spec(명세 4.6, G-D008). 컨트롤러는 이 인터페이스를 구현하고, 문서와 실제 응답이 같은지는 통합 테스트로 확인한다.
 * 모든 요청에 CSRF 헤더({@code X-XSRF-TOKEN}, G-D014)가 필요하다.
 */
@Tag(name = "Authentication", description = "회원가입·로그인 인증")
public interface ApiV1AuthSpec {

    String PROBLEM_DETAIL_REF = "#/components/schemas/ProblemDetail";
    String PROBLEM_JSON = "application/problem+json";
    String CSRF_SCHEME = OpenApiConfig.CSRF_HEADER_SCHEME;

    @Operation(summary = "일반 회원가입",
            description = "username·비밀번호·이메일·닉네임으로 가입한다. 가입 직후 계정은 PENDING·이메일 미확인이며 "
                    + "로그인 토큰을 발급하지 않는다. 비밀번호는 10~30자(NFC 정규화 뒤 code point 기준)이고 아이디·서비스 이름을 바탕으로 한 비밀번호와 "
                    + "유출된 적이 있는 비밀번호(HIBP 확인)는 거부한다. 유출 여부를 확인할 수 없으면 가입하지 않는다(503).",
            security = @SecurityRequirement(name = CSRF_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "가입 완료(PENDING)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SignupResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력 검증 실패(code=COMMON_INVALID_INPUT, errors[]에 필드별 이유) "
                    + "또는 유출된 비밀번호(code=MEMBER_PASSWORD_BREACHED, errors[] 없음)",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰 없음·불일치. code=AUTH_CSRF_INVALID",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "409",
                    description = "중복. code=MEMBER_USERNAME_DUPLICATE 또는 MEMBER_EMAIL_DUPLICATE",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "429", description = "IP당 가입 요청 초과(기본 10분 20회, 설정값). code=COMMON_TOO_MANY_REQUESTS",
                    headers = @Header(name = "Retry-After", description = "다시 시도할 수 있을 때까지 초"),
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "503",
                    description = "유출 비밀번호 확인 불가(code=MEMBER_PASSWORD_CHECK_UNAVAILABLE) 또는 요청 제한 저장소 장애"
                            + "(code=COMMON_RATE_LIMIT_UNAVAILABLE)",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF)))
    })
    ResponseEntity<SignupResponse> signup(@RequestBody SignupRequest request,
                                          @Parameter(hidden = true) HttpServletRequest httpRequest);

    @Operation(summary = "로그인 1차 인증(아이디·비밀번호)",
            description = "비밀번호가 맞으면 이메일로 4자리 OTP를 보내고 OTP Challenge 쿠키(__Host-yhs_otp, HttpOnly, 10분)를 준다. "
                    + "토큰은 아직 발급하지 않는다. 계정이 없거나 비밀번호가 틀려도 같은 401을 준다.",
            security = @SecurityRequirement(name = CSRF_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP 확인 대기. Set-Cookie: __Host-yhs_otp",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OtpRequiredResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류. code=COMMON_INVALID_INPUT",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치. code=AUTH_LOGIN_FAILED",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "403", description = "사용 중지 계정(code=MEMBER_ACCOUNT_DISABLED) 또는 "
                    + "CSRF 실패(code=AUTH_CSRF_INVALID)",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "429", description = "로그인 실패 차단(code=AUTH_LOGIN_TEMPORARILY_BLOCKED), "
                    + "OTP 발급 제한(code=AUTH_OTP_RATE_LIMITED), IP 요청 초과(code=COMMON_TOO_MANY_REQUESTS)",
                    headers = @Header(name = "Retry-After", description = "다시 시도할 수 있을 때까지 초"),
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "503", description = "메일 발송 실패(code=MEMBER_OTP_DELIVERY_UNAVAILABLE), "
                    + "인증 저장소 장애(code=AUTH_SESSION_STORE_UNAVAILABLE·COMMON_RATE_LIMIT_UNAVAILABLE)",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF)))
    })
    ResponseEntity<OtpRequiredResponse> login(@RequestBody LoginRequest request,
                                              @Parameter(hidden = true) HttpServletRequest httpRequest,
                                              @Parameter(hidden = true) HttpServletResponse httpResponse);

    @Operation(summary = "OTP 재발송",
            description = "같은 Challenge 안에서 새 OTP를 보낸다. 이전 OTP는 무효가 된다. 회원 단위 60초 간격·시간당 5회·"
                    + "24시간 20회 제한. Challenge 만료(로그인 후 10분)는 늘어나지 않는다.",
            security = @SecurityRequirement(name = CSRF_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "새 OTP 발송",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OtpRequiredResponse.class))),
            @ApiResponse(responseCode = "401", description = "Challenge 없음·만료·교체(다시 로그인). code=AUTH_OTP_CHALLENGE_INVALID",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "403", description = "CSRF 실패. code=AUTH_CSRF_INVALID",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "429", description = "OTP 발급 제한(code=AUTH_OTP_RATE_LIMITED) 또는 IP 요청 초과"
                    + "(code=COMMON_TOO_MANY_REQUESTS)",
                    headers = @Header(name = "Retry-After", description = "다시 시도할 수 있을 때까지 초"),
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "503", description = "메일 발송 실패(code=MEMBER_OTP_DELIVERY_UNAVAILABLE) 또는 "
                    + "저장소 장애",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF)))
    })
    ResponseEntity<OtpRequiredResponse> resendOtp(
            @Parameter(in = ParameterIn.COOKIE, name = MemberAuthCookies.OTP_CHALLENGE, description = "OTP Challenge") String challengeId,
            @Parameter(hidden = true) HttpServletRequest httpRequest);

    @Operation(summary = "OTP 확인(로그인 완료)",
            description = "OTP가 맞으면 Access Token(__Host-yhs_at, 15분)·Refresh Token(__Secure-yhs_rt, 7일 절대 만료) 쿠키를 "
                    + "주고 Challenge 쿠키를 지우며 CSRF 토큰을 새로 발급한다. 가입 후 첫 OTP 성공이면 계정이 ACTIVE가 된다.",
            security = @SecurityRequirement(name = CSRF_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 완료. Set-Cookie: __Host-yhs_at, __Secure-yhs_rt, XSRF-TOKEN",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LoginSuccessResponse.class))),
            @ApiResponse(responseCode = "400", description = "OTP 형식 오류(숫자 4자리). code=COMMON_INVALID_INPUT",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "401", description = "OTP 불일치(code=AUTH_OTP_MISMATCH), OTP 만료·시도 소진"
                    + "(code=AUTH_OTP_EXPIRED, 재발송 필요), Challenge 무효(code=AUTH_OTP_CHALLENGE_INVALID, 다시 로그인), "
                    + "가입 24시간 경과(code=AUTH_LOGIN_FAILED)",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "403", description = "사용 중지 계정(code=MEMBER_ACCOUNT_DISABLED) 또는 "
                    + "CSRF 실패(code=AUTH_CSRF_INVALID)",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "429", description = "24시간 OTP 실패 합산 초과(code=AUTH_OTP_RATE_LIMITED) 또는 "
                    + "IP 요청 초과(code=COMMON_TOO_MANY_REQUESTS)",
                    headers = @Header(name = "Retry-After", description = "다시 시도할 수 있을 때까지 초"),
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "503", description = "인증 저장소 장애. 인증 쿠키를 발급하지 않는다",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF)))
    })
    ResponseEntity<LoginSuccessResponse> verifyOtp(
            @Parameter(in = ParameterIn.COOKIE, name = MemberAuthCookies.OTP_CHALLENGE, description = "OTP Challenge") String challengeId,
            @RequestBody OtpVerifyRequest request,
            @Parameter(hidden = true) HttpServletRequest httpRequest,
            @Parameter(hidden = true) HttpServletResponse httpResponse);

    @Operation(summary = "로그인 연장(Refresh)",
            description = "Refresh Token 쿠키(__Secure-yhs_rt)만으로 인증한다. Access 쿠키가 없거나 만료돼도 된다. 성공하면 같은 "
                    + "로그인 세션에 새 Access·Refresh 쿠키를 주고 이전 Refresh Token은 다시 쓸 수 없다(7일 절대 만료는 늘어나지 "
                    + "않는다). 이미 쓴 Refresh Token을 다시 보내면 로그인 세션 전체가 폐기된다. CSRF 토큰은 새로 만들지 않는다. "
                    + "401·403이면 Access·Refresh 쿠키를 지우고, 그 밖의 실패에서는 쿠키를 남긴다.",
            security = @SecurityRequirement(name = CSRF_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "연장 완료. Set-Cookie: __Host-yhs_at, __Secure-yhs_rt",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LoginSuccessResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh Token 없음·모르는 토큰·만료·재사용, 회원 없음·PENDING. "
                    + "code=AUTH_REFRESH_INVALID(다시 로그인). Access·Refresh 쿠키 삭제",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "403", description = "사용 중지 계정(code=MEMBER_ACCOUNT_DISABLED, 쿠키 삭제) 또는 "
                    + "CSRF 실패(code=AUTH_CSRF_INVALID, 쿠키 유지)",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "429", description = "IP 요청 초과(code=COMMON_TOO_MANY_REQUESTS). 쿠키 유지",
                    headers = @Header(name = "Retry-After", description = "다시 시도할 수 있을 때까지 초"),
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "500", description = "회원 조회 장애 등 서버 오류(code=COMMON_INTERNAL_ERROR). "
                    + "토큰을 바꾸지 않고 쿠키 유지",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "503", description = "인증 저장소 장애(code=AUTH_SESSION_STORE_UNAVAILABLE·"
                    + "COMMON_RATE_LIMIT_UNAVAILABLE). 쿠키 유지",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF)))
    })
    ResponseEntity<LoginSuccessResponse> refresh(
            @Parameter(in = ParameterIn.COOKIE, name = MemberAuthCookies.REFRESH_TOKEN, description = "Refresh Token") String refreshToken,
            @Parameter(hidden = true) HttpServletRequest httpRequest,
            @Parameter(hidden = true) HttpServletResponse httpResponse);

    @Operation(summary = "로그아웃",
            description = "이 브라우저의 Access·Refresh 쿠키가 가리키는 로그인 세션을 서버에서 폐기하고 인증 쿠키를 지우며 CSRF "
                    + "토큰을 새로 발급한다. Access가 만료돼도 Refresh 쿠키로 폐기한다. 폐기할 세션이 없어도 204다. 저장소 장애면 "
                    + "503이고 쿠키·CSRF 토큰을 남긴다(다시 시도).",
            security = @SecurityRequirement(name = CSRF_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 완료. Set-Cookie: 인증 쿠키 삭제(Max-Age=0), XSRF-TOKEN"),
            @ApiResponse(responseCode = "403", description = "CSRF 실패. code=AUTH_CSRF_INVALID",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "503", description = "인증 저장소 장애. code=AUTH_SESSION_STORE_UNAVAILABLE",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF)))
    })
    ResponseEntity<Void> logout(
            @Parameter(hidden = true) AuthenticatedMember accessPrincipal,
            @Parameter(in = ParameterIn.COOKIE, name = MemberAuthCookies.REFRESH_TOKEN, description = "Refresh Token") String refreshToken,
            @Parameter(hidden = true) HttpServletRequest httpRequest,
            @Parameter(hidden = true) HttpServletResponse httpResponse);
}
