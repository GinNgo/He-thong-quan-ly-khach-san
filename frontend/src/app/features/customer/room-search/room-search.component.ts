import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';

@Component({
  standalone: true,
  imports: [SharedModule],
  selector: 'app-room-search',
  templateUrl: './room-search.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./room-search.component.css'],
})
export class RoomSearchComponent implements OnInit {
  priceRange: number[] = [100, 1500];

  roomTypes: string[] = ['Standard', 'Deluxe', 'Suite', 'VIP'];
  amenities: string[] = ['Free WiFi', 'Air Conditioning', 'Breakfast Included', 'Pool', 'Gym'];

  searchResults: any[] = [];

  constructor() {}

  ngOnInit() {
    // Mock data matching the screen.png template
    this.searchResults = [
      {
        title: 'Premium Deluxe Room',
        rating: 4.8,
        wifi: true,
        ac: true,
        breakfast: true,
        price: '$350',
        image:
          'https://images.unsplash.com/photo-1611892440504-42a792e24d32?ixlib=rb-1.2.1&auto=format&fit=crop&w=800&q=80',
      },
      {
        title: 'Luxury Suite',
        rating: 4.8,
        wifi: true,
        ac: true,
        breakfast: true,
        price: '$350',
        image:
          'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?ixlib=rb-1.2.1&auto=format&fit=crop&w=800&q=80',
      },
      {
        title: 'Ocean View VIP Villa',
        rating: 4.8,
        wifi: true,
        ac: true,
        breakfast: true,
        price: '$450',
        image:
          'https://images.unsplash.com/photo-1499793983690-e29da59ef1c2?ixlib=rb-1.2.1&auto=format&fit=crop&w=800&q=80',
      },
    ];
  }
}
