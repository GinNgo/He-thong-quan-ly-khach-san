import { isBookingReturnUrl, safeClientReturnUrl } from './client-return-url';

describe('client return URL contract', () => {
  it('preserves a local checkout URL with encoded query state and fragment', () => {
    const url = '/booking/49?checkIn=2026-08-10&checkOut=2026-08-12&roomTypeName=Deluxe%20Suite&quantity=2#summary';

    expect(safeClientReturnUrl(url)).toBe(url);
    expect(isBookingReturnUrl(url)).toBe(true);
  });

  it.each([
    'https://evil.example/booking/49',
    '//evil.example/booking/49',
    '/\\evil.example/booking/49',
    '/booking/49%0Ajavascript:alert(1)',
    '/booking/%E0%A4%A',
  ])('rejects an unsafe return URL: %s', value => {
    expect(safeClientReturnUrl(value, '/search')).toBe('/search');
  });

  it('falls back for missing values and does not classify non-booking client routes', () => {
    expect(safeClientReturnUrl(undefined)).toBe('/');
    expect(isBookingReturnUrl('/profile')).toBe(false);
  });
});
