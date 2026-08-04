import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, throwError } from 'rxjs';
import { PropertyOwnershipService } from '../../../core/services/property-ownership.service';
import { OwnerInvitationAcceptComponent } from './owner-invitation-accept.component';

describe('OwnerInvitationAcceptComponent', () => {
  let acceptInvitation: ReturnType<typeof vi.fn>;

  beforeEach(() => acceptInvitation = vi.fn());

  async function create(token = 'single-use-token') {
    await TestBed.configureTestingModule({
      imports: [OwnerInvitationAcceptComponent],
      providers: [
        provideRouter([]),
        { provide: PropertyOwnershipService, useValue: { acceptInvitation } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ token }) } } },
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(OwnerInvitationAcceptComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('requires owner terms and blocks duplicate acceptance', async () => {
    const response$ = new Subject<any>();
    acceptInvitation.mockReturnValue(response$);
    const fixture = await create();
    const component = fixture.componentInstance;

    component.accept();
    expect(acceptInvitation).not.toHaveBeenCalled();
    component.ownerTermsAccepted = true;
    component.accept();
    component.accept();
    expect(acceptInvitation).toHaveBeenCalledTimes(1);
    expect(acceptInvitation).toHaveBeenCalledWith('single-use-token', true);

    response$.next(ownerMembership());
    response$.complete();
    fixture.detectChanges();
    expect(component.token).toBe('');
    expect(fixture.nativeElement.textContent).toContain('Invitation accepted');
    expect(fixture.nativeElement.textContent).not.toContain('single-use-token');
  });

  it('fails closed when the token is missing', async () => {
    const fixture = await create('');
    expect(fixture.componentInstance.error).toContain('incomplete or invalid');
    expect((fixture.nativeElement.querySelector('button') as HTMLButtonElement).disabled).toBe(true);
  });

  it('shows a safe expired invitation error without backend detail', async () => {
    acceptInvitation.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { code: 'OWNER_INVITATION_EXPIRED', message: 'token hash row 991 expired internally' }
    })));
    const fixture = await create();
    fixture.componentInstance.ownerTermsAccepted = true;
    fixture.componentInstance.accept();
    fixture.detectChanges();

    expect(fixture.componentInstance.error).toContain('expired, cancelled, already used');
    expect(fixture.componentInstance.error).not.toContain('row 991');
  });
});

function ownerMembership() {
  return {
    membershipId: 142,
    userId: 42,
    fullName: 'Invited Owner',
    email: 'invited@example.com',
    role: 'CO_OWNER' as const,
    status: 'ACTIVE',
    acceptedAt: '2026-08-04T10:00:00Z',
    coolingEndsAt: '2026-08-11T10:00:00Z',
    billingAdmin: false,
    canManageOwners: false,
    canReceivePrimary: false,
  };
}
