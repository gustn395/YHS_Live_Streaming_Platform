import { ApiRequestError } from './requestOptions';

export const API_BASE_PATH = '/api';

const PROBE_ORIGIN = 'http://api-path.invalid';
// 제어 문자, 공백, 역슬래시, 쿼리·프래그먼트 구분자
// eslint-disable-next-line no-control-regex -- 제어 문자를 찾아 거부하는 것이 목적이다
const UNSAFE_PATH_CHARS = /[\u0000- \u007f\\?#]/;

/**
 * 요청 경로가 같은 출처의 `/api/` 아래만 가리키는지 검증하고 그대로 돌려준다(FE-D013).
 * 퍼센트 인코딩으로 숨긴 `/`·`\`·`.`·`..` 세그먼트도 거부한다.
 */
export function toApiPath(path: string): string {
  const reject = (reason: string): never => {
    throw new ApiRequestError(`허용하지 않는 API 경로(${reason}): ${JSON.stringify(path)}`);
  };

  if (!path.startsWith('/') || path.startsWith('//')) reject('/로 시작하는 상대 경로만 허용');
  if (UNSAFE_PATH_CHARS.test(path)) reject('금지 문자');

  for (const segment of path.slice(1).split('/')) {
    if (segment === '') reject('빈 세그먼트');
    let decoded: string;
    try {
      decoded = decodeURIComponent(segment);
    } catch {
      return reject('잘못된 퍼센트 인코딩');
    }
    if (decoded === '.' || decoded === '..') reject('점 세그먼트');
    if (/[/\\%]/.test(decoded)) reject('인코딩된 구분자');
  }

  const resolved = new URL(API_BASE_PATH + path, PROBE_ORIGIN);
  if (resolved.origin !== PROBE_ORIGIN || !resolved.pathname.startsWith(`${API_BASE_PATH}/`)) {
    reject('/api 밖');
  }
  return path;
}
