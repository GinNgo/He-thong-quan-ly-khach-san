import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface ManagedProperty { id: number; code: string; nameVi: string; nameEn?: string; propertyType: string; address: string; descriptionVi?: string; descriptionEn?: string; approvalStatus: string; operationStatus: string; operational?: boolean; mainImage?: string; isDemo: boolean; }
export interface ManagedPropertyUpdate { nameVi?: string; nameEn?: string; propertyType?: string; addressLine?: string; descriptionVi?: string; descriptionEn?: string; starRating?: number; phone?: string; email?: string; website?: string; mainImage?: string; reason: string; }
export interface ManagedPropertyProfile { id: number; code?: string; nameVi?: string; nameEn?: string; propertyType?: string; addressLine?: string; approvalStatus: string; operationStatus: string; status: string; }
export interface ManagementUsage { properties?: number; roomTypes?: number; rooms?: number; staff?: number; images?: number; }
export interface ManagementContext { properties: ManagedProperty[]; activePropertyId?: number; activePropertyOperational?: boolean; planCode: string; subscriptionStatus: string; subscriptionSource?: string; endAt?: string; lifetime: boolean; limits: Record<string, number>; usage: ManagementUsage; upgradeRequired: boolean; dashboard?: Record<string, number>; }

@Injectable({ providedIn: 'root' })
export class ManagementApiService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/management`;

  context(activePropertyId?: number) {
    return this.http.get<ManagementContext>(`${this.baseUrl}/context`, { params: activePropertyId ? { activePropertyId } : {} });
  }
  updateProperty(propertyId: number, body: ManagedPropertyUpdate) {
    return this.http.put<ManagedPropertyProfile>(`${this.baseUrl}/properties/${propertyId}`, body);
  }
  roomTypes(propertyId: number) { return this.http.get<any[]>(`${this.baseUrl}/room-types`, { params: { propertyId } }); }
  rooms(propertyId: number) { return this.http.get<any[]>(`${this.baseUrl}/rooms`, { params: { propertyId } }); }
  createRoomType(body: any) { return this.http.post<any>(`${this.baseUrl}/room-types`, body); }
  createRoom(body: any) { return this.http.post<any>(`${this.baseUrl}/rooms`, body); }
  bulkRooms(body: any) { return this.http.post<any[]>(`${this.baseUrl}/rooms/bulk`, body); }
  startRoomMaintenance(roomId: number) { return this.http.post<any>(`${this.baseUrl}/rooms/${roomId}/maintenance/start`, {}); }
  completeRoomMaintenance(roomId: number) { return this.http.post<any>(`${this.baseUrl}/rooms/${roomId}/maintenance/complete`, {}); }
}
