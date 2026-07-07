import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ClientApiService, Hotel, RoomType } from '../../../core/services/client-api.service';

@Component({
  selector: 'app-hotel-detail',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './hotel-detail.component.html',
  styleUrls: ['./hotel-detail.component.css']
})
export class HotelDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private clientApi = inject(ClientApiService);

  hotel: Hotel | null = null;
  roomTypes: RoomType[] = [];
  isLoading = true;

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const id = params.get('id');
      if (id) {
        this.loadHotelData(Number(id));
      }
    });
  }

  loadHotelData(hotelId: number) {
    this.isLoading = true;
    
    // Load Hotel info
    this.clientApi.getHotelById(hotelId).subscribe({
      next: (hotelData) => {
        this.hotel = hotelData;
        
        // Load Room Types for this hotel
        this.clientApi.getRoomTypesByHotel(hotelId).subscribe({
          next: (roomTypesData) => {
            this.roomTypes = roomTypesData;
            this.isLoading = false;
          },
          error: (err) => {
            console.error('Error fetching room types:', err);
            this.isLoading = false;
          }
        });
      },
      error: (err) => {
        console.error('Error fetching hotel details:', err);
        this.isLoading = false;
      }
    });
  }

  scrollToRooms() {
    const el = document.getElementById('rooms');
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  }
}
