import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ManagedProperty, ManagementApiService, ManagementContext, ManagementLocation } from '../../../core/services/management-api.service';
import { PropertyProfile } from '../../../core/models/property-profile.model';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';
import { PropertyGalleryComponent } from '../../../shared/components/property-gallery/property-gallery.component';

@Component({
  selector: 'app-management-dashboard', standalone: true, imports: [CommonModule, FormsModule, RouterLink, FeedbackStateComponent, PropertyGalleryComponent],
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
  profileDraft: PropertyProfile & { reason: string } = this.emptyProfile();
  profileProvinces: ManagementLocation[] = [];
  profileWards: ManagementLocation[] = [];

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
    this.profileDraft = {
      ...property,
      nameVi: property.nameVi || '',
      propertyType: property.propertyType || 'HOTEL',
      addressLine: property.addressLine || '',
      provinceId: property.provinceId,
      wardId: property.wardId,
      reason: ''
    };
    this.loadProfileLocations(property.provinceId, property.wardId);
    this.profileError = '';
    this.profileEditing = true;
  }
  cancelProfileEditor(): void { if (!this.profileSaving) this.profileEditing = false; }
  onProfileProvinceChange(): void {
    this.profileDraft.wardId = 0;
    this.loadProfileWards(this.profileDraft.provinceId);
  }
  saveProfile(): void {
    const property = this.activeProperty;
    if (!property || this.profileSaving) return;
    const profile = this.toProfile(this.profileDraft);
    const reason = this.profileDraft.reason.trim();
    if (!profile.nameVi || !profile.addressLine || !profile.provinceId || !profile.wardId || reason.length < 3) {
      this.profileError = 'Vui lòng nhập đủ tên, địa chỉ, vị trí và lý do cập nhật.';
      return;
    }
    if ((profile.latitude == null) !== (profile.longitude == null)) {
      this.profileError = 'Vĩ độ và kinh độ phải được nhập cùng nhau.';
      return;
    }
    if (profile.minPrice != null && profile.maxPrice != null && profile.minPrice > profile.maxPrice) {
      this.profileError = 'Giá tối thiểu không được lớn hơn giá tối đa.';
      return;
    }
    this.profileSaving = true;
    this.profileError = '';
    this.api.updateProperty(property.id!, { profile, reason }).subscribe({
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

  private emptyProfile(): PropertyProfile & { reason: string } {
    return { nameVi: '', propertyType: 'HOTEL', addressLine: '', provinceId: 0, wardId: 0, reason: '' };
  }

  private loadProfileLocations(provinceId: number, wardId: number): void {
    this.api.provinces().subscribe({
      next: provinces => { this.profileProvinces = provinces; this.cdr.markForCheck(); },
      error: () => { this.profileError = 'Không thể tải danh sách tỉnh/thành phố.'; this.cdr.markForCheck(); }
    });
    this.loadProfileWards(provinceId, wardId);
  }

  private loadProfileWards(provinceId: number, wardId?: number): void {
    this.profileWards = [];
    if (!provinceId) return;
    this.api.wards(provinceId).subscribe({
      next: wards => {
        this.profileWards = wards;
        if (wardId && wards.some(item => item.id === wardId)) this.profileDraft.wardId = wardId;
        this.cdr.markForCheck();
      },
      error: () => { this.profileError = 'Không thể tải danh sách phường/xã.'; this.cdr.markForCheck(); }
    });
  }

  private toProfile(value: PropertyProfile & { reason: string }): PropertyProfile {
    const clean = (field?: string) => field?.trim() || undefined;
    return {
      nameVi: value.nameVi.trim(),
      nameEn: clean(value.nameEn),
      propertyType: value.propertyType,
      addressLine: value.addressLine.trim(),
      provinceId: value.provinceId,
      wardId: value.wardId,
      latitude: value.latitude,
      longitude: value.longitude,
      descriptionVi: clean(value.descriptionVi),
      descriptionEn: clean(value.descriptionEn),
      starRating: value.starRating,
      phone: clean(value.phone),
      email: clean(value.email),
      website: clean(value.website),
      checkinTime: clean(value.checkinTime),
      checkoutTime: clean(value.checkoutTime),
      minPrice: value.minPrice,
      maxPrice: value.maxPrice,
      mainImage: clean(value.mainImage)
    };
  }
}
