import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

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
  imports: [CommonModule],
  templateUrl: './subscription-billing.component.html',
  styleUrls: ['./subscription-billing.component.css']
})
export class SubscriptionBillingComponent implements OnInit {
  private http = inject(HttpClient);

  plans: SubscriptionPlan[] = [];
  mySubscription: AccountSubscription | null = null;
  isLoading = true;

  ngOnInit() {
    this.loadPlans();
    this.loadMySubscription();
  }

  loadPlans() {
    this.http.get<SubscriptionPlan[]>(`${environment.apiUrl}/subscriptions/plans`)
      .subscribe({
        next: (data) => {
          this.plans = data;
        },
        error: (err) => console.error(err)
      });
  }

  loadMySubscription() {
    this.http.get<AccountSubscription[]>(`${environment.apiUrl}/subscriptions/me`)
      .subscribe({
        next: (data) => {
          if (data && data.length > 0) {
            this.mySubscription = data[0];
          }
          this.isLoading = false;
        },
        error: (err) => {
          console.error(err);
          this.isLoading = false;
        }
      });
  }

  buyPlan(planId: number) {
    if (confirm('Chức năng mô phỏng thanh toán VNPay sẽ được thực hiện tại đây. Bấm OK để tự động nâng cấp gói.')) {
      // Mock payment success by calling an order creation and payment success API
      // In a real app, this calls /api/v1/subscription-billing/order, redirects to VNPay, then returns.
      alert('Chức năng Mock đang được xử lý. Sẽ tích hợp API VNPay thực tế sau.');
    }
  }
}
