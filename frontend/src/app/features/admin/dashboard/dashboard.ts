import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AnalyticsService, AnalyticsData } from '../../../core/services/analytics';
import { ChartModule } from 'primeng/chart';
import { CardModule } from 'primeng/card';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, ChartModule, CardModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard implements OnInit {
  data: AnalyticsData | null = null;
  revenueChartData: any;
  occupancyChartData: any;
  chartOptions: any;

  constructor(private analyticsService: AnalyticsService) {}

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
}
