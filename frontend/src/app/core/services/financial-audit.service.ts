import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface FinancialAuditEvent { id: number; context: string; hotelId?: number; aggregateType: string; aggregateId: string; actorType: string; actorId?: number; source: string; previousState?: string; newState?: string; reason?: string; idempotencyReference?: string; providerReference?: string; correlationId: string; metadataJson: string; occurredAt: string; }
export interface FinancialAuditPage { content: FinancialAuditEvent[]; totalElements: number; totalPages: number; number: number; size: number; }
export interface FinancialAuditPolicy { appendOnly: boolean; retentionDays: number; exportMaxRows: number; redactionPolicy: string; }
export interface FinancialAuditFilters { context?: string; hotelId?: number; aggregateType?: string; aggregateId?: string; source?: string; correlationId?: string; from?: string; to?: string; page?: number; size?: number; }

@Injectable({ providedIn: 'root' })
export class FinancialAuditService {
  private http = inject(HttpClient); private endpoint = `${environment.apiUrl}/admin/financial-audit-events`;
  search(filters: FinancialAuditFilters = {}): Observable<FinancialAuditPage> { return this.http.get<FinancialAuditPage>(this.endpoint, { params: this.params(filters) }); }
  policy(): Observable<FinancialAuditPolicy> { return this.http.get<FinancialAuditPolicy>(`${this.endpoint}/policy`); }
  export(filters: FinancialAuditFilters = {}): Observable<Blob> { return this.http.get(`${this.endpoint}/export`, { params: this.params(filters), responseType: 'blob' }); }
  private params(filters: FinancialAuditFilters): HttpParams { return Object.entries(filters).reduce((params, [key, value]) => value === undefined || value === null || value === '' ? params : params.set(key, String(value)), new HttpParams()); }
}
