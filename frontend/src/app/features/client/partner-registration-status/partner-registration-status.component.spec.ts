import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { PartnerRegistrationStatusComponent } from './partner-registration-status.component';
import { PartnerRegistrationStatusResponse } from './partner-registration-status.service';

describe('PartnerRegistrationStatusComponent', () => {
  let fixture: ComponentFixture<PartnerRegistrationStatusComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PartnerRegistrationStatusComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
    fixture = TestBed.createComponent(PartnerRegistrationStatusComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders every property in a mixed response and gates management access canonically', () => {
    loadWith({
      overallStatus: 'MIXED',
      propertyCount: 3,
      properties: [
        row(1, 'Approved Active', 'APPROVED', 'APPROVED', 'ACTIVE', 'ACTIVE'),
        row(2, 'Approved Suspended', 'SUSPENDED', 'APPROVED', 'SUSPENDED', 'ACTIVE'),
        row(3, 'Pending Review', 'PENDING', 'PENDING_APPROVAL', 'INACTIVE', 'PENDING')
      ]
    });

    const cards = fixture.nativeElement.querySelectorAll('.property-card') as NodeListOf<HTMLElement>;
    expect(cards).toHaveLength(3);
    expect(fixture.nativeElement.textContent).toContain('Các cơ sở đang ở nhiều trạng thái');
    expect(Array.from(cards).map(card => card.textContent)).toEqual(expect.arrayContaining([
      expect.stringContaining('Approved Active'),
      expect.stringContaining('Approved Suspended'),
      expect.stringContaining('Pending Review')
    ]));

    const managementLinks = fixture.nativeElement.querySelectorAll('.management-link');
    expect(managementLinks).toHaveLength(1);
    expect(managementLinks[0].closest('.property-card')?.textContent).toContain('Approved Active');
  });

  it('shows a rejected reason only for the property that provides one', () => {
    loadWith({
      overallStatus: 'REJECTED',
      propertyCount: 2,
      properties: [
        { ...row(4, 'Needs Evidence', 'REJECTED', 'REJECTED', 'INACTIVE', 'INACTIVE'), rejectionReason: 'Business license is unreadable.' },
        { ...row(5, 'No Reason Returned', 'REJECTED', 'REJECTED', 'INACTIVE', 'INACTIVE'), rejectionReason: null }
      ]
    });

    const reasons = fixture.nativeElement.querySelectorAll('.rejection-reason') as NodeListOf<HTMLElement>;
    expect(reasons).toHaveLength(1);
    expect(reasons[0].textContent).toContain('Business license is unreadable.');
    expect(fixture.nativeElement.textContent).not.toContain('null');
  });

  it('renders suspended and cancelled guidance without a management CTA', () => {
    loadWith({
      overallStatus: 'MIXED',
      propertyCount: 2,
      properties: [
        row(6, 'Suspended Hotel', 'SUSPENDED', 'APPROVED', 'SUSPENDED', 'ACTIVE'),
        row(7, 'Cancelled Hotel', 'CANCELLED', 'DRAFT', 'INACTIVE', 'INACTIVE')
      ]
    });

    expect(fixture.nativeElement.textContent).toContain('Đang tạm ngưng');
    expect(fixture.nativeElement.textContent).toContain('Đã hủy');
    expect(fixture.nativeElement.textContent).toContain('Không thể tiếp tục vận hành');
    expect(fixture.nativeElement.querySelector('.management-link')).toBeNull();
  });

  it('renders the truthful empty state and registration CTA', () => {
    loadWith({ overallStatus: 'NONE', propertyCount: 0, properties: [] });

    expect(fixture.nativeElement.textContent).toContain('Chưa có hồ sơ đối tác');
    const link = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/partner/register');
  });

  it('lazy-loads history only for the selected property and renders safe event data', () => {
    loadWith({
      overallStatus: 'MIXED',
      propertyCount: 2,
      properties: [
        row(6, 'History Hotel', 'APPROVED', 'APPROVED', 'ACTIVE', 'ACTIVE'),
        row(7, 'Other Hotel', 'PENDING', 'PENDING_APPROVAL', 'INACTIVE', 'PENDING')
      ]
    });

    http.expectNone(`${environment.apiUrl}/partner/properties/6/history`);
    const historyButtons = fixture.nativeElement.querySelectorAll('.history-toggle') as NodeListOf<HTMLButtonElement>;
    historyButtons[0].click();

    const request = http.expectOne(`${environment.apiUrl}/partner/properties/6/history`);
    expect(request.request.method).toBe('GET');
    request.flush([historyEvent(6)]);
    fixture.detectChanges();

    const history = fixture.nativeElement.querySelector('.property-history-region') as HTMLElement;
    expect(history.textContent).toContain('Đã phê duyệt');
    expect(history.textContent).toContain('Hồ sơ đã được xác minh.');
    expect(history.textContent).not.toContain('actorUserId');
    http.expectNone(`${environment.apiUrl}/partner/properties/7/history`);
  });

  it('shows a safe history error and retries the same tenant-scoped request', () => {
    loadWith({
      overallStatus: 'APPROVED',
      propertyCount: 1,
      properties: [row(12, 'Retry History Hotel', 'APPROVED', 'APPROVED', 'ACTIVE', 'ACTIVE')]
    });

    (fixture.nativeElement.querySelector('.history-toggle') as HTMLButtonElement).click();
    http.expectOne(`${environment.apiUrl}/partner/properties/12/history`).flush(
      { message: 'Internal owner 42 audit storage failure' },
      { status: 500, statusText: 'Error' }
    );
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('.property-history-region [role="alert"]') as HTMLElement;
    expect(error.textContent).toContain('Không thể tải lịch sử xét duyệt');
    expect(error.textContent).not.toContain('owner 42');

    (error.querySelector('button') as HTMLButtonElement).click();
    http.expectOne(`${environment.apiUrl}/partner/properties/12/history`).flush([]);
  });

  it('shows submit only for the raw DRAFT owner mapping, blocks duplicates and refreshes mixed status', () => {
    const draftOwner = row(8, 'Draft Owner Hotel', 'PENDING', 'DRAFT', 'INACTIVE', 'PENDING');
    loadWith({
      overallStatus: 'MIXED',
      propertyCount: 3,
      properties: [
        draftOwner,
        row(9, 'Already Pending', 'PENDING', 'PENDING_APPROVAL', 'INACTIVE', 'PENDING'),
        row(10, 'Approved Active', 'APPROVED', 'APPROVED', 'ACTIVE', 'ACTIVE')
      ]
    });

    const submitButtons = fixture.nativeElement.querySelectorAll('.submit-review') as NodeListOf<HTMLButtonElement>;
    expect(submitButtons).toHaveLength(1);
    expect(submitButtons[0].closest('.property-card')?.textContent).toContain('Draft Owner Hotel');

    submitButtons[0].click();
    fixture.componentInstance.submitForReview(draftOwner);
    fixture.detectChanges();

    const submit = http.expectOne(`${environment.apiUrl}/partner/properties/8/submit`);
    expect(submit.request.method).toBe('POST');
    expect(submit.request.body).toEqual({});
    expect((fixture.nativeElement.querySelector('.submit-review') as HTMLButtonElement).disabled).toBe(true);
    submit.flush({
      propertyId: 8,
      status: 'PENDING_APPROVAL',
      approvalStatus: 'PENDING_APPROVAL',
      operationStatus: 'INACTIVE',
      submittedByUserId: 42,
      submittedAt: '2026-08-04T08:30:00Z'
    });

    http.expectOne(`${environment.apiUrl}/partner/registration-status`).flush({
      overallStatus: 'MIXED',
      propertyCount: 3,
      properties: [
        row(8, 'Draft Owner Hotel', 'PENDING', 'PENDING_APPROVAL', 'INACTIVE', 'PENDING'),
        row(9, 'Already Pending', 'PENDING', 'PENDING_APPROVAL', 'INACTIVE', 'PENDING'),
        row(10, 'Approved Active', 'APPROVED', 'APPROVED', 'ACTIVE', 'ACTIVE')
      ]
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.property-card')).toHaveLength(3);
    expect(fixture.nativeElement.querySelector('.submit-review')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Approved Active');
  });

  it('recovers from a failed submission and never exposes backend error details', () => {
    const draftOwner = row(11, 'Retry Hotel', 'PENDING', 'DRAFT', 'INACTIVE', 'PENDING');
    loadWith({ overallStatus: 'PENDING', propertyCount: 1, properties: [draftOwner] });

    (fixture.nativeElement.querySelector('.submit-review') as HTMLButtonElement).click();
    http.expectOne(`${environment.apiUrl}/partner/properties/11/submit`).flush(
      { message: 'Internal tenant 99 rejected property 11.' },
      { status: 409, statusText: 'Conflict' }
    );
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('.submission-error') as HTMLElement;
    expect(error.textContent).toContain('Không thể gửi cơ sở này');
    expect(error.textContent).not.toContain('tenant 99');
    const retry = fixture.nativeElement.querySelector('.submit-review') as HTMLButtonElement;
    expect(retry.disabled).toBe(false);

    retry.click();
    http.expectOne(`${environment.apiUrl}/partner/properties/11/submit`).flush(
      { message: 'Still unavailable' },
      { status: 503, statusText: 'Unavailable' }
    );
  });

  it('shows an error and retries the status request', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Đang kiểm tra hồ sơ');
    http.expectOne(`${environment.apiUrl}/partner/registration-status`).flush(
      { message: 'Unavailable' },
      { status: 503, statusText: 'Unavailable' }
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeTruthy();
    const retry = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    retry.click();
    fixture.detectChanges();

    http.expectOne(`${environment.apiUrl}/partner/registration-status`).flush({
      overallStatus: 'NONE', propertyCount: 0, properties: []
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Chưa có hồ sơ đối tác');
  });

  function loadWith(response: PartnerRegistrationStatusResponse): void {
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/partner/registration-status`).flush(response);
    fixture.detectChanges();
  }
});

function row(
  propertyId: number,
  propertyName: string,
  status: 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'CANCELLED',
  approvalStatus: string,
  operationStatus: string,
  ownershipStatus: string
) {
  return {
    propertyId,
    propertyName,
    status,
    approvalStatus,
    operationStatus,
    ownershipStatus,
    rejectionReason: null
  };
}

function historyEvent(propertyId: number) {
  return {
    eventId: 71,
    propertyId,
    eventType: 'PROPERTY_APPROVED' as const,
    actorKind: 'ADMIN' as const,
    note: 'Hồ sơ đã được xác minh.',
    beforeState: historyState('PENDING_APPROVAL', 'PENDING_APPROVAL', 'INACTIVE', 'PENDING'),
    afterState: historyState('ACTIVE', 'APPROVED', 'ACTIVE', 'ACTIVE'),
    occurredAt: '2026-08-04T10:00:00Z'
  };
}

function historyState(status: string, approvalStatus: string, operationStatus: string, ownershipStatus: string) {
  return { status, approvalStatus, operationStatus, ownershipStatus };
}
