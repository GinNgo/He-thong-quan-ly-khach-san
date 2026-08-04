import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { finalize, timeout } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { PropertyProfile } from '../../../core/models/property-profile.model';
import { AuthService } from '../../../core/services/auth';
import { AdminProperty, PropertyLocation, PropertyService } from '../../../core/services/property.service';
import { PropertyGalleryComponent } from '../../../shared/components/property-gallery/property-gallery.component';

type PropertyStatus = 'DRAFT' | 'PENDING' | 'ACTIVE' | 'INACTIVE' | 'REJECTED';

function profileRangeValidator(control: AbstractControl): ValidationErrors | null {
  const latitude = control.get('latitude')?.value;
  const longitude = control.get('longitude')?.value;
  const minPrice = control.get('minPrice')?.value;
  const maxPrice = control.get('maxPrice')?.value;
  const errors: ValidationErrors = {};
  if ((latitude == null) !== (longitude == null)) errors['coordinatePair'] = true;
  if (minPrice != null && maxPrice != null && minPrice > maxPrice) errors['priceRange'] = true;
  return Object.keys(errors).length ? errors : null;
}

@Component({
  selector: 'app-property-management',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, TableModule, ButtonModule, ToastModule, TagModule,
    TooltipModule, DialogModule, InputTextModule, SelectModule, TextareaModule, PropertyGalleryComponent
  ],
  providers: [MessageService],
  templateUrl: './property-management.html',
  styleUrl: './property-management.css'
})
export class PropertyManagementComponent implements OnInit {
  private readonly propertyService = inject(PropertyService);
  private readonly messageService = inject(MessageService);
  private readonly formBuilder = inject(FormBuilder);
  public readonly authService = inject(AuthService);

  properties: AdminProperty[] = [];
  provinces: PropertyLocation[] = [];
  wards: PropertyLocation[] = [];
  loading = false;
  locationsLoading = false;
  saving = false;
  dialogVisible = false;
  editingProperty?: AdminProperty;
  isAdmin = false;
  formError = '';

  readonly propertyTypes = [
    { label: 'Khách sạn', value: 'HOTEL' },
    { label: 'Nhà nghỉ', value: 'MOTEL' },
    { label: 'Homestay', value: 'HOMESTAY' },
    { label: 'Căn hộ', value: 'APARTMENT' },
    { label: 'Villa', value: 'VILLA' },
    { label: 'Resort', value: 'RESORT' }
  ];

  readonly form = this.formBuilder.group({
    nameVi: ['', [Validators.required, Validators.maxLength(255)]],
    nameEn: ['', Validators.maxLength(255)],
    propertyType: ['HOTEL', Validators.required],
    provinceId: [null as number | null, Validators.required],
    wardId: [null as number | null, Validators.required],
    addressLine: ['', [Validators.required, Validators.maxLength(1000)]],
    latitude: [null as number | null, [Validators.min(-90), Validators.max(90)]],
    longitude: [null as number | null, [Validators.min(-180), Validators.max(180)]],
    starRating: [0 as number | null, [Validators.min(0), Validators.max(5)]],
    phone: ['', [Validators.maxLength(50), Validators.pattern(/^\+?[0-9][0-9 .()\-]{7,19}$/)]],
    email: ['', [Validators.email, Validators.maxLength(320)]],
    website: ['', [Validators.maxLength(1000), Validators.pattern(/^https?:\/\/\S+$/i)]],
    checkinTime: ['', Validators.pattern(/^([01]\d|2[0-3]):[0-5]\d$/)],
    checkoutTime: ['', Validators.pattern(/^([01]\d|2[0-3]):[0-5]\d$/)],
    minPrice: [null as number | null, Validators.min(0)],
    maxPrice: [null as number | null, Validators.min(0)],
    mainImage: ['', Validators.maxLength(1000)],
    descriptionVi: ['', Validators.maxLength(4000)],
    descriptionEn: ['', Validators.maxLength(4000)],
    reason: ['', Validators.maxLength(500)]
  }, { validators: profileRangeValidator });

  ngOnInit(): void {
    this.isAdmin = this.authService.getRoles().includes('SUPER_ADMIN');
    this.loadProperties();
    this.loadProvinces();
  }

  loadProperties(): void {
    this.loading = true;
    this.propertyService.getAllProperties().pipe(
      timeout(10000), finalize(() => { this.loading = false; })
    ).subscribe({
      next: data => { this.properties = data; },
      error: error => this.messageService.add({
        severity: 'error', summary: 'Lỗi',
        detail: error?.error?.message || 'Không thể tải danh sách cơ sở.'
      })
    });
  }

  loadProvinces(): void {
    this.locationsLoading = true;
    this.propertyService.getProvinces().pipe(
      timeout(10000), finalize(() => { this.locationsLoading = false; })
    ).subscribe({
      next: data => { this.provinces = data; },
      error: () => this.messageService.add({
        severity: 'warn', summary: 'Thiếu dữ liệu địa điểm',
        detail: 'Không thể tải danh sách tỉnh/thành phố.'
      })
    });
  }

  onProvinceChange(): void {
    const provinceId = this.form.controls.provinceId.value;
    this.form.controls.wardId.setValue(null);
    this.loadWards(provinceId);
  }

  openCreate(): void {
    if (!this.isAdmin) {
      this.messageService.add({ severity: 'warn', summary: 'Không đủ quyền', detail: 'Chỉ quản trị hệ thống mới có thể tạo cơ sở.' });
      return;
    }
    this.editingProperty = undefined;
    this.form.reset({
      nameVi: '', nameEn: '', propertyType: 'HOTEL', provinceId: null, wardId: null,
      addressLine: '', latitude: null, longitude: null, starRating: 0, phone: '', email: '', website: '',
      checkinTime: '', checkoutTime: '', minPrice: null, maxPrice: null, mainImage: '',
      descriptionVi: '', descriptionEn: '', reason: ''
    });
    this.openDialog();
  }

  openEdit(property: AdminProperty): void {
    if (!this.isAdmin || !property.id) return;
    this.editingProperty = property;
    this.form.reset({
      nameVi: property.nameVi || '', nameEn: property.nameEn || '', propertyType: property.propertyType || 'HOTEL',
      provinceId: property.provinceId || null, wardId: property.wardId || null, addressLine: property.addressLine || '',
      latitude: property.latitude ?? null, longitude: property.longitude ?? null, starRating: property.starRating ?? 0,
      phone: property.phone || '', email: property.email || '', website: property.website || '',
      checkinTime: property.checkinTime || '', checkoutTime: property.checkoutTime || '',
      minPrice: property.minPrice ?? null, maxPrice: property.maxPrice ?? null, mainImage: property.mainImage || '',
      descriptionVi: property.descriptionVi || '', descriptionEn: property.descriptionEn || '', reason: ''
    });
    this.loadWards(property.provinceId, property.wardId);
    this.openDialog();
  }

  closeDialog(): void {
    if (!this.saving) this.dialogVisible = false;
  }

  save(): void {
    if (this.saving) return;
    this.formError = '';
    const reason = this.form.controls.reason.value?.trim() || '';
    if (this.form.invalid || (this.editingProperty && reason.length < 3)) {
      this.form.markAllAsTouched();
      this.formError = this.form.hasError('coordinatePair')
        ? 'Vĩ độ và kinh độ phải được nhập cùng nhau.'
        : this.form.hasError('priceRange')
          ? 'Giá tối thiểu không được lớn hơn giá tối đa.'
          : this.editingProperty && reason.length < 3
            ? 'Lý do cập nhật phải có ít nhất 3 ký tự.'
            : 'Vui lòng kiểm tra các trường bắt buộc và định dạng hồ sơ.';
      return;
    }

    const profile = this.toProfile();
    this.saving = true;
    const request$ = this.editingProperty?.id
      ? this.propertyService.updateProperty(this.editingProperty.id, { profile, reason })
      : this.propertyService.createProperty(profile);
    request$.pipe(timeout(15000), finalize(() => { this.saving = false; })).subscribe({
      next: () => {
        this.dialogVisible = false;
        this.messageService.add({
          severity: 'success', summary: this.editingProperty ? 'Đã cập nhật cơ sở' : 'Đã tạo cơ sở',
          detail: this.editingProperty ? 'Hồ sơ đã được lưu đầy đủ.' : 'Cơ sở được tạo ở trạng thái bản nháp.'
        });
        this.loadProperties();
      },
      error: error => {
        this.formError = error?.error?.message || 'Không thể lưu hồ sơ cơ sở. Vui lòng kiểm tra dữ liệu.';
      }
    });
  }

  submit(property: AdminProperty): void {
    if (!property.id) return;
    this.propertyService.submitProperty(property.id).pipe(timeout(10000)).subscribe({
      next: () => { this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã gửi yêu cầu duyệt.' }); this.loadProperties(); },
      error: error => this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: error?.error?.message || 'Không thể gửi yêu cầu duyệt.' })
    });
  }

  approve(property: AdminProperty): void {
    if (!property.id) return;
    this.propertyService.approveProperty(property.id).pipe(timeout(10000)).subscribe({
      next: () => { this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã duyệt cơ sở.' }); this.loadProperties(); },
      error: error => this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: error?.error?.message || 'Không thể duyệt cơ sở.' })
    });
  }

  reject(property: AdminProperty): void {
    if (!property.id) return;
    this.propertyService.rejectProperty(property.id).pipe(timeout(10000)).subscribe({
      next: () => { this.messageService.add({ severity: 'warn', summary: 'Đã từ chối', detail: 'Cơ sở đã được chuyển sang trạng thái từ chối.' }); this.loadProperties(); },
      error: error => this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: error?.error?.message || 'Không thể từ chối cơ sở.' })
    });
  }

  statusCode(property: AdminProperty): PropertyStatus {
    const status = property.status || property.approvalStatus || property.operationStatus;
    return ['DRAFT', 'PENDING', 'ACTIVE', 'INACTIVE', 'REJECTED'].includes(status || '')
      ? status as PropertyStatus : 'DRAFT';
  }

  statusLabel(property: AdminProperty): string {
    return { DRAFT: 'Bản nháp', PENDING: 'Chờ duyệt', ACTIVE: 'Hoạt động', INACTIVE: 'Tạm ngưng', REJECTED: 'Từ chối' }[this.statusCode(property)];
  }

  statusSeverity(property: AdminProperty): 'success' | 'info' | 'warn' | 'danger' {
    const status = this.statusCode(property);
    if (status === 'ACTIVE') return 'success';
    if (status === 'REJECTED' || status === 'INACTIVE') return 'danger';
    if (status === 'PENDING') return 'warn';
    return 'info';
  }

  isInvalid(controlName: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.dirty || control.touched);
  }

  private openDialog(): void {
    this.formError = '';
    this.dialogVisible = true;
    if (!this.provinces.length) this.loadProvinces();
  }

  private loadWards(provinceId?: number | null, selectedWardId?: number): void {
    this.wards = [];
    if (!provinceId) return;
    this.locationsLoading = true;
    this.propertyService.getWards(provinceId).pipe(
      timeout(10000), finalize(() => { this.locationsLoading = false; })
    ).subscribe({
      next: data => {
        this.wards = data;
        if (selectedWardId) this.form.controls.wardId.setValue(selectedWardId);
      },
      error: error => this.messageService.add({ severity: 'error', summary: 'Lỗi địa điểm', detail: error?.error?.message || 'Không thể tải danh sách phường/xã.' })
    });
  }

  private toProfile(): PropertyProfile {
    const value = this.form.getRawValue();
    const clean = (field?: string | null) => field?.trim() || undefined;
    return {
      nameVi: value.nameVi!.trim(),
      nameEn: clean(value.nameEn),
      propertyType: value.propertyType as PropertyProfile['propertyType'],
      addressLine: value.addressLine!.trim(),
      provinceId: value.provinceId!, wardId: value.wardId!,
      latitude: value.latitude ?? undefined, longitude: value.longitude ?? undefined,
      descriptionVi: clean(value.descriptionVi), descriptionEn: clean(value.descriptionEn),
      starRating: value.starRating ?? undefined, phone: clean(value.phone), email: clean(value.email),
      website: clean(value.website), checkinTime: clean(value.checkinTime), checkoutTime: clean(value.checkoutTime),
      minPrice: value.minPrice ?? undefined, maxPrice: value.maxPrice ?? undefined, mainImage: clean(value.mainImage)
    };
  }
}
