import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { PropertyClaimResponse, PropertyClaimStatus } from '../../../core/services/property-claim.service';
import { PropertyReviewHistoryEvent } from '../../../core/services/property.service';

export type PartnerPropertyStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'SUSPENDED'
  | 'CANCELLED';

export type PartnerOverallStatus = 'NONE' | 'MIXED' | PartnerPropertyStatus;

export interface PartnerPropertyStatusRow {
  propertyId: number;
  propertyName: string;
  status: PartnerPropertyStatus;
  approvalStatus: string;
  operationStatus: string;
  ownershipStatus: string;
  rejectionReason?: string | null;
  claimId: number | null;
  claimStatus: PropertyClaimStatus | null;
}

export interface PartnerRegistrationStatusResponse {
  overallStatus: PartnerOverallStatus;
  propertyCount: number;
  properties: PartnerPropertyStatusRow[];
}

export interface PartnerReviewSubmissionResponse {
  propertyId: number;
  status: 'PENDING_APPROVAL';
  approvalStatus: 'PENDING_APPROVAL';
  operationStatus: 'INACTIVE';
  submittedByUserId: number;
  submittedAt: string;
}

@Injectable({ providedIn: 'root' })
export class PartnerRegistrationStatusService {
  private readonly http = inject(HttpClient);

  load(): Observable<PartnerRegistrationStatusResponse> {
    return this.http.get<PartnerRegistrationStatusResponse>(
      `${environment.apiUrl}/partner/registration-status`
    );
  }

  submitForReview(propertyId: number): Observable<PartnerReviewSubmissionResponse> {
    return this.http.post<PartnerReviewSubmissionResponse>(
      `${environment.apiUrl}/partner/properties/${propertyId}/submit`,
      {}
    );
  }

  loadHistory(propertyId: number): Observable<PropertyReviewHistoryEvent[]> {
    return this.http.get<PropertyReviewHistoryEvent[]>(
      `${environment.apiUrl}/partner/properties/${propertyId}/history`
    );
  }

  cancelClaim(claimId: number): Observable<PropertyClaimResponse> {
    return this.http.post<PropertyClaimResponse>(
      `${environment.apiUrl}/property-claims/${claimId}/cancel`,
      {}
    );
  }
}
