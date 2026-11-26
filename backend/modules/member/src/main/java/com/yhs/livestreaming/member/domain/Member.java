package com.yhs.livestreaming.member.domain;

import com.yhs.livestreaming.global.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * 회원(docs/member/CONTEXT.md 4장). 스키마는 Flyway {@code V20261126_1__member_create_members.sql}이 원본이고
 * Hibernate는 검증만 한다(ddl-auto=validate).
 */
@Entity
@Table(name = "members")
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", length = 50, unique = true)
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "nickname", length = 50, nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20, nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", length = 20, nullable = false)
    private AccountStatus accountStatus;

    protected Member() {
    }

    /**
     * 일반(LOCAL) 가입 회원을 만든다. 가입 직후는 PENDING·이메일 미확인·USER다(MB-D003, 명세 M-01).
     * 값의 형식 검증과 정규화(소문자 등)는 입력 계층에서 끝난 값을 받는다(MB-D008).
     */
    public static Member registerLocal(String username, String passwordHash, String email, String nickname) {
        requireText(username, "username");
        requireText(passwordHash, "passwordHash");
        requireText(email, "email");
        requireText(nickname, "nickname");
        Member member = new Member();
        member.username = username;
        member.passwordHash = passwordHash;
        member.email = email;
        member.nickname = nickname;
        member.emailVerified = false;
        member.role = MemberRole.USER;
        member.accountType = AccountType.LOCAL;
        member.accountStatus = AccountStatus.PENDING;
        return member;
    }

    /**
     * PENDING 유지 기간(MB-D003): 가입 시각 기준 24시간, 연장 없음. 이 시각이 지나면 로그인할 수 없고 정리 대상이다.
     */
    public static final Duration PENDING_TTL = Duration.ofHours(24);

    /** PENDING이고 가입 후 24시간이 지났으면 true(지난 순간부터 만료). */
    public boolean isPendingExpired(Instant now) {
        return accountStatus == AccountStatus.PENDING && getCreatedAt() != null
                && !now.isBefore(getCreatedAt().plus(PENDING_TTL));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 값이 비어 있다");
        }
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public MemberRole getRole() {
        return role;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }
}
