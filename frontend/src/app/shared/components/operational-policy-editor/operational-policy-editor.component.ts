import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  OperationalPolicy,
  OperationalPolicyRequest,
  OperationalPolicyService,
} from '../../../core/services/operational-policy.service';

@Component({
  selector: 'app-operational-policy-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './operational-policy-editor.component.html',
  styles: [`
    :host{display:block;margin-top:1.25rem}.policy-shell{border:1px solid #d8e2ea;border-radius:16px;padding:1rem;background:#f8fbfd}
    header{display:flex;justify-content:space-between;gap:1rem;align-items:start}h3{margin:0}p{color:#52606d}.status{font-size:.8rem;padding:.25rem .55rem;border-radius:999px;background:#e8f1f8}
    form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:.8rem;margin-top:1rem}label{display:grid;gap:.3rem;font-weight:600}.wide{grid-column:1/-1}
    input,textarea{border:1px solid #bac8d3;border-radius:8px;padding:.65rem;font:inherit}textarea{min-height:80px;resize:vertical}
    .actions{grid-column:1/-1;display:flex;gap:.6rem}.error{color:#a61b1b}.versions{display:grid;gap:.45rem;margin-top:1rem}.version{display:flex;justify-content:space-between;gap:1rem;align-items:center;padding:.65rem;background:#fff;border-radius:10px}
    button{border:0;border-radius:8px;padding:.55rem .8rem;cursor:pointer;background:#0b6b91;color:#fff}button.secondary{background:#e4edf2;color:#173642}button:disabled{opacity:.55;cursor:not-allowed}
    @media(max-width:720px){form{grid-template-columns:1fr}.wide{grid-column:auto}header,.version{align-items:stretch;flex-direction:column}}
  `]
})
export class OperationalPolicyEditorComponent implements OnChanges {
  @Input({ required: true }) propertyId!: number;
  @Input() editable = true;

  private readonly api = inject(OperationalPolicyService);
  policies: OperationalPolicy[] = [];
  loading = false;
  saving = false;
  error = '';
  editingId?: number;
  draft: OperationalPolicyRequest = this.emptyDraft();

  ngOnChanges(): void {
    if (Number.isInteger(this.propertyId) && this.propertyId > 0) this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.list(this.propertyId).pipe(finalize(() => this.loading = false)).subscribe({
      next: policies => { this.policies = policies; this.selectDraftOrNew(); },
      error: error => this.error = error?.error?.message || 'Không thể tải chính sách lưu trú.'
    });
  }

  newVersion(): void {
    const latest = this.policies[0];
    this.editingId = undefined;
    this.draft = latest ? this.copy(latest) : this.emptyDraft();
    this.draft.effectiveFrom = this.defaultEffectiveFrom();
  }

  edit(policy: OperationalPolicy): void {
    if (policy.status !== 'DRAFT') return;
    this.editingId = policy.id;
    this.draft = this.copy(policy);
  }

  save(): void {
    if (this.saving || !this.valid()) return;
    this.saving = true;
    this.error = '';
    const request = { ...this.draft, effectiveFrom: this.normalizeDateTime(this.draft.effectiveFrom) };
    const operation = this.editingId
      ? this.api.update(this.propertyId, this.editingId, request)
      : this.api.create(this.propertyId, request);
    operation.pipe(finalize(() => this.saving = false)).subscribe({
      next: () => this.load(),
      error: error => this.error = error?.error?.message || 'Không thể lưu phiên bản chính sách.'
    });
  }

  publish(policy: OperationalPolicy): void {
    if (this.saving || policy.status !== 'DRAFT') return;
    this.saving = true;
    this.error = '';
    this.api.publish(this.propertyId, policy.id).pipe(finalize(() => this.saving = false)).subscribe({
      next: () => this.load(),
      error: error => this.error = error?.error?.message || 'Không thể công bố chính sách.'
    });
  }

  private selectDraftOrNew(): void {
    const draft = this.policies.find(item => item.status === 'DRAFT');
    if (draft) this.edit(draft); else this.newVersion();
  }

  private valid(): boolean {
    const required = [this.draft.effectiveFrom, this.draft.checkInVi, this.draft.checkOutVi,
      this.draft.cancellationVi, this.draft.childPolicyVi, this.draft.petPolicyVi,
      this.draft.smokingPolicyVi, this.draft.houseRulesVi];
    if (required.some(value => !value?.trim())) {
      this.error = 'Vui lòng nhập ngày hiệu lực và đầy đủ nội dung tiếng Việt.';
      return false;
    }
    return true;
  }

  private copy(policy: OperationalPolicy): OperationalPolicyRequest {
    return {
      effectiveFrom: policy.effectiveFrom.slice(0, 16),
      checkInVi: policy.checkInVi, checkInEn: policy.checkInEn || '',
      checkOutVi: policy.checkOutVi, checkOutEn: policy.checkOutEn || '',
      cancellationVi: policy.cancellationVi, cancellationEn: policy.cancellationEn || '',
      childPolicyVi: policy.childPolicyVi, childPolicyEn: policy.childPolicyEn || '',
      petPolicyVi: policy.petPolicyVi, petPolicyEn: policy.petPolicyEn || '',
      smokingPolicyVi: policy.smokingPolicyVi, smokingPolicyEn: policy.smokingPolicyEn || '',
      houseRulesVi: policy.houseRulesVi, houseRulesEn: policy.houseRulesEn || ''
    };
  }

  private emptyDraft(): OperationalPolicyRequest {
    return { effectiveFrom: this.defaultEffectiveFrom(), checkInVi: '', checkOutVi: '', cancellationVi: '',
      childPolicyVi: '', petPolicyVi: '', smokingPolicyVi: '', houseRulesVi: '' };
  }

  private defaultEffectiveFrom(): string {
    const date = new Date(Date.now() + 5 * 60 * 1000);
    date.setSeconds(0, 0);
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
  }

  private normalizeDateTime(value: string): string {
    return value.length === 16 ? `${value}:00` : value;
  }
}
