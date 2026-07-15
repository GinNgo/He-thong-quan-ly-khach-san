import { CommonModule } from '@angular/common';
import { Component, ChangeDetectorRef, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { finalize } from 'rxjs';
import { UserService } from '../../../core/services/user';

@Component({
  selector: 'app-account-settings', standalone: true, imports: [CommonModule, ReactiveFormsModule, RouterModule],
  template: `
    <main class="settings-page"><header><a routerLink="/profile"><i class="pi pi-arrow-left"></i> Tài khoản</a><h1>Cài đặt tài khoản</h1><p>Thay đổi mật khẩu đăng nhập của bạn.</p></header>
      <section><form [formGroup]="form" (ngSubmit)="submit()">
        <label>Mật khẩu hiện tại<input type="password" formControlName="currentPassword" autocomplete="current-password"></label>
        <label>Mật khẩu mới<input type="password" formControlName="newPassword" autocomplete="new-password"><small>Tối thiểu 8 ký tự.</small></label>
        <label>Nhập lại mật khẩu mới<input type="password" formControlName="confirmPassword" autocomplete="new-password"></label>
        <div *ngIf="error" class="alert error">{{ error }}</div><div *ngIf="success" class="alert success">{{ success }}</div>
        <button type="submit" [disabled]="form.invalid || saving">{{ saving ? 'Đang lưu...' : 'Đổi mật khẩu' }}</button>
      </form></section>
    </main>`,
  styles: [`
    .settings-page{max-width:720px;margin:auto;padding:38px 18px 70px}header{margin-bottom:22px}header a{color:#1d4ed8;text-decoration:none;font-weight:700}h1{font-size:30px;margin:20px 0 6px;color:#0f172a}p{margin:0;color:#64748b}section{background:#fff;border:1px solid #e2e8f0;padding:28px}form{display:grid;gap:18px}label{display:flex;flex-direction:column;gap:7px;color:#334155;font-weight:700;font-size:13px}input{min-height:44px;border:1px solid #cbd5e1;padding:0 12px;font:inherit}small{color:#64748b;font-weight:400}button{justify-self:start;min-height:44px;padding:0 18px;border:0;background:#1d4ed8;color:#fff;font-weight:700;cursor:pointer}button:disabled{opacity:.55}.alert{padding:12px}.error{background:#fef2f2;color:#b91c1c}.success{background:#ecfdf5;color:#047857}
  `]
})
export class AccountSettingsComponent {
  private readonly fb = inject(FormBuilder); private readonly users = inject(UserService); private readonly cdr = inject(ChangeDetectorRef);
  saving = false; error = ''; success = '';
  readonly form = this.fb.nonNullable.group({ currentPassword: ['', Validators.required], newPassword: ['', [Validators.required, Validators.minLength(8)]], confirmPassword: ['', Validators.required] });
  submit(): void {
    if (this.form.invalid) return;
    const value = this.form.getRawValue();
    if (value.newPassword !== value.confirmPassword) { this.error = 'Mật khẩu nhập lại chưa khớp.'; return; }
    this.saving = true; this.error = ''; this.success = '';
    this.users.changePassword({ currentPassword: value.currentPassword, newPassword: value.newPassword }).pipe(finalize(() => this.saving = false)).subscribe({
      next: () => { this.success = 'Mật khẩu đã được thay đổi.'; this.form.reset(); this.cdr.detectChanges(); },
      error: () => { this.error = 'Không thể đổi mật khẩu. Vui lòng kiểm tra mật khẩu hiện tại.'; this.cdr.detectChanges(); }
    });
  }
}
