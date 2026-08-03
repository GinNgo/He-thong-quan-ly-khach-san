import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';

import { EmailVerificationService } from '@app/core/services/email-verification.service';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './verify-email.component.html',
  styleUrls: ['../reset-password/reset-password.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyEmailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly verification = inject(EmailVerificationService);
  private readonly cdr = inject(ChangeDetectorRef);

  loading = true;
  successMessage = '';
  errorMessage = '';

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token') || '';
    if (!token) {
      this.loading = false;
      this.errorMessage = 'Liên kết xác minh không hợp lệ. / The verification link is invalid.';
      return;
    }

    this.verification.confirm(token).subscribe({
      next: (result) => {
        this.loading = false;
        this.successMessage = result.emailChanged
          ? 'Email mới đã được cập nhật. / Your new email was updated.'
          : 'Email đã được xác minh. / Your email was verified.';
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = error?.error?.message
          || 'Liên kết xác minh không hợp lệ hoặc đã hết hạn. / The link is invalid or expired.';
        this.cdr.markForCheck();
      },
    });
  }
}
