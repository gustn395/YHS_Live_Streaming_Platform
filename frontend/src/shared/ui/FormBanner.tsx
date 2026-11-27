import styles from './AuthForm.module.css';
import type { Banner } from './formFeedback';

/** 폼 위의 안내 한 줄. bannerKey가 바뀌면 새 요소로 다시 알린다. */
export function FormBanner({ banner, bannerKey }: { banner: Banner | null; bannerKey: number }) {
  if (banner === null) return null;
  return (
    <div
      key={bannerKey}
      role={banner.role}
      className={`${styles.banner} ${banner.tone === 'error' ? styles.bannerError : styles.bannerWarning}`}
    >
      {banner.text}
    </div>
  );
}
