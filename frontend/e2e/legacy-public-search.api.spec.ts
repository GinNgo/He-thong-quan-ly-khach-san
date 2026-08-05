import { expect, test } from '@playwright/test';

const backendPort = process.env['PUBLIC_BOOKING_BACKEND_PORT'] || '28743';
const backendBaseUrl = `http://localhost:${backendPort}`;

interface SearchProperty {
  id: number;
  slug: string;
  name: string;
  [key: string]: unknown;
}

interface SearchPage {
  content: SearchProperty[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

test.beforeAll(async ({ request }) => {
  await expect.poll(async () => {
    try {
      const response = await request.get(
        `${backendBaseUrl}/api/public/properties/search?keyword=DEMO-DN-01&pageNumber=1&pageSize=1`,
      );
      const payload = await response.json() as SearchPage;
      return response.ok() && payload.content.some(property => property.slug === 'demo-dn-01');
    } catch {
      return false;
    }
  }, { timeout: 120_000, intervals: [500, 1000, 2000] }).toBe(true);
});

test('legacy search delegates parameters to the canonical paged DTO contract', async ({ request }) => {
  const checkIn = localDate(7);
  const checkOut = localDate(9);
  const legacyParams = new URLSearchParams({
    city: 'Bạch Đằng',
    checkIn,
    checkOut,
    guests: '2',
    pageNumber: '1',
    pageSize: '20',
  });
  const canonicalParams = new URLSearchParams({
    keyword: 'Bạch Đằng',
    checkInDate: checkIn,
    checkOutDate: checkOut,
    adultCount: '2',
    pageNumber: '1',
    pageSize: '20',
  });

  const [legacyResponse, canonicalResponse] = await Promise.all([
    request.get(`${backendBaseUrl}/api/v1/hotels/public/search?${legacyParams}`),
    request.get(`${backendBaseUrl}/api/public/properties/search?${canonicalParams}`),
  ]);

  expect(legacyResponse.status()).toBe(200);
  expect(canonicalResponse.status()).toBe(200);
  expect(legacyResponse.headers()['cache-control']).toContain('no-store');
  expect(legacyResponse.headers()['x-luxestay-freshness']).toBe('LIVE_SEARCH');

  const legacy = await legacyResponse.json() as SearchPage;
  const canonical = await canonicalResponse.json() as SearchPage;
  expect(Array.isArray(legacy)).toBe(false);
  expect(legacy).toMatchObject({
    number: 0,
    size: 20,
    totalElements: canonical.totalElements,
    totalPages: canonical.totalPages,
  });
  expect(legacy.content.map(property => property.id))
    .toEqual(canonical.content.map(property => property.id));
  expect(legacy.content.map(property => property.slug)).toContain('demo-dn-01');

  const approvedDemo = legacy.content.find(property => property.slug === 'demo-dn-01');
  expect(approvedDemo).toBeTruthy();
  for (const entityField of [
    'approvalStatus', 'operationStatus', 'owner', 'status', 'email', 'phone',
    'code', 'isDemo', 'dataSource', 'seedKey',
  ]) {
    expect(approvedDemo).not.toHaveProperty(entityField);
  }

  const broadenedCityResponse = await request.get(
    `${backendBaseUrl}/api/v1/hotels/public/search?city=DEMO-DN-01`,
  );
  expect(broadenedCityResponse.status()).toBe(200);
  expect((await broadenedCityResponse.json() as SearchPage).totalElements).toBe(0);

  for (const wildcard of ['%', '_']) {
    const wildcardResponse = await request.get(
      `${backendBaseUrl}/api/v1/hotels/public/search?city=${encodeURIComponent(wildcard)}`,
    );
    expect(wildcardResponse.status()).toBe(200);
    expect((await wildcardResponse.json() as SearchPage).totalElements).toBe(0);
  }

  const internalFilterResponse = await request.get(
    `${backendBaseUrl}/api/public/properties/search?legacyAddressKeyword=${encodeURIComponent('Bạch Đằng')}`,
  );
  expect(internalFilterResponse.status()).toBe(400);

  const firstPage = await request.get(
    `${backendBaseUrl}/api/v1/hotels/public/search?city=${encodeURIComponent('Đường')}&pageNumber=1&pageSize=1`,
  );
  const secondPage = await request.get(
    `${backendBaseUrl}/api/v1/hotels/public/search?city=${encodeURIComponent('Đường')}&pageNumber=2&pageSize=1`,
  );
  const firstPayload = await firstPage.json() as SearchPage;
  const secondPayload = await secondPage.json() as SearchPage;
  expect(firstPayload.totalElements).toBeGreaterThanOrEqual(2);
  expect(secondPayload.totalElements).toBe(firstPayload.totalElements);
  expect(secondPayload.number).toBe(1);
  expect(secondPayload.size).toBe(1);
  expect(secondPayload.content[0].id).not.toBe(firstPayload.content[0].id);
});

test('legacy search preserves canonical public eligibility', async ({ request }) => {
  const response = await request.get(
    `${backendBaseUrl}/api/v1/hotels/public/search?city=T274`,
  );

  expect(response.status()).toBe(200);
  const payload = await response.json() as SearchPage;
  expect(payload.content.map(property => property.slug)).not.toEqual(expect.arrayContaining([
    'e2e-t274-draft',
    'e2e-t274-suspended',
  ]));
  expect(payload.totalElements).toBe(0);
});

test('legacy search rejects obsolete district and invalid guest count truthfully', async ({ request }) => {
  const districtResponse = await request.get(
    `${backendBaseUrl}/api/v1/hotels/public/search?districtId=7`,
  );
  expect(districtResponse.status()).toBe(400);
  expect(await districtResponse.json()).toMatchObject({
    code: 'INVALID_REQUEST',
    message: 'districtId is no longer supported; use provinceId and wardId.',
  });

  for (const guests of ['0', '-1']) {
    const guestsResponse = await request.get(
      `${backendBaseUrl}/api/v1/hotels/public/search?guests=${guests}`,
    );
    expect(guestsResponse.status()).toBe(400);
    expect(await guestsResponse.json()).toMatchObject({
      code: 'INVALID_REQUEST',
      message: 'guests must be greater than zero.',
    });
  }
});

function localDate(offsetDays: number): string {
  const value = new Date();
  value.setHours(0, 0, 0, 0);
  value.setDate(value.getDate() + offsetDays);
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
