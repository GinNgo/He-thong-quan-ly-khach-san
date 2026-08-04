import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ManagedProperty, ManagementApiService, ManagementContext } from '../../../core/services/management-api.service';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';

@Component({
  selector: 'app-management-dashboard', standalone: true, imports: [CommonModule, FormsModule, RouterLink, FeedbackStateComponent],
  templateUrl: './management-dashboard.component.html', styleUrl: './management-dashboard.component.css'
})
export class ManagementDashboardComponent implements OnInit {
  private api = inject(ManagementApiService);
  private cdr = inject(ChangeDetectorRef);
  context?: ManagementContext;
  selectedPropertyId?: number;
  loading = true;
  error = '';

  ngOnInit(): void { this.load(); }
  load(propertyId?: number): void {
    this.loading = true;
    this.error = '';
    this.api.context(propertyId).subscribe({
      next: context => { this.context = context; this.selectedPropertyId = context.activePropertyId; this.loading = false; this.cdr.markForCheck(); },
      error: error => { this.error = error?.error?.message || 'Không thể tải tổng quan.'; this.loading = false; this.cdr.markForCheck(); }
    });
  }
  selectProperty(): void { this.load(this.selectedPropertyId); }
  get activeProperty(): ManagedProperty | undefined { return this.context?.properties.find(property => property.id === this.selectedPropertyId); }
  get activePropertyOperational(): boolean { return this.context?.activePropertyOperational ?? this.activeProperty?.operational ?? false; }
  get activePropertySuspended(): boolean {
    return this.normalizedPropertyState() === 'SUSPENDED';
  }
  get activePropertyClosed(): boolean {
    return this.normalizedPropertyState() === 'CLOSED';
  }
  operationGateTitle(): string {
    if (this.activePropertySuspended) return 'Cơ sở đang tạm ngừng hoạt động';
    if (this.activePropertyClosed) return 'Cơ sở đã đóng';
    return 'Chưa thể vận hành cơ sở này';
  }
  operationGateMessage(): string {
    if (this.activePropertySuspended) {
      return 'Cơ sở không hiển thị công khai và các thao tác vận hành đang bị khóa. Dữ liệu cùng đặt phòng lịch sử vẫn được giữ nguyên để xem và đối soát.';
    }
    if (this.activePropertyClosed) {
      return 'Cơ sở không còn hiển thị công khai hoặc nhận thao tác vận hành. Dữ liệu và đặt phòng lịch sử vẫn được giữ ở chế độ chỉ đọc.';
    }
    return 'Hãy hoàn thiện hồ sơ hoặc chờ quản trị viên phê duyệt. Bạn vẫn có thể xem và mua gói dịch vụ.';
  }
  showPropertySetupLink(): boolean {
    return !this.activePropertySuspended && !this.activePropertyClosed;
  }
  value(name: string): number { return this.context?.dashboard?.[name] || 0; }
  limit(name: string): string { const value = this.context?.limits?.[name]; return value === -1 ? 'Không giới hạn' : String(value ?? 0); }

  private normalizedPropertyState(): string {
    const property = this.activeProperty;
    return String(property?.operationStatus || property?.status || '').trim().toUpperCase();
  }
}
