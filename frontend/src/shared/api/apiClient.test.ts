import axios from 'axios';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/msw/server';
import { AXIOS_SECURITY_DEFAULTS, createApiClient } from './apiClient';
import { ApiError } from './apiError';
import { ApiRequestError } from './requestOptions';

const PROBLEM_JSON = { 'Content-Type': 'application/problem+json' };

async function failureOf(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ApiError) return error.failure;
    throw error;
  }
  throw new Error('요청이 성공했다');
}

describe('성공 응답', () => {
  it('/api 아래로 요청하고 본문을 돌려준다', async () => {
    server.use(http.get('*/api/v1/ping', () => HttpResponse.json({ pong: true })));
    const client = createApiClient();
    await expect(client.request({ method: 'get', path: '/v1/ping', authScope: 'none' })).resolves.toEqual({ pong: true });
  });

  it('parse가 실패하면 invalid-response(status 보존)다', async () => {
    server.use(http.get('*/api/v1/ping', () => HttpResponse.json({ unexpected: 1 })));
    const client = createApiClient();
    const parse = (data: unknown) => {
      if (typeof data === 'object' && data !== null && 'pong' in data) return data;
      throw new Error('형식 오류');
    };
    expect(await failureOf(client.request({ method: 'get', path: '/v1/ping', authScope: 'none' }, parse))).toEqual({
      kind: 'invalid-response',
      status: 200,
    });
  });
});

// FE-T002: 비동기 parse도 기다린 뒤 형식 오류를 invalid-response로 바꾼다(Bot 리뷰 반영)
describe('비동기 parse', () => {
  it('비동기 parse 결과를 기다려 돌려준다', async () => {
    server.use(http.get('*/api/v1/ping', () => HttpResponse.json({ pong: true })));
    const client = createApiClient();
    const parse = async (data: unknown) => {
      await delay(5);
      return { parsed: data };
    };
    await expect(client.request({ method: 'get', path: '/v1/ping', authScope: 'none' }, parse)).resolves.toEqual({
      parsed: { pong: true },
    });
  });

  it('비동기 parse가 reject하면 invalid-response(status 보존)다', async () => {
    server.use(http.get('*/api/v1/ping', () => HttpResponse.json({ unexpected: 1 })));
    const client = createApiClient();
    const parse = async () => {
      await delay(5);
      throw new Error('형식 오류');
    };
    expect(await failureOf(client.request({ method: 'get', path: '/v1/ping', authScope: 'none' }, parse))).toEqual({
      kind: 'invalid-response',
      status: 200,
    });
  });
});

// FE-T002: 오류 응답 정규화(G-D003, FE-D007)
describe('HTTP 오류 정규화', () => {
  it('ProblemDetail의 code·title·detail·errors[]를 꺼낸다', async () => {
    server.use(
      http.post('*/api/v1/members', () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Bad Request',
            status: 400,
            detail: '입력값이 올바르지 않습니다.',
            code: 'COMMON_INVALID_INPUT',
            errors: [{ field: 'email', message: '형식 오류' }, { field: 1 }],
          },
          { status: 400, headers: PROBLEM_JSON },
        ),
      ),
    );
    const client = createApiClient();
    expect(await failureOf(client.request({ method: 'post', path: '/v1/members', authScope: 'none', data: {} }))).toEqual({
      kind: 'http',
      status: 400,
      problem: {
        code: 'COMMON_INVALID_INPUT',
        title: 'Bad Request',
        detail: '입력값이 올바르지 않습니다.',
        fieldErrors: [{ field: 'email', message: '형식 오류' }],
      },
    });
  });

  it('ProblemDetail이 아닌 JSON 오류는 status만 보존하고 code를 만들지 않는다', async () => {
    server.use(http.get('*/api/v1/x', () => HttpResponse.json({ code: 'FAKE' }, { status: 500 })));
    const client = createApiClient();
    expect(await failureOf(client.request({ method: 'get', path: '/v1/x', authScope: 'none' }))).toEqual({
      kind: 'http',
      status: 500,
      problem: null,
    });
  });

  it('JSON이 아닌 오류(프록시 HTML 등)도 status를 보존한다', async () => {
    server.use(http.get('*/api/v1/x', () => HttpResponse.text('<html>Bad Gateway</html>', { status: 502 })));
    const client = createApiClient();
    expect(await failureOf(client.request({ method: 'get', path: '/v1/x', authScope: 'none' }))).toEqual({
      kind: 'http',
      status: 502,
      problem: null,
    });
  });

  it('problem+json인데 본문이 객체가 아니면 ProblemDetail로 보지 않는다', async () => {
    server.use(http.get('*/api/v1/x', () => new HttpResponse('null', { status: 503, headers: PROBLEM_JSON })));
    const client = createApiClient();
    expect(await failureOf(client.request({ method: 'get', path: '/v1/x', authScope: 'none' }))).toEqual({
      kind: 'http',
      status: 503,
      problem: null,
    });
  });
});

// FE-T003: network·timeout·canceled 구분(FE-D007)
describe('전송 오류 분류', () => {
  it('네트워크 오류는 network', async () => {
    server.use(http.get('*/api/v1/x', () => HttpResponse.error()));
    const client = createApiClient();
    expect(await failureOf(client.request({ method: 'get', path: '/v1/x', authScope: 'none' }))).toEqual({ kind: 'network' });
  });

  it('요청별 Timeout을 넘으면 timeout', async () => {
    server.use(
      http.get('*/api/v1/slow', async () => {
        await delay(500);
        return HttpResponse.json({});
      }),
    );
    const client = createApiClient();
    expect(
      await failureOf(client.request({ method: 'get', path: '/v1/slow', authScope: 'none', timeoutMs: 30 })),
    ).toEqual({ kind: 'timeout' });
  });

  it('호출자 signal로 취소하면 canceled', async () => {
    server.use(
      http.get('*/api/v1/slow', async () => {
        await delay(500);
        return HttpResponse.json({});
      }),
    );
    const client = createApiClient();
    const controller = new AbortController();
    const pending = client.request({ method: 'get', path: '/v1/slow', authScope: 'none', signal: controller.signal });
    controller.abort();
    expect(await failureOf(pending)).toEqual({ kind: 'canceled' });
  });

  it('잘못된 timeoutMs·경로는 요청을 보내기 전에 거부한다', async () => {
    let called = 0;
    server.use(
      http.all('*', () => {
        called += 1;
        return HttpResponse.json({});
      }),
    );
    const client = createApiClient();
    await expect(client.request({ method: 'get', path: '/v1/x', authScope: 'none', timeoutMs: 0 })).rejects.toBeInstanceOf(
      ApiRequestError,
    );
    // FE-T005: 절대·다른 출처 URL
    await expect(
      client.request({ method: 'get', path: 'https://evil.example/x', authScope: 'none' }),
    ).rejects.toBeInstanceOf(ApiRequestError);
    await expect(client.request({ method: 'get', path: '//evil.example/x', authScope: 'none' })).rejects.toBeInstanceOf(
      ApiRequestError,
    );
    expect(called).toBe(0);
  });
});

// FE-T004: XSRF 쿠키가 있어도 X-XSRF-TOKEN을 붙이지 않는다(FE-D014). jsdom + XHR 어댑터 기준.
describe('XSRF 자동 처리 비활성', () => {
  function captureHeaders() {
    const seen: Headers[] = [];
    server.use(
      http.get('*/api/v1/xsrf', ({ request }) => {
        seen.push(request.headers);
        return HttpResponse.json({});
      }),
    );
    return seen;
  }

  it('대조군: 기본 Axios(XHR)는 같은 출처 요청에 XSRF 헤더를 붙인다', async () => {
    document.cookie = 'XSRF-TOKEN=control-token; path=/';
    const seen = captureHeaders();
    await axios.create({ adapter: 'xhr' }).get('/api/v1/xsrf');
    expect(seen[0]?.get('x-xsrf-token')).toBe('control-token');
  });

  it('공통 Client는 XSRF 쿠키가 있어도 헤더를 붙이지 않는다', async () => {
    document.cookie = 'XSRF-TOKEN=should-not-leak; path=/';
    const seen = captureHeaders();
    await createApiClient().request({ method: 'get', path: '/v1/xsrf', authScope: 'none' });
    expect(seen).toHaveLength(1);
    expect(seen[0]?.has('x-xsrf-token')).toBe(false);
  });

  it('보안 기본값: XSRF 끔, 이름 비움, 절대 URL 불허, XHR 어댑터, /api 기준', () => {
    expect(AXIOS_SECURITY_DEFAULTS).toEqual({
      baseURL: '/api',
      allowAbsoluteUrls: false,
      withXSRFToken: false,
      xsrfCookieName: '',
      xsrfHeaderName: '',
      withCredentials: false,
      adapter: 'xhr',
    });
  });
});
