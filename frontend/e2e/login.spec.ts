import { expect, test, type Page } from '@playwright/test';
import { createAccount, type TestAccount } from './support/accounts';
import { messageIds, waitForNewOtp } from './support/mailpit';

/**
 * E2E(FE-D039, CI 실행 FE-D044): 실제 Backend·Mailpit으로 일반 로그인 → 이메일 OTP 흐름을 확인한다(test-spec E2E-01~03).
 * 단언 실패 출력에 OTP·쿠키 값이 찍히지 않게 쓴다(CI 로그는 공개된다).
 * 앱 소스를 import하지 않고 화면에 보이는 이름으로만 조작한다.
 */

async function logIn(page: Page, account: TestAccount): Promise<string[]> {
  await page.getByLabel('아이디').fill(account.username);
  await page.getByLabel('비밀번호').fill(account.password);
  const before = await messageIds(account.email);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('heading', { name: '인증 번호 확인' })).toBeVisible();
  return before;
}

async function enterOtp(page: Page, code: string): Promise<void> {
  await page.getByLabel('인증 번호').fill(code);
  await page.getByRole('button', { name: '확인', exact: true }).click();
}

function anotherCode(code: string): string {
  return String((Number(code) + 1) % 10_000).padStart(4, '0');
}

test('E2E-01 일반 로그인 → OTP → 원래 화면으로 이동하고 인증 쿠키를 받는다 @smoke', async ({ page, context }) => {
  const account = await createAccount();
  await page.goto('/app');
  await expect(page).toHaveURL(/\/login\?from=%2Fapp$/);
  const before = await logIn(page, account);
  const otp = await waitForNewOtp(account.email, before);

  const verify = page.waitForRequest((r) => r.url().endsWith('/api/v1/auth/otp/verify') && r.method() === 'POST');
  await enterOtp(page, otp);
  expect((await verify).headers()['x-xsrf-token']).toBeTruthy();

  await expect(page).toHaveURL(/\/app$/);
  await expect(page.getByRole('heading', { name: '내 화면' })).toBeVisible();
  const access = (await context.cookies()).find((c) => c.name === '__Host-yhs_at');
  // 실패 출력에 쿠키 값(토큰)이 찍히지 않게 검사할 속성만 꺼내 비교한다(fe06-e2e-ci.md Q10)
  const attributes = access && { httpOnly: access.httpOnly, secure: access.secure, sameSite: access.sameSite, path: access.path };
  expect(attributes, '__Host-yhs_at 쿠키 속성').toEqual({ httpOnly: true, secure: true, sameSite: 'Strict', path: '/' });
});

test('E2E-02 OTP를 틀리면 안내하고 입력을 비우며, 맞는 번호로 성공한다', async ({ page }) => {
  const account = await createAccount();
  await page.goto('/login');
  const before = await logIn(page, account);
  const otp = await waitForNewOtp(account.email, before);

  await enterOtp(page, anotherCode(otp));
  await expect(page.getByRole('alert')).toHaveText('인증 번호가 일치하지 않습니다. 다시 입력해 주세요.');
  // 입력값(OTP)이 실패 출력에 찍히지 않게 길이만 기다려 확인한다(fe06-e2e-ci.md Q10)
  await expect
    .poll(async () => (await page.getByLabel('인증 번호').inputValue()).length, { message: '오입력 뒤 인증 번호 입력이 비워져야 한다' })
    .toBe(0);

  await enterOtp(page, otp);
  await expect(page).toHaveURL(/\/app$/);
});

// @slow: 재발송 60초 대기. CI의 PR 실행에서는 빼고 main push·수동 실행에서 돈다(FE-D044 (2))
test('E2E-03 60초 뒤 재발송하면 새 번호가 오고, 이전 번호는 거부되며 새 번호로 성공한다 @slow', async ({ page }) => {
  test.setTimeout(120_000);
  const account = await createAccount();
  await page.goto('/login');
  const before = await logIn(page, account);
  const first = await waitForNewOtp(account.email, before);

  const resend = page.getByRole('button', { name: '인증 번호 다시 받기' });
  await expect(resend).toBeEnabled({ timeout: 75_000 });
  const beforeResend = await messageIds(account.email);
  await resend.click();
  await expect(page.getByRole('status')).toHaveText('새 인증 번호를 보냈습니다. 메일을 확인해 주세요.');
  const second = await waitForNewOtp(account.email, beforeResend);
  // Backend는 최근에 보낸 번호와 같은 번호를 다시 뽑는다(member BE-03). 같으면 이전 번호 무효를 확인할 수 없다
  expect(second === first, '재발송 번호가 이전 번호와 달라야 한다(번호는 출력하지 않는다)').toBe(false);

  await enterOtp(page, first);
  await expect(page.getByRole('alert')).toHaveText('인증 번호가 일치하지 않습니다. 다시 입력해 주세요.');

  await enterOtp(page, second);
  await expect(page).toHaveURL(/\/app$/);
});
