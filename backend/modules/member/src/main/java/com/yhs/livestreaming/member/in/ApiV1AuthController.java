package com.yhs.livestreaming.member.in;

import com.yhs.livestreaming.member.app.SignupCommand;
import com.yhs.livestreaming.member.app.SignupResult;
import com.yhs.livestreaming.member.app.SignupService;
import com.yhs.livestreaming.member.in.dto.SignupRequest;
import com.yhs.livestreaming.member.in.dto.SignupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class ApiV1AuthController implements ApiV1AuthSpec {

    private final SignupService signupService;

    ApiV1AuthController(SignupService signupService) {
        this.signupService = signupService;
    }

    @Override
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResult result = signupService.signup(
                new SignupCommand(request.username(), request.password(), request.email(), request.nickname()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SignupResponse(result.memberId(), result.accountStatus()));
    }
}
