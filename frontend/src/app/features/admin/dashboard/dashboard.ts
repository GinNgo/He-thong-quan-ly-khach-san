import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AnalyticsService, AnalyticsData } from '../../../core/services/analytics';
import { StatCard } from '../../../shared/components/stat-card/stat-card';
import { RevenueChart } from '../../../shared/components/charts/revenue-chart/revenue-chart';
import { OccupancyChart } from '../../../shared/components/charts/occupancy-chart/occupancy-chart';
import { DataTable, ColumnDefinition } from '../../../shared/components/data-table/data-table';
import { PageRequest, SortRequest, FilterRequest } from '../../../shared/models/pagination.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, StatCard, RevenueChart, OccupancyChart, DataTable],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard implements OnInit {
  data: AnalyticsData | null = null;
  revenueChartData: any;
  occupancyChartData: any;
  chartOptions: any;

  // Work Orders Table Data
  workOrderColumns: ColumnDefinition[] = [
    { field: 'priority', header: 'Ưu tiên', sortable: true, type: 'badge' },
    { field: 'roomNumber', header: 'Số phòng', sortable: true },
    { field: 'issue', header: 'Sự cố báo cáo' },
    { field: 'reporter', header: 'Người báo cáo' },
    { field: 'createdAt', header: 'Ngày tạo', sortable: true },
    { field: 'status', header: 'Trạng thái', type: 'badge' }
  ];
  
  workOrders: any[] = [];
  totalWorkOrders = 0;
  loadingWorkOrders = false;

  constructor(private analyticsService: AnalyticsService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.analyticsService.getDashboardData().subscribe((res) => {
      this.data = res;
      this.initCharts();
    });

    const documentStyle = getComputedStyle(document.documentElement);
    const textColor = documentStyle.getPropertyValue('--hotel-text');
    const textColorSecondary = documentStyle.getPropertyValue('--hotel-text-muted');
    const surfaceBorder = documentStyle.getPropertyValue('--hotel-border');

    this.chartOptions = {
      maintainAspectRatio: false,
      aspectRatio: 0.8,
      plugins: {
        legend: {
          labels: {
            color: textColor
          }
        }
      },
      scales: {
        x: {
          ticks: {
            color: textColorSecondary,
            font: {
              weight: 500
            }
          },
          grid: {
            color: surfaceBorder,
            drawBorder: false
          }
        },
        y: {
          ticks: {
            color: textColorSecondary
          },
          grid: {
            color: surfaceBorder,
            drawBorder: false
          }
        }
      }
    };
  }

  initCharts() {
    if (!this.data) return;

    const documentStyle = getComputedStyle(document.documentElement);

    this.revenueChartData = {
      labels: this.data.labels,
      datasets: [
        {
          label: 'Doanh thu (VNĐ)',
          data: this.data.revenueData,
          fill: true,
          borderColor: documentStyle.getPropertyValue('--hotel-primary'),
          tension: 0.4,
          backgroundColor: 'rgba(37, 99, 235, 0.2)'
        }
      ]
    };

    this.occupancyChartData = {
      labels: this.data.labels,
      datasets: [
        {
          label: 'Thực tế (%)',
          data: this.data.occupancyData,
          fill: false,
          borderColor: documentStyle.getPropertyValue('--hotel-success'),
          tension: 0.4
        },
        {
          label: 'AI Dự báo (%)',
          data: this.data.aiPredictedOccupancy,
          fill: false,
          borderDash: [5, 5],
          borderColor: documentStyle.getPropertyValue('--hotel-gold'),
          tension: 0.4
        }
      ]
    };
  }

  loadWorkOrders() {
    if (this.loadingWorkOrders) return;
    this.loadingWorkOrders = true;
    
    // Simulate API call returning empty data to demonstrate Empty State
    setTimeout(() => {
      this.workOrders = [];
      this.totalWorkOrders = 0;
      this.loadingWorkOrders = false;
      this.cdr.detectChanges();
    }, 500);
  }

  onPageChange(event: PageRequest) {
    this.loadWorkOrders();
  }

  onSortChange(event: SortRequest) {
    this.loadWorkOrders();
  }

  onFilterChange(event: FilterRequest) {
    this.loadWorkOrders();
  }
}
