import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface HotelServiceDTO {
  id?: number;
  code: string;
  nameVi: string;
  nameEn: string;
  price: number;
  descriptionVi?: string;
  descriptionEn?: string;
  status: string;
  createdAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class HotelServiceService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/services`;

  getServices(): Observable<HotelServiceDTO[]> {
    return this.http.get<HotelServiceDTO[]>(this.apiUrl);
  }

  createService(service: HotelServiceDTO): Observable<HotelServiceDTO> {
    return this.http.post<HotelServiceDTO>(this.apiUrl, service);
  }

  updateService(id: number, service: HotelServiceDTO): Observable<HotelServiceDTO> {
    return this.http.put<HotelServiceDTO>(`${this.apiUrl}/${id}`, service);
  }

  deleteService(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
