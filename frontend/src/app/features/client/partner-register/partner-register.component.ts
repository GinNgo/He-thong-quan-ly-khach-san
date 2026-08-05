import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth';
import { PropertyLocation, PropertyService } from '../../../core/services/property.service';
import { PartnerRegistrationService } from './partner-registration.service';

interface ApiErrorResponse {
  message?: string;
  fieldErrors?: Record<string, string>;
}

@Component({
  selector: 'app-partner-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './partner-register.component.html',
  styleUrls: ['./partner-register.component.css']
})
export class PartnerRegisterComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly propertyService = inject(PropertyService);
  private readonly registrationService = inject(PartnerRegistrationService);

  readonly registerForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(256)]],
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    phone: ['', [Validators.required, Validators.pattern(/^[0-9+() .-]{8,30}$/)]],
    propertyName: ['', [Validators.required, Validators.maxLength(255)]],
    provinceId: [null as number | null, Validators.required],
    wardId: [null as number | null, Validators.required],
    address: ['', [Validators.required, Validators.maxLength(1000)]]
  });

  provinces: PropertyLocation[] = [];
  wards: PropertyLocation[] = [];
  locationsLoading = false;
  locationError = '';
  isLoading = false;
  errorMessage = '';
  isSignedIn = false;

  ngOnInit(): void {
    this.isSignedIn = this.authService.isLoggedIn();
    this.configureAccountValidators();
    this.loadProvinces();
  }

  onProvinceChange(): void {
    const provinceId = this.registerForm.controls.provinceId.value;
    this.registerForm.controls.wardId.setValue(null);
    this.wards = [];
    this.locationError = '';
    if (provinceId === null) return;

    this.locationsLoading = true;
    this.propertyService.getWards(provinceId).pipe(
      finalize(() => this.locationsLoading = false)
    ).subscribe({
      next: wards => this.wards = wards,
      error: () => this.locationError = 'Không thể tải danh sách phường/xã. Vui lòng thử lại.'
    });
  }

  onSubmit(): void {
    this.normalizeTextControls();
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    const value = this.registerForm.getRawValue();
    const propertyPayload = {
      propertyName: value.propertyName.trim(),
      provinceId: value.provinceId!,
      wardId: value.wardId!,
      address: value.address.trim()
    };
    const request$ = this.isSignedIn
      ? this.registrationService.convertAuthenticated(propertyPayload)
      : this.registrationService.registerAnonymous({
          email: value.email.trim(),
          password: value.password,
          fullName: value.fullName.trim(),
          phone: value.phone.trim(),
          ...propertyPayload
        });

    this.isLoading = true;
    this.errorMessage = '';
    request$.pipe(
      finalize(() => this.isLoading = false)
    ).subscribe({
      next: () => this.isSignedIn
        ? this.router.navigate(['/partner/registration-status'])
        : this.router.navigate(['/login'], { queryParams: { registration: 'partner-draft' } }),
      error: (error: HttpErrorResponse) => this.errorMessage = this.resolveError(error)
    });
  }

  isInvalid(controlName: keyof typeof this.registerForm.controls): boolean {
    const control = this.registerForm.controls[controlName];
    return control.invalid && control.touched;
  }

  private loadProvinces(): void {
    this.locationsLoading = true;
    this.locationError = '';
    this.propertyService.getProvinces().pipe(
      finalize(() => this.locationsLoading = false)
    ).subscribe({
      next: provinces => this.provinces = provinces,
      error: () => this.locationError = 'Không thể tải danh sách tỉnh/thành phố. Vui lòng thử lại.'
    });
  }

  private resolveError(error: HttpErrorResponse): string {
    const body = error.error as ApiErrorResponse | string | null;
    if (typeof body === 'string' && body.trim()) return body;
    if (body && typeof body === 'object') {
      const firstFieldError = Object.values(body.fieldErrors ?? {})[0];
      return firstFieldError || body.message || 'Không thể gửi hồ sơ đối tác.';
    }
    return 'Không thể gửi hồ sơ đối tác. Vui lòng thử lại.';
  }

  private normalizeTextControls(): void {
    const controls = this.registerForm.controls;
    controls.email.setValue(controls.email.value.trim(), { emitEvent: false });
    controls.fullName.setValue(controls.fullName.value.trim(), { emitEvent: false });
    controls.phone.setValue(controls.phone.value.trim(), { emitEvent: false });
    controls.propertyName.setValue(controls.propertyName.value.trim(), { emitEvent: false });
    controls.address.setValue(controls.address.value.trim(), { emitEvent: false });
  }

  private configureAccountValidators(): void {
    if (!this.isSignedIn) return;

    const controls = this.registerForm.controls;
    [controls.email, controls.password, controls.fullName, controls.phone].forEach(control => {
      control.clearValidators();
      control.updateValueAndValidity({ emitEvent: false });
    });
  }
}
