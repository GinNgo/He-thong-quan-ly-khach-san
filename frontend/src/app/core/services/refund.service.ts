import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FinancialAmount, FinancialCurrency } from '../../shared/financial/financial.models';

export type RefundStatus =
  | 'REQUESTED'
  | 'PENDING_APPROVAL'
  | 'POLICY_BLOCKED'
  | 'PENDING_PROVIDER'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED';
export type RefundProviderEnvironment = 'SIMULATOR' | 'SANDBOX' | 'PRODUCTION';

export interface RefundMutationOptions {
  idempotencyKey?: string;
  correlationId?: string;
}

export interface RefundRequestInput {
  amount: FinancialAmount;
  reason: string;
}

export interface PropertyRefundResult {
  publicId: string;
  originalTransactionPublicId: string;
  requestedAmount: FinancialAmount;
  currency: FinancialCurrency;
  status: RefundStatus;
  remainingRefundableAmount: FinancialAmount;
  requestedAt: string;
  completedAt?: string | null;
  replayed: boolean;
}

export interface PlatformRefundResult extends PropertyRefundResult {
  orderPublicId: string;
  policyVersion?: string | null;
  policyAvailable: boolean;
}

export interface RefundProviderAttemptInput {
  provider: string;
  environment: RefundProviderEnvironment;
}

export interface RefundProviderAttemptResult {
  refundPublicId: string;
  attemptNumber: number;
  provider: string;
  environment: RefundProviderEnvironment;
  providerReference: string;
  status: RefundStatus;
  replayed: boolean;
}

@Injectable({ providedIn: 'root' })
export class RefundService {
  private readonly http = inject(HttpClient);
  private readonly propertyPaymentsUrl = `${environment.apiUrl}/property-payments`;
  private readonly propertyRefundsUrl = `${environment.apiUrl}/property-refunds`;
  private readonly platformPaymentsUrl = `${environment.apiUrl}/platform-payments`;
  private readonly platformRefundsUrl = `${environment.apiUrl}/platform-refunds`;

  requestPropertyRefund(
    transactionPublicId: string,
    request: RefundRequestInput,
    options?: RefundMutationOptions,
  ): Observable<PropertyRefundResult> {
    return this.http.post<PropertyRefundResult>(
      `${this.propertyPaymentsUrl}/${this.encode(transactionPublicId)}/refunds`,
      request,
      { headers: this.mutationHeaders(options) },
    );
  }

  getPropertyRefund(refundPublicId: string): Observable<PropertyRefundResult> {
    return this.http.get<PropertyRefundResult>(
      `${this.propertyRefundsUrl}/${this.encode(refundPublicId)}`,
    );
  }

  approvePropertyRefund(
    refundPublicId: string,
    options?: RefundMutationOptions,
  ): Observable<PropertyRefundResult> {
    return this.http.post<PropertyRefundResult>(
      `${this.propertyRefundsUrl}/${this.encode(refundPublicId)}/approve`,
      null,
      { headers: this.mutationHeaders(options) },
    );
  }

  createPropertyRefundAttempt(
    refundPublicId: string,
    request: RefundProviderAttemptInput,
    options?: RefundMutationOptions,
  ): Observable<RefundProviderAttemptResult> {
    return this.http.post<RefundProviderAttemptResult>(
      `${this.propertyRefundsUrl}/${this.encode(refundPublicId)}/attempts`,
      request,
      { headers: this.mutationHeaders(options) },
    );
  }

  requestPlatformRefund(
    transactionPublicId: string,
    request: RefundRequestInput,
    options?: RefundMutationOptions,
  ): Observable<PlatformRefundResult> {
    return this.http.post<PlatformRefundResult>(
      `${this.platformPaymentsUrl}/${this.encode(transactionPublicId)}/refunds`,
      request,
      { headers: this.mutationHeaders(options) },
    );
  }

  getPlatformRefund(refundPublicId: string): Observable<PlatformRefundResult> {
    return this.http.get<PlatformRefundResult>(
      `${this.platformRefundsUrl}/${this.encode(refundPublicId)}`,
    );
  }

  approvePlatformRefund(
    refundPublicId: string,
    options?: RefundMutationOptions,
  ): Observable<PlatformRefundResult> {
    return this.http.post<PlatformRefundResult>(
      `${this.platformRefundsUrl}/${this.encode(refundPublicId)}/approve`,
      null,
      { headers: this.mutationHeaders(options) },
    );
  }

  createPlatformRefundAttempt(
    refundPublicId: string,
    request: Omit<RefundProviderAttemptInput, 'environment'> & { environment?: never },
    options?: RefundMutationOptions,
  ): Observable<RefundProviderAttemptResult> {
    return this.http.post<RefundProviderAttemptResult>(
      `${this.platformRefundsUrl}/${this.encode(refundPublicId)}/attempts`,
      request,
      { headers: this.mutationHeaders(options) },
    );
  }

  private mutationHeaders(options?: RefundMutationOptions): HttpHeaders {
    let headers = new HttpHeaders();
    if (options?.idempotencyKey) headers = headers.set('Idempotency-Key', options.idempotencyKey);
    if (options?.correlationId) headers = headers.set('X-Correlation-ID', options.correlationId);
    return headers;
  }

  private encode(value: string): string {
    return encodeURIComponent(value);
  }
}
