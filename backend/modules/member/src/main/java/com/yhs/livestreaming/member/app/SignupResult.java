package com.yhs.livestreaming.member.app;

import com.yhs.livestreaming.member.domain.AccountStatus;

public record SignupResult(long memberId, AccountStatus accountStatus) {
}
