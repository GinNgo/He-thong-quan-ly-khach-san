import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { PropertyOwnerDirectory, PropertyOwnershipService } from '../../../core/services/property-ownership.service';
import { PropertyOwnershipComponent } from './property-ownership.component';

describe('PropertyOwnershipComponent', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(async () => {
    api = {
      getOwners: vi.fn(() => of(primaryDirectory())),
      inviteCoOwner: vi.fn(),
      cancelInvitation: vi.fn(),
      initiatePrimaryTransfer: vi.fn(),
      acceptPrimaryTransfer: vi.fn(),
      cancelPrimaryTransfer: vi.fn(),
      leaveProperty: vi.fn(),
      removeCoOwner: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [PropertyOwnershipComponent],
      providers: [
        provideRouter([]),
        { provide: PropertyOwnershipService, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ propertyId: 17 }) } } },
      ]
    }).compileComponents();
  });

  afterEach(() => vi.restoreAllMocks());

  it('renders owner authority and excludes cooling co-owners from primary transfer', () => {
    const fixture = TestBed.createComponent(PropertyOwnershipComponent);
    fixture.detectChanges();

    expect(api['getOwners']).toHaveBeenCalledWith(17);
    expect(fixture.nativeElement.querySelectorAll('.owner-row')).toHaveLength(3);
    expect(fixture.nativeElement.textContent).toContain('Cooling until');
    expect(fixture.componentInstance.eligibleTransferOwners.map(owner => owner.userId)).toEqual([42]);
    expect(fixture.nativeElement.querySelector('#owner-email')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#transfer-password')).not.toBeNull();
  }, 15000);

  it('hides primary-owner actions from a co-owner and allows voluntary leave', () => {
    api['getOwners'].mockReturnValue(of(coOwnerDirectory()));
    const fixture = TestBed.createComponent(PropertyOwnershipComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#owner-email')).toBeNull();
    expect(fixture.nativeElement.querySelector('#transfer-owner')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.danger-ghost')).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Only co-owners may leave');
  });

  it('validates invitation email, blocks duplicate submit and hides conflict details', () => {
    const invite$ = new Subject<any>();
    api['inviteCoOwner'].mockReturnValue(invite$);
    const fixture = TestBed.createComponent(PropertyOwnershipComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.invitationEmail = 'invalid';
    component.inviteCoOwner();
    expect(api['inviteCoOwner']).not.toHaveBeenCalled();

    component.invitationEmail = ' Co-Owner@Example.com ';
    component.inviteCoOwner();
    component.inviteCoOwner();
    expect(api['inviteCoOwner']).toHaveBeenCalledTimes(1);
    expect(api['inviteCoOwner']).toHaveBeenCalledWith(17, 'co-owner@example.com');
    invite$.error(new HttpErrorResponse({
      status: 409,
      error: { code: 'OWNER_INVITATION_CONFLICT', message: 'membership 99 internal details' }
    }));
    expect(component.actionError).toContain('pending invitation already exists');
    expect(component.actionError).not.toContain('membership 99');
  });

  it('sends current password once, clears it and renders transfer responsibility', async () => {
    const transfer$ = new Subject<any>();
    api['initiatePrimaryTransfer'].mockReturnValue(transfer$);
    const fixture = TestBed.createComponent(PropertyOwnershipComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.transferTargetUserId = 42;
    component.currentPassword = 'current-secret';

    component.initiateTransfer();
    component.initiateTransfer();

    expect(api['initiatePrimaryTransfer']).toHaveBeenCalledTimes(1);
    expect(api['initiatePrimaryTransfer']).toHaveBeenCalledWith(17, 42, 'current-secret');
    expect(component.currentPassword).toBe('');
    transfer$.next(pendingTransfer());
    transfer$.complete();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.directory?.pendingTransfer).toEqual(pendingTransfer());
    expect(fixture.nativeElement.textContent).toContain('Current subscription');
    expect(fixture.nativeElement.textContent).toContain('A personal payment method is never transferred automatically');
  });

  it('accepts a recipient transfer only after responsibility confirmation', () => {
    const directory = coOwnerDirectory();
    directory.pendingTransfer = pendingTransfer();
    api['getOwners'].mockReturnValue(of(directory));
    api['acceptPrimaryTransfer'].mockReturnValue(of(primaryDirectory()));
    const fixture = TestBed.createComponent(PropertyOwnershipComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.acceptTransfer();
    expect(api['acceptPrimaryTransfer']).not.toHaveBeenCalled();
    component.responsibilityAccepted = true;
    component.acceptTransfer();

    expect(api['acceptPrimaryTransfer']).toHaveBeenCalledWith(71, true);
    expect(component.actionMessage).toContain('Primary ownership transferred');
  });

  it('lets only the primary actor cancel invitations and pending transfers', () => {
    api['cancelInvitation'].mockReturnValue(of(undefined));
    api['cancelPrimaryTransfer'].mockReturnValue(of(undefined));
    const directory = primaryDirectory();
    directory.pendingTransfer = pendingTransfer();
    api['getOwners'].mockReturnValue(of(directory));
    const fixture = TestBed.createComponent(PropertyOwnershipComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.invitationResult = { invitationId: 31, email: 'invite@example.com', status: 'PENDING', expiresAt: '2026-08-11T00:00:00Z' };

    component.cancelInvitation();
    expect(api['cancelInvitation']).toHaveBeenCalledWith(17, 31);
    expect(component.invitationResult).toBeNull();
    component.cancelTransfer();
    expect(api['cancelPrimaryTransfer']).toHaveBeenCalledWith(71);
    expect(component.directory?.pendingTransfer).toBeNull();
  });

  it('fails closed when readiness is unavailable and blocks expired acceptance', () => {
    api['initiatePrimaryTransfer'].mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { code: 'OWNERSHIP_FINANCIAL_READINESS_UNAVAILABLE', message: 'internal billing detail' }
    })));
    const fixture = TestBed.createComponent(PropertyOwnershipComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.transferTargetUserId = 42;
    component.currentPassword = 'current-secret';
    component.initiateTransfer();
    expect(component.directory?.pendingTransfer).toBeNull();
    expect(component.actionError).toContain('readiness is unavailable');
    expect(component.actionError).not.toContain('internal billing detail');

    const expired = coOwnerDirectory();
    expired.pendingTransfer = { ...pendingTransfer(), expiresAt: '2020-01-01T00:00:00Z' };
    api['getOwners'].mockReturnValue(of(expired));
    const expiredFixture = TestBed.createComponent(PropertyOwnershipComponent);
    expiredFixture.detectChanges();
    expect(expiredFixture.componentInstance.canAcceptPendingTransfer).toBe(false);
    expect(expiredFixture.nativeElement.textContent).toContain('expired and can no longer be accepted');
  });

  it('requires a reason and never allows the primary actor to leave', () => {
    api['removeCoOwner'].mockReturnValue(of(undefined));
    const fixture = TestBed.createComponent(PropertyOwnershipComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const target = primaryDirectory().owners[1];

    component.openRemoval(target);
    component.removalReason = 'short';
    component.removeCoOwner();
    expect(api['removeCoOwner']).not.toHaveBeenCalled();
    component.leaveReason = 'Primary must never leave directly.';
    component.leaveProperty();
    expect(api['leaveProperty']).not.toHaveBeenCalled();
  });

  it('maps replay and terminal membership conflicts without exposing backend detail', () => {
    api['removeCoOwner'].mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409,
      error: { code: 'OWNER_MEMBERSHIP_NOT_ACTIVE', message: 'internal membership row 123' }
    })));
    const fixture = TestBed.createComponent(PropertyOwnershipComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.openRemoval(primaryDirectory().owners[1]);
    component.removalReason = 'Access is no longer required.';
    component.removeCoOwner();

    expect(component.actionError).toContain('already inactive');
    expect(component.actionError).not.toContain('row 123');
  });
});

function primaryDirectory(): PropertyOwnerDirectory {
  return {
    propertyId: 17,
    actor: { userId: 7, role: 'PRIMARY_OWNER', canInvite: true, canLeave: false, canTransferPrimary: true },
    owners: [
      owner(7, 'Primary Owner', 'primary@example.com', 'PRIMARY_OWNER', true, true),
      owner(42, 'Eligible Co-owner', 'eligible@example.com', 'CO_OWNER', true, true),
      owner(43, 'Cooling Co-owner', 'cooling@example.com', 'CO_OWNER', false, false),
    ],
    pendingTransfer: null,
  };
}

function coOwnerDirectory(): PropertyOwnerDirectory {
  const directory = primaryDirectory();
  return {
    ...directory,
    actor: { userId: 42, role: 'CO_OWNER', canInvite: false, canLeave: true, canTransferPrimary: false },
  };
}

function owner(
  userId: number,
  fullName: string,
  email: string,
  role: 'PRIMARY_OWNER' | 'CO_OWNER',
  canReceivePrimary: boolean,
  canManageOwners: boolean
) {
  return {
    membershipId: userId + 100,
    userId,
    fullName,
    email,
    role,
    status: 'ACTIVE',
    acceptedAt: '2026-07-01T00:00:00Z',
    coolingEndsAt: canReceivePrimary ? '2026-07-08T00:00:00Z' : '2026-08-11T00:00:00Z',
    billingAdmin: false,
    canManageOwners,
    canReceivePrimary,
  };
}

function pendingTransfer() {
  return {
    transferId: 71,
    status: 'PENDING',
    expiresAt: '2026-08-11T00:00:00Z',
    targetUserId: 42,
    responsibility: {
      subscriptionPlan: 'PRO',
      renewalAt: '2026-09-01T00:00:00Z',
      overdueInvoiceCount: 0,
      openDisputeCount: 0,
      pendingRefundCount: 0,
      pendingContractChangeCount: 0,
    }
  };
}
