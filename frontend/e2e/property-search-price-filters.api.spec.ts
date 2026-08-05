import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const backendPort = process.env['PUBLIC_BOOKING_BACKEND_PORT'] || '28743';
const frontendPort = process.env['PUBLIC_BOOKING_FRONTEND_PORT'] || '42769';
const backendBaseUrl = `http://localhost:${backendPort}`;

interface PricingSummary {
  nightlyPrice: number;
  discountedNightlyPrice?: number;
}

interface SearchProperty {
  id: number;
  slug: string;
  name: string;
  startingPrice: number;
  pricing: PricingSummary;
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
        `${backendBaseUrl}/api/public/properties/search?keyword=DEMO-HN-01&pageNumber=1&pageSize=1`,
      );
      const payload = await response.json() as SearchPage;
      return response.ok() && payload.content.some(property => property.slug === 'demo-hn-01');
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

test('price bounds use one eligible room and expose the exact inclusive displayed price', async ({ request }) => {
  const splitBounds = await search(request, {
    keyword: 'DEMO-HN-01',
    minPrice: '600000',
    maxPrice: '700000',
  });
  expect(splitBounds.totalElements).toBe(0);
  expect(splitBounds.content).toEqual([]);

  const inclusive = await search(request, {
    keyword: 'DEMO-HN-01',
    minPrice: '500000',
    maxPrice: '500000',
  });
  expect(inclusive.content.map(property => property.slug)).toEqual(['demo-hn-01']);
  expect(inclusive.content[0].startingPrice).toBe(500000);
  expect(inclusive.content[0].pricing.nightlyPrice).toBe(500000);
  expect(inclusive.content[0].pricing.discountedNightlyPrice).toBe(500000);
});

test('PRICE_ASC and PRICE_DESC sort the same bounded displayed-price projection', async ({ request }) => {
  const bounds = { keyword: 'DEMO', minPrice: '500000', maxPrice: '800000' };
  const ascending = await search(request, { ...bounds, sortBy: 'PRICE_ASC' });
  const descending = await search(request, { ...bounds, sortBy: 'PRICE_DESC' });

  const ascPrices = displayedPrices(ascending);
  const descPrices = displayedPrices(descending);
  expect(ascPrices).toEqual([500000, 500000, 500000, 500000, 550000, 550000, 575000, 575000, 650000]);
  expect(descPrices).toEqual([...ascPrices].reverse());
  expect(ascending.content.map(property => property.slug).sort())
    .toEqual(descending.content.map(property => property.slug).sort());

  for (const page of [ascending, descending]) {
    for (const property of page.content) {
      expect(property.startingPrice).toBeGreaterThanOrEqual(500000);
      expect(property.startingPrice).toBeLessThanOrEqual(800000);
      expect(property.pricing.nightlyPrice).toBe(property.startingPrice);
    }
  }
});

test('price chip applies an inclusive range, resets page and preserves canonical query state', async ({ page }) => {
  const initial = new URLSearchParams({
    keyword: 'DEMO-HN-01',
    displayLocation: 'Price filter sentinel',
    propertyTypes: 'HOTEL',
    starRatings: '5',
    sortBy: 'PRICE_DESC',
    minPrice: '500000',
    maxPrice: '500000',
    pageNumber: '3',
    pageSize: '1',
  });
  await page.goto(`/search?${initial}`);

  const priceChip = page.locator('[data-filter-chip="price"]');
  await expect(priceChip).toBeVisible();

  const responsePromise = page.waitForResponse(response => {
    if (!response.url().includes('/api/public/properties/search')) return false;
    const params = new URL(response.url()).searchParams;
    return params.get('minPrice') === '500000'
      && params.get('maxPrice') === '500000'
      && params.get('pageNumber') === '1';
  });
  await page.locator('.sidebar .apply-button').click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);

  await expect.poll(() => canonicalPriceState(page)).toEqual({
    keyword: 'DEMO-HN-01',
    displayLocation: 'Price filter sentinel',
    propertyTypes: 'HOTEL',
    starRatings: '5',
    sortBy: 'PRICE_DESC',
    minPrice: '500000',
    maxPrice: '500000',
    pageNumber: '1',
    pageSize: '1',
  });

  const payload = await response.json() as SearchPage;
  expect(payload.content.map(property => property.slug)).toEqual(['demo-hn-01']);
  expect(payload.content[0].startingPrice).toBe(500000);
  await expect(page.locator('app-property-result-card')).toHaveCount(1);
  const displayedPrice = page.locator('app-property-result-card [data-nightly-price]');
  await expect(displayedPrice).toHaveAttribute('data-price-value', '500000');
  await expect(displayedPrice).toContainText('500.000');

  const pageTwo = new URL(page.url());
  pageTwo.searchParams.set('pageNumber', '2');
  await page.goto(pageTwo.toString());
  await page.locator('[data-filter-chip="price"]').click();
  await expect.poll(() => new URL(page.url()).searchParams.get('pageNumber')).toBe('1');

  const cleared = new URL(page.url()).searchParams;
  expect(cleared.has('minPrice')).toBe(false);
  expect(cleared.has('maxPrice')).toBe(false);
  expect(cleared.get('keyword')).toBe('DEMO-HN-01');
  expect(cleared.get('displayLocation')).toBe('Price filter sentinel');
  expect(cleared.get('propertyTypes')).toBe('HOTEL');
  expect(cleared.get('starRatings')).toBe('5');
  expect(cleared.get('sortBy')).toBe('PRICE_DESC');
  expect(cleared.get('pageSize')).toBe('1');
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

function displayedPrices(page: SearchPage): number[] {
  return page.content.map(property => property.startingPrice);
}

function canonicalPriceState(page: Page): Record<string, string | null> {
  const params = new URL(page.url()).searchParams;
  return {
    keyword: params.get('keyword'),
    displayLocation: params.get('displayLocation'),
    propertyTypes: params.get('propertyTypes'),
    starRatings: params.get('starRatings'),
    sortBy: params.get('sortBy'),
    minPrice: params.get('minPrice'),
    maxPrice: params.get('maxPrice'),
    pageNumber: params.get('pageNumber'),
    pageSize: params.get('pageSize'),
  };
}
