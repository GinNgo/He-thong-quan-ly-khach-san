import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { PropertyOwnershipService } from './property-ownership.service';

describe('PropertyOwnershipService', () => {
  let service: PropertyOwnershipService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PropertyOwnershipService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses typed owner, invitation and transfer contracts', () => {
    service.getOwners(17).subscribe();
    expect(http.expectOne(`${environment.apiUrl}/properties/17/owners`).request.method).toBe('GET');

    service.inviteCoOwner(17, 'co-owner@example.com').subscribe();
    const invite = http.expectOne(`${environment.apiUrl}/properties/17/owner-invitations`);
    expect(invite.request.method).toBe('POST');
    expect(invite.request.body).toEqual({ email: 'co-owner@example.com' });

    service.cancelInvitation(17, 31).subscribe();
    expect(http.expectOne(`${environment.apiUrl}/properties/17/owner-invitations/31`).request.method).toBe('DELETE');

    service.acceptInvitation('one-time-token', true).subscribe();
    const acceptInvite = http.expectOne(`${environment.apiUrl}/owner-invitations/accept`);
    expect(acceptInvite.request.body).toEqual({ token: 'one-time-token', ownerTermsAccepted: true });

    service.initiatePrimaryTransfer(17, 42, 'current-password').subscribe();
    const transfer = http.expectOne(`${environment.apiUrl}/properties/17/ownership-transfers`);
    expect(transfer.request.body).toEqual({ targetUserId: 42, currentPassword: 'current-password' });

    service.acceptPrimaryTransfer(71, true).subscribe();
    const acceptTransfer = http.expectOne(`${environment.apiUrl}/ownership-transfers/71/accept`);
    expect(acceptTransfer.request.body).toEqual({ responsibilityAccepted: true });

    service.cancelPrimaryTransfer(71).subscribe();
    expect(http.expectOne(`${environment.apiUrl}/ownership-transfers/71`).request.method).toBe('DELETE');

    service.leaveProperty(17, 'No longer managing this property.').subscribe();
    const leave = http.expectOne(`${environment.apiUrl}/properties/17/owners/leave`);
    expect(leave.request.body).toEqual({ reason: 'No longer managing this property.' });

    service.removeCoOwner(17, 42, 'Access is no longer required.').subscribe();
    const remove = http.expectOne(`${environment.apiUrl}/properties/17/owners/42`);
    expect(remove.request.method).toBe('DELETE');
    expect(remove.request.body).toEqual({ reason: 'Access is no longer required.' });

    expect(() => service.leaveProperty(17, '   ')).toThrowError(/between 10 and 500/);
    expect(() => service.removeCoOwner(17, 42, 'short')).toThrowError(/between 10 and 500/);

    http.match(() => true).forEach(request => request.flush({}));
  });
});
