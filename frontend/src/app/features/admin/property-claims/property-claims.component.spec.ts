import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
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

  afterEach(() => http.verify());

  it('renders the safe claim summary contract', async () => {
    fixture.detectChanges();
    http.expectOne(request =>
      request.url === `${environment.apiUrl}/admin/property-claims`
      && request.params.get('page') === '0'
      && request.params.get('size') === '20'
    ).flush({
      content: [{
        id: 81,
        property: { id: 17, code: 'HOTEL-17', name: 'Safe Hotel', approvalStatus: 'PENDING_APPROVAL', operationStatus: 'INACTIVE' },
        requesterUser: { id: 42, username: 'owner', email: 'owner@example.com', fullName: 'Owner' },
        verificationMethod: 'EMAIL', verificationData: 'owner@example.com', note: null,
        status: 'PENDING', reviewedBy: null, reviewedAt: null, rejectionReason: null, createdAt: null
      }],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.claims).toHaveLength(1);
    expect(fixture.componentInstance.claims[0].property?.name).toBe('Safe Hotel');
    expect(fixture.componentInstance.claims[0].requesterUser?.username).toBe('owner');
    expect(fixture.componentInstance.claims[0].requesterUser).not.toHaveProperty('passwordHash');
    expect(fixture.componentInstance.claims[0].requesterUser).not.toHaveProperty('roles');
  });
});
