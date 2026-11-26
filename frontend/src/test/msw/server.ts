import { setupServer } from 'msw/node';

// 기본 핸들러는 두지 않는다. 테스트마다 server.use로 필요한 응답만 정의하고, 정의하지 않은 요청은 오류로 본다.
// 여기서 정의하는 Backend 응답은 확정 계약이 아닌 가안이다(FE-D005).
export const server = setupServer();
