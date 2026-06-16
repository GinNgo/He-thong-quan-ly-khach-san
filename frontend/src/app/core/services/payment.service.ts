import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Payment {
  id?: number;
  reservationId: number;
  amount: number;
  paymentMethod: string;
  status?: string;
  transactionId?: string;
  paymentDate?: string;
}

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private apiUrl = 'http://localhost:8080/api/payments';

  constructor(private http: HttpClient) {}

  getPaymentsByReservation(reservationId: number): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.apiUrl}/reservation/${reservationId}`);
  }

  processPayment(payment: Payment): Observable<Payment> {
    return this.http.post<Payment>(this.apiUrl, payment);
  }
}
