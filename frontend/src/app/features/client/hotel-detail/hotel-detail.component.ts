import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ClientApiService, Hotel, RoomType } from '../../../core/services/client-api.service';

@Component({
  selector: 'app-hotel-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './hotel-detail.component.html',
  styleUrls: ['./hotel-detail.component.css']
})
export class HotelDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private clientApi = inject(ClientApiService);

  hotel: Hotel | null = null;
  roomTypes: RoomType[] = [];
  isLoading = true;
  roomError = '';
  bookingQueryParams: { checkIn?: string; checkOut?: string; guests?: number } = {};

  showClaimModal = false;
  claimForm = {
    verificationMethod: 'BUSINESS_LICENSE',
    verificationData: '',
    note: ''
  };

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      this.bookingQueryParams = {
        checkIn: params['checkIn'] || undefined,
        checkOut: params['checkOut'] || undefined,
        guests: Number(params['guests']) || undefined,
      };

      if (this.hotel?.id) {
        this.loadRoomTypes(this.hotel.id);
      }
    });

    this.route.paramMap.subscribe((params) => {
      const id = params.get('id');
      if (id) {
        this.loadHotelData(Number(id));
      }
    });
  }

  loadHotelData(hotelId: number) {
    this.isLoading = true;
    this.clientApi.getHotelById(hotelId).subscribe({
      next: (hotelData) => {
        this.hotel = hotelData;
        this.loadRoomTypes(hotelId);
      },
      error: (err) => {
        console.error('Error fetching hotel details:', err);
        this.isLoading = false;
      }
    });
  }

  loadRoomTypes(hotelId: number) {
    this.roomError = '';
    this.clientApi
      .getRoomTypesByHotel(
        hotelId,
        this.bookingQueryParams.checkIn,
        this.bookingQueryParams.checkOut,
        this.bookingQueryParams.guests
      )
      .subscribe({
        next: (roomTypesData) => {
          this.roomTypes = roomTypesData;
          this.isLoading = false;
        },
        error: (err) => {
          console.error('Error fetching room types:', err);
          this.roomTypes = [];
          this.roomError = 'Khong the tai tinh trang phong. Vui long thu lai.';
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
