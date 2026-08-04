import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { PropertyClaimResponse } from '../../../core/services/property-claim.service';
import { PropertyClaimsComponent } from './property-claims.component';

describe('PropertyClaimsComponent', () => {
  let fixture: ComponentFixture<PropertyClaimsComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PropertyClaimsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    fixture = TestBed.createComponent(PropertyClaimsComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it('renders the safe claim summary contract', async () => {
    fixture.detectChanges();
    flushQueue(http, [pendingClaim()]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.claims).toHaveLength(1);
    expect(fixture.componentInstance.claims[0].property?.name).toBe('Safe Hotel');
    expect(fixture.componentInstance.claims[0].requesterUser?.username).toBe('owner');
    expect(fixture.componentInstance.claims[0].requesterUser).not.toHaveProperty('passwordHash');
    expect(fixture.componentInstance.claims[0].requesterUser).not.toHaveProperty('roles');
  });

  it('uses inline confirmation, typed response and busy protection for approval', () => {
    const browserConfirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    fixture.detectChanges();
    flushQueue(http, [pendingClaim()]);
    const claim = fixture.componentInstance.claims[0];

    fixture.componentInstance.requestApproval(claim);
    expect(fixture.componentInstance.approvalPromptId).toBe(81);
    http.expectNone(`${environment.apiUrl}/admin/property-claims/81/approve`);

    fixture.componentInstance.confirmApproval(claim);
    fixture.componentInstance.confirmApproval(claim);
    expect(fixture.componentInstance.approvingClaimId).toBe(81);
    fixture.componentInstance.loadClaims();
    http.expectNone(request => request.url === `${environment.apiUrl}/admin/property-claims`);

    const approval = http.expectOne(`${environment.apiUrl}/admin/property-claims/81/approve`);
    expect(approval.request.method).toBe('POST');
    expect(approval.request.body).toEqual({});
    approval.flush(approvedClaim());
    fixture.detectChanges();

    expect(browserConfirm).not.toHaveBeenCalled();
    expect(fixture.componentInstance.approvingClaimId).toBeNull();
    expect(fixture.componentInstance.approvalPromptId).toBeNull();
    expect(fixture.componentInstance.claims[0].status).toBe('APPROVED');
    expect(fixture.componentInstance.claims[0].property?.operationStatus).toBe('ACTIVE');
    expect(fixture.componentInstance.actionMessage).toContain('approved successfully');
    expect(fixture.nativeElement.querySelector('button.btn-success')).toBeNull();
  });

  it('shows a safe conflict error without exposing backend ownership details', () => {
    fixture.detectChanges();
    flushQueue(http, [pendingClaim()]);
    const claim = fixture.componentInstance.claims[0];
    fixture.componentInstance.requestApproval(claim);
    fixture.componentInstance.confirmApproval(claim);

    http.expectOne(`${environment.apiUrl}/admin/property-claims/81/approve`).flush(
      { message: 'Existing owner mapping 99 violates internal index UX_PROPERTY_OWNER' },
      { status: 409, statusText: 'Conflict' }
    );

    expect(fixture.componentInstance.actionError).toContain('current state');
    expect(fixture.componentInstance.actionError).not.toContain('mapping 99');
    expect(fixture.componentInstance.claims[0].status).toBe('PENDING');
    expect(fixture.componentInstance.approvalPromptId).toBe(81);
  });

  it('does not announce activation when the property status is not active', () => {
    fixture.detectChanges();
    flushQueue(http, [pendingClaim()]);
    const claim = fixture.componentInstance.claims[0];
    fixture.componentInstance.requestApproval(claim);
    fixture.componentInstance.confirmApproval(claim);

    const incompleteActivation = approvedClaim();
    incompleteActivation.property = { ...incompleteActivation.property!, status: 'DRAFT' };
    http.expectOne(`${environment.apiUrl}/admin/property-claims/81/approve`).flush(incompleteActivation);

    expect(fixture.componentInstance.actionMessage).toBe('');
    expect(fixture.componentInstance.actionError).toContain('did not confirm owner activation');
    expect(fixture.componentInstance.claims[0].status).toBe('PENDING');
  });

  it('uses a validated inline rejection editor and blocks duplicate submission', () => {
    const browserPrompt = vi.spyOn(window, 'prompt').mockReturnValue('unsafe browser reason');
    fixture.detectChanges();
    flushQueue(http, [pendingClaim()]);
    const claim = fixture.componentInstance.claims[0];

    fixture.componentInstance.requestRejection(claim);
    expect(fixture.componentInstance.rejectionPromptId).toBe(81);
    expect(fixture.componentInstance.rejectionReasonError(81)).toContain('at least 10');
    fixture.componentInstance.confirmRejection(claim);
    http.expectNone(`${environment.apiUrl}/admin/property-claims/81/reject`);

    fixture.componentInstance.rejectionReasons[81] = 'Ownership evidence could not be verified.';
    fixture.componentInstance.confirmRejection(claim);
    fixture.componentInstance.confirmRejection(claim);
    expect(fixture.componentInstance.rejectingClaimId).toBe(81);
    fixture.componentInstance.loadClaims();
    http.expectNone(request => request.url === `${environment.apiUrl}/admin/property-claims`);

    const rejection = http.expectOne(`${environment.apiUrl}/admin/property-claims/81/reject`);
    expect(rejection.request.method).toBe('POST');
    expect(rejection.request.body).toEqual({ reason: 'Ownership evidence could not be verified.' });
    rejection.flush(rejectedClaim('Ownership evidence could not be verified.'));
    fixture.detectChanges();

    expect(browserPrompt).not.toHaveBeenCalled();
    expect(fixture.componentInstance.rejectingClaimId).toBeNull();
    expect(fixture.componentInstance.rejectionPromptId).toBeNull();
    expect(fixture.componentInstance.claims[0].status).toBe('REJECTED');
    expect(fixture.componentInstance.claims[0].rejectionReason).toContain('could not be verified');
    expect(fixture.componentInstance.actionMessage).toContain('rejected successfully');
  });

  it('keeps the rejection editor retryable and hides backend validation details', () => {
    fixture.detectChanges();
    flushQueue(http, [pendingClaim()]);
    const claim = fixture.componentInstance.claims[0];
    fixture.componentInstance.requestRejection(claim);
    fixture.componentInstance.rejectionReasons[81] = 'Ownership evidence is incomplete.';
    fixture.componentInstance.confirmRejection(claim);

    http.expectOne(`${environment.apiUrl}/admin/property-claims/81/reject`).flush(
      { message: 'Requester 42 failed internal ownership rule CLAIM_OWNER_17' },
      { status: 400, statusText: 'Bad Request' }
    );

    expect(fixture.componentInstance.actionError).toContain('between 10 and 500');
    expect(fixture.componentInstance.actionError).not.toContain('Requester 42');
    expect(fixture.componentInstance.rejectionPromptId).toBe(81);
    expect(fixture.componentInstance.rejectingClaimId).toBeNull();
  });
});

function flushQueue(http: HttpTestingController, content: PropertyClaimResponse[]): void {
  http.expectOne(request =>
    request.url === `${environment.apiUrl}/admin/property-claims`
    && request.params.get('page') === '0'
    && request.params.get('size') === '20'
  ).flush({
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    number: 0,
    size: 20
  });
}

function pendingClaim(): PropertyClaimResponse {
  return {
    id: 81,
    property: {
      id: 17,
      code: 'HOTEL-17',
      name: 'Safe Hotel',
      status: 'DRAFT',
      approvalStatus: 'IMPORTED_PENDING_REVIEW',
      operationStatus: 'ACTIVE'
    },
    requesterUser: { id: 42, username: 'owner', email: 'owner@example.com', fullName: 'Owner' },
    verificationMethod: 'EMAIL',
    verificationData: 'owner@example.com',
    note: null,
    status: 'PENDING',
    reviewedBy: null,
    reviewedAt: null,
    rejectionReason: null,
    createdAt: '2026-08-04T06:00:00'
  };
}

function approvedClaim(): PropertyClaimResponse {
  return {
    ...pendingClaim(),
    property: {
      ...pendingClaim().property!,
      status: 'ACTIVE',
      approvalStatus: 'APPROVED',
      operationStatus: 'ACTIVE'
    },
    status: 'APPROVED',
    reviewedBy: { id: 7, username: 'admin', email: 'admin@example.com', fullName: 'Admin' },
    reviewedAt: '2026-08-04T06:30:00'
  };
}

function rejectedClaim(reason: string): PropertyClaimResponse {
  return {
    ...pendingClaim(),
    status: 'REJECTED',
    reviewedBy: { id: 7, username: 'admin', email: 'admin@example.com', fullName: 'Admin' },
    reviewedAt: '2026-08-04T06:30:00',
    rejectionReason: reason
  };
}
