import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Location {
  id: number;
  code: string;
  nameVi: string;
  nameEn: string;
  type: string;
  slug: string;
}

@Injectable({
  providedIn: 'root'
})
export class LocationService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/public/locations`;

  getRootLocations(): Observable<Location[]> {
    return this.http.get<Location[]>(this.apiUrl);
  }

  getChildrenLocations(parentId: number): Observable<Location[]> {
    return this.http.get<Location[]>(`${this.apiUrl}/${parentId}/children`);
  }
}
