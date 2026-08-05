import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AnalyticsData {
  totalRevenue: number;
  totalBookings: number;
  occupancyRate: number;
  totalRooms: number;
  occupiedRooms: number;
  operationalProperties: number;
  scope: 'SYSTEM_NON_DEMO';
  revenueBasis: 'PLATFORM_BILLING_NET';
  occupancyBasis: string;
  reconciliationStatus: 'RECONCILED' | 'UNRECONCILED';
  sourceWatermark: string;
  generatedAt: string;
  periodFrom: string;
  periodTo: string;
  labels: string[];
  revenueData: number[];
  occupancyData: number[];
}

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {
  private apiUrl = `${environment.apiUrl}/analytics`;

  constructor(private http: HttpClient) {}

  getDashboardData(): Observable<AnalyticsData> {
    return this.http.get<AnalyticsData>(`${this.apiUrl}/dashboard`);
  }
}
