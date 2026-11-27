package com.yhs.livestreaming.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * OAuth 연결(docs/member/CONTEXT.md 4장, MB-D025): GOOGLE 회원과 공급자 계정(provider·OIDC sub)을 잇는다. 스키마는 Flyway
 * {@code V20261126_1__member_create_members.sql}의 {@code member_oauth_accounts}가 원본이다(수정 시각 열 없음).
 */
@Entity
@Table(name = "member_oauth_accounts")
@EntityListeners(AuditingEntityListener.class)
public class OAuthAccount {

    /** 지금 지원하는 공급자. 열 값으로 그대로 저장한다. */
    public static final String GOOGLE = "GOOGLE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "provider", length = 30, nullable = false)
    private String provider;

    @Column(name = "provider_user_id", length = 255, nullable = false)
    private String providerUserId;

    @Column(name = "email", length = 255)
    private String email;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OAuthAccount() {
    }

    public static OAuthAccount google(long memberId, String sub, String email) {
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("sub 값이 비어 있다");
        }
        OAuthAccount account = new OAuthAccount();
        account.memberId = memberId;
        account.provider = GOOGLE;
        account.providerUserId = sub;
        account.email = email;
        return account;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "OAuthAccount[id=" + id + ", memberId=" + memberId + ", provider=" + provider + ", sub=****, email=****]";
    }
}
