import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { inject } from '@angular/core';
import { SliderModule } from 'primeng/slider';

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

  // Mock Data for Rooms
  rooms = [
    {
      id: 1,
      name: 'Premium Deluxe Room',
      image: 'https://images.unsplash.com/photo-1611892440504-42a792e24d32?auto=format&fit=crop&q=80&w=800',
      rating: 4.8,
      features: ['WiFi', 'AC', 'Breakfast Included'],
      price: 350
    },
    {
      id: 2,
      name: 'Luxury Suite',
      image: 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&q=80&w=800',
      rating: 4.8,
      features: ['WiFi', 'AC', 'Breakfast Included'],
      price: 350
    },
    {
      id: 3,
      name: 'Luxury Suite',
      image: 'https://images.unsplash.com/photo-1566665797739-1674de7a421a?auto=format&fit=crop&q=80&w=800',
      rating: 4.8,
      features: ['WiFi', 'AC', 'Breakfast Included'],
      price: 350
    },
    {
      id: 4,
      name: 'Ocean View VIP Villa',
      image: 'https://images.unsplash.com/photo-1499793983690-e29da59ef1c2?auto=format&fit=crop&q=80&w=800',
      rating: 4.8,
      features: ['WiFi', 'AC', 'Breakfast Included'],
      price: 350
    },
    {
      id: 5,
      name: 'Premium Deluxe Room',
      image: 'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&q=80&w=800',
      rating: 4.8,
      features: ['WiFi', 'AC', 'Breakfast Included'],
      price: 350
    },
    {
      id: 6,
      name: 'Ocean View VIP Villa',
      image: 'https://images.unsplash.com/photo-1584132967334-10e028bd69f7?auto=format&fit=crop&q=80&w=800',
      rating: 4.8,
      features: ['WiFi', 'AC', 'Breakfast Included'],
      price: 450
    }
  ];

  constructor() {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      if (params['checkIn']) this.dateRange = params['checkIn'] + (params['checkOut'] ? ' to ' + params['checkOut'] : '');
      if (params['guests']) this.guests = params['guests'];
    });
  }

  search() {
    console.log('Searching with:', {
      destination: this.destination,
      dateRange: this.dateRange,
      guests: this.guests,
      priceRange: this.priceRange,
      roomTypes: this.roomTypes.filter(t => t.checked).map(t => t.value),
      amenities: this.amenities.filter(a => a.checked).map(a => a.value)
    });
  }
}
