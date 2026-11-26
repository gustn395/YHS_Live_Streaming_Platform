import { Link } from 'react-router';

/**
 * 라우팅(loader·렌더) 오류 전용 화면이다(FE-D012). 비동기 API 오류는 API Client와 호출부가 처리한다.
 * 오류 상세는 화면에 노출하지 않는다.
 */
export function RouteError() {
  return (
    <main>
      <h1>화면을 표시하지 못했습니다</h1>
      <p>잠시 후 다시 시도해 주세요.</p>
      <Link to="/">처음으로</Link>
    </main>
  );
}
