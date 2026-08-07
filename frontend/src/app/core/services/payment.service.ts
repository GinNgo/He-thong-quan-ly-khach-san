import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Payment {
  id?: number;
  reservationId: number;
  amount: number;
  paymentMethod: string;
  status?: string;
  transactionId?: string;
  paymentDate?: string;
}

export interface PaymentSession {
  sessionId: string;
  reservationId: number;
  provider: 'VNPAY' | 'MOMO' | 'ZALOPAY';
  method: string;
  amount: number;
  currency: 'VND';
  status: 'CREATED' | 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED';
  mode: 'SANDBOX' | 'SIMULATOR';
  expiresAt: string;
  url: string;
  reconciliationRequired: boolean;
}

export interface PaymentSessionStatus {
  sessionId: string;
  reservationId: number;
  provider: 'VNPAY' | 'MOMO' | 'ZALOPAY';
  amount: number;
  currency: 'VND';
  status: 'CREATED' | 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED';
  expiresAt: string;
  completedAt?: string;
  reconciliationRequired: boolean;
  failureCode?: string;
}

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private apiUrl = `${environment.apiUrl}/payments`;


  constructor(private http: HttpClient) {}

  getPaymentsByReservation(reservationId: number): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.apiUrl}/reservation/${reservationId}`);
  }

  createPaymentSession(
    reservationId: number,
    provider: string,
    idempotencyKey: string,
  ): Observable<PaymentSession> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<PaymentSession>(
      `${this.apiUrl}/sessions`,
      { reservationId, provider },
      { headers },
    );
  }

  getPaymentSessionStatus(sessionId: string): Observable<PaymentSessionStatus> {
    return this.http.get<PaymentSessionStatus>(`${this.apiUrl}/sessions/${encodeURIComponent(sessionId)}`);
  }

  confirmDemoPayment(token: string): Observable<{ status: string; message: string }> {
    return this.http.post<{ status: string; message: string }>(
      `${this.apiUrl}/simulator/confirm`,
      { token },
    );
  }
}
