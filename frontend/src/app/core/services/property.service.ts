import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { Hotel } from './client-api.service';

export interface AdminProperty extends Hotel {
  nameVi?: string;
  nameEn?: string;
  status?: string;
  operationStatus?: string;
}

export interface PropertyLocation {
  id: number;
  nameVi: string;
  nameEn?: string;
  locationType: 'PROVINCE' | 'WARD';
  parent?: { id: number };
  fullPath?: string;
  legacyParentName?: string;
  displayName?: string;
}

export interface CreatePropertyRequest {
  name: string;
  nameVi: string;
  nameEn?: string;
  propertyType: string;
  addressLine: string;
  city: string;
  country: string;
  provinceId: number;
  wardId: number;
  description?: string;
  descriptionVi?: string;
  descriptionEn?: string;
  starRating?: number;
  phone?: string;
  email?: string;
  website?: string;
  mainImage?: string;
  status: 'DRAFT';
  approvalStatus: 'DRAFT';
  operationStatus: 'INACTIVE';
  isDemo: false;
  dataSource: 'ADMIN';
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
    return this.http.get<PropertyLocation[]>(`${environment.apiUrl}/public/locations/provinces/${provinceId}/wards`).pipe(
      map((wards) => wards
        .map((ward) => ({
          ...ward,
          displayName: ward.legacyParentName
            ? `${ward.nameVi} — ${ward.legacyParentName}`
            : ward.nameVi,
        }))
        .sort((left, right) => (left.displayName ?? left.nameVi).localeCompare(
          right.displayName ?? right.nameVi,
          'vi',
          { numeric: true },
        )),
      ),
    );
  }

  createProperty(property: CreatePropertyRequest): Observable<AdminProperty> {
    return this.http.post<AdminProperty>(this.apiUrl, property);
  }

  updateProperty(id: number, property: Partial<CreatePropertyRequest>): Observable<AdminProperty> {
    return this.http.put<AdminProperty>(`${this.apiUrl}/${id}`, property);
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
