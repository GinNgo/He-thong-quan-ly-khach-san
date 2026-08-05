import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth';
import { PropertyLifecycleAction, PropertyLifecycleSummary } from '../../../core/services/property.service';
import { PropertyManagementComponent } from './property-management';

describe('PropertyManagementComponent', { timeout: 60_000 }, () => {
  let http: HttpTestingController;
  let authService: {
    getRoles: ReturnType<typeof vi.fn>;
    getPermissions: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    authService = {
      getRoles: vi.fn(() => ['SUPER_ADMIN']),
      getPermissions: vi.fn(() => [])
    };
    await TestBed.configureTestingModule({
      imports: [PropertyManagementComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService }
      ]
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('opens the create form and submits a typed draft payload', () => {
    const fixture = createFixture([]);
    const component = fixture.componentInstance;

    component.openCreate();
    component.form.patchValue({
      nameVi: 'LuxeStay T235',
      propertyType: 'HOTEL',
      provinceId: 1,
      wardId: 10,
      address: '01 Đường Biển',
      starRating: 4
    });
    component.save();

    const create = http.expectOne({ method: 'POST', url: `${environment.apiUrl}/v1/hotels` });
    expect(create.request.body).toMatchObject({
      name: 'LuxeStay T235',
      addressLine: '01 Đường Biển',
      city: 'Đà Nẵng',
      country: 'Việt Nam',
      status: 'DRAFT',
      approvalStatus: 'DRAFT',
      operationStatus: 'INACTIVE',
      isDemo: false
    });
    create.flush({ id: 99, name: 'LuxeStay T235' });
    http.expectOne(`${environment.apiUrl}/admin/properties/lifecycle`).flush([]);
    fixture.detectChanges();

    expect(component.dialogVisible).toBe(false);
    expect(component.saving).toBe(false);
  });

  it('blocks an incomplete create form before sending a request', () => {
    const fixture = createFixture([]);
    const component = fixture.componentInstance;

    component.openCreate();
    component.save();

    expect(component.formError).toContain('bắt buộc');
    expect(http.match({ method: 'POST', url: `${environment.apiUrl}/v1/hotels` })).toHaveLength(0);
    component.closeCreate();
  });

  it('renders separate status fields and only server-allowed lifecycle actions', async () => {
    const fixture = createFixture([
      lifecycleRow({ allowedTransitions: ['SUSPEND', 'CLOSE'] })
    ]);
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(text).toContain('Hoạt động');
    expect(text).toContain('Đã duyệt');
    expect(fixture.nativeElement.querySelector('[aria-label^="Tạm ngừng cơ sở"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-label^="Đóng cơ sở"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-label^="Kích hoạt lại cơ sở"]')).toBeNull();
  });

  it('keeps lifecycle actions read-only without APPROVE permission', async () => {
    authService.getRoles.mockReturnValue(['ADMIN']);
    authService.getPermissions.mockReturnValue([{ function: 'PROPERTY_LIFECYCLE', actionMask: 1 }]);

    const fixture = TestBed.createComponent(PropertyManagementComponent);
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/admin/properties/lifecycle`).flush([
      lifecycleRow({ allowedTransitions: ['SUSPEND', 'CLOSE'] })
    ]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.canManageLifecycle).toBe(false);
    expect(fixture.nativeElement.querySelector('[aria-label^="Tạm ngừng cơ sở"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Chỉ xem');
  });

  it('validates and trims a reason while preventing duplicate submission', () => {
    const row = lifecycleRow({ allowedTransitions: ['SUSPEND'] });
    const fixture = createFixture([row]);
    const component = fixture.componentInstance;

    component.openLifecycle(row, 'SUSPEND');
    component.submitLifecycle();
    expect(component.lifecycleError).toContain('nhập lý do');

    component.updateLifecycleReason('short');
    component.submitLifecycle();
    expect(component.lifecycleError).toContain('ít nhất 10');

    component.updateLifecycleReason('  Tạm ngừng để hoàn tất kiểm tra an toàn.  ');
    component.submitLifecycle();
    component.submitLifecycle();

    const request = http.expectOne(`${environment.apiUrl}/admin/properties/7/suspend`);
    expect(request.request.body).toEqual({ reason: 'Tạm ngừng để hoàn tất kiểm tra an toàn.' });
    expect(request.request.headers.get('Idempotency-Key')).toBe(component.lifecycleIdempotencyKey);
    expect(component.isLifecycleBusy(row)).toBe(true);
    request.flush(decision('SUSPEND'));
    http.expectOne(`${environment.apiUrl}/admin/properties/lifecycle`).flush([]);
    fixture.detectChanges();

    expect(component.lifecycleDialogVisible).toBe(false);
  });

  it('retains the same idempotency key after a safe retry', () => {
    const row = lifecycleRow({ allowedTransitions: ['SUSPEND'] });
    const fixture = createFixture([row]);
    const component = fixture.componentInstance;

    component.openLifecycle(row, 'SUSPEND');
    component.updateLifecycleReason('Tạm ngừng để kiểm tra hệ thống an toàn.');
    const key = component.lifecycleIdempotencyKey;
    component.submitLifecycle();

    const first = http.expectOne(`${environment.apiUrl}/admin/properties/7/suspend`);
    expect(first.request.headers.get('Idempotency-Key')).toBe(key);
    first.flush(
      { message: 'Internal tenant lifecycle secret' },
      { status: 500, statusText: 'Error' }
    );
    fixture.detectChanges();

    expect(component.lifecycleDialogVisible).toBe(true);
    expect(component.lifecycleError).toContain('thử lại an toàn');
    expect(component.lifecycleError).not.toContain('tenant lifecycle secret');
    expect(component.lifecycleIdempotencyKey).toBe(key);

    component.submitLifecycle();
    const retry = http.expectOne(`${environment.apiUrl}/admin/properties/7/suspend`);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush(decision('SUSPEND'));
    http.expectOne(`${environment.apiUrl}/admin/properties/lifecycle`).flush([]);
  });

  it('shows a terminal close warning and clears stale input when cancelled', () => {
    const row = lifecycleRow({ allowedTransitions: ['CLOSE'] });
    const fixture = createFixture([row]);
    const component = fixture.componentInstance;

    component.openLifecycle(row, 'CLOSE');
    component.updateLifecycleReason('Đóng cơ sở theo quyết định quản trị hợp lệ.');

    expect(component.lifecycleWarning()).toContain('chuyển đổi kết thúc');
    expect(component.lifecycleWarning()).toContain('không bị xóa');
    expect(component.lifecycleIdempotencyKey).not.toBe('');

    component.closeLifecycleDialog();
    expect(component.lifecycleDialogVisible).toBe(false);
    expect(component.lifecycleReason).toBe('');
    expect(component.lifecycleIdempotencyKey).toBe('');
  });

  it('lazy-loads lifecycle history with safe retry and blocks duplicate requests', () => {
    const row = lifecycleRow();
    const fixture = createFixture([row]);
    const component = fixture.componentInstance;

    component.openHistory(row);
    component.openHistory(row);
    const first = http.expectOne(`${environment.apiUrl}/admin/properties/7/history`);
    expect(first.request.method).toBe('GET');
    first.flush(
      { message: 'Internal tenant 99 history failure' },
      { status: 500, statusText: 'Error' }
    );
    fixture.detectChanges();

    expect(component.historyDialogVisible).toBe(true);
    expect(component.historyError).toContain('Không thể tải lịch sử xét duyệt');
    expect(component.historyError).not.toContain('tenant 99');

    component.retryHistory();
    http.expectOne(`${environment.apiUrl}/admin/properties/7/history`).flush([historyEvent()]);
    fixture.detectChanges();

    expect(component.historyEvents).toHaveLength(1);
    expect(component.historyEvents[0].beforeState).toBeNull();
    expect(JSON.stringify(component.historyEvents[0])).not.toContain('actorUserId');
  });

  function createFixture(rows: PropertyLifecycleSummary[]): ComponentFixture<PropertyManagementComponent> {
    const fixture = TestBed.createComponent(PropertyManagementComponent);
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/admin/properties/lifecycle`).flush(rows);
    http.expectOne(`${environment.apiUrl}/public/locations/provinces`).flush([
      { id: 1, nameVi: 'Đà Nẵng', locationType: 'PROVINCE' }
    ]);
    fixture.detectChanges();
    return fixture;
  }
});

function lifecycleRow(overrides: Partial<PropertyLifecycleSummary> = {}): PropertyLifecycleSummary {
  return {
    propertyId: 7,
    code: 'H-7',
    name: 'Hotel One',
    address: '12 Bach Dang',
    propertyType: 'HOTEL',
    status: 'ACTIVE',
    approvalStatus: 'APPROVED',
    operationStatus: 'ACTIVE',
    lifecycleAction: null,
    lifecycleReason: null,
    lifecycleChangedByUserId: null,
    lifecycleChangedAt: null,
    allowedTransitions: [],
    ...overrides
  };
}

function decision(action: PropertyLifecycleAction) {
  return {
    propertyId: 7,
    action,
    changed: true,
    actorUserId: 1,
    changedAt: '2026-08-04T10:00:00Z',
    reason: 'Tạm ngừng để hoàn tất kiểm tra an toàn.',
    status: action === 'REACTIVATE' ? 'ACTIVE' : action === 'SUSPEND' ? 'SUSPENDED' : 'CLOSED',
    approvalStatus: 'APPROVED',
    operationStatus: action === 'REACTIVATE' ? 'ACTIVE' : action === 'SUSPEND' ? 'SUSPENDED' : 'CLOSED'
  };
}

function historyEvent() {
  return {
    eventId: 71,
    propertyId: 7,
    eventType: 'PROPERTY_SUSPENDED',
    actorKind: 'ADMIN',
    note: 'Tạm ngừng để kiểm tra an toàn.',
    beforeState: null,
    afterState: {
      status: 'SUSPENDED',
      approvalStatus: 'APPROVED',
      operationStatus: 'SUSPENDED',
      ownershipStatus: null
    },
    occurredAt: '2026-08-04T10:00:00Z'
  };
}
