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
