import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { PlatformBillingService } from './platform-billing.service';

describe('PlatformBillingService', () => {
  let service: PlatformBillingService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PlatformBillingService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the backend-owned platform catalog', () => {
    service.getCatalog().subscribe();
    const request = http.expectOne(`${environment.apiUrl}/platform/subscription-plans`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates a purchase order with only property, plan and idempotency identity', () => {
    service.createPurchaseOrder(42, 7, 'purchase-key').subscribe();
    const request = http.expectOne(`${environment.apiUrl}/platform/subscription-orders`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ targetHotelId: 42, planId: 7 });
    expect(request.request.body.price).toBeUndefined();
    expect(request.request.headers.get('Idempotency-Key')).toBe('purchase-key');
    request.flush({ publicId: 'order-1' });
  });

  it('creates a payment attempt without client amount or merchant fields', () => {
    service.createPaymentAttempt('order/1', { provider: 'SIMULATOR', method: 'SIMULATOR' }, 'attempt-key').subscribe();
    const request = http.expectOne(
      `${environment.apiUrl}/platform/subscription-orders/order%2F1/payment-attempts`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ provider: 'SIMULATOR', method: 'SIMULATOR' });
    expect(request.request.body.amount).toBeUndefined();
    expect(request.request.body.merchant).toBeUndefined();
    expect(request.request.headers.get('Idempotency-Key')).toBe('attempt-key');
    request.flush({ publicId: 'attempt-1' });
  });

  it('loads history and sends cancellation correlation metadata', () => {
    service.getHistory(42).subscribe();
    const history = http.expectOne(`${environment.apiUrl}/platform/subscriptions/42/history`);
    expect(history.request.method).toBe('GET');
    history.flush([]);

    service.cancelOrder('order-1', { correlationId: 'cancel-correlation' }).subscribe();
    const cancel = http.expectOne(`${environment.apiUrl}/platform/subscription-orders/order-1/cancel`);
    expect(cancel.request.method).toBe('POST');
    expect(cancel.request.body).toBeNull();
    expect(cancel.request.headers.get('X-Correlation-ID')).toBe('cancel-correlation');
    cancel.flush({ publicId: 'order-1', attempts: [] });
  });

  it('revokes with a bounded trimmed reason and exports retained history', () => {
    service.revokeSubscription(42, '  Contract ended by authorized administrator.  ').subscribe();
    const revoke = http.expectOne(`${environment.apiUrl}/platform/subscriptions/42/revoke`);
    expect(revoke.request.method).toBe('POST');
    expect(revoke.request.body).toEqual({ reason: 'Contract ended by authorized administrator.' });
    revoke.flush({ targetHotelId: 42, contractPublicId: 'contract-1', contractStatus: 'REVOKED', entitlementStatus: 'REVOKED', transitioned: true, occurredAt: '2026-08-04T00:00:00' });

    service.exportHistory(42).subscribe();
    const historyExport = http.expectOne(`${environment.apiUrl}/platform/subscriptions/42/history/export`);
    expect(historyExport.request.method).toBe('GET');
    expect(historyExport.request.responseType).toBe('blob');
    historyExport.flush(new Blob(['action,occurredAt']));

    expect(() => service.revokeSubscription(42, 'short')).toThrowError(/between 10 and 1000/);
  });

  it('reads masked readiness without accepting a secret from the client', () => {
    service.validatePaymentConfiguration('MOMO').subscribe();
    const request = http.expectOne(
      `${environment.apiUrl}/platform/payment-configuration/validate?provider=MOMO`,
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();
    request.flush({ ready: false, mode: 'SANDBOX', provider: 'MOMO', blockers: ['missing'] });
  });

  it('uses idempotent governed plan-version commands without client contract mutation', () => {
    service.getPlanVersions().subscribe();
    http.expectOne(`${environment.apiUrl}/platform/subscription-plan-admin`).flush([]);

    const request = { familyCode: 'PRO', nameVi: 'Pro', nameEn: 'Pro', billingType: 'YEARLY' as const, price: 2400000, durationValue: 1, durationUnit: 'YEAR' as const, features: [{ code: 'MAX_ROOMS', limit: 50 }] };
    service.createPlanVersion(request, 'create-version-key').subscribe();
    const create = http.expectOne(`${environment.apiUrl}/platform/subscription-plan-admin`);
    expect(create.request.body).toEqual(request);
    expect(create.request.headers.get('Idempotency-Key')).toBe('create-version-key');
    create.flush({ id: 9, versionCode: 'PRO-V2' });

    service.activatePlanVersion(9, 'activate-key').subscribe();
    const activate = http.expectOne(`${environment.apiUrl}/platform/subscription-plan-admin/9/activate`);
    expect(activate.request.body).toBeNull();
    expect(activate.request.headers.get('Idempotency-Key')).toBe('activate-key');
    activate.flush({ id: 9, status: 'ACTIVE' });

    service.deactivatePlanVersion(9, '  Replaced by a governed newer version.  ', 'deactivate-key').subscribe();
    const deactivate = http.expectOne(`${environment.apiUrl}/platform/subscription-plan-admin/9/deactivate`);
    expect(deactivate.request.body).toEqual({ reason: 'Replaced by a governed newer version.' });
    deactivate.flush({ id: 9, status: 'INACTIVE' });
    expect(() => service.deactivatePlanVersion(9, 'short', 'key')).toThrowError(/between 10 and 1000/);
  });
});
