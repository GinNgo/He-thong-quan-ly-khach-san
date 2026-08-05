export const ACCOUNT_DISABLED_CODE = 'ACCOUNT_DISABLED';
export const ACCOUNT_DISABLED_MESSAGE =
  'Tài khoản đã bị tạm ngưng hoặc vô hiệu hóa. Vui lòng liên hệ bộ phận hỗ trợ. / This account is suspended or disabled.';
export const LOGIN_TEMPORARILY_BLOCKED_CODE = 'LOGIN_TEMPORARILY_BLOCKED';
export const LOGIN_TEMPORARILY_BLOCKED_MESSAGE =
  'Quá nhiều lần đăng nhập. Vui lòng thử lại sau. / Too many login attempts. Please try again later.';

interface AuthenticationError {
  status?: number;
  error?: {
    code?: string;
    retryAfterSeconds?: number;
  };
  headers?: {
    get(name: string): string | null;
  };
}

export function isAccountDisabledError(error: unknown): boolean {
  const candidate = error as AuthenticationError | null;
  return candidate?.error?.code === ACCOUNT_DISABLED_CODE;
}

export function isLoginTemporarilyBlockedError(error: unknown): boolean {
  const candidate = error as AuthenticationError | null;
  return candidate?.status === 429
    || candidate?.error?.code === LOGIN_TEMPORARILY_BLOCKED_CODE;
}

export function loginRetryAfterSeconds(error: unknown, now = Date.now()): number | null {
  const candidate = error as AuthenticationError | null;
  const bodySeconds = candidate?.error?.retryAfterSeconds;
  if (typeof bodySeconds === 'number' && Number.isFinite(bodySeconds) && bodySeconds >= 0) {
    return Math.ceil(bodySeconds);
  }

  const retryAfter = candidate?.headers?.get('Retry-After')?.trim();
  if (!retryAfter) return null;

  if (/^\d+$/.test(retryAfter)) return Number(retryAfter);

  const retryAt = Date.parse(retryAfter);
  if (Number.isNaN(retryAt)) return null;
  return Math.max(0, Math.ceil((retryAt - now) / 1000));
}

export function loginTemporarilyBlockedMessage(error: unknown): string {
  const retryAfterSeconds = loginRetryAfterSeconds(error);
  if (retryAfterSeconds === null) return LOGIN_TEMPORARILY_BLOCKED_MESSAGE;

  if (retryAfterSeconds < 60) {
    return `Quá nhiều lần đăng nhập. Vui lòng thử lại sau ${retryAfterSeconds} giây. / Too many login attempts. Please try again in ${retryAfterSeconds} seconds.`;
  }

  const retryAfterMinutes = Math.ceil(retryAfterSeconds / 60);
  return `Quá nhiều lần đăng nhập. Vui lòng thử lại sau ${retryAfterMinutes} phút. / Too many login attempts. Please try again in ${retryAfterMinutes} minutes.`;
}

export function authenticationErrorMessage(error: unknown, fallback: string): string {
  if (isAccountDisabledError(error)) return ACCOUNT_DISABLED_MESSAGE;
  if (isLoginTemporarilyBlockedError(error)) return loginTemporarilyBlockedMessage(error);
  return fallback;
}
