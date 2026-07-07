import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { inject } from '@angular/core';
import { SliderModule } from 'primeng/slider';
import { ClientApiService, Hotel } from '../../../core/services/client-api.service';

@Component({
  selector: 'app-room-search',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, SliderModule],
  templateUrl: './room-search.component.html',
  styleUrls: ['./room-search.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RoomSearchComponent implements OnInit {
  private route = inject(ActivatedRoute);

  // Search Bar
  destination: string = '';
  dateRange: string = '';
  guests: string = '';

  // Filters
  priceRange: number[] = [100, 1500];
  
  roomTypes = [
    { label: 'Standard', value: 'standard', checked: false },
    { label: 'Deluxe', value: 'deluxe', checked: false },
    { label: 'Suite', value: 'suite', checked: false },
    { label: 'VIP', value: 'vip', checked: false }
  ];

  amenities = [
    { label: 'Free WiFi', value: 'wifi', checked: false },
    { label: 'Air Conditioning', value: 'ac', checked: false },
    { label: 'Breakfast Included', value: 'breakfast', checked: false },
    { label: 'Pool', value: 'pool', checked: false },
    { label: 'Gym', value: 'gym', checked: false }
  ];

  // Real Data for Hotels
  hotels: Hotel[] = [];
  isLoading = false;

  private clientApi = inject(ClientApiService);

  constructor() {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.destination = params['destination'] || '';
      if (params['checkIn']) this.dateRange = params['checkIn'] + (params['checkOut'] ? ' to ' + params['checkOut'] : '');
      if (params['guests']) this.guests = params['guests'];
      
      this.searchHotels();
    });
  }

  searchHotels() {
    this.isLoading = true;
    let checkIn = undefined;
    let checkOut = undefined;
    if (this.dateRange && this.dateRange.includes(' to ')) {
      const parts = this.dateRange.split(' to ');
      checkIn = parts[0];
      checkOut = parts[1];
    }
    this.clientApi.searchHotels(this.destination, checkIn, checkOut, Number(this.guests) || undefined).subscribe({
      next: (data) => {
        this.hotels = data;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error fetching hotels:', err);
        this.isLoading = false;
      }
    });
  }

  search() {
    this.searchHotels();
}
