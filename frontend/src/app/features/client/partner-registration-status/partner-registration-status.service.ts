import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';

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
}

export interface PartnerRegistrationStatusResponse {
  overallStatus: PartnerOverallStatus;
  propertyCount: number;
  properties: PartnerPropertyStatusRow[];
}

@Injectable({ providedIn: 'root' })
export class PartnerRegistrationStatusService {
  private readonly http = inject(HttpClient);

  load(): Observable<PartnerRegistrationStatusResponse> {
    return this.http.get<PartnerRegistrationStatusResponse>(
      `${environment.apiUrl}/partner/registration-status`
    );
  }
}
