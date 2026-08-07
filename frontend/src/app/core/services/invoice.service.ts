import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FinancialAmount } from '../../shared/financial/financial.models';

export interface Invoice {
  id?: number;
  invoiceCode?: string;
  reservationId: number;
  reservation?: {
    user?: {
      fullName?: string;
    };
  };
  issueDate?: string;
  totalAmount?: number;
  status?: string;
}

export interface CustomerInvoiceSummary {
  id: number;
  reservationId: number;
  invoiceCode?: string;
  invoiceNumber?: string;
  issueDate?: string;
  finalizedAt?: string;
  totalAmount: FinancialAmount;
  status: string;
  currency?: 'VND';
  customerSnapshotJson?: string;
  propertySnapshotJson?: string;
}

export interface PropertyInvoiceLine {
  id: number;
  lineType: string;
  code: string;
  name: string;
  description: string | null;
  quantity: number;
  unitPrice: FinancialAmount;
  taxAmount: FinancialAmount;
  discountAmount: FinancialAmount;
  totalAmount: FinancialAmount;
  usageStartedAt: string | null;
  usageEndedAt: string | null;
}

export interface InvoicePaymentAllocation {
  id: number;
  transactionId: number;
  transactionPublicId: string;
  allocatedAmount: FinancialAmount;
  method: string;
  provider: string;
  occurredAt: string;
}

export interface InvoiceCreditNoteLine {
  id: number;
  invoiceLineId: number | null;
  description: string;
  amount: FinancialAmount;
}

export interface InvoiceCreditNote {
  id: number;
  creditNoteNumber: string;
  reason: string;
  amount: FinancialAmount;
  issuedAt: string;
  lines: InvoiceCreditNoteLine[];
}

export interface PropertyInvoiceDetail {
  id: number;
  reservationId: number;
  invoiceNumber: string;
  status: 'FINALIZED';
  currency: 'VND';
  subtotal: FinancialAmount;
  taxAmount: FinancialAmount;
  feeAmount: FinancialAmount;
  discountAmount: FinancialAmount;
  totalAmount: FinancialAmount;
  paidAmount: FinancialAmount;
  refundedAmount: FinancialAmount;
  balanceAmount: FinancialAmount;
  customerSnapshotJson: string;
  propertySnapshotJson: string;
  finalizedAt: string;
  lines: PropertyInvoiceLine[];
  allocations: InvoicePaymentAllocation[];
  creditNotes: InvoiceCreditNote[];
}

export interface InvoiceEmailResult {
  invoiceId: number;
  invoiceNumber: string;
  recipient: string;
  sent: boolean;
  contentSha256: string;
  correlationId: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class InvoiceService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/invoices`;

  getAllInvoices(): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(this.apiUrl);
  }

  getMyInvoices(): Observable<CustomerInvoiceSummary[]> {
    return this.http.get<CustomerInvoiceSummary[]>(`${this.apiUrl}/finalized/my`);
  }

  getFinalizedInvoices(): Observable<CustomerInvoiceSummary[]> {
    return this.http.get<CustomerInvoiceSummary[]>(`${environment.apiUrl}/management/invoices/finalized`);
  }

  getInvoice(invoiceId: number): Observable<PropertyInvoiceDetail> {
    return this.http.get<PropertyInvoiceDetail>(`${this.apiUrl}/${invoiceId}`);
  }

  downloadPdf(invoiceId: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.apiUrl}/${invoiceId}/pdf`, {
      observe: 'response',
      responseType: 'blob',
    });
  }

  emailInvoice(invoiceId: number): Observable<InvoiceEmailResult> {
    return this.http.post<InvoiceEmailResult>(`${this.apiUrl}/${invoiceId}/email`, null);
  }

  getInvoiceByReservation(reservationId: number): Observable<PropertyInvoiceDetail> {
    return this.http.get<PropertyInvoiceDetail>(
      `${environment.apiUrl}/management/reservations/${reservationId}/invoice`,
    );
  }

  /** @deprecated Checkout finalizes invoices; this route only returns that existing snapshot. */
  generateInvoice(reservationId: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.apiUrl}/reservation/${reservationId}`, {});
  }
}
