import { expect, test, type APIRequestContext } from '@playwright/test';

const backendPort = process.env['PUBLIC_BOOKING_BACKEND_PORT'] || '28743';
const frontendPort = process.env['PUBLIC_BOOKING_FRONTEND_PORT'] || '42769';
const backendBaseUrl = `http://localhost:${backendPort}`;
const stay = {
  checkInDate: '2030-06-10',
  checkOutDate: '2030-06-12',
  adultCount: '1',
  childCount: '0',
  roomCount: '1',
};

interface SearchProperty {
  id: number;
  slug: string;
  startingPrice: number | null;
  availableRoomCount: number;
  lowestRoomType?: { id: number };
}

interface SearchPage {
  content: SearchProperty[];
}

interface PublicRoomType {
  id: number;
  basePrice: number;
  availableRooms: number;
}

test.beforeAll(async ({ request }) => {
  await expect.poll(async () => {
    try {
      const response = await request.get(
        `${backendBaseUrl}/api/public/properties/search?keyword=DEMO-HN-01&pageNumber=1&pageSize=1`,
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

test('search and public detail expose one authoritative available-room projection', async ({ request }) => {
  const property = await searchDemoProperty(request);
  const roomTypes = await publicRoomTypes(request, property.id);
  const available = roomTypes.filter(roomType => roomType.availableRooms > 0);

  expect(available.length).toBeGreaterThan(0);
  expect(property.availableRoomCount).toBe(
    available.reduce((total, roomType) => total + roomType.availableRooms, 0),
  );

  const lowest = [...available].sort((left, right) =>
    left.basePrice - right.basePrice || left.id - right.id)[0];
  expect(property.lowestRoomType?.id).toBe(lowest.id);
  expect(property.startingPrice).toBe(lowest.basePrice);
});

test('search card count survives the search-to-detail journey without overstatement', async ({ page, request }) => {
  const property = await searchDemoProperty(request);
  const query = new URLSearchParams({ keyword: 'DEMO-HN-01', ...stay, pageNumber: '1', pageSize: '10' });
  await page.goto(`/search?${query}`);

  const card = page.locator('app-property-result-card');
  await expect(card).toHaveCount(1);
  await expect(card.locator('[data-availability-count]'))
    .toHaveAttribute('data-availability-value', String(property.availableRoomCount));

  await card.locator('.view-button').click();
  await expect(page).toHaveURL(new RegExp(`/hotel/${property.id}`));

  const detailCounts = page.locator('[data-room-availability-count]');
  await expect(detailCounts.first()).toBeVisible();
  const values = await detailCounts.evaluateAll(elements => elements.map(element =>
    Number(element.getAttribute('data-availability-value'))));
  expect(values.every(value => Number.isInteger(value) && value > 0)).toBe(true);
  expect(values.reduce((total, value) => total + value, 0)).toBe(property.availableRoomCount);
  await expect(page.locator('[data-availability-error]')).toHaveCount(0);
  await expect(page.locator('[data-availability-empty]')).toHaveCount(0);
});

async function searchDemoProperty(request: APIRequestContext): Promise<SearchProperty> {
  const params = new URLSearchParams({ keyword: 'DEMO-HN-01', ...stay, pageNumber: '1', pageSize: '10' });
  const response = await request.get(`${backendBaseUrl}/api/public/properties/search?${params}`);
  expect(response.status()).toBe(200);
  const payload = await response.json() as SearchPage;
  expect(payload.content).toHaveLength(1);
  expect(payload.content[0].slug).toBe('demo-hn-01');
  expect(payload.content[0].availableRoomCount).toBeGreaterThan(0);
  return payload.content[0];
}

async function publicRoomTypes(request: APIRequestContext, propertyId: number): Promise<PublicRoomType[]> {
  const params = new URLSearchParams({
    checkIn: stay.checkInDate,
    checkOut: stay.checkOutDate,
    guests: stay.adultCount,
  });
  const response = await request.get(
    `${backendBaseUrl}/api/room-types/public/hotel/${propertyId}?${params}`,
  );
  expect(response.status()).toBe(200);
  return response.json() as Promise<PublicRoomType[]>;
}
