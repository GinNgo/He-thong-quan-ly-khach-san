import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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
  private apiUrl = 'http://localhost:8080/api/invoices';

  constructor(private http: HttpClient) {}

  getInvoiceByReservation(reservationId: number): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.apiUrl}/reservation/${reservationId}`);
  }

  generateInvoice(reservationId: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.apiUrl}/reservation/${reservationId}`, {});
  }
}
