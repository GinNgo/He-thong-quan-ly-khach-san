import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';
import {
  PlatformBillingService,
  PlatformCatalogPlan,
  PlatformSubscriptionEntitlement,
  PlatformOrder,
  PlatformPolicyAvailability,
} from '../../../core/services/platform-billing.service';
import { PlatformPaymentPanelComponent } from './platform-payment-panel.component';

@Component({
  selector: 'app-subscription-billing',
  standalone: true,
  imports: [CommonModule, FeedbackStateComponent, PlatformPaymentPanelComponent],
  templateUrl: './subscription-billing.component.html',
  styleUrls: ['./subscription-billing.component.css'],
})
export class SubscriptionBillingComponent implements OnInit {
  private readonly platformBilling = inject(PlatformBillingService);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);

  plans: PlatformCatalogPlan[] = [];
  currentEntitlement: PlatformSubscriptionEntitlement | null = null;
  policyAvailability: PlatformPolicyAvailability | null = null;
  latestOrder: PlatformOrder | null = null;
  activePropertyId?: number;
  isLoading = true;
  plansError = '';
  subscriptionError = '';
  policyError = '';
  orderError = '';
  creatingOrderFor?: number;
  private loadingPlans = true;
  private loadingSubscription = true;
  loadingPolicy = true;

  ngOnInit(): void {
    const propertyId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    this.activePropertyId = Number.isInteger(propertyId) && propertyId > 0 ? propertyId : undefined;
    this.loadPlans();
    this.loadEntitlement();
    this.loadPolicyAvailability();
  }

  loadPlans(): void {
    this.loadingPlans = true;
    this.plansError = '';
    this.updateLoadingState();
    this.platformBilling.getCatalog().subscribe({
      next: (data) => {
        this.plans = data;
        this.loadingPlans = false;
        this.updateLoadingState();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.plansError = err?.error?.message || 'Unable to load the backend subscription catalog.';
        this.loadingPlans = false;
        this.updateLoadingState();
        this.cdr.markForCheck();
      },
    });
  }

  loadEntitlement(): void {
    this.loadingSubscription = true;
    this.subscriptionError = '';
    this.updateLoadingState();
    if (!this.activePropertyId) {
      this.currentEntitlement = null;
      this.subscriptionError = 'Select a managed property to view its entitlement.';
      this.loadingSubscription = false;
      this.updateLoadingState();
      return;
    }
    this.platformBilling.getEntitlement(this.activePropertyId).subscribe({
      next: (data) => {
        this.currentEntitlement = data;
        this.loadingSubscription = false;
        this.updateLoadingState();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.subscriptionError = err?.error?.message || 'Unable to load the selected property entitlement.';
        this.loadingSubscription = false;
        this.updateLoadingState();
        this.cdr.markForCheck();
      },
    });
  }

  loadPolicyAvailability(): void {
    this.loadingPolicy = true;
    this.policyError = '';
    this.updateLoadingState();
    this.platformBilling.getPolicyAvailability().subscribe({
      next: (data) => {
        this.policyAvailability = data;
        this.loadingPolicy = false;
        this.updateLoadingState();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.policyError = err?.error?.message || 'Unable to load subscription policy status.';
        this.loadingPolicy = false;
        this.updateLoadingState();
        this.cdr.markForCheck();
      },
    });
  }

  createOrder(plan: PlatformCatalogPlan): void {
    if (!this.activePropertyId || this.creatingOrderFor) {
      this.orderError = 'Select a managed property before creating a platform order.';
      return;
    }
    this.orderError = '';
    this.creatingOrderFor = plan.id;
    const idempotencyKey = this.newIdempotencyKey('platform-order');
    const currentPlanId = this.currentEntitlement?.planId ?? undefined;
    const request = currentPlanId === plan.id
      ? this.platformBilling.createRenewalOrder(this.activePropertyId, idempotencyKey)
      : currentPlanId
        ? this.platformBilling.createUpgradeOrder(this.activePropertyId, plan.id, idempotencyKey)
        : this.platformBilling.createPurchaseOrder(this.activePropertyId, plan.id, idempotencyKey);
    request.subscribe({
      next: (order) => {
        this.latestOrder = order;
        this.creatingOrderFor = undefined;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.orderError = err?.error?.message || 'The platform order could not be created.';
        this.creatingOrderFor = undefined;
        this.cdr.markForCheck();
      },
    });
  }

  planName(plan: PlatformCatalogPlan): string {
    return plan.nameVi || plan.nameEn || plan.code;
  }

  featureLimit(limit: number): string {
    return limit === -1 ? 'Unlimited' : String(limit);
  }

  orderActionLabel(plan: PlatformCatalogPlan): string {
    if (this.creatingOrderFor === plan.id) return 'Creating secure order...';
    if (this.currentEntitlement?.planId === plan.id) return 'Create renewal order';
    return this.currentEntitlement?.planId ? 'Create upgrade order' : 'Create purchase order';
  }

  updateLatestOrder(order: PlatformOrder): void {
    this.latestOrder = order;
  }

  private updateLoadingState(): void {
    this.isLoading = this.loadingPlans || this.loadingSubscription || this.loadingPolicy;
  }

  private newIdempotencyKey(prefix: string): string {
    const randomUuid = globalThis.crypto?.randomUUID?.();
    return randomUuid ? `${prefix}-${randomUuid}` : `${prefix}-${Date.now()}`;
  }
}
