import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';

@Component({
  standalone: true,
  imports: [SharedModule],
  selector: 'app-checkout',
  templateUrl: './checkout.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./checkout.component.css'],
})
export class CheckoutComponent implements OnInit {
  constructor() {}

  ngOnInit(): void {}

  completeBooking() {
    console.log('Completing booking...');
    // Call ReservationService -> POST /api/reservations
  }
}
