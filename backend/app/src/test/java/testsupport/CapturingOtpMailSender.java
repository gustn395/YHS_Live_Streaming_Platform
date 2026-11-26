package testsupport;

import com.yhs.livestreaming.member.app.otp.OtpMailSender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 통합 테스트용 OTP 메일 대역(MB-D016): 실제 SMTP를 부르지 않고 보낸 OTP를 기록한다. 실패도 흉내 낸다. */
public class CapturingOtpMailSender implements OtpMailSender {

    public record SentOtp(String email, String code, Instant expiresAt) {
    }

    private final List<SentOtp> sent = new ArrayList<>();
    private volatile boolean failing;

    @Override
    public synchronized void send(String email, String code, Instant expiresAt) {
        if (failing) {
            throw new IllegalStateException("smtp unavailable for " + email + " code " + code);
        }
        sent.add(new SentOtp(email, code, expiresAt));
    }

    public synchronized List<SentOtp> sent() {
        return List.copyOf(sent);
    }

    public synchronized String lastCodeFor(String email) {
        for (int i = sent.size() - 1; i >= 0; i--) {
            if (sent.get(i).email().equals(email)) {
                return sent.get(i).code();
            }
        }
        throw new AssertionError("보낸 OTP 없음: " + email);
    }

    public void failing(boolean failing) {
        this.failing = failing;
    }

    public synchronized void reset() {
        sent.clear();
        failing = false;
    }
}
