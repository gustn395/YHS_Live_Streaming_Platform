import { readE2eEnv } from './env';

/** OTP 메일 본문의 번호 줄(member SmtpOtpMailSender). 같은 본문의 날짜·시각 숫자를 OTP로 읽지 않게 이 줄만 본다. */
const OTP_LINE = /로그인 인증 번호: (\d{4})/;
const POLL_MS = 250;
const WAIT_MS = 15_000;

interface MessageSummary {
  ID: string;
}

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${readE2eEnv().mailpitUrl}${path}`);
  if (!response.ok) throw new Error(`Mailpit 요청 실패: ${response.status} ${path}`);
  return (await response.json()) as T;
}

/** 수신자에게 온 메일 ID 목록(최신순). 테스트마다 수신자가 달라 다른 테스트 메일과 섞이지 않는다. */
export async function messageIds(email: string): Promise<string[]> {
  const query = encodeURIComponent(`to:"${email}"`);
  const result = await getJson<{ messages: MessageSummary[] | null }>(`/api/v1/search?query=${query}`);
  return (result.messages ?? []).map((m) => m.ID);
}

/**
 * before(요청 직전의 메일 ID 목록)에 없던 새 메일이 올 때까지 기다려 그 OTP를 돌려준다.
 * 이전 메일을 새 OTP로 잘못 읽지 않게 한다.
 */
export async function waitForNewOtp(email: string, before: readonly string[]): Promise<string> {
  const deadline = Date.now() + WAIT_MS;
  while (Date.now() < deadline) {
    const fresh = (await messageIds(email)).filter((id) => !before.includes(id));
    if (fresh.length > 1) throw new Error(`새 OTP 메일이 ${fresh.length}통 왔습니다(1통이어야 함): ${email}`);
    const id = fresh[0];
    if (id !== undefined) {
      const message = await getJson<{ Text: string }>(`/api/v1/message/${encodeURIComponent(id)}`);
      const match = OTP_LINE.exec(message.Text);
      if (match?.[1] === undefined) throw new Error('OTP 메일 본문에서 인증 번호를 찾지 못했습니다');
      return match[1];
    }
    await new Promise((resolve) => setTimeout(resolve, POLL_MS));
  }
  throw new Error(`${WAIT_MS}ms 안에 새 OTP 메일이 오지 않았습니다: ${email}`);
}
