import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';

import { AuthService } from '@app/core/services/auth';
import { SharedModule } from '@app/shared/shared.module';

@Component({
  standalone: true,
  imports: [SharedModule, RouterModule],
  selector: 'app-reset-password',
  templateUrl: './reset-password.component.html',
  styleUrls: ['./reset-password.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPasswordComponent {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly token = this.route.snapshot.queryParamMap.get('token') || '';
  newPassword = '';
  confirmPassword = '';
  errorMessage = '';
  successMessage = '';
  isLoading = false;

  submit(): void {
    if (!this.token) {
      this.errorMessage = 'This reset link is invalid or incomplete.';
      return;
    }
    if (this.newPassword.length < 8) {
      this.errorMessage = 'Password must be at least 8 characters.';
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage = 'Passwords do not match.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';
    this.authService.resetPassword(this.token, this.newPassword).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = 'Password updated. You can sign in with the new password.';
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error?.error?.message || 'This reset link is invalid or has expired.';
        this.cdr.markForCheck();
      },
    });
  }
}
