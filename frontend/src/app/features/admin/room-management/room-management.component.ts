import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';

@Component({
  standalone: true,
  imports: [SharedModule],
  selector: 'app-room-management',
  templateUrl: './room-management.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./room-management.component.css'],
})
export class RoomManagementComponent implements OnInit {
  rooms: any[] = [];
  floors: any[] = [];
  statuses: any[] = [];

  constructor() {}

  ngOnInit() {
    this.floors = [
      { label: 'All Floors', value: null },
      { label: 'Floor 1', value: 1 },
      { label: 'Floor 2', value: 2 },
      { label: 'Floor 3', value: 3 },
    ];

    this.statuses = [
      { label: 'All Statuses', value: null },
      { label: 'Active', value: 'Active' },
      { label: 'Maintenance', value: 'Maintenance' },
      { label: 'Cleaning', value: 'Cleaning' },
    ];

    // Mock data matching the screen.png template
    this.rooms = [
      {
        roomNumber: '101',
        roomType: 'Deluxe Suite',
        floor: 1,
        status: 'Active',
        price: '$250/night',
      },
      {
        roomNumber: '102',
        roomType: 'Standard King',
        floor: 1,
        status: 'Maintenance',
        price: '$180/night',
      },
      {
        roomNumber: '201',
        roomType: 'Deluxe Suite',
        floor: 2,
        status: 'Cleaning',
        price: '$250/night',
      },
      {
        roomNumber: '202',
        roomType: 'Standard King',
        floor: 2,
        status: 'Active',
        price: '$180/night',
      },
    ];
  }
}
