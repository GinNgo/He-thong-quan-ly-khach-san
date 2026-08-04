import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const backendPort = process.env['PUBLIC_BOOKING_BACKEND_PORT'] || '28743';
const frontendPort = process.env['PUBLIC_BOOKING_FRONTEND_PORT'] || '42769';
const backendBaseUrl = `http://localhost:${backendPort}`;

interface SearchProperty {
  id: number;
  slug: string;
  name: string;
  propertyType: string;
  starRating: number | null;
  reviewScore: number | null;
  reviewCount: number;
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
        `${backendBaseUrl}/api/public/properties/search?keyword=DEMO-DN-03&pageNumber=1&pageSize=1`,
      );
      const payload = await response.json() as SearchPage;
      return response.ok() && payload.content.some(property => property.slug === 'demo-dn-03');
    } catch {
      return false;
    }
  }, { timeout: 120_000, intervals: [500, 1000, 2000] }).toBe(true);
});

test.beforeEach(async ({ page }) => {
  await page.route('http://localhost:8080/api/**', async route => {
    const response = await route.fetch({
      url: route.request().url().replace('http://localhost:8080', backendBaseUrl),
      headers: { ...route.request().headers(), origin: 'http://localhost:4200' },
    });
    await route.fulfill({
      response,
      headers: { ...response.headers(), 'access-control-allow-origin': `http://localhost:${frontendPort}` },
    });
  });
});

test('property filters return exact eligible results and preserve unrated zero/null rules', async ({ request }) => {
  const exact = await search(request, {
    propertyTypes: 'resort,RESORT',
    starRatings: '5,5',
    minReviewScore: '8',
  });
  expect(exact.content.map(property => property.slug)).toEqual(['demo-dn-03']);
  expect(exact.content[0]).toMatchObject({
    propertyType: 'RESORT',
    starRating: 5,
  });
  expect(exact.content[0].reviewScore).toBeGreaterThanOrEqual(8);
  expect(exact.content[0].reviewCount).toBeGreaterThan(0);

  const unratedWithoutThreshold = await search(request, {
    keyword: 'DEMO-HN-01',
    starRatings: '5',
  });
  expect(unratedWithoutThreshold.content.map(property => property.slug)).toEqual(['demo-hn-01']);
  expect(unratedWithoutThreshold.content[0]).toMatchObject({
    reviewScore: null,
    reviewCount: 0,
  });

  const unratedWithZeroThreshold = await search(request, {
    keyword: 'DEMO-HN-01',
    starRatings: '5',
    minReviewScore: '0',
  });
  expect(unratedWithZeroThreshold.totalElements).toBe(0);

  const reviewedWithZeroThreshold = await search(request, {
    keyword: 'DEMO-DN-03',
    minReviewScore: '0',
  });
  expect(reviewedWithZeroThreshold.content.map(property => property.slug)).toEqual(['demo-dn-03']);
  expect(reviewedWithZeroThreshold.content[0].reviewScore).not.toBeNull();
  expect(reviewedWithZeroThreshold.content[0].reviewCount).toBeGreaterThan(0);
});

test('desktop filters canonicalize URL state, reset pagination and keep chips/count aligned', async ({ page }) => {
  await page.goto('/search?pageNumber=3&pageSize=1');

  await page.locator('#type-RESORT').check();
  await page.locator('#star-5').check();
  await page.locator('.sidebar input[name="review-score"]').nth(1).check();

  const responsePromise = page.waitForResponse(response => {
    if (!response.url().includes('/api/public/properties/search')) return false;
    const params = new URL(response.url()).searchParams;
    return params.get('propertyTypes') === 'RESORT'
      && params.get('starRatings') === '5'
      && params.get('minReviewScore') === '8';
  });
  await page.locator('.sidebar .apply-button').click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);

  await expect.poll(() => canonicalFilterState(page)).toEqual({
    propertyTypes: 'RESORT',
    starRatings: '5',
    minReviewScore: '8',
    pageNumber: '1',
    pageSize: '1',
  });

  const payload = await response.json() as SearchPage;
  expect(payload.content.map(property => property.slug)).toEqual(['demo-dn-03']);
  await expect(page.locator('app-property-result-card')).toHaveCount(1);
  await expect(page.getByRole('button', { name: payload.content[0].name, exact: true })).toBeVisible();
  await expect(page.locator('.chips button:not(.clear-chip)')).toHaveCount(3);
  await expect(page.locator('.mobile-filter b')).toHaveText('3');

  await page.goto(`${page.url().replace('pageNumber=1', 'pageNumber=2')}`);
  const typeChip = page.locator('[data-filter-chip="propertyType:RESORT"]');
  await expect(typeChip).toBeVisible();
  await typeChip.click();
  await expect.poll(() => new URL(page.url()).searchParams.get('pageNumber')).toBe('1');
  expect(new URL(page.url()).searchParams.has('propertyTypes')).toBe(false);
  await expect(page.locator('.chips button:not(.clear-chip)')).toHaveCount(2);
  await expect(page.locator('.mobile-filter b')).toHaveText('2');
});

test('mobile filter drawer traps keyboard focus, closes with Escape and restores its trigger', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/search?pageNumber=2&pageSize=10');

  const trigger = page.locator('[data-mobile-filter-trigger]');
  await trigger.focus();
  await trigger.click();

  const drawer = page.locator('#mobile-property-filters');
  const close = drawer.locator('[data-mobile-filter-close]');
  const apply = drawer.locator('.apply-button');
  await expect(drawer).toBeVisible();
  await expect(close).toBeFocused();

  await page.keyboard.press('Shift+Tab');
  await expect(apply).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(close).toBeFocused();

  await page.keyboard.press('Escape');
  await expect(drawer).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

async function search(
  request: APIRequestContext,
  values: Record<string, string>,
): Promise<SearchPage> {
  const params = new URLSearchParams({ pageNumber: '1', pageSize: '50', ...values });
  const response = await request.get(`${backendBaseUrl}/api/public/properties/search?${params}`);
  expect(response.status()).toBe(200);
  return response.json() as Promise<SearchPage>;
}

function canonicalFilterState(page: Page): Record<string, string | null> {
  const params = new URL(page.url()).searchParams;
  return {
    propertyTypes: params.get('propertyTypes'),
    starRatings: params.get('starRatings'),
    minReviewScore: params.get('minReviewScore'),
    pageNumber: params.get('pageNumber'),
    pageSize: params.get('pageSize'),
  };
}
