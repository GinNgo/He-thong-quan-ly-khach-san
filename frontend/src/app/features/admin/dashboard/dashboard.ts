import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

import { AnalyticsData, AnalyticsService } from '../../../core/services/analytics';
import { DataTable, ColumnDefinition } from '../../../shared/components/data-table/data-table';
import { OccupancyChart } from '../../../shared/components/charts/occupancy-chart/occupancy-chart';
import { RevenueChart } from '../../../shared/components/charts/revenue-chart/revenue-chart';
import { StatCard } from '../../../shared/components/stat-card/stat-card';
import { FilterRequest, PageRequest, SortRequest } from '../../../shared/models/pagination.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, StatCard, RevenueChart, OccupancyChart, DataTable],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard implements OnInit {
  data: AnalyticsData | null = null;
  loadingDashboard = false;
  dashboardError = '';
  revenueChartData: any;
  occupancyChartData: any;
  chartOptions: any;

  // T331 replaces this intentionally isolated legacy work-order placeholder.
  workOrderColumns: ColumnDefinition[] = [
    { field: 'priority', header: 'Uu tien', sortable: true, type: 'badge' },
    { field: 'roomNumber', header: 'So phong', sortable: true },
    { field: 'issue', header: 'Su co bao cao' },
    { field: 'reporter', header: 'Nguoi bao cao' },
    { field: 'createdAt', header: 'Ngay tao', sortable: true },
    { field: 'status', header: 'Trang thai', type: 'badge' }
  ];
  workOrders: any[] = [];
  totalWorkOrders = 0;
  loadingWorkOrders = false;

  constructor(
    private readonly analyticsService: AnalyticsService,
    private readonly cdr: ChangeDetectorRef,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.loadDashboard();
    const documentStyle = getComputedStyle(document.documentElement);
    const textColor = documentStyle.getPropertyValue('--hotel-text');
    const textColorSecondary = documentStyle.getPropertyValue('--hotel-text-muted');
    const surfaceBorder = documentStyle.getPropertyValue('--hotel-border');
    this.chartOptions = {
      maintainAspectRatio: false,
      aspectRatio: 0.8,
      plugins: { legend: { labels: { color: textColor } } },
      scales: {
        x: { ticks: { color: textColorSecondary, font: { weight: 500 } },
          grid: { color: surfaceBorder, drawBorder: false } },
        y: { ticks: { color: textColorSecondary }, grid: { color: surfaceBorder, drawBorder: false } }
      }
    };
  }

  loadDashboard(): void {
    if (this.loadingDashboard) return;
    this.loadingDashboard = true;
    this.dashboardError = '';
    this.analyticsService.getDashboardData().subscribe({
      next: result => {
        this.data = result;
        this.loadingDashboard = false;
        this.initCharts();
        this.cdr.detectChanges();
      },
      error: () => {
        this.data = null;
        this.loadingDashboard = false;
        this.dashboardError = 'Khong the tai du lieu van hanh. Hay thu lai.';
        this.cdr.detectChanges();
      }
    });
  }

  initCharts(): void {
    if (!this.data) return;
    const documentStyle = getComputedStyle(document.documentElement);
    this.revenueChartData = {
      labels: this.data.labels,
      datasets: [{
        label: 'Doanh thu thuan Platform Billing (VND)',
        data: this.data.revenueData,
        fill: true,
        borderColor: documentStyle.getPropertyValue('--hotel-primary'),
        tension: 0.4,
        backgroundColor: 'rgba(37, 99, 235, 0.2)'
      }]
    };
    this.occupancyChartData = {
      labels: this.data.labels,
      datasets: [{
        label: 'Cong suat thuc te (%)',
        data: this.data.occupancyData,
        fill: false,
        borderColor: documentStyle.getPropertyValue('--hotel-success'),
        tension: 0.4
      }]
    };
  }

  formatVnd(value: number): string {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency', currency: 'VND', maximumFractionDigits: 0
    }).format(value || 0);
  }

  dashboardIsEmpty(): boolean {
    return !!this.data && this.data.totalRevenue === 0
      && this.data.totalBookings === 0 && this.data.totalRooms === 0;
  }

  navigateTo(route: string): void {
    void this.router.navigate([route]);
  }

  loadWorkOrders(): void {
    if (this.loadingWorkOrders) return;
    this.loadingWorkOrders = true;
    setTimeout(() => {
      this.workOrders = [];
      this.totalWorkOrders = 0;
      this.loadingWorkOrders = false;
      this.cdr.detectChanges();
    }, 500);
  }

  onPageChange(_event: PageRequest): void { this.loadWorkOrders(); }
  onSortChange(_event: SortRequest): void { this.loadWorkOrders(); }
  onFilterChange(_event: FilterRequest): void { this.loadWorkOrders(); }
}
