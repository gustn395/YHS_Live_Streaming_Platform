/**
 * E2E 실행 환경 값(FE-D039). 기본값을 두지 않는다: 값이 없으면 어떤 서버에 붙는지 모르므로 바로 멈춘다.
 * 비밀 값은 여기서 다루지 않는다(서버 기동에만 쓰고 테스트는 알 필요가 없다).
 */
export interface E2eEnv {
  baseUrl: string;
  mailpitUrl: string;
}

function required(name: string): string {
  const value = process.env[name];
  if (value === undefined || value.trim() === '') {
    throw new Error(`E2E 환경변수 ${name}가 없습니다. docs/frontend/test-spec.md의 E2E 실행 절차를 따르세요.`);
  }
  return value.replace(/\/+$/, '');
}

export function readE2eEnv(): E2eEnv {
  return { baseUrl: required('E2E_BASE_URL'), mailpitUrl: required('E2E_MAILPIT_URL') };
}
