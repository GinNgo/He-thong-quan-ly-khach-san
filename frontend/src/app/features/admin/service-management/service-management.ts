import { Component, inject, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { HotelServiceService, HotelServiceDTO } from '@app/core/services/hotel-service.service';

@Component({
  selector: 'app-service-management',
  imports: [SharedModule],
  templateUrl: './service-management.html',
  styleUrl: './service-management.css'
})
export class ServiceManagement implements OnInit {
  services: HotelServiceDTO[] = [];
  loading = true;

  private hotelService = inject(HotelServiceService);

  ngOnInit(): void {
    this.loadServices();
  }

  loadServices(): void {
    this.loading = true;
    this.hotelService.getServices().subscribe({
      next: (data) => {
        this.services = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Error fetching services', err);
        this.loading = false;
      }
    });
  }
}
