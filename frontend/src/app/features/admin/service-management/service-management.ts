import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SharedModule } from '@app/shared/shared.module';
import { HotelServiceService, HotelServiceDTO } from '@app/core/services/hotel-service.service';
import { ManagementApiService, ManagedProperty } from '@app/core/services/management-api.service';
import { ConfirmationService, MessageService } from 'primeng/api';
import { finalize, timeout } from 'rxjs/operators';

@Component({
  selector: 'app-service-management',
  imports: [SharedModule],
  providers: [MessageService, ConfirmationService],
  templateUrl: './service-management.html',
  styleUrl: './service-management.css'
})
export class ServiceManagement implements OnInit {
  services: HotelServiceDTO[] = [];
  properties: ManagedProperty[] = [];
  selectedPropertyId: number | null = null;
  loading = true;
  saving = false;
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
        this.properties = context.properties ?? [];
        this.selectedPropertyId = context.activePropertyId ?? this.properties[0]?.id ?? null;
        this.loadServices();
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = error?.error?.message || 'Không thể tải danh sách cơ sở quản lý.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: this.errorMessage });
      },
    });
  }

  loadServices(): void {
    this.loading = true;
    this.errorMessage = '';

    if (!this.selectedPropertyId) {
      this.loading = false;
      this.errorMessage = 'Hãy chọn một cơ sở trước khi tải dịch vụ.';
      return;
    }

    this.hotelService.getServices(this.selectedPropertyId).pipe(
      timeout(10000),
      finalize(() => {
        this.loading = false;
      })
    ).subscribe({
      next: (data) => {
        this.services = data;
      },
      error: (error) => {
        this.errorMessage = error?.error?.message || 'Không thể tải danh sách dịch vụ.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: this.errorMessage });
      }
    });
  }

  onPropertyChange(): void {
    this.loadServices();
  }

  openCreate(): void {
    if (!this.selectedPropertyId) {
      this.messageService.add({ severity: 'warn', summary: 'Chưa chọn cơ sở', detail: 'Hãy chọn cơ sở trước khi thêm dịch vụ.' });
      return;
    }
    this.editingId = undefined;
    this.form = this.emptyForm();
    this.dialogVisible = true;
  }

  openEdit(service: HotelServiceDTO): void {
    if (service.systemService || !service.id) return;
    this.editingId = service.id;
    this.form = { ...service };
    this.dialogVisible = true;
  }

  save(): void {
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
        });
      },
    });
  }

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
  }
}
