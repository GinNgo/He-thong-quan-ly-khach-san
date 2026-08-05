import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const backendPort = process.env['PUBLIC_BOOKING_BACKEND_PORT'] || '28743';
const frontendPort = process.env['PUBLIC_BOOKING_FRONTEND_PORT'] || '42769';
const backendBaseUrl = `http://localhost:${backendPort}`;

interface SearchProperty {
  id: number;
  slug: string;
  startingPrice: number;
}

interface SearchPage {
  content: SearchProperty[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

const sortCases = ['POPULAR', 'NEAREST', 'PRICE_ASC', 'PRICE_DESC', 'RATING'] as const;

test.beforeAll(async ({ request }) => {
  await expect.poll(async () => {
    try {
      const response = await request.get(
        `${backendBaseUrl}/api/public/properties/search?keyword=DEMO&pageNumber=1&pageSize=1`,
      );
      return response.ok();
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

test('every public sort is repeatable and page-through count parity has no duplicates', async ({ request }) => {
  for (const sortBy of sortCases) {
    const values: Record<string, string> = {
      keyword: 'DEMO',
      minPrice: '500000',
      maxPrice: '800000',
      sortBy,
    };
    if (sortBy === 'NEAREST') {
      values['latitude'] = '16.05';
      values['longitude'] = '108.24';
    }

    const first = await search(request, values, 1, 100);
    const repeated = await search(request, values, 1, 100);
    const firstIds = first.content.map(property => property.id);
    expect(repeated.content.map(property => property.id), `${sortBy} repeated order`).toEqual(firstIds);
    expect(first.totalElements, `${sortBy} filtered total`).toBe(9);
    expect(first.totalPages).toBe(1);
    expect(new Set(firstIds).size).toBe(firstIds.length);

    const pagedIds: number[] = [];
    let authoritativeTotal = -1;
    let authoritativePages = -1;
    for (let pageNumber = 1; ; pageNumber += 1) {
      const current = await search(request, values, pageNumber, 3);
      if (pageNumber === 1) {
        authoritativeTotal = current.totalElements;
        authoritativePages = current.totalPages;
      }
      expect(current.totalElements).toBe(authoritativeTotal);
      expect(current.totalPages).toBe(authoritativePages);
      expect(current.number).toBe(pageNumber - 1);
      expect(current.size).toBe(3);
      pagedIds.push(...current.content.map(property => property.id));
      if (pageNumber === current.totalPages) break;
    }
    expect(pagedIds, `${sortBy} page-through order`).toEqual(firstIds);
    expect(pagedIds).toHaveLength(authoritativeTotal);
    expect(new Set(pagedIds).size).toBe(authoritativeTotal);

    if (sortBy === 'PRICE_ASC') {
      expect(first.content.map(property => property.startingPrice))
        .toEqual([500000, 500000, 500000, 500000, 550000, 550000, 575000, 575000, 650000]);
    }
    if (sortBy === 'PRICE_DESC') {
      expect(first.content.map(property => property.startingPrice))
        .toEqual([650000, 575000, 575000, 550000, 550000, 500000, 500000, 500000, 500000]);
    }
  }

  await expectInvalidRequest(request, { sortBy: 'RANDOM' });
  await expectInvalidRequest(request, { pageNumber: '0' });
  await expectInvalidRequest(request, { pageSize: '101' });

  const beyondLast = await search(request, {
    keyword: 'DEMO', minPrice: '500000', maxPrice: '800000', sortBy: 'PRICE_ASC',
  }, 999999, 3);
  expect(beyondLast.content).toEqual([]);
  expect(beyondLast.totalElements).toBe(9);
  expect(beyondLast.totalPages).toBe(3);
  expect(beyondLast.number).toBe(999998);
});

test('sort, page and filter actions reset or preserve canonical URL state', async ({ page }) => {
  const initial = new URLSearchParams({
    keyword: 'DEMO',
    displayLocation: 'Sort matrix sentinel',
    minPrice: '500000',
    maxPrice: '800000',
    sortBy: 'POPULAR',
    pageNumber: '3',
    pageSize: '3',
  });
  await page.goto(`/search?${initial}`);
  await expect(page.locator('[data-search-pagination]')).toHaveAttribute('data-page-number', '3');

  const sortResponse = page.waitForResponse(response => {
    if (!response.url().includes('/api/public/properties/search')) return false;
    const params = new URL(response.url()).searchParams;
    return params.get('sortBy') === 'PRICE_ASC' && params.get('pageNumber') === '1';
  });
  await page.locator('[data-search-sort]').getByRole('combobox').click();
  await page.getByRole('option', { name: 'Giá thấp nhất', exact: true }).click();
  expect((await sortResponse).status()).toBe(200);
  await expectCanonicalState(page, { sortBy: 'PRICE_ASC', pageNumber: '1' });

  const pageTwoResponse = page.waitForResponse(response => {
    if (!response.url().includes('/api/public/properties/search')) return false;
    const params = new URL(response.url()).searchParams;
    return params.get('sortBy') === 'PRICE_ASC' && params.get('pageNumber') === '2';
  });
  await page.locator('[data-search-pagination]').getByRole('button', { name: '2', exact: true }).click();
  expect((await pageTwoResponse).status()).toBe(200);
  await expectCanonicalState(page, { sortBy: 'PRICE_ASC', pageNumber: '2' });

  await page.locator('[data-filter-chip="price"]').click();
  await expect.poll(() => new URL(page.url()).searchParams.get('pageNumber')).toBe('1');
  const afterFilterRemoval = new URL(page.url()).searchParams;
  expect(afterFilterRemoval.has('minPrice')).toBe(false);
  expect(afterFilterRemoval.has('maxPrice')).toBe(false);
  expect(afterFilterRemoval.get('sortBy')).toBe('PRICE_ASC');
  expect(afterFilterRemoval.get('keyword')).toBe('DEMO');
  expect(afterFilterRemoval.get('displayLocation')).toBe('Sort matrix sentinel');
  expect(afterFilterRemoval.get('pageSize')).toBe('3');
});

test('a huge out-of-range page recovers to the authoritative last page', async ({ page }) => {
  const hugeResponse = page.waitForResponse(response => {
    if (!response.url().includes('/api/public/properties/search')) return false;
    return new URL(response.url()).searchParams.get('pageNumber') === '999999';
  });
  const recoveredResponse = page.waitForResponse(response => {
    if (!response.url().includes('/api/public/properties/search')) return false;
    return new URL(response.url()).searchParams.get('pageNumber') === '3';
  });

  await page.goto('/search?keyword=DEMO&displayLocation=Recovery%20sentinel'
    + '&minPrice=500000&maxPrice=800000&sortBy=PRICE_ASC&pageNumber=999999&pageSize=3');

  const hugePayload = await (await hugeResponse).json() as SearchPage;
  expect(hugePayload.content).toEqual([]);
  expect(hugePayload.totalElements).toBe(9);
  expect(hugePayload.totalPages).toBe(3);

  const recovered = await recoveredResponse;
  expect(recovered.status()).toBe(200);
  const recoveredPayload = await recovered.json() as SearchPage;
  expect(recoveredPayload.number).toBe(2);
  expect(recoveredPayload.content).toHaveLength(3);

  await expect.poll(() => new URL(page.url()).searchParams.get('pageNumber')).toBe('3');
  await expect(page.locator('[data-search-pagination]')).toHaveAttribute('data-page-number', '3');
  await expect(page.locator('app-property-result-card')).toHaveCount(3);
  await expect(page.locator('[data-search-empty]')).toHaveCount(0);
  const finalParams = new URL(page.url()).searchParams;
  expect(finalParams.get('keyword')).toBe('DEMO');
  expect(finalParams.get('displayLocation')).toBe('Recovery sentinel');
  expect(finalParams.get('minPrice')).toBe('500000');
  expect(finalParams.get('maxPrice')).toBe('800000');
  expect(finalParams.get('sortBy')).toBe('PRICE_ASC');
  expect(finalParams.get('pageSize')).toBe('3');
});

async function search(
  request: APIRequestContext,
  values: Record<string, string>,
  pageNumber: number,
  pageSize: number,
): Promise<SearchPage> {
  const params = new URLSearchParams({ ...values, pageNumber: String(pageNumber), pageSize: String(pageSize) });
  const response = await request.get(`${backendBaseUrl}/api/public/properties/search?${params}`);
  expect(response.status()).toBe(200);
  return response.json() as Promise<SearchPage>;
}

async function expectInvalidRequest(
  request: APIRequestContext,
  values: Record<string, string>,
): Promise<void> {
  const params = new URLSearchParams({ pageNumber: '1', pageSize: '20', ...values });
  const response = await request.get(`${backendBaseUrl}/api/public/properties/search?${params}`);
  expect(response.status()).toBe(400);
  expect(await response.json()).toMatchObject({ code: 'INVALID_REQUEST' });
}

async function expectCanonicalState(
  page: Page,
  expected: { sortBy: string; pageNumber: string },
): Promise<void> {
  await expect.poll(() => {
    const params = new URL(page.url()).searchParams;
    return {
      keyword: params.get('keyword'),
      displayLocation: params.get('displayLocation'),
      minPrice: params.get('minPrice'),
      maxPrice: params.get('maxPrice'),
      sortBy: params.get('sortBy'),
      pageNumber: params.get('pageNumber'),
      pageSize: params.get('pageSize'),
    };
  }).toEqual({
    keyword: 'DEMO',
    displayLocation: 'Sort matrix sentinel',
    minPrice: '500000',
    maxPrice: '800000',
    sortBy: expected.sortBy,
    pageNumber: expected.pageNumber,
    pageSize: '3',
  });
}
