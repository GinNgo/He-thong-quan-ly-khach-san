const CONTROL_CHARACTER = /[\u0000-\u001f\u007f]/;

export function safeClientReturnUrl(value: unknown, fallback = '/'): string {
  if (typeof value !== 'string') return fallback;
  const candidate = value.trim();
  if (!candidate.startsWith('/') || candidate.startsWith('//') || candidate.includes('\\')) return fallback;
  if (CONTROL_CHARACTER.test(candidate) || containsUnsafeEncoding(candidate)) return fallback;

  try {
    const parsed = new URL(candidate, 'https://luxestay.local');
    if (parsed.origin !== 'https://luxestay.local') return fallback;
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return fallback;
  }
}

export function isBookingReturnUrl(value: string): boolean {
  return /^\/booking\/[^/?#]+(?:[?#]|$)/.test(value);
}

function containsUnsafeEncoding(value: string): boolean {
  try {
    const decoded = decodeURIComponent(value);
    return decoded.includes('\\') || CONTROL_CHARACTER.test(decoded);
  } catch {
    return true;
  }
}
