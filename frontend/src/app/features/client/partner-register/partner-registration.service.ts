import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';

export interface PartnerRegistrationResponse {
  userId: number;
  propertyId: number;
  status: 'DRAFT';
}

export interface PartnerPropertyDraft {
  propertyName: string;
  provinceId: number;
  wardId: number;
  address: string;
}

export interface AnonymousPartnerRegistration extends PartnerPropertyDraft {
  email: string;
  password: string;
  fullName: string;
  phone: string;
}

@Injectable({ providedIn: 'root' })
export class PartnerRegistrationService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/partner`;

  registerAnonymous(payload: AnonymousPartnerRegistration): Observable<PartnerRegistrationResponse> {
    return this.http.post<PartnerRegistrationResponse>(`${this.apiUrl}/register`, payload);
  }

  convertAuthenticated(payload: PartnerPropertyDraft): Observable<PartnerRegistrationResponse> {
    return this.http.post<PartnerRegistrationResponse>(`${this.apiUrl}/convert`, payload);
  }
}
