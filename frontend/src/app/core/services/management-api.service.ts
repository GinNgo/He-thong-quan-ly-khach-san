import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface ManagedProperty { id: number; code: string; nameVi: string; nameEn?: string; propertyType: string; address: string; provinceId?: number; wardId?: number; approvalStatus: string; operationStatus: string; operational?: boolean; mainImage?: string; phone?: string; email?: string; website?: string; descriptionVi?: string; descriptionEn?: string; checkinTime?: string; checkoutTime?: string; starRating?: number; isDemo: boolean; }
export interface ManagementUsage { properties?: number; roomTypes?: number; rooms?: number; staff?: number; images?: number; }
export interface ManagementDashboardMetrics { totalRooms: number; availableRooms: number; reservedRooms: number; occupiedRooms: number; dirtyRooms: number; maintenanceRooms: number; unclassifiedRooms: number; pendingHousekeeping: number; classifiedRooms: number; reconciliationStatus: 'RECONCILED' | 'MISMATCH'; countBasis: string; }
export interface ManagementContext { properties: ManagedProperty[]; activePropertyId?: number; activePropertyOperational?: boolean; planCode: string; subscriptionStatus: string; subscriptionSource?: string; entitlementAuthoritative?: boolean; entitlementReference?: string; endAt?: string; lifetime: boolean; limits: Record<string, number>; usage: ManagementUsage; usageScope?: Record<keyof ManagementUsage, 'OWNER_ACCOUNT' | 'SELECTED_PROPERTY'>; scope?: 'SELECTED_PROPERTY'; generatedAt?: string; sourceWatermark?: string; upgradeRequired: boolean; dashboard?: ManagementDashboardMetrics; }

@Injectable({ providedIn: 'root' })
export class ManagementApiService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/management`;

  context(activePropertyId?: number) {
    return this.http.get<ManagementContext>(`${this.baseUrl}/context`, { params: activePropertyId ? { activePropertyId } : {} });
  }
  properties() { return this.http.get<ManagedProperty[]>(`${this.baseUrl}/properties`); }
  updateProperty(id: number, body: Record<string, unknown>) { return this.http.put<ManagedProperty>(`${this.baseUrl}/properties/${id}`, body); }
  roomTypes(propertyId: number) { return this.http.get<any[]>(`${this.baseUrl}/room-types`, { params: { propertyId } }); }
  rooms(propertyId: number) { return this.http.get<any[]>(`${this.baseUrl}/rooms`, { params: { propertyId } }); }
  createRoomType(body: any) { return this.http.post<any>(`${this.baseUrl}/room-types`, body); }
  createRoom(body: any) { return this.http.post<any>(`${this.baseUrl}/rooms`, body); }
  bulkRooms(body: any) { return this.http.post<any[]>(`${this.baseUrl}/rooms/bulk`, body); }
  startRoomMaintenance(roomId: number) { return this.http.post<any>(`${this.baseUrl}/rooms/${roomId}/maintenance/start`, {}); }
  completeRoomMaintenance(roomId: number) { return this.http.post<any>(`${this.baseUrl}/rooms/${roomId}/maintenance/complete`, {}); }
}
