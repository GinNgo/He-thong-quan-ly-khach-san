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
  features: any[];
}

export interface AccountSubscription {
  id: number;
  plan: SubscriptionPlan;
  startAt: string;
  endAt: string;
  isLifetime: boolean;
  status: string;
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

  getMySubscriptions(): Observable<AccountSubscription[]> {
    return this.http.get<AccountSubscription[]>(`${this.apiUrl}/me`);
  }

  getMyFeatures(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/me/features`);
  }
}
