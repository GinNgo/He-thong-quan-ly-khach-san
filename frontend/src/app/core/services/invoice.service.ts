import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Invoice {
  id?: number;
  invoiceCode?: string;
  reservationId: number;
  issueDate?: string;
  totalAmount?: number;
  status?: string;
}

@Injectable({
  providedIn: 'root'
})
export class InvoiceService {
  private apiUrl = `${environment.apiUrl}/invoices`;

  constructor(private http: HttpClient) {}

  getInvoiceByReservation(reservationId: number): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.apiUrl}/reservation/${reservationId}`);
  }

  generateInvoice(reservationId: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.apiUrl}/reservation/${reservationId}`, {});
  }
}
