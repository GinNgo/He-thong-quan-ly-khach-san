import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';

interface SubscriptionPlan {
  id: number;
  code: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  durationDays: number;
}

interface AccountSubscription {
  id: number;
  plan: SubscriptionPlan;
  status: string;
  startAt: string;
  endAt: string;
}

@Component({
  selector: 'app-subscription-billing',
  standalone: true,
  imports: [CommonModule, FeedbackStateComponent],
  templateUrl: './subscription-billing.component.html',
  styleUrls: ['./subscription-billing.component.css']
})
export class SubscriptionBillingComponent implements OnInit {
  private http = inject(HttpClient);
  private cdr = inject(ChangeDetectorRef);

  plans: SubscriptionPlan[] = [];
  mySubscription: AccountSubscription | null = null;
  isLoading = true;
  plansError = '';
  subscriptionError = '';
  private loadingPlans = true;
  private loadingSubscription = true;
  readonly purchaseUnavailableMessage = 'Thanh toán online chưa hỗ trợ. Vui lòng liên hệ quản trị viên.';

  ngOnInit() {
    this.loadPlans();
    this.loadMySubscription();
  }

  loadPlans(): void {
    this.loadingPlans = true;
    this.plansError = '';
    this.updateLoadingState();
    this.http.get<SubscriptionPlan[]>(`${environment.apiUrl}/subscriptions/plans`)
      .subscribe({
        next: (data) => {
          this.plans = data;
          this.loadingPlans = false;
          this.updateLoadingState();
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.plansError = err?.error?.message || 'Không thể tải danh sách gói dịch vụ.';
          this.loadingPlans = false;
          this.updateLoadingState();
          this.cdr.markForCheck();
        }
      });
  }

  loadMySubscription(): void {
    this.loadingSubscription = true;
    this.subscriptionError = '';
    this.updateLoadingState();
    this.http.get<AccountSubscription[]>(`${environment.apiUrl}/subscriptions/me`)
      .subscribe({
        next: (data) => {
          if (data && data.length > 0) {
            this.mySubscription = data[0];
          }
          this.loadingSubscription = false;
          this.updateLoadingState();
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.subscriptionError = err?.error?.message || 'Không thể tải gói hiện tại.';
          this.loadingSubscription = false;
          this.updateLoadingState();
          this.cdr.markForCheck();
        }
      });
  }

  private updateLoadingState(): void {
    this.isLoading = this.loadingPlans || this.loadingSubscription;
  }
}
