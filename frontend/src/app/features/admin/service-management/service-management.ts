import { Component, inject, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { HotelServiceService, HotelServiceDTO } from '@app/core/services/hotel-service.service';
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
  loading = true;
  errorMessage = '';

  private hotelService = inject(HotelServiceService);
  private messageService = inject(MessageService);

  ngOnInit(): void {
    this.loadServices();
  }

  loadServices(): void {
    this.loading = true;
    this.errorMessage = '';

    this.hotelService.getServices().pipe(
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
}
