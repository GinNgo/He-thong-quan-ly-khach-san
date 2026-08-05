import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface OperationalPolicyRequest {
  effectiveFrom: string;
  checkInVi: string;
  checkInEn?: string;
  checkOutVi: string;
  checkOutEn?: string;
  cancellationVi: string;
  cancellationEn?: string;
  childPolicyVi: string;
  childPolicyEn?: string;
  petPolicyVi: string;
  petPolicyEn?: string;
  smokingPolicyVi: string;
  smokingPolicyEn?: string;
  houseRulesVi: string;
  houseRulesEn?: string;
}

export interface OperationalPolicy extends OperationalPolicyRequest {
  id: number;
  hotelId: number;
  version: number;
  status: 'DRAFT' | 'PUBLISHED';
  effectiveUntil?: string;
  rowVersion?: number;
}

export interface PublicOperationalPolicy {
  version: number;
  effectiveFrom: string;
  locale: string;
  checkIn: string;
  checkOut: string;
  cancellation: string;
  childPolicy: string;
  petPolicy: string;
  smokingPolicy: string;
  houseRules: string;
}

@Injectable({ providedIn: 'root' })
export class OperationalPolicyService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/v1/hotels`;

  list(hotelId: number): Observable<OperationalPolicy[]> {
    return this.http.get<OperationalPolicy[]>(`${this.base}/${hotelId}/policies`);
  }

  create(hotelId: number, request: OperationalPolicyRequest): Observable<OperationalPolicy> {
    return this.http.post<OperationalPolicy>(`${this.base}/${hotelId}/policies`, request);
  }

  update(hotelId: number, policyId: number, request: OperationalPolicyRequest): Observable<OperationalPolicy> {
    return this.http.put<OperationalPolicy>(`${this.base}/${hotelId}/policies/${policyId}`, request);
  }

  publish(hotelId: number, policyId: number): Observable<OperationalPolicy> {
    return this.http.post<OperationalPolicy>(`${this.base}/${hotelId}/policies/${policyId}/publish`, {});
  }

  current(hotelId: number, locale = 'vi', stayDate?: string): Observable<PublicOperationalPolicy> {
    let params = new HttpParams().set('locale', locale);
    if (stayDate) params = params.set('stayDate', stayDate);
    return this.http.get<PublicOperationalPolicy>(`${this.base}/public/${hotelId}/policies/current`, { params });
  }
}
