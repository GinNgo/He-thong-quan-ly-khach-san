import { describe, expect, it } from 'vitest';

import {
  LOGIN_TEMPORARILY_BLOCKED_MESSAGE,
  authenticationErrorMessage,
  isLoginTemporarilyBlockedError,
  loginRetryAfterSeconds,
} from './account-status-error';

describe('authentication error presentation', () => {
  it('recognizes the stable temporary-block code without relying on account details', () => {
    const error = {
      status: 400,
      error: {
        code: 'LOGIN_TEMPORARILY_BLOCKED',
        message: 'Known account customer@example.com is locked.',
      },
    };

    expect(isLoginTemporarilyBlockedError(error)).toBe(true);
    expect(authenticationErrorMessage(error, 'fallback')).toBe(LOGIN_TEMPORARILY_BLOCKED_MESSAGE);
    expect(authenticationErrorMessage(error, 'fallback')).not.toContain('customer@example.com');
  });

  it('recognizes HTTP 429 and presents a numeric Retry-After value', () => {
    const error = {
      status: 429,
      error: { message: 'Internal throttle detail' },
      headers: { get: (name: string) => name === 'Retry-After' ? '75' : null },
    };

    const message = authenticationErrorMessage(error, 'fallback');

    expect(loginRetryAfterSeconds(error)).toBe(75);
    expect(message).toContain('2 phút');
    expect(message).toContain('2 minutes');
    expect(message).not.toContain('Internal throttle detail');
  });

  it('parses an HTTP-date Retry-After value deterministically', () => {
    const now = Date.parse('2026-08-04T10:00:00Z');
    const error = {
      status: 429,
      headers: { get: () => 'Tue, 04 Aug 2026 10:00:45 GMT' },
    };

    expect(loginRetryAfterSeconds(error, now)).toBe(45);
  });

  it('keeps the existing generic fallback for ordinary credential failures', () => {
    const fallback = 'Sai tài khoản hoặc mật khẩu.';

    expect(authenticationErrorMessage({ status: 401, error: { code: 'INVALID_CREDENTIALS' } }, fallback))
      .toBe(fallback);
  });
});
