import { Component, inject, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { HotelServiceService, HotelServiceDTO } from '@app/core/services/hotel-service.service';
import { ManagementApiService, ManagedProperty } from '@app/core/services/management-api.service';
import { MessageService } from 'primeng/api';
import { finalize, timeout } from 'rxjs/operators';

@Component({
  selector: 'app-service-management',
  imports: [SharedModule],
  providers: [MessageService],
  templateUrl: './service-management.html',
  styleUrl: './service-management.css'
})
export class ServiceManagement implements OnInit {
  services: HotelServiceDTO[] = [];
  properties: ManagedProperty[] = [];
  selectedPropertyId: number | null = null;
  loading = true;
  errorMessage = '';

  private hotelService = inject(HotelServiceService);
  private managementApi = inject(ManagementApiService);
  private messageService = inject(MessageService);

  ngOnInit(): void {
    this.managementApi.context().subscribe({
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
}
