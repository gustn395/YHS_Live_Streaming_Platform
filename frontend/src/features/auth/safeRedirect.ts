// eslint-disable-next-line no-control-regex -- 제어 문자를 찾아 거부하는 것이 목적이다
const UNSAFE_REDIRECT_CHARS = /[\u0000-\u001f\u007f\\]/;
const LOGIN_PATH = '/login';

/** 경로 부분(? # 앞)에 빈 세그먼트나 `.`·`..`(인코딩 포함) 세그먼트가 있는지. */
function hasUnsafeSegment(from: string): boolean {
  const pathPart = from.split(/[?#]/, 1)[0] ?? '';
  return pathPart
    .slice(1)
    .split('/')
    .some((segment) => {
      if (segment === '') return pathPart !== '/';
      try {
        const decoded = decodeURIComponent(segment);
        return decoded === '.' || decoded === '..';
      } catch {
        return true;
      }
    });
}

/**
 * 로그인 뒤 돌아갈 경로를 안전한 같은 출처 내부 경로로 제한한다. 아니면 "/"(FE-D012).
 * 점 세그먼트는 URL 정규화 뒤 `//host` 같은 다른 출처 주소를 만들 수 있으므로 거부한다.
 * 로그인 성공 처리와 Open Redirect 후속 검토는 FE-U03이다.
 */
export function safeRedirect(from: string | null, origin: string): string {
  if (!from || !from.startsWith('/') || from.startsWith('//') || UNSAFE_REDIRECT_CHARS.test(from)) return '/';
  let decoded: string;
  try {
    decoded = decodeURIComponent(from);
  } catch {
    return '/';
  }
  if (decoded.startsWith('//') || UNSAFE_REDIRECT_CHARS.test(decoded) || hasUnsafeSegment(from)) return '/';

  const url = new URL(from, origin);
  if (url.origin !== new URL(origin).origin) return '/';
  if (url.pathname.startsWith('//')) return '/';
  if (url.pathname === LOGIN_PATH || url.pathname.startsWith(`${LOGIN_PATH}/`)) return '/';
  return url.pathname + url.search + url.hash;
}
