import { Link } from 'react-router';

// FE-01 가드 검증용 최소 화면이다(Issue #11 범위). 실제 화면은 이후 FE 작업에서 만든다. 로그인 화면은 FE-04의 LoginPage다.

export function PublicHome() {
  return (
    <main>
      <h1>YHS Live Streaming</h1>
      <Link to="/app">내 화면</Link> · <Link to="/signup">회원가입</Link>
    </main>
  );
}

export function ProtectedHome() {
  return (
    <main>
      <h1>내 화면</h1>
      <p>로그인한 사용자 화면은 준비 중입니다.</p>
    </main>
  );
}

export function Forbidden() {
  return (
    <main>
      <h1>접근 권한이 없습니다</h1>
      <Link to="/">처음으로</Link>
    </main>
  );
}

export function NotFound() {
  return (
    <main>
      <h1>페이지를 찾을 수 없습니다</h1>
      <Link to="/">처음으로</Link>
    </main>
  );
}
