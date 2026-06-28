import { Component, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegisterComponent {
  registerObj = {
    fullName: '',
    email: '',
    password: '',
    confirmPassword: '',
    countryCode: '+84',
    phone: '',
    terms: false
  };

  onSubmit() {
    console.log('Registering user: ', this.registerObj);
    // TODO: Connect to AuthService
  }
}
