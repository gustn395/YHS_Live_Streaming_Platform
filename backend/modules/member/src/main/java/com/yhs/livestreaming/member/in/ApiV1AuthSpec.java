package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.member.in.dto.SignupRequest;
import com.yhs.livestreaming.member.in.dto.SignupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 인증 API의 Swagger Spec(명세 4.6, G-D008). 컨트롤러는 이 인터페이스를 구현하고, 문서와 실제 응답이 같은지는 통합 테스트로 확인한다.
 */
@Tag(name = "Authentication", description = "회원가입·로그인 인증")
public interface ApiV1AuthSpec {

    String PROBLEM_DETAIL_REF = "#/components/schemas/ProblemDetail";

    @Operation(summary = "일반 회원가입",
            description = "username·비밀번호·이메일·닉네임으로 가입한다. 가입 직후 계정은 PENDING·이메일 미확인이며 "
                    + "로그인 토큰을 발급하지 않는다. 비밀번호는 15~64자이고 아이디·서비스 이름을 바탕으로 한 비밀번호와 "
                    + "유출된 적이 있는 비밀번호(HIBP 확인)는 거부한다. 유출 여부를 확인할 수 없으면 가입하지 않는다(503).")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "가입 완료(PENDING)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SignupResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력 검증 실패(code=COMMON_INVALID_INPUT, errors[]에 필드별 이유) "
                    + "또는 유출된 비밀번호(code=MEMBER_PASSWORD_BREACHED, errors[] 없음)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "409",
                    description = "중복. code=MEMBER_USERNAME_DUPLICATE 또는 MEMBER_EMAIL_DUPLICATE",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "503",
                    description = "유출 비밀번호 확인 불가(외부 확인 서비스 장애). code=MEMBER_PASSWORD_CHECK_UNAVAILABLE",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM_DETAIL_REF)))
    })
    ResponseEntity<SignupResponse> signup(@RequestBody SignupRequest request);
}
