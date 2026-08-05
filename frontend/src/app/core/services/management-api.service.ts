import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PropertyProfile, PropertyProfileUpdateRequest } from '../models/property-profile.model';

export interface ManagedProperty extends PropertyProfile {
  id: number;
  code: string;
  approvalStatus: string;
  operationStatus: string;
  address?: string;
  lifecycleAction?: 'SUSPEND' | 'REACTIVATE' | 'CLOSE' | null;
  lifecycleReason?: string | null;
  lifecycleChangedAt?: string | null;
  isDemo: boolean;
}
export interface ManagementLocation { id: number; nameVi: string; locationType: 'PROVINCE' | 'WARD'; }
export interface ManagementUsage { properties?: number; roomTypes?: number; rooms?: number; staff?: number; images?: number; }
export interface ManagementDashboardMetrics { totalRooms: number; availableRooms: number; reservedRooms: number; occupiedRooms: number; dirtyRooms: number; maintenanceRooms: number; unclassifiedRooms: number; pendingHousekeeping: number; classifiedRooms: number; statusCountTotal: number; reconciled: boolean; reconciliationStatus: 'RECONCILED' | 'MISMATCH'; countBasis: string; }
export interface ManagementContext { properties: ManagedProperty[]; activePropertyId?: number; activePropertyOperational?: boolean; planCode: string; subscriptionStatus: string; subscriptionSource?: string; entitlementAuthoritative?: boolean; entitlementReference?: string; endAt?: string; lifetime: boolean; limits: Record<string, number>; usage: ManagementUsage; upgradeRequired: boolean; generatedAt?: string; dataStatus?: 'COMPLETE' | 'DEGRADED'; errors?: string[]; usageScope?: Partial<Record<keyof ManagementUsage, 'OWNER_ACCOUNT' | 'SELECTED_PROPERTY'>> | 'PROPERTY' | 'NONE'; scope?: 'SELECTED_PROPERTY' | 'NONE'; sourceWatermark?: string; dashboard?: Partial<ManagementDashboardMetrics>; }
export type OperationalExportDataset = 'RESERVATIONS' | 'CUSTOMERS' | 'ROOMS' | 'HOUSEKEEPING';
export interface OperationalExportDownload { blob: Blob; filename: string; checksum: string; rowCount: number; schema: string; }

@Injectable({ providedIn: 'root' })
export class ManagementApiService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/management`;

  context(activePropertyId?: number) {
    return this.http.get<ManagementContext>(`${this.baseUrl}/context`, { params: activePropertyId ? { activePropertyId } : {} });
  }
  properties() { return this.http.get<ManagedProperty[]>(`${this.baseUrl}/properties`); }
  provinces() { return this.http.get<ManagementLocation[]>(`${environment.apiUrl}/public/locations/provinces`); }
  wards(provinceId: number) { return this.http.get<ManagementLocation[]>(`${environment.apiUrl}/public/locations/provinces/${provinceId}/wards`); }
  property(propertyId: number) {
    return this.http.get<PropertyProfile>(`${this.baseUrl}/properties/${propertyId}`);
  }
  createProperty(profile: PropertyProfile) {
    return this.http.post<PropertyProfile>(`${this.baseUrl}/properties`, profile);
  }
  updateProperty(propertyId: number, body: PropertyProfileUpdateRequest) {
    return this.http.put<ManagedProperty>(`${this.baseUrl}/properties/${propertyId}`, body);
  }
  roomTypes(propertyId: number) { return this.http.get<any[]>(`${this.baseUrl}/room-types`, { params: { propertyId } }); }
  rooms(propertyId: number) { return this.http.get<any[]>(`${this.baseUrl}/rooms`, { params: { propertyId } }); }
  createRoomType(body: any) { return this.http.post<any>(`${this.baseUrl}/room-types`, body); }
  updateRoomType(id: number, body: any) { return this.http.put<any>(`${this.baseUrl}/room-types/${id}`, body); }
  deleteRoomType(id: number) { return this.http.delete<void>(`${this.baseUrl}/room-types/${id}`); }
  createRoom(body: any) { return this.http.post<any>(`${this.baseUrl}/rooms`, body); }
  updateRoom(id: number, body: any) { return this.http.put<any>(`${this.baseUrl}/rooms/${id}`, body); }
  deleteRoom(id: number) { return this.http.delete<void>(`${this.baseUrl}/rooms/${id}`); }
  bulkRooms(body: any) { return this.http.post<{ created: any[]; failedRoomNumbers: string[] }>(`${this.baseUrl}/rooms/bulk`, body); }
  startRoomMaintenance(roomId: number) { return this.http.post<any>(`${this.baseUrl}/rooms/${roomId}/maintenance/start`, {}); }
  completeRoomMaintenance(roomId: number) { return this.http.post<any>(`${this.baseUrl}/rooms/${roomId}/maintenance/complete`, {}); }
  operationalExport(propertyId: number, dataset: OperationalExportDataset, filters: { status?: string; from?: string; to?: string } = {}) {
    return this.http.get(`${this.baseUrl}/operational-exports`, {
      params: { propertyId, dataset, ...(filters.status ? { status: filters.status } : {}), ...(filters.from ? { from: filters.from } : {}), ...(filters.to ? { to: filters.to } : {}) },
      responseType: 'blob', observe: 'response',
    }).pipe(map(response => ({
      blob: response.body ?? new Blob(),
      filename: response.headers.get('Content-Disposition')?.match(/filename="?([^";]+)"?/i)?.[1] || `${dataset.toLowerCase()}.csv`,
      checksum: response.headers.get('X-Export-Checksum') || '',
      rowCount: Number(response.headers.get('X-Export-Row-Count') || 0),
      schema: response.headers.get('X-Export-Schema') || '',
    }) as OperationalExportDownload));
  }
}
