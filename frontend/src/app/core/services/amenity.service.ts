import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Amenity {
  id: number;
  code: string;
  nameVi: string;
  nameEn?: string;
  category: string;
  icon?: string;
  sortOrder: number;
  status: 'ACTIVE' | 'INACTIVE';
}

export interface AmenityDraft {
  code: string;
  nameVi: string;
  nameEn?: string;
  category: string;
  icon?: string;
  sortOrder: number;
}

export type AmenityScope = 'property' | 'roomType';

@Injectable({ providedIn: 'root' })
export class AmenityService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  publicCatalog(): Observable<Amenity[]> {
    return this.http.get<Amenity[]>(`${this.api}/public/amenities`);
  }

  managementCatalog(): Observable<Amenity[]> {
    return this.http.get<Amenity[]>(`${this.api}/admin/amenities`);
  }

  createCatalogEntry(draft: AmenityDraft): Observable<Amenity> {
    return this.http.post<Amenity>(`${this.api}/admin/amenities`, draft);
  }

  updateCatalogEntry(id: number, draft: AmenityDraft): Observable<Amenity> {
    return this.http.put<Amenity>(`${this.api}/admin/amenities/${id}`, draft);
  }

  deactivateCatalogEntry(id: number): Observable<Amenity> {
    return this.http.post<Amenity>(`${this.api}/admin/amenities/${id}/deactivate`, {});
  }

  assignments(scope: AmenityScope, entityId: number): Observable<Amenity[]> {
    return this.http.get<Amenity[]>(this.assignmentUrl(scope, entityId));
  }

  replaceAssignments(scope: AmenityScope, entityId: number, amenityIds: number[]): Observable<Amenity[]> {
    return this.http.put<Amenity[]>(this.assignmentUrl(scope, entityId), { amenityIds });
  }

  private assignmentUrl(scope: AmenityScope, entityId: number): string {
    return scope === 'property'
      ? `${this.api}/v1/properties/${entityId}/amenities`
      : `${this.api}/v1/room-types/${entityId}/amenities`;
  }
}
