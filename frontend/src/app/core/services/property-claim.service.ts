import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export const PROPERTY_CLAIM_VERIFICATION_METHODS = [
  'BUSINESS_LICENSE',
  'EMAIL',
  'PHONE'
] as const;

export type PropertyClaimVerificationMethod = typeof PROPERTY_CLAIM_VERIFICATION_METHODS[number];
export type PropertyClaimStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface PropertyClaimRequest {
  verificationMethod: PropertyClaimVerificationMethod;
  verificationData: string;
  note?: string;
}

export interface PropertyClaimPropertySummary {
  id: number;
  code: string | null;
  name: string | null;
  approvalStatus: string | null;
  operationStatus: string | null;
}

export interface PropertyClaimUserSummary {
  id: number;
  username: string | null;
  email: string | null;
  fullName: string | null;
}

export interface PropertyClaimResponse {
  id: number;
  property: PropertyClaimPropertySummary | null;
  requesterUser: PropertyClaimUserSummary | null;
  verificationMethod: PropertyClaimVerificationMethod | null;
  verificationData: string | null;
  note: string | null;
  status: PropertyClaimStatus;
  reviewedBy: PropertyClaimUserSummary | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  createdAt: string | null;
}

export interface PropertyClaimPage {
  content: PropertyClaimResponse[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  numberOfElements?: number;
  first?: boolean;
  last?: boolean;
  empty?: boolean;
}

@Injectable({ providedIn: 'root' })
export class PropertyClaimService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  submit(propertyId: number, request: PropertyClaimRequest): Observable<PropertyClaimResponse> {
    return this.http.post<PropertyClaimResponse>(`${this.apiUrl}/properties/${propertyId}/claim`, request);
  }

  list(status?: PropertyClaimStatus, page = 0, size = 20): Observable<PropertyClaimPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    return this.http.get<PropertyClaimPage>(`${this.apiUrl}/admin/property-claims`, { params });
  }

  approve(id: number): Observable<PropertyClaimResponse> {
    return this.http.post<PropertyClaimResponse>(`${this.apiUrl}/admin/property-claims/${id}/approve`, {});
  }

  reject(id: number, reason: string): Observable<PropertyClaimResponse> {
    return this.http.post<PropertyClaimResponse>(`${this.apiUrl}/admin/property-claims/${id}/reject`, { reason });
  }
}

export function propertyClaimRequestErrorMessage(error: HttpErrorResponse): string {
  if (error.status === 400) {
    return 'Thông tin xác minh chưa hợp lệ. Vui lòng kiểm tra phương thức, nội dung và độ dài đã nhập.';
  }
  if (error.status === 409) {
    return 'Cơ sở này đã có yêu cầu xác nhận đang được xử lý. Vui lòng chờ kết quả trước khi gửi lại.';
  }
  if (error.status === 429) {
    const retryAfter = retryAfterSeconds(error);
    if (retryAfter === null) {
      return 'Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau.';
    }
    if (retryAfter < 60) {
      return `Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau ${retryAfter} giây.`;
    }
    return `Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau ${Math.ceil(retryAfter / 60)} phút.`;
  }
  return 'Không thể gửi yêu cầu lúc này. Vui lòng thử lại sau.';
}

function retryAfterSeconds(error: HttpErrorResponse): number | null {
  const value = error.headers.get('Retry-After')?.trim();
  if (!value) return null;
  if (/^\d+$/.test(value)) return Number(value);

  const retryAt = Date.parse(value);
  if (!Number.isFinite(retryAt)) return null;
  return Math.max(0, Math.ceil((retryAt - Date.now()) / 1000));
}
