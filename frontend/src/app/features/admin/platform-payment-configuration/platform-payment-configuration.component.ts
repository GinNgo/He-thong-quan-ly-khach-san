import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';
import {
  PlatformBillingService,
  PlatformPaymentConfiguration,
  PlatformPaymentConfigurationRequest,
  PlatformPaymentEnvironment,
  PlatformPaymentReadiness,
} from '../../../core/services/platform-billing.service';

@Component({
  selector: 'app-platform-payment-configuration',
  standalone: true,
  imports: [CommonModule, FormsModule, FeedbackStateComponent],
  templateUrl: './platform-payment-configuration.component.html',
  styleUrls: ['./platform-payment-configuration.component.css'],
})
export class PlatformPaymentConfigurationComponent implements OnInit {
  private readonly billing = inject(PlatformBillingService);
  private readonly cdr = inject(ChangeDetectorRef);

  configurations: PlatformPaymentConfiguration[] = [];
  selected: PlatformPaymentConfiguration | null = null;
  readiness: PlatformPaymentReadiness | null = null;
  form: PlatformPaymentConfigurationRequest = {
    provider: 'SIMULATOR', environment: 'SIMULATOR', enabled: false,
    secretReference: '', bankName: '', bankAccountMasked: '', callbackUrl: '',
  };
  loading = true;
  saving = false;
  validating = false;
  error = '';
  message = '';
  readonly environments: PlatformPaymentEnvironment[] = ['SIMULATOR', 'SANDBOX', 'PRODUCTION'];

  ngOnInit(): void { this.loadConfigurations(); }

  loadConfigurations(): void {
    this.loading = true;
    this.error = '';
    this.billing.getPaymentConfigurations().subscribe({
      next: (data) => {
        this.configurations = data;
        if (!this.selected && data.length) this.select(data[0]);
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.error = err?.error?.message || 'Unable to load platform merchant configurations.';
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  select(configuration: PlatformPaymentConfiguration): void {
    this.selected = configuration;
    this.form = {
      provider: configuration.provider,
      environment: configuration.environment,
      enabled: configuration.enabled,
      secretReference: '',
      bankName: configuration.bankName || '',
      bankAccountMasked: configuration.bankAccountMasked || '',
      callbackUrl: configuration.callbackUrl || '',
    };
    this.readiness = null;
    this.message = '';
  }

  save(): void {
    this.saving = true;
    this.error = '';
    this.message = '';
    this.billing.configurePayment({
      ...this.form,
      secretReference: this.form.secretReference?.trim() || null,
    }).subscribe({
      next: (configuration) => {
        this.message = 'Platform merchant configuration saved. Secrets remain outside the browser.';
        this.selected = configuration;
        this.saving = false;
        this.loadConfigurations();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.error = err?.error?.message || 'The platform merchant configuration could not be saved.';
        this.saving = false;
        this.cdr.markForCheck();
      },
    });
  }

  validate(): void {
    this.validating = true;
    this.error = '';
    this.billing.validatePaymentConfiguration(this.form.provider).subscribe({
      next: (readiness) => {
        this.readiness = readiness;
        this.validating = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.error = err?.error?.message || 'Readiness validation failed.';
        this.validating = false;
        this.cdr.markForCheck();
      },
    });
  }

  trackByConfiguration(_: number, configuration: PlatformPaymentConfiguration): string {
    return `${configuration.provider}-${configuration.environment}`;
  }
}
