import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './forbidden.component.html',
  styleUrls: ['./forbidden.component.css']
})
export class ForbiddenComponent {

  constructor(private router: Router) {}

  returnToDashboard() {
    this.router.navigate(['/']);
  }

  goBack() {
    window.history.back();
  }
}
