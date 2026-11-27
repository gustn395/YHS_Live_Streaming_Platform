package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.global.auth.AuthenticatedMember;
import com.yhs.livestreaming.global.openapi.OpenApiConfig;
import com.yhs.livestreaming.member.in.dto.MemberProfileResponse;
import com.yhs.livestreaming.member.in.dto.NicknameUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 회원 정보 API의 Swagger Spec(MB-D028, G-D008). 컨트롤러는 이 인터페이스를 구현하고, 문서와 실제 응답이 같은지는 통합 테스트로
 * 확인한다. 모든 요청에 Access 쿠키가 필요하고, 수정 요청은 CSRF 헤더({@code X-XSRF-TOKEN}, G-D014)도 필요하다.
 */
@Tag(name = "Member", description = "로그인한 회원 본인의 정보")
public interface ApiV1MemberSpec {

    String PROBLEM_DETAIL_REF = "#/components/schemas/ProblemDetail";
    String PROBLEM_JSON = "application/problem+json";
    String ACCESS_SCHEME = OpenApiConfig.ACCESS_TOKEN_COOKIE_SCHEME;
    String CSRF_SCHEME = OpenApiConfig.CSRF_HEADER_SCHEME;

    @Operation(summary = "내 정보 조회",
            description = "로그인한 회원 본인의 정보를 돌려준다. 요청마다 계정 상태를 다시 확인한다: 사용이 중지된 계정이면 이 로그인 "
                    + "세션을 폐기하고 인증 쿠키를 지운 뒤 403, 회원이 없으면 세션을 폐기하고 인증 쿠키를 지운 뒤 401이다.",
            security = @SecurityRequirement(name = ACCESS_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내 정보"),
            @ApiResponse(responseCode = "401", description = "인증 없음·세션 만료·회원 없음. code=AUTH_UNAUTHENTICATED",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "403", description = "사용이 중지된 계정. code=MEMBER_ACCOUNT_DISABLED",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "503", description = "인증 저장소 장애. code=AUTH_SESSION_STORE_UNAVAILABLE",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF)))
    })
    ResponseEntity<MemberProfileResponse> getMyProfile(@Parameter(hidden = true) AuthenticatedMember me,
                                                       @Parameter(hidden = true) HttpServletResponse httpResponse);

    @Operation(summary = "닉네임 수정",
            description = "로그인한 회원 본인의 닉네임을 바꾸고 바뀐 내 정보를 돌려준다. 닉네임만 바꿀 수 있다(2~20자, 중복 허용). "
                    + "계정 상태 처리는 내 정보 조회와 같다.",
            security = {@SecurityRequirement(name = ACCESS_SCHEME), @SecurityRequirement(name = CSRF_SCHEME)})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정한 내 정보"),
            @ApiResponse(responseCode = "400", description = "닉네임 형식 오류. code=COMMON_INVALID_INPUT, errors[]",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "401", description = "인증 없음·세션 만료·회원 없음. code=AUTH_UNAUTHENTICATED",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "403",
                    description = "CSRF 실패(code=AUTH_CSRF_INVALID) 또는 사용이 중지된 계정(code=MEMBER_ACCOUNT_DISABLED)",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF))),
            @ApiResponse(responseCode = "503", description = "인증 저장소 장애. code=AUTH_SESSION_STORE_UNAVAILABLE",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = PROBLEM_DETAIL_REF)))
    })
    ResponseEntity<MemberProfileResponse> changeNickname(@Parameter(hidden = true) AuthenticatedMember me,
                                                         @RequestBody NicknameUpdateRequest request,
                                                         @Parameter(hidden = true) HttpServletResponse httpResponse);
}
