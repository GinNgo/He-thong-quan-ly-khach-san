import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface SubscriptionPlan {
  id: number;
  code: string;
  nameVi: string;
  nameEn: string;
  billingType: string;
  price: number;
  isLifetime: boolean;
  status: string;
  features: SubscriptionFeature[];
}

export interface SubscriptionFeature {
  code: string;
  nameVi: string;
  nameEn: string;
  valueType: string;
  limit: number;
}

export interface AccountSubscription {
  id: number;
  plan: SubscriptionPlan;
  startAt: string;
  endAt: string;
  isLifetime: boolean;
  status: string;
}

export interface SubscriptionEntitlement {
  code: string;
  nameVi: string;
  nameEn: string;
  limit: number;
  usage: number;
  allowed: boolean;
}

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
export class SubscriptionService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/subscriptions`;

  getPlans(): Observable<SubscriptionPlan[]> {
    return this.http.get<SubscriptionPlan[]>(`${this.apiUrl}/plans`);
  }

  getAdminPlans(): Observable<SubscriptionPlan[]> {
    return this.http.get<SubscriptionPlan[]>(`${environment.apiUrl}/admin/subscription-plans`);
  }

  getMySubscriptions(): Observable<AccountSubscription[]> {
    return this.http.get<AccountSubscription[]>(`${this.apiUrl}/me`);
  }

  getMyFeatures(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`${this.apiUrl}/me/features`);
  }

  getMyUsage(): Observable<SubscriptionUsage> {
    return this.http.get<SubscriptionUsage>(`${this.apiUrl}/me/usage`);
  }
}
