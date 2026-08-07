import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ManagedProperty, ManagementApiService, ManagementContext, ManagementLocation } from '../../../core/services/management-api.service';
import { PropertyProfile } from '../../../core/models/property-profile.model';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';
import { PropertyGalleryComponent } from '../../../shared/components/property-gallery/property-gallery.component';
import { AmenityAssignmentComponent } from '../../../shared/components/amenity-assignment/amenity-assignment.component';
import { OperationalPolicyEditorComponent } from '../../../shared/components/operational-policy-editor/operational-policy-editor.component';
import { ActionCode, FunctionCode, PermissionService } from '../../../core/services/permission.service';
import { ManagementPropertyContextService } from '../../../core/services/management-property-context.service';

@Component({
  selector: 'app-management-dashboard', standalone: true, imports: [CommonModule, FormsModule, RouterLink, FeedbackStateComponent, PropertyGalleryComponent, AmenityAssignmentComponent, OperationalPolicyEditorComponent],
  templateUrl: './management-dashboard.component.html', styleUrl: './management-dashboard.component.css'
})
export class ManagementDashboardComponent implements OnInit {
  private api = inject(ManagementApiService);
  private cdr = inject(ChangeDetectorRef);
  private permissions = inject(PermissionService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private propertyContext = inject(ManagementPropertyContextService);
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
  private loadRequestId = 0;

  ngOnInit(): void {
    this.propertyContext.propertyId$.subscribe(propertyId => this.load(propertyId));
    const propertyId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    if (Number.isInteger(propertyId) && propertyId > 0) this.propertyContext.select(propertyId);
  }
  load(propertyId?: number): void {
    const requestId = ++this.loadRequestId;
    this.loading = true;
    this.error = '';
    this.profileEditing = false;
    this.profileError = '';
    this.api.context(propertyId).subscribe({
      next: context => {
        if (requestId !== this.loadRequestId) return;
        this.context = context; this.selectedPropertyId = context.activePropertyId; this.loading = false; this.cdr.markForCheck();
      },
      error: error => {
        if (requestId !== this.loadRequestId) return;
        this.error = error?.error?.message || 'Không thể tải tổng quan.'; this.loading = false; this.cdr.markForCheck();
      }
    });
  }
  selectProperty(): void {
    if (!this.selectedPropertyId) return;
    this.propertyContext.select(this.selectedPropertyId);
    void this.router.navigate([], { queryParams: { propertyId: this.selectedPropertyId }, queryParamsHandling: 'merge' });
  }
  get activeProperty(): ManagedProperty | undefined { return this.context?.properties.find(property => property.id === this.selectedPropertyId); }
<<<<<<< HEAD
  get activePropertyOperational(): boolean { return this.context?.activePropertyOperational ?? this.activeProperty?.operational ?? false; }
  get activePropertySuspended(): boolean { return this.normalizedPropertyState() === 'SUSPENDED'; }
  get activePropertyClosed(): boolean { return this.normalizedPropertyState() === 'CLOSED'; }
  operationGateTitle(): string {
    if (this.activePropertySuspended) return 'Cơ sở đang tạm ngừng hoạt động';
    if (this.activePropertyClosed) return 'Cơ sở đã đóng';
    return 'Chưa thể vận hành cơ sở này';
  }
  operationGateMessage(): string {
    if (this.activePropertySuspended) {
      return 'Các thao tác vận hành và hiển thị công khai đang bị khóa cho tới khi quản trị viên kích hoạt lại.';
    }
    if (this.activePropertyClosed) {
      return 'Cơ sở được giữ lại để tra cứu dữ liệu lịch sử; không thể mở lại bằng thao tác thiết lập thông thường.';
    }
    return 'Hoàn thiện hồ sơ, phê duyệt và gói dịch vụ trước khi sử dụng các chức năng vận hành.';
  }
  showPropertySetupLink(): boolean { return !this.activePropertySuspended && !this.activePropertyClosed; }
  get canEditProfile(): boolean {
    const property = this.activeProperty;
    if (!this.permissions.hasPermission(FunctionCode.HOTEL, ActionCode.UPDATE) || !property) return false;
    const approval = String(property.approvalStatus || '').toUpperCase();
    const operation = String(property.operationStatus || '').toUpperCase();
    if (operation === 'CLOSED' || operation === 'SUSPENDED') return false;
    return approval === 'DRAFT'
      || approval === 'REJECTED'
      || (approval === 'APPROVED' && operation === 'ACTIVE');
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
  value(name: keyof NonNullable<ManagementContext['dashboard']>): number { const value = this.context?.dashboard?.[name]; return typeof value === 'number' ? value : 0; }
  limit(name: string): string { const value = this.context?.limits?.[name]; return value === -1 ? 'Không giới hạn' : String(value ?? 0); }
  generatedAtLabel(): string {
    return this.context?.generatedAt
      ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(this.context.generatedAt))
      : 'Không có dữ liệu';
  }

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
      maxPrice: value.maxPrice
    };
  }

  private normalizedPropertyState(): string {
    const property = this.activeProperty;
    return String(property?.operationStatus || property?.status || '').trim().toUpperCase();
=======
  get activePropertyOperational(): boolean {
    return this.context?.activePropertyOperational
      ?? this.activeProperty?.operational
      ?? (this.activeProperty?.approvalStatus === 'APPROVED' && this.activeProperty?.operationStatus === 'ACTIVE');
  }
  value(name: string): number { return this.context?.dashboard?.[name] || 0; }
  limit(name: string): string { const value = this.context?.limits?.[name]; return value === -1 ? 'Không giới hạn' : String(value ?? 0); }
  statusLabel(status?: string): string {
    return ({
      ACTIVE: 'Đang hoạt động',
      INACTIVE: 'Không hoạt động',
      EXPIRED: 'Đã hết hạn',
      SUSPENDED: 'Tạm ngưng',
      PENDING: 'Chờ xử lý',
      PENDING_PAYMENT: 'Chờ thanh toán',
      DRAFT: 'Bản nháp',
      PENDING_APPROVAL: 'Chờ duyệt',
      APPROVED: 'Đã duyệt',
      REJECTED: 'Bị từ chối',
      NONE: 'Chưa có',
    } as Record<string, string>)[status || 'NONE'] || status || 'Chưa có';
  }
  sourceLabel(source?: string): string {
    return ({ PLATFORM: 'Hệ thống thanh toán gói', LEGACY: 'Dữ liệu thuê bao cũ', NONE: 'Chưa có' } as Record<string, string>)[source || 'NONE'] || source || 'Chưa có';
>>>>>>> codex/ui-functional-audit-polish
  }
}
