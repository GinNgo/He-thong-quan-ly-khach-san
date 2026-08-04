import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SubscriptionService } from '../../../core/services/subscription.service';
import { SubscriptionPlansComponent } from './subscription-plans';

describe('SubscriptionPlansComponent', () => {
  const api = {
    getPlans: vi.fn(),
    getPropertySubscription: vi.fn(),
    getPropertyUsage: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    api.getPlans.mockReturnValue(of([plan()]));
    api.getPropertySubscription.mockReturnValue(of(current()));
    api.getPropertyUsage.mockReturnValue(of(usage()));
    await TestBed.configureTestingModule({
      imports: [SubscriptionPlansComponent],
      providers: [
        provideRouter([]),
        { provide: SubscriptionService, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ propertyId: 17 }) } } },
      ]
    }).compileComponents();
  });

  it('renders catalog and authoritative state only for the selected property', () => {
    const fixture = TestBed.createComponent(SubscriptionPlansComponent);
    fixture.detectChanges();
    expect(api.getPropertySubscription).toHaveBeenCalledWith(17);
    expect(api.getPropertyUsage).toHaveBeenCalledWith(17);
    expect(fixture.componentInstance.isCurrentPlan(plan())).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Platform authoritative: Có');
    expect(fixture.nativeElement.textContent).toContain('12 / 50');
  });

  it('clears property data and shows a safe error when the scoped read fails', () => {
    api.getPropertySubscription.mockReturnValue(throwError(() => new Error('foreign hotel 99')));
    const fixture = TestBed.createComponent(SubscriptionPlansComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.current).toBeNull();
    expect(fixture.componentInstance.usage).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Kiểm tra quyền truy cập');
    expect(fixture.nativeElement.textContent).not.toContain('hotel 99');
  });
});

function plan() {
  return { id: 7, code: 'PRO', nameVi: 'Chuyên nghiệp', nameEn: 'Professional', billingType: 'YEARLY', price: 2400000, currency: 'VND', lifetime: false, status: 'ACTIVE', features: [] };
}

function current() {
  return { targetHotelId: 17, source: 'PLATFORM', platformAuthoritative: true, planId: 7, planCode: 'PRO', planName: 'Chuyên nghiệp', status: 'ACTIVE', effectiveFrom: '2026-01-01', effectiveUntil: '2027-01-01', lifetime: false, sourceReference: 'contract-1', migrationBlocker: null };
}

function usage() {
  return { targetHotelId: 17, source: 'PLATFORM', platformAuthoritative: true, planCode: 'PRO', subscriptionStatus: 'ACTIVE', effectiveFrom: '2026-01-01', effectiveUntil: '2027-01-01', lifetime: false, limits: { MAX_ROOMS: 50 }, usage: { MAX_ROOMS: 12 }, features: [{ code: 'MAX_ROOMS', nameVi: 'Phòng', nameEn: 'Rooms', limit: 50, usage: 12, allowed: true }], migrationBlocker: null };
}
