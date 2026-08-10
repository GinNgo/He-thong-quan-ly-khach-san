import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
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
import { AuthService } from '../../../core/services/auth';
import {
  AdminProperty,
  CreatePropertyRequest,
  PropertyLocation,
  PropertyService
} from '../../../core/services/property.service';

type PropertyStatus = 'DRAFT' | 'PENDING' | 'ACTIVE' | 'INACTIVE' | 'REJECTED';

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
    TextareaModule
  ],
  providers: [MessageService],
  templateUrl: './property-management.html',
  styleUrl: './property-management.css'
})
export class PropertyManagementComponent implements OnInit {
  private readonly propertyService = inject(PropertyService);
  private readonly messageService = inject(MessageService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly cdr = inject(ChangeDetectorRef);
  public readonly authService = inject(AuthService);

  properties: AdminProperty[] = [];
  provinces: PropertyLocation[] = [];
  wards: PropertyLocation[] = [];
  loading = false;
  locationsLoading = false;
  saving = false;
  dialogVisible = false;
  isAdmin = false;
  formError = '';

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
    this.isAdmin = this.authService.getRoles().includes('SUPER_ADMIN');
    this.loadProperties();
    this.loadProvinces();
  }

  loadProperties(): void {
    this.loading = true;
    this.propertyService.getAllProperties().pipe(
      timeout(10000),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: data => { this.properties = data; },
      error: error => {
        const detail = error?.error?.message || 'Không thể tải danh sách cơ sở.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  loadProvinces(): void {
    this.locationsLoading = true;
    this.propertyService.getProvinces().pipe(
      timeout(10000),
      finalize(() => {
        this.locationsLoading = false;
        this.cdr.detectChanges();
      })
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
      finalize(() => {
        this.locationsLoading = false;
        this.cdr.detectChanges();
      })
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

  submit(property: AdminProperty): void {
    this.propertyService.submitProperty(property.id).pipe(timeout(10000)).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã gửi yêu cầu duyệt.' });
        this.loadProperties();
      },
      error: error => {
        const detail = error?.error?.message || 'Không thể gửi yêu cầu duyệt.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  approve(property: AdminProperty): void {
    this.propertyService.approveProperty(property.id).pipe(timeout(10000)).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã duyệt cơ sở.' });
        this.loadProperties();
      },
      error: error => {
        const detail = error?.error?.message || 'Không thể duyệt cơ sở.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  reject(property: AdminProperty): void {
    this.propertyService.rejectProperty(property.id).pipe(timeout(10000)).subscribe({
      next: () => {
        this.messageService.add({ severity: 'warn', summary: 'Đã từ chối', detail: 'Cơ sở đã được chuyển sang trạng thái từ chối.' });
        this.loadProperties();
      },
      error: error => {
        const detail = error?.error?.message || 'Không thể từ chối cơ sở.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  statusCode(property: AdminProperty): PropertyStatus {
    const status = property.status || property.approvalStatus || property.operationStatus;
    return ['DRAFT', 'PENDING', 'ACTIVE', 'INACTIVE', 'REJECTED'].includes(status || '')
      ? status as PropertyStatus
      : 'DRAFT';
  }

  statusLabel(property: AdminProperty): string {
    return {
      DRAFT: 'Bản nháp', PENDING: 'Chờ duyệt', ACTIVE: 'Hoạt động',
      INACTIVE: 'Tạm ngưng', REJECTED: 'Từ chối'
    }[this.statusCode(property)];
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
}
