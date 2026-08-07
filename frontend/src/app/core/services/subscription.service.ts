import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface SubscriptionFeature {
  code: string;
  nameVi: string;
  nameEn: string;
  valueType: string;
  limit: number | null;
}

export interface SubscriptionPlan {
  id: number;
  code: string;
  nameVi: string;
  nameEn: string;
  billingType: string;
  price: number;
  currency: string;
  lifetime: boolean;
  status: string;
  features: SubscriptionFeature[];
<<<<<<< HEAD
=======
}

export interface SubscriptionFeature {
  code: string;
  nameVi: string;
  nameEn: string;
  valueType: string;
  limit: number;
>>>>>>> codex/ui-functional-audit-polish
}

export interface PropertyAccountSubscription {
  targetHotelId: number;
  source: 'PLATFORM' | 'LEGACY_PROJECTION' | 'NONE' | string;
  platformAuthoritative: boolean;
  planId: number | null;
  planCode: string;
  planName: string | null;
  status: string;
  effectiveFrom: string | null;
  effectiveUntil: string | null;
  lifetime: boolean;
  sourceReference: string | null;
  migrationBlocker: string | null;
}

<<<<<<< HEAD
export interface SubscriptionUsageFeature {
  code: string;
  nameVi: string;
  nameEn: string;
  limit: number | null;
=======
export interface SubscriptionEntitlement {
  code: string;
  nameVi: string;
  nameEn: string;
  limit: number;
>>>>>>> codex/ui-functional-audit-polish
  usage: number;
  allowed: boolean;
}

<<<<<<< HEAD
export interface PropertySubscriptionUsage {
  targetHotelId: number;
  source: string;
  platformAuthoritative: boolean;
  planCode: string;
  subscriptionStatus: string;
  effectiveFrom: string | null;
  effectiveUntil: string | null;
  lifetime: boolean;
  limits: Record<string, number | null>;
  usage: Record<string, number>;
  features: SubscriptionUsageFeature[];
  migrationBlocker: string | null;
}

@Injectable({ providedIn: 'root' })
=======
export interface SubscriptionUsage {
  planCode: string;
  subscriptionStatus: string;
  startAt?: string;
  endAt?: string;
  lifetime: boolean;
  limits: Record<string, number>;
  usage: Record<string, number>;
  features: SubscriptionEntitlement[];
}

@Injectable({
  providedIn: 'root'
})
>>>>>>> codex/ui-functional-audit-polish
export class SubscriptionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/subscriptions`;

  getPlans(): Observable<SubscriptionPlan[]> {
    return this.http.get<SubscriptionPlan[]>(`${this.apiUrl}/plans`);
  }

  getPropertySubscription(targetHotelId: number): Observable<PropertyAccountSubscription> {
    return this.http.get<PropertyAccountSubscription>(`${this.apiUrl}/me`, {
      params: this.propertyParams(targetHotelId)
    });
  }

<<<<<<< HEAD
  getPropertyFeatures(targetHotelId: number): Observable<Record<string, number | null>> {
    return this.http.get<Record<string, number | null>>(`${this.apiUrl}/me/features`, {
      params: this.propertyParams(targetHotelId)
    });
  }

  getPropertyUsage(targetHotelId: number): Observable<PropertySubscriptionUsage> {
    return this.http.get<PropertySubscriptionUsage>(`${this.apiUrl}/me/usage`, {
      params: this.propertyParams(targetHotelId)
    });
  }

  private propertyParams(targetHotelId: number): HttpParams {
    if (!Number.isInteger(targetHotelId) || targetHotelId <= 0) {
      throw new Error('A valid selected property is required for subscription reads.');
    }
    return new HttpParams().set('targetHotelId', targetHotelId);
=======
  getMyFeatures(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${this.apiUrl}/me/features`);
  }

  getMyUsage(): Observable<SubscriptionUsage> {
    return this.http.get<SubscriptionUsage>(`${this.apiUrl}/me/usage`);
>>>>>>> codex/ui-functional-audit-polish
  }
}
