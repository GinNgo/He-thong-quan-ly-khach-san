import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Hotel } from './client-api.service';

@Injectable({
  providedIn: 'root'
})
export class PropertyService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/v1/hotels`;

  getAllProperties(): Observable<Hotel[]> {
    return this.http.get<Hotel[]>(this.apiUrl);
  }

  createProperty(property: any): Observable<Hotel> {
    return this.http.post<Hotel>(this.apiUrl, property);
  }

  updateProperty(id: number, property: any): Observable<Hotel> {
    return this.http.put<Hotel>(`${this.apiUrl}/${id}`, property);
  }

  submitProperty(id: number): Observable<Hotel> {
    return this.http.post<Hotel>(`${this.apiUrl}/${id}/submit`, {});
  }

  approveProperty(id: number): Observable<Hotel> {
    return this.http.post<Hotel>(`${this.apiUrl}/${id}/approve`, {});
  }

  rejectProperty(id: number): Observable<Hotel> {
    return this.http.post<Hotel>(`${this.apiUrl}/${id}/reject`, {});
  }
}
