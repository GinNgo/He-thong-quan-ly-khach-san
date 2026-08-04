import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { PropertyProfile, PropertyProfileUpdateRequest } from '../models/property-profile.model';

export type ManagedProperty = PropertyProfile;
export interface ManagementLocation { id: number; nameVi: string; locationType: 'PROVINCE' | 'WARD'; }
export interface ManagementUsage { properties?: number; roomTypes?: number; rooms?: number; staff?: number; images?: number; }
export interface ManagementContext { properties: ManagedProperty[]; activePropertyId?: number; activePropertyOperational?: boolean; planCode: string; subscriptionStatus: string; subscriptionSource?: string; endAt?: string; lifetime: boolean; limits: Record<string, number>; usage: ManagementUsage; upgradeRequired: boolean; dashboard?: Record<string, number>; }

@Injectable({ providedIn: 'root' })
export class ManagementApiService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/management`;

  context(activePropertyId?: number) {
    return this.http.get<ManagementContext>(`${this.baseUrl}/context`, { params: activePropertyId ? { activePropertyId } : {} });
  }
  provinces() { return this.http.get<ManagementLocation[]>(`${environment.apiUrl}/public/locations/provinces`); }
  wards(provinceId: number) { return this.http.get<ManagementLocation[]>(`${environment.apiUrl}/public/locations/provinces/${provinceId}/wards`); }
  property(propertyId: number) {
    return this.http.get<PropertyProfile>(`${this.baseUrl}/properties/${propertyId}`);
  }
  createProperty(profile: PropertyProfile) {
    return this.http.post<PropertyProfile>(`${this.baseUrl}/properties`, profile);
  }
  updateProperty(propertyId: number, body: PropertyProfileUpdateRequest) {
    return this.http.put<PropertyProfile>(`${this.baseUrl}/properties/${propertyId}`, body);
  }
  roomTypes(propertyId: number) { return this.http.get<any[]>(`${this.baseUrl}/room-types`, { params: { propertyId } }); }
  rooms(propertyId: number) { return this.http.get<any[]>(`${this.baseUrl}/rooms`, { params: { propertyId } }); }
  createRoomType(body: any) { return this.http.post<any>(`${this.baseUrl}/room-types`, body); }
  updateRoomType(id: number, body: any) { return this.http.put<any>(`${this.baseUrl}/room-types/${id}`, body); }
  deleteRoomType(id: number) { return this.http.delete<void>(`${this.baseUrl}/room-types/${id}`); }
  createRoom(body: any) { return this.http.post<any>(`${this.baseUrl}/rooms`, body); }
  bulkRooms(body: any) { return this.http.post<any[]>(`${this.baseUrl}/rooms/bulk`, body); }
  startRoomMaintenance(roomId: number) { return this.http.post<any>(`${this.baseUrl}/rooms/${roomId}/maintenance/start`, {}); }
  completeRoomMaintenance(roomId: number) { return this.http.post<any>(`${this.baseUrl}/rooms/${roomId}/maintenance/complete`, {}); }
}
