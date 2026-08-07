import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth';

export interface AdminPropertyOption { id: number; name: string; nameVi?: string; code?: string; }
export interface AdminRoomType {
  id: number; hotelId: number; code: string; nameVi: string; nameEn?: string;
  descriptionVi?: string; descriptionEn?: string; bedType?: string; bedCount?: number;
  area?: number; maxAdults?: number; maxChildren?: number; maxGuests: number;
  basePrice: number; status: string; totalRooms?: number; imageUrls?: string[];
  createdAt?: string; updatedAt?: string;
}
export interface AdminRoom {
  id: number; hotelId: number; roomTypeId: number; roomTypeCode?: string;
  roomTypeNameVi?: string; roomNumber: string; floor: number; status: string;
  housekeepingStatus: string; maintenanceStatus: string; maxGuests?: number;
  note?: string; createdAt?: string; updatedAt?: string;
}
export interface BulkRoomRequest {
  hotelId: number; roomTypeId: number; floor: number; fromNumber: number;
  toNumber: number; prefix?: string; status?: 'AVAILABLE';
}
export interface BulkRoomResult { created: AdminRoom[]; failedRoomNumbers: string[]; }

@Injectable({ providedIn: 'root' })
export class AdminInventoryService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private api = environment.apiUrl;

  getProperties(): Observable<AdminPropertyOption[]> {
    const isSystemAdministrator = this.auth.getRoles().includes('SUPER_ADMIN');
    const endpoint = isSystemAdministrator ? '/v1/hotels' : '/v1/hotels/accessible';
    return this.http.get<AdminPropertyOption[]>(`${this.api}${endpoint}`);
  }
  getRoomTypes(): Observable<AdminRoomType[]> { return this.http.get<AdminRoomType[]>(`${this.api}/room-types`); }
  createRoomType(value: Partial<AdminRoomType>): Observable<AdminRoomType> { return this.http.post<AdminRoomType>(`${this.api}/room-types`, value); }
  updateRoomType(id: number, value: Partial<AdminRoomType>): Observable<AdminRoomType> { return this.http.put<AdminRoomType>(`${this.api}/room-types/${id}`, value); }
  deleteRoomType(id: number): Observable<void> { return this.http.delete<void>(`${this.api}/room-types/${id}`); }

  getRooms(): Observable<AdminRoom[]> { return this.http.get<AdminRoom[]>(`${this.api}/rooms`); }
  createRoom(value: Partial<AdminRoom>): Observable<AdminRoom> { return this.http.post<AdminRoom>(`${this.api}/rooms`, value); }
  updateRoom(id: number, value: Partial<AdminRoom>): Observable<AdminRoom> { return this.http.put<AdminRoom>(`${this.api}/rooms/${id}`, value); }
  startRoomMaintenance(id: number): Observable<AdminRoom> { return this.http.post<AdminRoom>(`${this.api}/rooms/${id}/maintenance/start`, {}); }
  completeRoomMaintenance(id: number): Observable<AdminRoom> { return this.http.post<AdminRoom>(`${this.api}/rooms/${id}/maintenance/complete`, {}); }
  deleteRoom(id: number): Observable<void> { return this.http.delete<void>(`${this.api}/rooms/${id}`); }
  bulkCreateRooms(value: BulkRoomRequest): Observable<BulkRoomResult> { return this.http.post<BulkRoomResult>(`${this.api}/rooms/bulk`, value); }
}
