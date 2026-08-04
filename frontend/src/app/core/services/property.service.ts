import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Hotel } from './client-api.service';

export interface AdminProperty extends Hotel {
  nameVi?: string;
  nameEn?: string;
  status?: string;
  operationStatus?: string;
}

export interface PropertyLocation {
  id: number;
  nameVi: string;
  nameEn?: string;
  locationType: 'PROVINCE' | 'WARD';
  parent?: { id: number };
}

export interface CreatePropertyRequest {
  name: string;
  nameVi: string;
  nameEn?: string;
  propertyType: string;
  addressLine: string;
  city: string;
  country: string;
  provinceId: number;
  wardId: number;
  description?: string;
  descriptionVi?: string;
  descriptionEn?: string;
  starRating?: number;
  phone?: string;
  email?: string;
  website?: string;
  mainImage?: string;
  status: 'DRAFT';
  approvalStatus: 'DRAFT';
  operationStatus: 'INACTIVE';
  isDemo: false;
  dataSource: 'ADMIN';
}

export interface PropertyApprovalQueueItem extends Record<string, unknown> {
  propertyId: number;
  code: string;
  name: string;
  address: string;
  propertyType: string;
  status: 'PENDING_APPROVAL';
  approvalStatus: 'PENDING_APPROVAL';
  operationStatus: 'INACTIVE';
  ownershipStatus: 'PENDING';
  ownerId: number;
  ownerName: string;
  ownerEmail: string;
  submittedByUserId: number | null;
  submittedAt: string | null;
  reviewedByUserId: number | null;
  reviewedAt: string | null;
  reason: string | null;
}

export interface PropertyApprovalDecisionResponse {
  propertyId: number;
  status: 'ACTIVE' | 'REJECTED';
  approvalStatus: 'APPROVED' | 'REJECTED';
  operationStatus: 'ACTIVE' | 'INACTIVE';
  ownershipStatus: 'ACTIVE' | 'INACTIVE';
  reviewedByUserId: number;
  reviewedAt: string;
  reason?: string | null;
}

export type PropertyLifecycleAction = 'SUSPEND' | 'REACTIVATE' | 'CLOSE';

export interface PropertyLifecycleSummary {
  propertyId: number;
  code: string;
  name: string;
  address: string;
  propertyType: string;
  status: string;
  approvalStatus: string;
  operationStatus: string;
  lifecycleAction: PropertyLifecycleAction | null;
  lifecycleReason: string | null;
  lifecycleChangedByUserId: number | null;
  lifecycleChangedAt: string | null;
  allowedTransitions: PropertyLifecycleAction[];
}

export interface PropertyLifecycleDecisionResponse {
  propertyId: number;
  action: PropertyLifecycleAction;
  changed: boolean;
  actorUserId: number;
  changedAt: string;
  reason: string;
  status: string;
  approvalStatus: string;
  operationStatus: string;
}

@Injectable({
  providedIn: 'root'
})
export class PropertyService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/v1/hotels`;

  getAllProperties(): Observable<AdminProperty[]> {
    return this.http.get<AdminProperty[]>(this.apiUrl);
  }

  getProvinces(): Observable<PropertyLocation[]> {
    return this.http.get<PropertyLocation[]>(`${environment.apiUrl}/public/locations/provinces`);
  }

  getWards(provinceId: number): Observable<PropertyLocation[]> {
    return this.http.get<PropertyLocation[]>(`${environment.apiUrl}/public/locations/provinces/${provinceId}/wards`);
  }

  createProperty(property: CreatePropertyRequest): Observable<AdminProperty> {
    return this.http.post<AdminProperty>(this.apiUrl, property);
  }

  updateProperty(id: number, property: Partial<CreatePropertyRequest>): Observable<AdminProperty> {
    return this.http.put<AdminProperty>(`${this.apiUrl}/${id}`, property);
  }

  submitProperty(id: number): Observable<AdminProperty> {
    return this.http.post<AdminProperty>(`${this.apiUrl}/${id}/submit`, {});
  }

  approveProperty(id: number): Observable<AdminProperty> {
    return this.http.post<AdminProperty>(`${this.apiUrl}/${id}/approve`, {});
  }

  rejectProperty(id: number): Observable<AdminProperty> {
    return this.http.post<AdminProperty>(`${this.apiUrl}/${id}/reject`, {});
  }

  getPropertyApprovalQueue(): Observable<PropertyApprovalQueueItem[]> {
    return this.http.get<PropertyApprovalQueueItem[]>(`${environment.apiUrl}/admin/property-approvals`);
  }

  approvePropertyReview(id: number): Observable<PropertyApprovalDecisionResponse> {
    return this.http.post<PropertyApprovalDecisionResponse>(
      `${environment.apiUrl}/admin/property-approvals/${id}/approve`,
      {}
    );
  }

  rejectPropertyReview(id: number, reason: string): Observable<PropertyApprovalDecisionResponse> {
    return this.http.post<PropertyApprovalDecisionResponse>(
      `${environment.apiUrl}/admin/property-approvals/${id}/reject`,
      { reason }
    );
  }

  getPropertyLifecycleSummaries(): Observable<PropertyLifecycleSummary[]> {
    return this.http.get<PropertyLifecycleSummary[]>(`${environment.apiUrl}/admin/properties/lifecycle`);
  }

  suspendProperty(id: number, reason: string, idempotencyKey: string): Observable<PropertyLifecycleDecisionResponse> {
    return this.propertyLifecycleTransition(id, 'suspend', reason, idempotencyKey);
  }

  reactivateProperty(id: number, reason: string, idempotencyKey: string): Observable<PropertyLifecycleDecisionResponse> {
    return this.propertyLifecycleTransition(id, 'reactivate', reason, idempotencyKey);
  }

  closeProperty(id: number, reason: string, idempotencyKey: string): Observable<PropertyLifecycleDecisionResponse> {
    return this.propertyLifecycleTransition(id, 'close', reason, idempotencyKey);
  }

  private propertyLifecycleTransition(
    id: number,
    action: 'suspend' | 'reactivate' | 'close',
    reason: string,
    idempotencyKey: string
  ): Observable<PropertyLifecycleDecisionResponse> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<PropertyLifecycleDecisionResponse>(
      `${environment.apiUrl}/admin/properties/${id}/${action}`,
      { reason },
      { headers }
    );
  }
}
