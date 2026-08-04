import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Hotel } from './client-api.service';

export interface AdminProperty extends Hotel {
  nameVi?: string;
  nameEn?: string;
  status?: string;
  operationStatus?: string;
  approvalStatus?: string;
}

export interface PropertyLocation {
  id: number;
  nameVi: string;
  nameEn?: string;
  locationType: 'PROVINCE' | 'WARD';
  parent?: { id: number };
}

export interface CreatePropertyRequest {
  nameVi: string;
  nameEn?: string;
  propertyType: string;
  addressLine: string;
  provinceId: number;
  wardId: number;
  descriptionVi?: string;
  descriptionEn?: string;
  starRating?: number;
  phone?: string;
  email?: string;
  website?: string;
  mainImage?: string;
}

export interface UpdatePropertyRequest extends Partial<CreatePropertyRequest> {
  reason: string;
}

@Injectable({
  providedIn: 'root'
})
export class PropertyService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/v1/hotels`;

  getAllProperties(): Observable<AdminProperty[]> {
    return this.http.get<AdminProperty[]>(this.apiUrl);
  }

  getProvinces(): Observable<PropertyLocation[]> {
    return this.http.get<PropertyLocation[]>(`${environment.apiUrl}/public/locations/provinces`);
  }

  getWards(provinceId: number): Observable<PropertyLocation[]> {
    return this.http.get<PropertyLocation[]>(`${environment.apiUrl}/public/locations/provinces/${provinceId}/wards`);
  }

  createProperty(property: CreatePropertyRequest): Observable<AdminProperty> {
    return this.http.post<AdminProperty>(this.apiUrl, property);
  }

  updateProperty(id: number, property: UpdatePropertyRequest): Observable<AdminProperty> {
    return this.http.put<AdminProperty>(`${this.apiUrl}/${id}`, property);
  }

  closeProperty(id: number, reason: string): Observable<AdminProperty> {
    return this.http.post<AdminProperty>(`${this.apiUrl}/${id}/close`, { reason });
  }

  submitProperty(id: number): Observable<AdminProperty> {
    return this.http.post<AdminProperty>(`${this.apiUrl}/${id}/submit`, {});
  }

  approveProperty(id: number): Observable<AdminProperty> {
    return this.http.post<AdminProperty>(`${this.apiUrl}/${id}/approve`, {});
  }

  rejectProperty(id: number): Observable<AdminProperty> {
    return this.http.post<AdminProperty>(`${this.apiUrl}/${id}/reject`, {});
  }
}
