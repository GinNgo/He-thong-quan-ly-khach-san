import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { finalize, timeout } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { ActionCode, FunctionCode, PermissionService } from '../../../core/services/permission.service';
import {
  CreatePropertyRequest,
  PropertyLifecycleAction,
  PropertyLifecycleDecisionResponse,
  PropertyLifecycleSummary,
  PropertyLocation,
  PropertyReviewHistoryEvent,
  PropertyService
} from '../../../core/services/property.service';
import { PropertyReviewHistoryComponent } from '../../../shared/components/property-review-history/property-review-history.component';

const LIFECYCLE_REASON_MIN_LENGTH = 10;
const LIFECYCLE_REASON_MAX_LENGTH = 500;

@Component({
  selector: 'app-property-management',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TableModule,
    ButtonModule,
    ToastModule,
    TagModule,
    TooltipModule,
    DialogModule,
    InputTextModule,
    SelectModule,
    TextareaModule,
    PropertyReviewHistoryComponent
  ],
  providers: [MessageService],
  templateUrl: './property-management.html',
  styleUrl: './property-management.css'
})
export class PropertyManagementComponent implements OnInit {
  private readonly propertyService = inject(PropertyService);
  private readonly messageService = inject(MessageService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly permissionService = inject(PermissionService);

  properties: PropertyLifecycleSummary[] = [];
  provinces: PropertyLocation[] = [];
  wards: PropertyLocation[] = [];
  loading = false;
  locationsLoading = false;
  saving = false;
  dialogVisible = false;
  isAdmin = false;
  canManageLifecycle = false;
  canViewHistory = false;
  formError = '';
  lifecycleDialogVisible = false;
  lifecycleTarget: PropertyLifecycleSummary | null = null;
  lifecycleAction: PropertyLifecycleAction | null = null;
  lifecycleReason = '';
  lifecycleError = '';
  lifecycleIdempotencyKey = '';
  readonly lifecycleInFlight: Record<number, PropertyLifecycleAction | undefined> = {};
  historyDialogVisible = false;
  historyPropertyId: number | null = null;
  historyPropertyName = '';
  historyEvents: PropertyReviewHistoryEvent[] = [];
  historyLoading = false;
  historyError = '';

  readonly propertyTypes = [
    { label: 'Khách sạn', value: 'HOTEL' },
    { label: 'Nhà nghỉ', value: 'MOTEL' },
    { label: 'Homestay', value: 'HOMESTAY' },
    { label: 'Căn hộ / Villa', value: 'APARTMENT' }
  ];

  readonly form = this.formBuilder.nonNullable.group({
    nameVi: ['', [Validators.required, Validators.maxLength(255)]],
    nameEn: ['', Validators.maxLength(255)],
    propertyType: ['HOTEL', Validators.required],
    provinceId: [null as number | null, Validators.required],
    wardId: [null as number | null, Validators.required],
    address: ['', [Validators.required, Validators.maxLength(1000)]],
    starRating: [0, [Validators.min(0), Validators.max(5)]],
    phone: ['', Validators.maxLength(50)],
    email: ['', [Validators.email, Validators.maxLength(255)]],
    website: ['', Validators.maxLength(255)],
    mainImage: ['', Validators.maxLength(1000)],
    descriptionVi: ['', Validators.maxLength(4000)],
    descriptionEn: ['', Validators.maxLength(4000)]
  });

  ngOnInit(): void {
    this.isAdmin = this.permissionService.isSuperAdmin();
    this.canManageLifecycle = this.permissionService.hasPermission(
      FunctionCode.PROPERTY_LIFECYCLE,
      ActionCode.APPROVE
    );
    this.canViewHistory = this.permissionService.hasPermission(
      FunctionCode.PROPERTY_LIFECYCLE,
      ActionCode.VIEW
    );
    this.loadProperties();
    if (this.isAdmin) this.loadProvinces();
  }

  loadProperties(): void {
    this.loading = true;
    this.propertyService.getPropertyLifecycleSummaries().pipe(
      timeout(10000),
      finalize(() => { this.loading = false; })
    ).subscribe({
      next: data => { this.properties = data; },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Không thể tải dữ liệu',
          detail: 'Không thể tải danh sách trạng thái cơ sở. Vui lòng thử lại.'
        });
      }
    });
  }

  loadProvinces(): void {
    this.locationsLoading = true;
    this.propertyService.getProvinces().pipe(
      timeout(10000),
      finalize(() => { this.locationsLoading = false; })
    ).subscribe({
      next: data => { this.provinces = data; },
      error: () => {
        this.messageService.add({
          severity: 'warn',
          summary: 'Thiếu dữ liệu địa điểm',
          detail: 'Không thể tải danh sách tỉnh/thành phố. Bạn có thể thử lại khi mở biểu mẫu.'
        });
      }
    });
  }

  onProvinceChange(): void {
    const provinceId = this.form.controls.provinceId.value;
    this.form.controls.wardId.setValue(null);
    this.wards = [];
    if (!provinceId) return;

    this.locationsLoading = true;
    this.propertyService.getWards(provinceId).pipe(
      timeout(10000),
      finalize(() => { this.locationsLoading = false; })
    ).subscribe({
      next: data => { this.wards = data; },
      error: error => {
        const detail = error?.error?.message || 'Không thể tải danh sách phường/xã.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi địa điểm', detail });
      }
    });
  }

  openCreate(): void {
    if (!this.isAdmin) {
      this.messageService.add({ severity: 'warn', summary: 'Không đủ quyền', detail: 'Chỉ quản trị hệ thống mới có thể tạo cơ sở.' });
      return;
    }
    this.form.reset({
      nameVi: '', nameEn: '', propertyType: 'HOTEL', provinceId: null, wardId: null,
      address: '', starRating: 0, phone: '', email: '', website: '', mainImage: '',
      descriptionVi: '', descriptionEn: ''
    });
    this.formError = '';
    this.wards = [];
    this.dialogVisible = true;
    if (!this.provinces.length) this.loadProvinces();
  }

  closeCreate(): void {
    if (!this.saving) this.dialogVisible = false;
  }

  save(): void {
    if (this.saving) return;
    this.formError = '';
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.formError = 'Vui lòng bổ sung các trường bắt buộc trước khi lưu.';
      this.messageService.add({ severity: 'warn', summary: 'Thiếu thông tin', detail: this.formError });
      return;
    }

    const value = this.form.getRawValue();
    const province = this.provinces.find(item => item.id === value.provinceId);
    if (!province || value.wardId === null || value.provinceId === null) {
      this.formError = 'Vui lòng chọn tỉnh/thành phố và phường/xã hợp lệ.';
      this.messageService.add({ severity: 'warn', summary: 'Thiếu địa điểm', detail: this.formError });
      return;
    }

    const request: CreatePropertyRequest = {
      name: value.nameVi.trim(),
      nameVi: value.nameVi.trim(),
      nameEn: value.nameEn.trim() || undefined,
      propertyType: value.propertyType,
      addressLine: value.address.trim(),
      city: province.nameVi,
      country: 'Việt Nam',
      provinceId: value.provinceId,
      wardId: value.wardId,
      description: value.descriptionVi.trim() || undefined,
      descriptionVi: value.descriptionVi.trim() || undefined,
      descriptionEn: value.descriptionEn.trim() || undefined,
      starRating: value.starRating,
      phone: value.phone.trim() || undefined,
      email: value.email.trim() || undefined,
      website: value.website.trim() || undefined,
      mainImage: value.mainImage.trim() || undefined,
      status: 'DRAFT',
      approvalStatus: 'DRAFT',
      operationStatus: 'INACTIVE',
      isDemo: false,
      dataSource: 'ADMIN'
    };

    this.saving = true;
    this.propertyService.createProperty(request).pipe(
      timeout(15000),
      finalize(() => { this.saving = false; })
    ).subscribe({
      next: () => {
        this.dialogVisible = false;
        this.messageService.add({ severity: 'success', summary: 'Đã tạo cơ sở', detail: 'Cơ sở được tạo ở trạng thái bản nháp.' });
        this.loadProperties();
      },
      error: error => {
        this.formError = error?.error?.message || 'Không thể tạo cơ sở. Vui lòng kiểm tra dữ liệu và thử lại.';
        this.messageService.add({ severity: 'error', summary: 'Không thể tạo cơ sở', detail: this.formError });
      }
    });
  }

  canRunLifecycle(property: PropertyLifecycleSummary, action: PropertyLifecycleAction): boolean {
    return this.canManageLifecycle && (property.allowedTransitions ?? []).includes(action);
  }

  openLifecycle(property: PropertyLifecycleSummary, action: PropertyLifecycleAction): void {
    if (!this.canRunLifecycle(property, action) || this.isLifecycleBusy(property)) return;
    this.lifecycleTarget = property;
    this.lifecycleAction = action;
    this.lifecycleReason = '';
    this.lifecycleError = '';
    this.lifecycleIdempotencyKey = this.requestId();
    this.lifecycleDialogVisible = true;
  }

  closeLifecycleDialog(): void {
    if (this.lifecycleTarget && this.isLifecycleBusy(this.lifecycleTarget)) return;
    this.resetLifecycleDialog();
  }

  updateLifecycleReason(reason: string): void {
    this.lifecycleReason = reason;
    this.lifecycleError = '';
  }

  lifecycleReasonError(): string {
    const reason = this.lifecycleReason.trim();
    if (!reason) return 'Vui lòng nhập lý do thực hiện thay đổi.';
    if (reason.length < LIFECYCLE_REASON_MIN_LENGTH) {
      return `Lý do phải có ít nhất ${LIFECYCLE_REASON_MIN_LENGTH} ký tự.`;
    }
    if (reason.length > LIFECYCLE_REASON_MAX_LENGTH) {
      return `Lý do không được vượt quá ${LIFECYCLE_REASON_MAX_LENGTH} ký tự.`;
    }
    return '';
  }

  submitLifecycle(): void {
    const property = this.lifecycleTarget;
    const action = this.lifecycleAction;
    if (!property || !action || !this.canRunLifecycle(property, action) || this.isLifecycleBusy(property)) return;

    const validationError = this.lifecycleReasonError();
    if (validationError) {
      this.lifecycleError = validationError;
      return;
    }

    const reason = this.lifecycleReason.trim();
    const idempotencyKey = this.lifecycleIdempotencyKey || this.requestId();
    this.lifecycleIdempotencyKey = idempotencyKey;
    this.lifecycleInFlight[property.propertyId] = action;
    this.lifecycleError = '';

    this.lifecycleRequest(property.propertyId, action, reason, idempotencyKey).pipe(
      timeout(10000),
      finalize(() => { delete this.lifecycleInFlight[property.propertyId]; })
    ).subscribe({
      next: decision => {
        this.resetLifecycleDialog();
        this.messageService.add({
          severity: action === 'CLOSE' ? 'warn' : 'success',
          summary: this.lifecycleActionLabel(action),
          detail: decision.changed
            ? `Đã cập nhật trạng thái của ${property.name}.`
            : `Cơ sở ${property.name} đã ở trạng thái yêu cầu.`
        });
        this.loadProperties();
      },
      error: error => {
        if (error?.status === 409) {
          this.messageService.add({
            severity: 'warn',
            summary: 'Trạng thái đã thay đổi',
            detail: 'Dữ liệu cơ sở đã được cập nhật ở phiên khác. Danh sách sẽ được tải lại.'
          });
          this.resetLifecycleDialog();
          this.loadProperties();
          return;
        }
        this.lifecycleError = error?.status === 403
          ? 'Bạn không có quyền thực hiện thay đổi này.'
          : 'Không thể cập nhật trạng thái cơ sở. Vui lòng thử lại an toàn.';
      }
    });
  }

  isLifecycleBusy(property: PropertyLifecycleSummary): boolean {
    return this.lifecycleInFlight[property.propertyId] !== undefined;
  }

  openHistory(property: PropertyLifecycleSummary): void {
    if (!this.canViewHistory || this.isLifecycleBusy(property) || this.historyLoading) return;
    this.historyPropertyId = property.propertyId;
    this.historyPropertyName = property.name;
    this.historyEvents = [];
    this.historyError = '';
    this.historyDialogVisible = true;
    this.loadHistory(property.propertyId);
  }

  closeHistoryDialog(): void {
    if (this.historyLoading) return;
    this.historyDialogVisible = false;
    this.historyPropertyId = null;
    this.historyPropertyName = '';
    this.historyEvents = [];
    this.historyError = '';
  }

  retryHistory(): void {
    if (this.historyPropertyId !== null) this.loadHistory(this.historyPropertyId);
  }

  lifecycleDialogTitle(): string {
    return this.lifecycleAction ? this.lifecycleActionLabel(this.lifecycleAction) : 'Cập nhật cơ sở';
  }

  lifecycleActionLabel(action: PropertyLifecycleAction): string {
    return {
      SUSPEND: 'Tạm ngừng cơ sở',
      REACTIVATE: 'Kích hoạt lại cơ sở',
      CLOSE: 'Đóng cơ sở'
    }[action];
  }

  lifecycleWarning(): string {
    if (this.lifecycleAction === 'SUSPEND') {
      return 'Cơ sở sẽ ngừng hiển thị công khai và ngừng các thao tác vận hành. Dữ liệu và đặt phòng lịch sử vẫn được giữ nguyên.';
    }
    if (this.lifecycleAction === 'REACTIVATE') {
      return 'Cơ sở sẽ được mở lại quyền vận hành và hiển thị công khai theo điều kiện hệ thống.';
    }
    if (this.lifecycleAction === 'CLOSE') {
      return 'Đây là chuyển đổi kết thúc. Cơ sở sẽ không còn hiển thị công khai hoặc vận hành; dữ liệu và đặt phòng lịch sử không bị xóa.';
    }
    return '';
  }

  statusLabel(status: string | null | undefined): string {
    const normalized = String(status ?? '').trim().toUpperCase();
    return ({
      DRAFT: 'Bản nháp',
      PENDING: 'Chờ xử lý',
      PENDING_APPROVAL: 'Chờ duyệt',
      ACTIVE: 'Hoạt động',
      APPROVED: 'Đã duyệt',
      INACTIVE: 'Chưa hoạt động',
      SUSPENDED: 'Tạm ngừng',
      CLOSED: 'Đã đóng',
      REJECTED: 'Từ chối'
    } as Record<string, string>)[normalized] ?? (normalized.replaceAll('_', ' ') || 'Chưa xác định');
  }

  statusSeverity(status: string | null | undefined): 'success' | 'info' | 'warn' | 'danger' {
    const normalized = String(status ?? '').trim().toUpperCase();
    if (normalized === 'ACTIVE' || normalized === 'APPROVED') return 'success';
    if (normalized === 'REJECTED' || normalized === 'CLOSED') return 'danger';
    if (normalized === 'PENDING' || normalized === 'PENDING_APPROVAL' || normalized === 'SUSPENDED') return 'warn';
    return 'info';
  }

  isInvalid(controlName: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.dirty || control.touched);
  }

  private lifecycleRequest(
    propertyId: number,
    action: PropertyLifecycleAction,
    reason: string,
    idempotencyKey: string
  ): Observable<PropertyLifecycleDecisionResponse> {
    if (action === 'SUSPEND') {
      return this.propertyService.suspendProperty(propertyId, reason, idempotencyKey);
    }
    if (action === 'REACTIVATE') {
      return this.propertyService.reactivateProperty(propertyId, reason, idempotencyKey);
    }
    return this.propertyService.closeProperty(propertyId, reason, idempotencyKey);
  }

  private loadHistory(propertyId: number): void {
    if (this.historyLoading) return;
    this.historyLoading = true;
    this.historyError = '';
    this.propertyService.getAdminPropertyHistory(propertyId).pipe(
      timeout(10000),
      finalize(() => { this.historyLoading = false; })
    ).subscribe({
      next: events => {
        this.historyEvents = Array.isArray(events) ? events : [];
      },
      error: error => {
        this.historyEvents = [];
        this.historyError = error?.status === 403
          ? 'Bạn không có quyền xem lịch sử của cơ sở này.'
          : 'Không thể tải lịch sử xét duyệt. Vui lòng thử lại.';
      }
    });
  }

  private resetLifecycleDialog(): void {
    this.lifecycleDialogVisible = false;
    this.lifecycleTarget = null;
    this.lifecycleAction = null;
    this.lifecycleReason = '';
    this.lifecycleError = '';
    this.lifecycleIdempotencyKey = '';
  }

  private requestId(): string {
    const cryptoApi = globalThis.crypto as Crypto | undefined;
    if (cryptoApi?.randomUUID) return cryptoApi.randomUUID();
    return `property-lifecycle-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
}
