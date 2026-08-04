import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SubscriptionService } from '../../../core/services/subscription.service';
import { PlatformBillingService, PlatformPlanVersion } from '../../../core/services/platform-billing.service';
import { PermissionService } from '../../../core/services/permission.service';
import { SubscriptionPlansComponent } from './subscription-plans';

describe('SubscriptionPlansComponent', () => {
  const api = {
    getPlans: vi.fn(),
    getPropertySubscription: vi.fn(),
    getPropertyUsage: vi.fn(),
  };
  const platform = { revokeSubscription: vi.fn(), getPlanVersions: vi.fn(), createPlanVersion: vi.fn(), activatePlanVersion: vi.fn(), deactivatePlanVersion: vi.fn() };
  const permissions = { hasPermission: vi.fn(), isSuperAdmin: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    api.getPlans.mockReturnValue(of([plan()]));
    api.getPropertySubscription.mockReturnValue(of(current()));
    api.getPropertyUsage.mockReturnValue(of(usage()));
    platform.revokeSubscription.mockReturnValue(of({ targetHotelId: 17, contractPublicId: 'contract-1', contractStatus: 'REVOKED', entitlementStatus: 'REVOKED', transitioned: true, occurredAt: '2026-08-04T00:00:00' }));
    permissions.hasPermission.mockReturnValue(true);
    permissions.isSuperAdmin.mockReturnValue(true);
    platform.getPlanVersions.mockReturnValue(of([planVersion()]));
    platform.createPlanVersion.mockReturnValue(of({ ...planVersion(), id: 10, versionNumber: 2, versionCode: 'PRO-V2', status: 'INACTIVE' }));
    platform.activatePlanVersion.mockReturnValue(of({ ...planVersion(), status: 'ACTIVE' }));
    platform.deactivatePlanVersion.mockReturnValue(of({ ...planVersion(), status: 'INACTIVE' }));
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
    expect(fixture.nativeElement.textContent).not.toContain('Governed plan versions');
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

  it('creates a validated immutable version and never sends contract identifiers', () => {
    const fixture = TestBed.createComponent(SubscriptionPlansComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.versionForm = { familyCode: ' pro ', nameVi: 'Gói Pro', nameEn: 'Pro plan', billingType: 'YEARLY', price: 2400000, lifetime: false, durationValue: 1, durationUnit: 'YEAR', featuresText: 'MAX_ROOMS=50\nAI_CHAT=-1' };
    component.createPlanVersion();
    expect(platform.createPlanVersion).toHaveBeenCalledWith(expect.objectContaining({
      familyCode: 'PRO', price: 2400000, features: [{ code: 'MAX_ROOMS', limit: 50 }, { code: 'AI_CHAT', limit: -1 }]
    }), expect.stringContaining('plan-version-'));
    const sent = platform.createPlanVersion.mock.calls[0][0];
    expect(sent.contractId).toBeUndefined();
    expect(sent.lifetime).toBeUndefined();
    expect(component.versionMessage).toContain('INACTIVE');
  });

  it('blocks invalid price, duration and feature limits before the API', () => {
    const fixture = TestBed.createComponent(SubscriptionPlansComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.versionForm = { familyCode: 'P', nameVi: '', nameEn: 'Pro', billingType: 'MONTHLY', price: -1, lifetime: false, durationValue: 121, durationUnit: 'MONTH', featuresText: 'MAX_ROOMS=-2' };
    component.createPlanVersion();
    expect(platform.createPlanVersion).not.toHaveBeenCalled();
    expect(component.versionsError).toContain('Kiểm tra mã');
  });

  it('requires explicit confirmation before activate or deactivate', () => {
    const fixture = TestBed.createComponent(SubscriptionPlansComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.confirmVersionAction();
    expect(platform.activatePlanVersion).not.toHaveBeenCalled();
    component.requestVersionAction(planVersion(), 'activate');
    expect(platform.activatePlanVersion).not.toHaveBeenCalled();
    component.confirmVersionAction();
    expect(platform.activatePlanVersion).toHaveBeenCalledWith(9, expect.stringContaining('plan-activate-'));

    const active = { ...planVersion(), status: 'ACTIVE', activatedAt: '2026-08-04T00:00:00' };
    component.requestVersionAction(active, 'deactivate');
    expect(platform.deactivatePlanVersion).not.toHaveBeenCalled();
    component.confirmVersionAction();
    expect(platform.deactivatePlanVersion).not.toHaveBeenCalled();
    expect(component.versionsError).toContain('10 đến 1000');
    component.versionActionReason = 'Replaced by a governed newer version.';
    component.confirmVersionAction();
    expect(platform.deactivatePlanVersion).toHaveBeenCalledWith(9, 'Replaced by a governed newer version.', expect.stringContaining('plan-deactivate-'));
  });

  it('sends lifetime as ONCE/LIFETIME with null duration and never reactivates a deactivated version', () => {
    const fixture = TestBed.createComponent(SubscriptionPlansComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.versionForm = { familyCode: 'LIFETIME', nameVi: 'Trọn đời', nameEn: 'Lifetime', billingType: 'ONCE', price: 9000000, lifetime: true, durationValue: 1, durationUnit: 'LIFETIME', featuresText: 'MAX_ROOMS=-1' };
    component.createPlanVersion();
    expect(platform.createPlanVersion).toHaveBeenCalledWith(expect.objectContaining({ billingType: 'ONCE', durationUnit: 'LIFETIME', durationValue: null }), expect.any(String));

    const deactivated = { ...planVersion(), status: 'INACTIVE', deactivatedAt: '2026-08-05T00:00:00' };
    component.requestVersionAction(deactivated, 'activate');
    component.confirmVersionAction();
    expect(platform.activatePlanVersion).not.toHaveBeenCalled();
  });

  it('shows safe activation conflicts without backend persistence detail', () => {
    platform.activatePlanVersion.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'INVALID_STATE_TRANSITION', message: 'row version 77 internal' } })));
    const fixture = TestBed.createComponent(SubscriptionPlansComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.requestVersionAction(planVersion(), 'activate');
    component.confirmVersionAction();
    expect(component.versionsError).toContain('xung đột');
    expect(component.versionsError).not.toContain('row version 77');
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

function planVersion(): PlatformPlanVersion {
  return { id: 9, familyCode: 'PRO', versionNumber: 1, versionCode: 'PRO-V1', nameVi: 'Gói Pro', nameEn: 'Pro plan', billingType: 'YEARLY', price: 2400000, currency: 'VND', lifetime: false, durationValue: 1, durationUnit: 'YEAR', status: 'INACTIVE', recordVersion: 0, features: [{ code: 'MAX_ROOMS', limit: 50 }], createdAt: '2026-08-04T00:00:00', activatedAt: null, deactivatedAt: null };
}
