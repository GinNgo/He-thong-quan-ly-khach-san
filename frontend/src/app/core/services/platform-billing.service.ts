import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type PlatformPaymentEnvironment = 'SIMULATOR' | 'SANDBOX' | 'PRODUCTION';
export type PlatformOrderOperation = 'PURCHASE' | 'RENEW' | 'UPGRADE' | 'DOWNGRADE' | 'REFUND';
export type PlatformOrderStatus =
  | 'CREATED'
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'APPLIED'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'REFUNDED';
export type PlatformPaymentAttemptStatus =
  | 'CREATED'
  | 'PENDING'
  | 'PROCESSING'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELLED'
  | 'EXPIRED';

export interface PlatformFeature {
  code: string;
  nameVi: string;
  nameEn: string;
  valueType: string;
  limit: number;
}

export interface PlatformCatalogPlan {
  id: number;
  code: string;
  nameVi: string;
  nameEn: string;
  billingType: string;
  price: number;
  currency: 'VND' | string;
  isLifetime: boolean;
  status: string;
  features: PlatformFeature[];
}

export interface PlatformOrder {
  id?: number;
  publicId: string;
  orderCode: string;
  ownerUserId: number;
  targetHotelId: number;
  operation: PlatformOrderOperation;
  planId: number;
  planVersion: string;
  planCode: string;
  planName: string;
  price: number;
  currency: 'VND' | string;
  billingPeriod: string;
  durationValue: number;
  durationUnit: 'DAY' | 'MONTH' | 'YEAR' | 'LIFETIME';
  featureSnapshotJson: string;
  status: PlatformOrderStatus;
  expiresAt: string;
  appliedAt?: string | null;
  replayed?: boolean;
}

export interface PlatformPaymentAttempt {
  publicId: string;
  orderPublicId?: string;
  status: PlatformPaymentAttemptStatus;
  provider: string;
  method: string;
  environment: PlatformPaymentEnvironment;
  expectedAmount: number;
  currency: 'VND' | string;
  providerOrderReference: string;
  expiresAt: string;
  completedAt?: string | null;
  merchantReferenceMasked?: string | null;
  replayed?: boolean;
  redirectUrl?: string | null;
}

export interface PlatformOrderDetails extends PlatformOrder {
  attempts: PlatformPaymentAttempt[];
}

export interface PlatformSubscriptionHistoryItem {
  id: number;
  orderPublicId: string;
  contractPublicId?: string | null;
  transactionPublicId?: string | null;
  actionType: string;
  previousStateJson?: string | null;
  newStateJson?: string | null;
  actorType: string;
  actorId?: number | null;
  reason?: string | null;
  occurredAt: string;
}

export interface PlatformSubscriptionEntitlement {
  targetHotelId: number;
  source: 'PLATFORM' | 'LEGACY_PROJECTION' | 'NONE' | string;
  platformAuthoritative: boolean;
  planId?: number | null;
  planCode: string;
  planName?: string | null;
  status: string;
  effectiveFrom?: string | null;
  effectiveUntil?: string | null;
  lifetime: boolean;
  limits: Record<string, number>;
  sourceReference?: string | null;
  migrationBlocker?: string | null;
}

export interface PlatformPolicyAvailability {
  downgradeConfigured: boolean;
  prorationConfigured: boolean;
  errorCode: string;
  downgradeMessage: string;
  prorationMessage: string;
}

export interface PlatformPaymentConfiguration {
  id?: number;
  provider: string;
  environment: PlatformPaymentEnvironment;
  enabled: boolean;
  merchantReferenceMasked?: string | null;
  secretConfigured: boolean;
  bankName?: string | null;
  bankAccountMasked?: string | null;
  callbackUrl?: string | null;
  productionApproved: boolean;
  ready: boolean;
  blockers: string[];
}

export interface PlatformPaymentConfigurationRequest {
  provider: string;
  environment: PlatformPaymentEnvironment;
  enabled: boolean;
  secretReference?: string | null;
  bankName?: string | null;
  bankAccountMasked?: string | null;
  callbackUrl?: string | null;
}

export interface PlatformPaymentReadiness {
  ready: boolean;
  mode: PlatformPaymentEnvironment;
  provider: string;
  maskedMerchant?: string | null;
  blockers: string[];
}

export interface PlatformBillingMutationOptions {
  idempotencyKey?: string;
  correlationId?: string;
}

export interface CreatePlatformPaymentAttemptRequest {
  provider: string;
  method: string;
}

@Injectable({ providedIn: 'root' })
export class PlatformBillingService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/platform`;

  getCatalog(): Observable<PlatformCatalogPlan[]> {
    return this.http.get<PlatformCatalogPlan[]>(`${this.baseUrl}/subscription-plans`);
  }

  getSubscriptionPlans(): Observable<PlatformCatalogPlan[]> {
    return this.getCatalog();
  }

  createPurchaseOrder(
    targetHotelId: number,
    planId: number,
    idempotencyKey: string,
  ): Observable<PlatformOrder> {
    return this.http.post<PlatformOrder>(
      `${this.baseUrl}/subscription-orders`,
      { targetHotelId, planId },
      { headers: this.mutationHeaders({ idempotencyKey }) },
    );
  }

  getOrder(orderPublicId: string): Observable<PlatformOrderDetails> {
    return this.http.get<PlatformOrderDetails>(
      `${this.baseUrl}/subscription-orders/${this.encode(orderPublicId)}`,
    );
  }

  createPaymentAttempt(
    orderPublicId: string,
    request: CreatePlatformPaymentAttemptRequest,
    idempotencyKey: string,
  ): Observable<PlatformPaymentAttempt> {
    return this.http.post<PlatformPaymentAttempt>(
      `${this.baseUrl}/subscription-orders/${this.encode(orderPublicId)}/payment-attempts`,
      request,
      { headers: this.mutationHeaders({ idempotencyKey }) },
    );
  }

  confirmSimulatorPayment(orderPublicId: string, attemptPublicId: string): Observable<unknown> {
    return this.http.post(
      `${environment.apiUrl}/financial-simulator/platform-orders/${this.encode(orderPublicId)}/attempts/${this.encode(attemptPublicId)}/confirm`,
      null,
    );
  }

  cancelOrder(
    orderPublicId: string,
    options?: PlatformBillingMutationOptions,
  ): Observable<PlatformOrderDetails> {
    return this.http.post<PlatformOrderDetails>(
      `${this.baseUrl}/subscription-orders/${this.encode(orderPublicId)}/cancel`,
      null,
      { headers: this.mutationHeaders(options) },
    );
  }

  createRenewalOrder(
    targetHotelId: number,
    idempotencyKey: string,
  ): Observable<PlatformOrder> {
    return this.http.post<PlatformOrder>(
      `${this.baseUrl}/subscriptions/${targetHotelId}/renewal-orders`,
      null,
      { headers: this.mutationHeaders({ idempotencyKey }) },
    );
  }

  createUpgradeOrder(
    targetHotelId: number,
    targetPlanId: number,
    idempotencyKey: string,
  ): Observable<PlatformOrder> {
    return this.http.post<PlatformOrder>(
      `${this.baseUrl}/subscriptions/${targetHotelId}/upgrade-orders`,
      { targetPlanId },
      { headers: this.mutationHeaders({ idempotencyKey }) },
    );
  }

  createDowngradeOrder(
    targetHotelId: number,
    targetPlanId: number,
    idempotencyKey: string,
  ): Observable<PlatformOrder> {
    return this.http.post<PlatformOrder>(
      `${this.baseUrl}/subscriptions/${targetHotelId}/downgrade-orders`,
      { targetPlanId },
      { headers: this.mutationHeaders({ idempotencyKey }) },
    );
  }

  getHistory(targetHotelId: number): Observable<PlatformSubscriptionHistoryItem[]> {
    return this.http.get<PlatformSubscriptionHistoryItem[]>(
      `${this.baseUrl}/subscriptions/${targetHotelId}/history`,
    );
  }

  getEntitlement(targetHotelId: number): Observable<PlatformSubscriptionEntitlement> {
    return this.http.get<PlatformSubscriptionEntitlement>(
      `${this.baseUrl}/subscriptions/${targetHotelId}/entitlement`,
    );
  }

  getPolicyAvailability(): Observable<PlatformPolicyAvailability> {
    return this.http.get<PlatformPolicyAvailability>(`${this.baseUrl}/subscription-policies`);
  }

  getPaymentConfigurations(): Observable<PlatformPaymentConfiguration[]> {
    return this.http.get<PlatformPaymentConfiguration[]>(`${this.baseUrl}/payment-configuration`);
  }

  getPaymentConfiguration(
    provider: string,
    environment: PlatformPaymentEnvironment,
  ): Observable<PlatformPaymentConfiguration> {
    return this.http.get<PlatformPaymentConfiguration>(
      `${this.baseUrl}/payment-configuration/${this.encode(provider)}/${environment}`,
    );
  }

  configurePayment(
    request: PlatformPaymentConfigurationRequest,
  ): Observable<PlatformPaymentConfiguration> {
    return this.http.put<PlatformPaymentConfiguration>(
      `${this.baseUrl}/payment-configuration`,
      request,
    );
  }

  validatePaymentConfiguration(provider: string): Observable<PlatformPaymentReadiness> {
    return this.http.post<PlatformPaymentReadiness>(
      `${this.baseUrl}/payment-configuration/validate`,
      null,
      { params: { provider } },
    );
  }

  private mutationHeaders(options?: PlatformBillingMutationOptions): HttpHeaders {
    let headers = new HttpHeaders();
    if (options?.idempotencyKey) headers = headers.set('Idempotency-Key', options.idempotencyKey);
    if (options?.correlationId) headers = headers.set('X-Correlation-ID', options.correlationId);
    return headers;
  }

  private encode(value: string): string {
    return encodeURIComponent(value);
  }
}
