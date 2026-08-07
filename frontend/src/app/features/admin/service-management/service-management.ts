import { Component, inject, OnInit } from '@angular/core';
<<<<<<< HEAD
import { HotelServiceDTO, HotelServiceService } from '@app/core/services/hotel-service.service';
import { ManagementApiService, ManagedProperty } from '@app/core/services/management-api.service';
import { ActionCode, FunctionCode, PermissionService } from '@app/core/services/permission.service';
import { SharedModule } from '@app/shared/shared.module';
=======
import { ActivatedRoute } from '@angular/router';
import { SharedModule } from '@app/shared/shared.module';
import { HotelServiceService, HotelServiceDTO } from '@app/core/services/hotel-service.service';
import { ManagementApiService, ManagedProperty } from '@app/core/services/management-api.service';
>>>>>>> codex/ui-functional-audit-polish
import { ConfirmationService, MessageService } from 'primeng/api';
import { finalize, timeout } from 'rxjs/operators';

type ServiceForm = Pick<HotelServiceDTO, 'code' | 'nameVi' | 'nameEn' | 'price' | 'descriptionVi' | 'descriptionEn' | 'status'>;

@Component({
  selector: 'app-service-management',
  imports: [SharedModule],
<<<<<<< HEAD
  providers: [ConfirmationService, MessageService],
=======
  providers: [MessageService, ConfirmationService],
>>>>>>> codex/ui-functional-audit-polish
  templateUrl: './service-management.html',
  styleUrl: './service-management.css'
})
export class ServiceManagement implements OnInit {
  services: HotelServiceDTO[] = [];
  properties: ManagedProperty[] = [];
  selectedPropertyId: number | null = null;
  loading = true;
  saving = false;
<<<<<<< HEAD
  deactivatingId?: number;
  errorMessage = '';
  dialogVisible = false;
  editingId?: number;
  submitted = false;
  form: ServiceForm = this.emptyForm();

  private readonly hotelService = inject(HotelServiceService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly permissions = inject(PermissionService);
  private readonly messages = inject(MessageService);
  private readonly confirmations = inject(ConfirmationService);

  readonly canCreate = this.permissions.hasPermission(FunctionCode.HOTEL_SERVICE, ActionCode.CREATE);
  readonly canUpdate = this.permissions.hasPermission(FunctionCode.HOTEL_SERVICE, ActionCode.UPDATE);
  readonly canDelete = this.permissions.hasPermission(FunctionCode.HOTEL_SERVICE, ActionCode.DELETE);

  ngOnInit(): void {
    this.managementApi.context().subscribe({
      next: context => {
=======
  errorMessage = '';
  dialogVisible = false;
  editingId?: number;
  form: HotelServiceDTO = this.emptyForm();

  private hotelService = inject(HotelServiceService);
  private managementApi = inject(ManagementApiService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  private route = inject(ActivatedRoute);

  ngOnInit(): void {
    const propertyId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    const requestedPropertyId = Number.isInteger(propertyId) && propertyId > 0 ? propertyId : undefined;
    this.managementApi.context(requestedPropertyId).subscribe({
      next: (context) => {
>>>>>>> codex/ui-functional-audit-polish
        this.properties = context.properties ?? [];
        this.selectedPropertyId = context.activePropertyId ?? this.properties[0]?.id ?? null;
        this.loadServices();
      },
      error: error => {
        this.loading = false;
        this.errorMessage = error?.error?.message || 'Không thể tải danh sách cơ sở quản lý.';
      },
    });
  }

  loadServices(): void {
    this.loading = true;
    this.errorMessage = '';
    if (!this.selectedPropertyId) {
      this.loading = false;
      this.services = [];
      this.errorMessage = 'Hãy chọn một cơ sở trước khi tải dịch vụ.';
      return;
    }
    this.hotelService.getServices(this.selectedPropertyId).pipe(
      timeout(10000),
      finalize(() => this.loading = false),
    ).subscribe({
      next: data => this.services = data,
      error: error => this.errorMessage = error?.error?.message || 'Không thể tải danh sách dịch vụ.',
    });
  }

  onPropertyChange(): void {
    this.closeDialog();
    this.loadServices();
  }

  openCreate(): void {
<<<<<<< HEAD
    if (!this.canCreate || !this.selectedPropertyId) return;
    this.editingId = undefined;
    this.form = this.emptyForm();
    this.submitted = false;
=======
    if (!this.selectedPropertyId) {
      this.messageService.add({ severity: 'warn', summary: 'Chưa chọn cơ sở', detail: 'Hãy chọn cơ sở trước khi thêm dịch vụ.' });
      return;
    }
    this.editingId = undefined;
    this.form = this.emptyForm();
>>>>>>> codex/ui-functional-audit-polish
    this.dialogVisible = true;
  }

  openEdit(service: HotelServiceDTO): void {
<<<<<<< HEAD
    if (!this.canUpdate || service.systemService) return;
    this.editingId = service.id;
    this.form = {
      code: service.code,
      nameVi: service.nameVi,
      nameEn: service.nameEn,
      price: service.price,
      descriptionVi: service.descriptionVi || '',
      descriptionEn: service.descriptionEn || '',
      status: service.status,
    };
    this.submitted = false;
=======
    if (service.systemService || !service.id) return;
    this.editingId = service.id;
    this.form = { ...service };
>>>>>>> codex/ui-functional-audit-polish
    this.dialogVisible = true;
  }

  save(): void {
<<<<<<< HEAD
    this.submitted = true;
    if (this.saving || !this.selectedPropertyId || !this.valid) return;
    this.saving = true;
    this.errorMessage = '';
    const payload: HotelServiceDTO = {
      ...this.form,
      code: this.form.code.trim().toUpperCase(),
      nameVi: this.form.nameVi.trim(),
      nameEn: this.form.nameEn.trim(),
      descriptionVi: this.form.descriptionVi?.trim(),
      descriptionEn: this.form.descriptionEn?.trim(),
      price: Number(this.form.price),
    };
    const request = this.editingId
      ? this.hotelService.updateService(this.editingId, payload)
      : this.hotelService.createService(payload, this.selectedPropertyId);
    request.pipe(finalize(() => this.saving = false)).subscribe({
      next: () => {
        this.dialogVisible = false;
        this.messages.add({ severity: 'success', summary: 'Thành công', detail: this.editingId ? 'Đã cập nhật dịch vụ.' : 'Đã thêm dịch vụ.' });
        this.loadServices();
      },
      error: error => this.errorMessage = error?.error?.message || 'Không thể lưu dịch vụ.',
    });
  }

  deactivate(service: HotelServiceDTO): void {
    if (!this.canDelete || !service.id || service.systemService || service.status !== 'ACTIVE' || this.deactivatingId) return;
    this.confirmations.confirm({
      header: 'Ngừng cung cấp dịch vụ',
      message: `Ngừng cung cấp “${service.nameVi}”? Dữ liệu hóa đơn lịch sử vẫn được giữ nguyên.`,
      acceptLabel: 'Ngừng cung cấp',
      rejectLabel: 'Hủy',
      accept: () => {
        const reason = globalThis.prompt('Nhap ly do ngung cung cap dich vu:')?.trim();
        if (!reason) {
          this.errorMessage = 'Vui long nhap ly do ngung cung cap dich vu.';
          return;
        }
        this.deactivatingId = service.id;
        this.errorMessage = '';
        this.hotelService.deleteService(service.id!, reason).pipe(finalize(() => this.deactivatingId = undefined)).subscribe({
          next: () => {
            this.messages.add({ severity: 'success', summary: 'Thành công', detail: 'Đã ngừng cung cấp dịch vụ.' });
            this.loadServices();
          },
          error: error => this.errorMessage = error?.error?.message || 'Không thể ngừng cung cấp dịch vụ.',
=======
    if (this.saving || !this.selectedPropertyId) return;
    const payload = this.normalizedPayload();
    if (!payload) return;

    this.saving = true;
    const request = this.editingId
      ? this.hotelService.updateService(this.editingId, payload)
      : this.hotelService.createService(payload, this.selectedPropertyId);
    request.pipe(finalize(() => { this.saving = false; })).subscribe({
      next: () => {
        this.dialogVisible = false;
        this.messageService.add({
          severity: 'success',
          summary: this.editingId ? 'Đã cập nhật' : 'Đã thêm dịch vụ',
          detail: payload.nameVi,
        });
        this.loadServices();
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Không thể lưu dịch vụ',
          detail: error?.error?.message || 'Vui lòng kiểm tra dữ liệu và thử lại.',
>>>>>>> codex/ui-functional-audit-polish
        });
      },
    });
  }

<<<<<<< HEAD
  closeDialog(): void {
    if (this.saving) return;
    this.dialogVisible = false;
    this.editingId = undefined;
    this.submitted = false;
  }

  get valid(): boolean {
    return /^[A-Z0-9][A-Z0-9_-]{1,39}$/.test(this.form.code.trim().toUpperCase())
      && this.form.nameVi.trim().length >= 2
      && this.form.nameEn.trim().length >= 2
      && Number.isInteger(Number(this.form.price))
      && Number(this.form.price) >= 0
      && ['ACTIVE', 'INACTIVE'].includes(this.form.status);
  }

  private emptyForm(): ServiceForm {
    return { code: '', nameVi: '', nameEn: '', price: 0, descriptionVi: '', descriptionEn: '', status: 'ACTIVE' };
=======
  confirmDelete(service: HotelServiceDTO): void {
    if (!service.id || service.systemService || this.saving) return;
    this.confirmationService.confirm({
      header: 'Xóa dịch vụ lưu trú',
      message: `Bạn có chắc muốn xóa "${service.nameVi}"?`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Xóa',
      rejectLabel: 'Giữ lại',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.delete(service),
    });
  }

  statusLabel(status: string): string {
    return status === 'ACTIVE' ? 'Đang cung cấp' : 'Tạm ngừng';
  }

  private delete(service: HotelServiceDTO): void {
    if (!service.id) return;
    this.saving = true;
    this.hotelService.deleteService(service.id).pipe(finalize(() => { this.saving = false; })).subscribe({
      next: () => {
        this.services = this.services.filter((item) => item.id !== service.id);
        this.messageService.add({ severity: 'success', summary: 'Đã xóa dịch vụ', detail: service.nameVi });
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Không thể xóa dịch vụ',
          detail: error?.error?.message || 'Dịch vụ có thể đang được sử dụng.',
        });
      },
    });
  }

  private normalizedPayload(): HotelServiceDTO | null {
    const code = this.form.code.trim().toUpperCase();
    const nameVi = this.form.nameVi.trim();
    const price = Number(this.form.price);
    if (!code || !nameVi || !Number.isFinite(price) || price < 0 || !Number.isInteger(price)) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Dữ liệu chưa hợp lệ',
        detail: 'Mã, tên dịch vụ và giá VND nguyên không âm là bắt buộc.',
      });
      return null;
    }
    return {
      ...this.form,
      hotelId: this.selectedPropertyId ?? undefined,
      code,
      nameVi,
      nameEn: this.form.nameEn.trim() || nameVi,
      price,
      descriptionVi: this.form.descriptionVi?.trim() || undefined,
      descriptionEn: this.form.descriptionEn?.trim() || undefined,
      status: this.form.status === 'INACTIVE' ? 'INACTIVE' : 'ACTIVE',
      systemService: false,
    };
  }

  private emptyForm(): HotelServiceDTO {
    return {
      code: '',
      nameVi: '',
      nameEn: '',
      price: 0,
      descriptionVi: '',
      descriptionEn: '',
      status: 'ACTIVE',
      systemService: false,
    };
>>>>>>> codex/ui-functional-audit-polish
  }
}
