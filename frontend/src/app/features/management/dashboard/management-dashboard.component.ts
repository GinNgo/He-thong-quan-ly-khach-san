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
  profileEditing = false;
  profileSaving = false;
  profileError = '';
  profileDraft = { nameVi: '', addressLine: '', reason: '' };

  ngOnInit(): void { this.load(); }
  load(propertyId?: number): void {
    this.loading = true;
    this.error = '';
    this.profileEditing = false;
    this.profileError = '';
    this.api.context(propertyId).subscribe({
      next: context => { this.context = context; this.selectedPropertyId = context.activePropertyId; this.loading = false; this.cdr.markForCheck(); },
      error: error => { this.error = error?.error?.message || 'Không thể tải tổng quan.'; this.loading = false; this.cdr.markForCheck(); }
    });
  }
  selectProperty(): void { this.load(this.selectedPropertyId); }
  get activeProperty(): ManagedProperty | undefined { return this.context?.properties.find(property => property.id === this.selectedPropertyId); }
  get activePropertyOperational(): boolean { return this.context?.activePropertyOperational ?? this.activeProperty?.operational ?? false; }
  get canEditProfile(): boolean {
    const property = this.activeProperty;
    if (!property || property.operationStatus === 'CLOSED') return false;
    return property.approvalStatus === 'DRAFT'
      || property.approvalStatus === 'REJECTED'
      || (property.approvalStatus === 'APPROVED' && property.operationStatus === 'ACTIVE');
  }
  openProfileEditor(): void {
    const property = this.activeProperty;
    if (!property || !this.canEditProfile) return;
    this.profileDraft = { nameVi: property.nameVi || '', addressLine: property.address || '', reason: '' };
    this.profileError = '';
    this.profileEditing = true;
  }
  cancelProfileEditor(): void { if (!this.profileSaving) this.profileEditing = false; }
  saveProfile(): void {
    const property = this.activeProperty;
    if (!property || this.profileSaving) return;
    const nameVi = this.profileDraft.nameVi.trim();
    const addressLine = this.profileDraft.addressLine.trim();
    const reason = this.profileDraft.reason.trim();
    if (!nameVi || !addressLine || reason.length < 3) {
      this.profileError = 'Vui lòng nhập tên, địa chỉ và lý do cập nhật.';
      return;
    }
    this.profileSaving = true;
    this.profileError = '';
    this.api.updateProperty(property.id, { nameVi, addressLine, reason }).subscribe({
      next: () => { this.profileSaving = false; this.profileEditing = false; this.load(property.id); },
      error: error => {
        this.profileSaving = false;
        this.profileError = error?.error?.message || 'Không thể cập nhật hồ sơ cơ sở.';
        this.cdr.markForCheck();
      }
    });
  }
  value(name: string): number { return this.context?.dashboard?.[name] || 0; }
  limit(name: string): string { const value = this.context?.limits?.[name]; return value === -1 ? 'Không giới hạn' : String(value ?? 0); }
}
