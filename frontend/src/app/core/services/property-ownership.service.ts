import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type OwnerRole = 'PRIMARY_OWNER' | 'CO_OWNER';

export interface PropertyOwnerMembership {
  membershipId: number;
  userId: number;
  fullName: string | null;
  email: string;
  role: OwnerRole;
  status: string;
  acceptedAt: string | null;
  coolingEndsAt: string | null;
  billingAdmin: boolean;
  canManageOwners: boolean;
  canReceivePrimary: boolean;
}

export interface OwnershipActor {
  userId: number;
  role: OwnerRole;
  canInvite: boolean;
  canLeave: boolean;
  canTransferPrimary: boolean;
}

export interface OwnershipResponsibilitySummary {
  subscriptionPlan: string | null;
  renewalAt: string | null;
  overdueInvoiceCount: number;
  openDisputeCount: number;
  pendingRefundCount: number;
  pendingContractChangeCount: number;
}

export interface PendingOwnershipTransfer {
  transferId: number;
  status: string;
  expiresAt: string;
  targetUserId: number;
  responsibility: OwnershipResponsibilitySummary;
}

export interface PropertyOwnerDirectory {
  propertyId: number;
  actor: OwnershipActor;
  owners: PropertyOwnerMembership[];
  pendingTransfer: PendingOwnershipTransfer | null;
}

export interface OwnerInvitationResponse {
  invitationId: number;
  email: string;
  status: string;
  expiresAt: string;
}

export interface OwnershipTransferResponse extends PendingOwnershipTransfer {}

@Injectable({ providedIn: 'root' })
export class PropertyOwnershipService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  getOwners(propertyId: number): Observable<PropertyOwnerDirectory> {
    return this.http.get<PropertyOwnerDirectory>(`${this.apiUrl}/properties/${propertyId}/owners`);
  }

  inviteCoOwner(propertyId: number, email: string): Observable<OwnerInvitationResponse> {
    return this.http.post<OwnerInvitationResponse>(
      `${this.apiUrl}/properties/${propertyId}/owner-invitations`,
      { email }
    );
  }

  cancelInvitation(propertyId: number, invitationId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.apiUrl}/properties/${propertyId}/owner-invitations/${invitationId}`
    );
  }

  acceptInvitation(token: string, ownerTermsAccepted: boolean): Observable<PropertyOwnerMembership> {
    return this.http.post<PropertyOwnerMembership>(`${this.apiUrl}/owner-invitations/accept`, {
      token,
      ownerTermsAccepted
    });
  }

  initiatePrimaryTransfer(
    propertyId: number,
    targetUserId: number,
    currentPassword: string
  ): Observable<OwnershipTransferResponse> {
    return this.http.post<OwnershipTransferResponse>(
      `${this.apiUrl}/properties/${propertyId}/ownership-transfers`,
      { targetUserId, currentPassword }
    );
  }

  acceptPrimaryTransfer(transferId: number, responsibilityAccepted: boolean): Observable<PropertyOwnerDirectory> {
    return this.http.post<PropertyOwnerDirectory>(
      `${this.apiUrl}/ownership-transfers/${transferId}/accept`,
      { responsibilityAccepted }
    );
  }

  cancelPrimaryTransfer(transferId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/ownership-transfers/${transferId}`);
  }

  leaveProperty(propertyId: number, reason: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/properties/${propertyId}/owners/leave`, {
      reason: this.requiredReason(reason)
    });
  }

  removeCoOwner(propertyId: number, userId: number, reason: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/properties/${propertyId}/owners/${userId}`, {
      body: { reason: this.requiredReason(reason) }
    });
  }

  private requiredReason(reason: string): string {
    const normalized = reason.trim();
    if (normalized.length < 10 || normalized.length > 500) {
      throw new Error('Ownership reason must contain between 10 and 500 characters.');
    }
    return normalized;
  }
}
