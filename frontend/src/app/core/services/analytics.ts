import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AnalyticsData {
  totalRevenue: number;
  totalBookings: number;
  occupancyRate: number;
  labels: string[];
  revenueData: number[];
  occupancyData: number[];
  aiPredictedOccupancy: number[];
}

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {
  private apiUrl = 'http://localhost:8080/api/analytics';

  constructor(private http: HttpClient) {}

  getDashboardData(): Observable<AnalyticsData> {
    return this.http.get<AnalyticsData>(`${this.apiUrl}/dashboard`);
  }
}
