import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SubscriptionService } from '../../../core/services/subscription.service';
import { PlatformBillingService } from '../../../core/services/platform-billing.service';
import { PermissionService } from '../../../core/services/permission.service';
import { SubscriptionPlansComponent } from './subscription-plans';

describe('SubscriptionPlansComponent', () => {
  const api = {
    getPlans: vi.fn(),
    getPropertySubscription: vi.fn(),
    getPropertyUsage: vi.fn(),
  };
  const platform = { revokeSubscription: vi.fn() };
  const permissions = { hasPermission: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    api.getPlans.mockReturnValue(of([plan()]));
    api.getPropertySubscription.mockReturnValue(of(current()));
    api.getPropertyUsage.mockReturnValue(of(usage()));
    platform.revokeSubscription.mockReturnValue(of({ targetHotelId: 17, contractPublicId: 'contract-1', contractStatus: 'REVOKED', entitlementStatus: 'REVOKED', transitioned: true, occurredAt: '2026-08-04T00:00:00' }));
    permissions.hasPermission.mockReturnValue(true);
    await TestBed.configureTestingModule({
      imports: [SubscriptionPlansComponent],
      providers: [
        provideRouter([]),
        { provide: SubscriptionService, useValue: api },
        { provide: PlatformBillingService, useValue: platform },
        { provide: PermissionService, useValue: permissions },
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

  it('shows revoke only with PLATFORM_BILLING UPDATE and requires confirmation plus reason', () => {
    const fixture = TestBed.createComponent(SubscriptionPlansComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(component.canRevokeSelectedProperty).toBe(true);
    component.revokeReason = 'Authorized administrative subscription revoke.';
    component.revokeSubscription();
    expect(platform.revokeSubscription).not.toHaveBeenCalled();
    component.revokeConfirmed = true;
    component.revokeSubscription();
    expect(platform.revokeSubscription).toHaveBeenCalledWith(17, 'Authorized administrative subscription revoke.');
    expect(component.revokeMessage).toContain('được thu hồi');
  });

  it('keeps VIEW-only admin read access but hides revoke', () => {
    permissions.hasPermission.mockReturnValue(false);
    const fixture = TestBed.createComponent(SubscriptionPlansComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.current?.planCode).toBe('PRO');
    expect(fixture.componentInstance.canRevokeSelectedProperty).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('Thu hồi subscription');
  });

  it('reports clock-driven expiry truthfully when revoke resolves an elapsed term', () => {
    platform.revokeSubscription.mockReturnValue(of({ targetHotelId: 17, contractPublicId: 'contract-1', contractStatus: 'EXPIRED', entitlementStatus: 'EXPIRED', transitioned: true, occurredAt: '2026-08-04T00:00:00' }));
    const fixture = TestBed.createComponent(SubscriptionPlansComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.revokeReason = 'Administrative lifecycle reconciliation.';
    component.revokeConfirmed = true;
    component.revokeSubscription();
    expect(component.revokeMessage).toContain('đã hết hạn');
    expect(component.revokeMessage).not.toContain('được thu hồi');
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
