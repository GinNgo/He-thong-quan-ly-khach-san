import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AnalyticsData, AnalyticsService } from '../../../core/services/analytics';
import { Dashboard } from './dashboard';

describe('AdminDashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let getDashboardData: ReturnType<typeof vi.fn>;

  const data: AnalyticsData = {
    totalRevenue: 125000000,
    totalBookings: 6,
    occupancyRate: 50,
    totalRooms: 10,
    occupiedRooms: 5,
    operationalProperties: 3,
    scope: 'SYSTEM_NON_DEMO',
    revenueBasis: 'PLATFORM_BILLING_NET',
    occupancyBasis: 'ASSIGNED_AND_LEGACY_STAYS_OVER_OPERATIONAL_ROOMS',
    reconciliationStatus: 'RECONCILED',
    sourceWatermark: 'platform-watermark-12',
    generatedAt: '2026-08-04T06:00:00Z',
    periodFrom: '2026-07-29',
    periodTo: '2026-08-04',
    labels: ['29/07', '30/07', '31/07', '01/08', '02/08', '03/08', '04/08'],
    revenueData: [10000000, 15000000, 20000000, 10000000, 25000000, 20000000, 25000000],
    occupancyData: [20, 30, 40, 50, 50, 40, 50],
  };

  beforeEach(async () => {
    getDashboardData = vi.fn(() => of(data));
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        { provide: AnalyticsService, useValue: { getDashboardData } },
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
      ]
    }).compileComponents();
  });

  it('renders reconciled revenue, booking and occupancy values with truthful basis metadata', async () => {
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;

    expect(text).toContain('125.000.000');
    expect(text).toContain('Dat phong 7 ngay');
    expect(text).toContain('5 / 10');
    expect(text).toContain('50.0%');
    expect(text).toContain('PLATFORM_BILLING_NET');
    expect(text).toContain('RECONCILED');
    expect(text).toContain('platform-watermark-12');
    expect(text).not.toContain('Thiet lap co so');
    expect(text).not.toContain('Gui yeu cau');
    expect(fixture.componentInstance.occupancyChartData.datasets).toHaveLength(1);
  });

  it('shows an accessible retry state instead of leaving zero cards after a load failure', async () => {
    getDashboardData.mockReturnValueOnce(throwError(() => new Error('offline')));
    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Thu lai');
    expect(fixture.nativeElement.querySelectorAll('app-stat-card')).toHaveLength(0);
  });
});
