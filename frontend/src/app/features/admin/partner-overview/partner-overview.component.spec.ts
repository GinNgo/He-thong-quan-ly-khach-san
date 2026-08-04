import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth';
import { PartnerOverviewComponent } from './partner-overview.component';

describe('PartnerOverviewComponent', () => {
  let http: HttpTestingController;
  let routeData: Record<string, string>;
  let authService: { getRoles: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    routeData = { title: 'Chủ cơ sở', endpoint: 'property-owners' };
    authService = { getRoles: vi.fn(() => []) };
    await TestBed.configureTestingModule({
      imports: [PartnerOverviewComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useFactory: () => ({ snapshot: { data: routeData } }) },
        { provide: AuthService, useValue: authService },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders endpoint-specific columns instead of raw API keys', async () => {
    const fixture = TestBed.createComponent(PartnerOverviewComponent);
    fixture.detectChanges();

    http.expectOne(`${environment.apiUrl}/admin/property-owners`).flush([
      { user_id: 1, full_name: 'Owner One', email: 'owner@example.com', property_count: 2, plan_code: 'PRO' },
    ]);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Owner One');
    expect(fixture.nativeElement.querySelector('table')).not.toBeNull();
  });

  it('renders an explicit permission state for a denied endpoint', async () => {
    const fixture = TestBed.createComponent(PartnerOverviewComponent);
    fixture.detectChanges();

    http.expectOne(`${environment.apiUrl}/admin/property-owners`).flush(
      { message: 'Forbidden' },
      { status: 403, statusText: 'Forbidden' },
    );
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Bạn không có quyền truy cập');
  });

  it('approves an actionable row once, disables cross-actions and refreshes the queue', () => {
    const fixture = approvalFixture();
    loadApprovalQueue(fixture, [approvalItem()]);

    const approve = actionButton(fixture, 'Duyệt cơ sở');
    approve.click();
    approve.click();
    fixture.detectChanges();

    expect(actionButton(fixture, 'Duyệt cơ sở').disabled).toBe(true);
    expect(actionButton(fixture, 'Từ chối cơ sở').disabled).toBe(true);
    fixture.componentInstance.updateApprovalNote(7, '  Hồ sơ đã được đối chiếu đầy đủ.  ');
    approvalConfirm(fixture).click();
    approvalConfirm(fixture).click();
    const request = http.expectOne(`${environment.apiUrl}/admin/property-approvals/7/approve`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ note: 'Hồ sơ đã được đối chiếu đầy đủ.' });
    request.flush(decision('APPROVED'));
    http.expectOne(`${environment.apiUrl}/admin/property-approvals`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã duyệt cơ sở.');
  });

  it('shows actions only for exact PENDING_APPROVAL and renders safe review metadata', () => {
    const fixture = approvalFixture();
    loadApprovalQueue(fixture, [
      approvalItem(),
      {
        ...approvalItem({ propertyId: 8, name: 'Imported Claim', code: 'H-8' }),
        status: 'IMPORTED_PENDING_REVIEW',
        approvalStatus: 'IMPORTED_PENDING_REVIEW',
        submittedByUserId: null,
        submittedAt: null,
        reviewedByUserId: 2,
        reviewedAt: '2026-08-04T09:30:00Z',
        reason: 'Needs claim review'
      }
    ]);

    expect(fixture.nativeElement.querySelectorAll('.row-action-buttons .row-action')).toHaveLength(4);
    expect(fixture.nativeElement.textContent).toContain('Needs claim review');
    expect(fixture.nativeElement.textContent).not.toContain('null');
  });

  it('validates a typed rejection reason and prevents approve while the editor is open', () => {
    const fixture = approvalFixture();
    const item = approvalItem();
    loadApprovalQueue(fixture, [item]);

    actionButton(fixture, 'Từ chối cơ sở').click();
    fixture.detectChanges();
    expect(actionButton(fixture, 'Duyệt cơ sở').disabled).toBe(true);
    fixture.componentInstance.runAction(
      fixture.componentInstance.config.actions.find(action => action.key === 'approve')!,
      item
    );
    http.expectNone(`${environment.apiUrl}/admin/property-approvals/7/approve`);

    rejectionConfirm(fixture).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Vui lòng nhập lý do từ chối.');

    fixture.componentInstance.updateRejectionReason(7, 'short');
    fixture.componentInstance.confirmRejection(item);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('ít nhất 10 ký tự');

    fixture.componentInstance.updateRejectionReason(7, 'x'.repeat(501));
    fixture.componentInstance.confirmRejection(item);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('vượt quá 500 ký tự');

    fixture.componentInstance.updateRejectionReason(7, '  Thiếu giấy phép kinh doanh hợp lệ.  ');
    fixture.componentInstance.confirmRejection(item);
    const request = http.expectOne(`${environment.apiUrl}/admin/property-approvals/7/reject`);
    expect(request.request.body).toEqual({ reason: 'Thiếu giấy phép kinh doanh hợp lệ.' });
    request.flush(decision('REJECTED'));
    http.expectOne(`${environment.apiUrl}/admin/property-approvals`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã chuyển cơ sở sang trạng thái từ chối.');
  });

  it('clears stale rejection input when the editor is cancelled', () => {
    const fixture = approvalFixture();
    loadApprovalQueue(fixture, [approvalItem()]);

    actionButton(fixture, 'Từ chối cơ sở').click();
    fixture.componentInstance.updateRejectionReason(7, 'Lý do tạm thời cần được xóa.');
    fixture.detectChanges();

    const editorButtons = Array.from(
      fixture.nativeElement.querySelectorAll('.reject-editor-actions button')
    ) as HTMLButtonElement[];
    const cancel = editorButtons.find(button => button.textContent?.includes('Hủy'));
    expect(cancel).toBeDefined();
    cancel!.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.reject-editor')).toBeNull();
    expect(fixture.componentInstance.rejectionReasons[7]).toBeUndefined();

    actionButton(fixture, 'Từ chối cơ sở').click();
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('.reject-editor textarea') as HTMLTextAreaElement).value).toBe('');
  });

  it('accepts an empty approval note and rejects notes above 500 characters', () => {
    const fixture = approvalFixture();
    const item = approvalItem();
    loadApprovalQueue(fixture, [item]);

    actionButton(fixture, 'Duyệt cơ sở').click();
    fixture.componentInstance.updateApprovalNote(7, 'x'.repeat(501));
    fixture.componentInstance.confirmApproval(item);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('vượt quá 500 ký tự');
    http.expectNone(`${environment.apiUrl}/admin/property-approvals/7/approve`);

    fixture.componentInstance.updateApprovalNote(7, '   ');
    fixture.componentInstance.confirmApproval(item);
    const request = http.expectOne(`${environment.apiUrl}/admin/property-approvals/7/approve`);
    expect(request.request.body).toEqual({});
    request.flush(decision('APPROVED'));
    http.expectOne(`${environment.apiUrl}/admin/property-approvals`).flush([]);
  });

  it('lazy-loads safe admin history in a retryable dialog', () => {
    const fixture = approvalFixture();
    loadApprovalQueue(fixture, [approvalItem()]);

    actionButton(fixture, 'Xem lịch sử').click();
    const first = http.expectOne(`${environment.apiUrl}/admin/properties/7/history`);
    expect(first.request.method).toBe('GET');
    first.flush(
      { message: 'Internal reviewer 99 history failure' },
      { status: 500, statusText: 'Error' }
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Không thể tải lịch sử xét duyệt');
    expect(fixture.nativeElement.textContent).not.toContain('reviewer 99');
    fixture.componentInstance.retryHistory();
    http.expectOne(`${environment.apiUrl}/admin/properties/7/history`).flush([historyEvent()]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Đã phê duyệt');
    expect(fixture.nativeElement.textContent).toContain('Hồ sơ đã được xác minh.');
  });

  it('shows a safe per-row failure and allows retry', () => {
    const fixture = approvalFixture();
    loadApprovalQueue(fixture, [approvalItem()]);

    actionButton(fixture, 'Duyệt cơ sở').click();
    fixture.detectChanges();
    approvalConfirm(fixture).click();
    http.expectOne(`${environment.apiUrl}/admin/property-approvals/7/approve`).flush(
      { message: 'Internal tenant 99 reviewer failure' },
      { status: 500, statusText: 'Error' }
    );
    fixture.detectChanges();

    const rowError = fixture.nativeElement.querySelector('.row-action-error') as HTMLElement;
    expect(rowError.textContent).toContain('Không thể thực hiện thao tác');
    expect(rowError.textContent).not.toContain('tenant 99');
    expect(approvalConfirm(fixture).disabled).toBe(false);

    approvalConfirm(fixture).click();
    http.expectOne(`${environment.apiUrl}/admin/property-approvals/7/approve`).flush(decision('APPROVED'));
    http.expectOne(`${environment.apiUrl}/admin/property-approvals`).flush([]);
  });

  function approvalFixture(): ComponentFixture<PartnerOverviewComponent> {
    routeData = { title: 'Duyệt cơ sở', endpoint: 'property-approvals' };
    authService.getRoles.mockReturnValue(['ADMIN']);
    const fixture = TestBed.createComponent(PartnerOverviewComponent);
    fixture.detectChanges();
    return fixture;
  }

  function loadApprovalQueue(
    fixture: ComponentFixture<PartnerOverviewComponent>,
    items: Record<string, unknown>[]
  ): void {
    http.expectOne(`${environment.apiUrl}/admin/property-approvals`).flush(items);
    fixture.detectChanges();
  }
});

function actionButton(fixture: ComponentFixture<PartnerOverviewComponent>, label: string): HTMLButtonElement {
  const button = fixture.nativeElement.querySelector(`button[aria-label="${label}"]`) as HTMLButtonElement | null;
  if (!button) throw new Error(`Missing action: ${label}`);
  return button;
}

function rejectionConfirm(fixture: ComponentFixture<PartnerOverviewComponent>): HTMLButtonElement {
  const buttons = Array.from(fixture.nativeElement.querySelectorAll('.reject-editor-actions button')) as HTMLButtonElement[];
  const button = buttons.find(candidate => candidate.textContent?.includes('Xác nhận từ chối'));
  if (!button) throw new Error('Missing rejection confirmation');
  return button;
}

function approvalConfirm(fixture: ComponentFixture<PartnerOverviewComponent>): HTMLButtonElement {
  const buttons = Array.from(fixture.nativeElement.querySelectorAll('.review-note-editor-actions button')) as HTMLButtonElement[];
  const button = buttons.find(candidate => candidate.textContent?.includes('Xác nhận phê duyệt'));
  if (!button) throw new Error('Missing approval confirmation');
  return button;
}

function approvalItem(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    propertyId: 7,
    code: 'H-7',
    name: 'Hotel One',
    address: '12 Bach Dang',
    propertyType: 'HOTEL',
    status: 'PENDING_APPROVAL',
    approvalStatus: 'PENDING_APPROVAL',
    operationStatus: 'INACTIVE',
    ownerId: 42,
    ownerName: 'Owner One',
    ownerEmail: 'owner@example.com',
    ownershipStatus: 'PENDING',
    submittedByUserId: 42,
    submittedAt: '2026-08-04T08:00:00Z',
    ...overrides
  };
}

function decision(outcome: 'APPROVED' | 'REJECTED') {
  return {
    propertyId: 7,
    status: outcome === 'APPROVED' ? 'ACTIVE' : 'REJECTED',
    approvalStatus: outcome,
    operationStatus: outcome === 'APPROVED' ? 'ACTIVE' : 'INACTIVE',
    ownershipStatus: outcome === 'APPROVED' ? 'ACTIVE' : 'INACTIVE',
    reviewedByUserId: 1,
    reviewedAt: '2026-08-04T10:00:00Z',
    reason: outcome === 'REJECTED' ? 'Thiếu giấy phép kinh doanh hợp lệ.' : null
  };
}

function historyEvent() {
  return {
    eventId: 71,
    propertyId: 7,
    eventType: 'PROPERTY_APPROVED',
    actorKind: 'ADMIN',
    note: 'Hồ sơ đã được xác minh.',
    beforeState: historyState('PENDING_APPROVAL', 'PENDING_APPROVAL', 'INACTIVE', 'PENDING'),
    afterState: historyState('ACTIVE', 'APPROVED', 'ACTIVE', 'ACTIVE'),
    occurredAt: '2026-08-04T10:00:00Z'
  };
}

function historyState(status: string, approvalStatus: string, operationStatus: string, ownershipStatus: string) {
  return { status, approvalStatus, operationStatus, ownershipStatus };
}
