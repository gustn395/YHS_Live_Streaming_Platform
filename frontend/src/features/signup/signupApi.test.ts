import { http, HttpResponse, type JsonBodyType } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/msw/server';
import { createApiClient } from '../../shared/api/apiClient';
import { ApiError, type ApiFailure } from '../../shared/api/apiError';
import { submitSignup } from './signupApi';

const SIGNUP = '*/api/v1/auth/signup';
const INPUT = {
  username: 'Tester_01',
  email: ' Tester@Example.com ',
  nickname: ' 테스터 ',
  password: '  correct horse battery  ',
  passwordConfirm: '  correct horse battery  ',
};

async function failureOf(promise: Promise<unknown>): Promise<ApiFailure> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ApiError) return error.failure;
    throw error;
  }
  throw new Error('요청이 성공했다');
}

function captureSignup(response: () => Response) {
  const calls: { body: unknown; token: string | null }[] = [];
  server.use(
    http.post(SIGNUP, async ({ request }) => {
      calls.push({ body: await request.json(), token: request.headers.get('x-xsrf-token') });
      return response();
    }),
  );
  return calls;
}

// FE-T025: 가입 요청·응답 계약(Backend SignupRequest·SignupResponse, FE-D031)
describe('submitSignup 요청', () => {
  it('passwordConfirm을 포함하고 비밀번호는 원문 그대로, username은 소문자로 보낸다', async () => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const calls = captureSignup(() => HttpResponse.json({ memberId: 7, accountStatus: 'PENDING' }, { status: 201 }));
    await expect(submitSignup(createApiClient(), INPUT)).resolves.toEqual({ memberId: 7, accountStatus: 'PENDING' });
    expect(calls).toEqual([
      {
        body: {
          username: 'tester_01',
          password: '  correct horse battery  ',
          passwordConfirm: '  correct horse battery  ',
          email: ' Tester@Example.com ',
          nickname: ' 테스터 ',
        },
        token: 't1',
      },
    ]);
  });
});

describe('submitSignup 201 계약', () => {
  it.each<[string, JsonBodyType]>([
    ['memberId 누락', { accountStatus: 'PENDING' }],
    ['memberId 0', { memberId: 0, accountStatus: 'PENDING' }],
    ['memberId 음수', { memberId: -1, accountStatus: 'PENDING' }],
    ['memberId 소수', { memberId: 1.5, accountStatus: 'PENDING' }],
    ['memberId 문자열', { memberId: '7', accountStatus: 'PENDING' }],
    ['memberId 안전 정수 초과', { memberId: 2 ** 53, accountStatus: 'PENDING' }],
    ['PENDING 아님', { memberId: 7, accountStatus: 'ACTIVE' }],
    ['본문 없음', null],
  ])('%s → invalid-response, 재전송 없음', async (_name, body) => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const calls = captureSignup(() => HttpResponse.json(body, { status: 201 }));
    expect(await failureOf(submitSignup(createApiClient(), INPUT))).toEqual({ kind: 'invalid-response', status: 201 });
    expect(calls).toHaveLength(1);
  });

  // CR-6: 201만 성공이다. 정상 본문이어도 다른 2xx는 invalid-response(재전송 없음)
  it.each([200, 202])('%s + 정상 본문 → invalid-response, 재전송 없음', async (status) => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const calls = captureSignup(() => HttpResponse.json({ memberId: 7, accountStatus: 'PENDING' }, { status }));
    expect(await failureOf(submitSignup(createApiClient(), INPUT))).toEqual({ kind: 'invalid-response', status });
    expect(calls).toHaveLength(1);
  });

  it('204(본문 없음) → invalid-response', async () => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    captureSignup(() => new HttpResponse(null, { status: 204 }));
    expect(await failureOf(submitSignup(createApiClient(), INPUT))).toEqual({ kind: 'invalid-response', status: 204 });
  });

  it('network 오류는 재전송하지 않는다', async () => {
    document.cookie = 'XSRF-TOKEN=t1; path=/';
    const calls = captureSignup(() => HttpResponse.error());
    expect((await failureOf(submitSignup(createApiClient(), INPUT))).kind).toBe('network');
    expect(calls).toHaveLength(1);
  });
});
