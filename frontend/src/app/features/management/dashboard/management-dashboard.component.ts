import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ManagementApiService, ManagementContext } from '../../../core/services/management-api.service';

@Component({
  selector: 'app-management-dashboard', standalone: true, imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './management-dashboard.component.html', styleUrl: './management-dashboard.component.css'
})
export class ManagementDashboardComponent implements OnInit {
  private api = inject(ManagementApiService);
  context?: ManagementContext;
  selectedPropertyId?: number;
  loading = true;
  error = '';

  ngOnInit(): void { this.load(); }
  load(propertyId?: number): void {
    this.loading = true;
    this.api.context(propertyId).subscribe({
      next: context => { this.context = context; this.selectedPropertyId = context.activePropertyId; this.loading = false; },
      error: error => { this.error = error?.error?.message || 'Không thể tải tổng quan.'; this.loading = false; }
    });
  }
  selectProperty(): void { this.load(this.selectedPropertyId); }
  value(name: string): number { return this.context?.dashboard?.[name] || 0; }
  limit(name: string): string { const value = this.context?.limits?.[name]; return value === -1 ? 'Không giới hạn' : String(value ?? 0); }
}
