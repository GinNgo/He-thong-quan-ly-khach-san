import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import {
  OwnerInvitationResponse,
  OwnershipResponsibilitySummary,
  PropertyOwnerMembership,
  PropertyOwnerDirectory,
  PropertyOwnershipService,
} from '../../../core/services/property-ownership.service';

@Component({
  selector: 'app-property-ownership',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './property-ownership.component.html',
  styleUrls: ['./property-ownership.component.css'],
})
export class PropertyOwnershipComponent implements OnInit {
  private readonly ownership = inject(PropertyOwnershipService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  propertyId?: number;
  directory: PropertyOwnerDirectory | null = null;
  loading = true;
  loadError = '';
  actionError = '';
  actionMessage = '';
  busyAction = '';

  invitationEmail = '';
  invitationResult: OwnerInvitationResponse | null = null;
  transferTargetUserId?: number;
  currentPassword = '';
  responsibilityAccepted = false;
  removeTarget: PropertyOwnerMembership | null = null;
  removalReason = '';
  leaveEditorOpen = false;
  leaveReason = '';

  ngOnInit(): void {
    const propertyId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    if (!Number.isInteger(propertyId) || propertyId <= 0) {
      this.loading = false;
      this.loadError = 'Select a managed property before opening owner administration.';
      return;
    }
    this.propertyId = propertyId;
    this.loadOwners();
  }

  get actor() {
    return this.directory?.actor ?? null;
  }

  get eligibleTransferOwners(): PropertyOwnerMembership[] {
    return (this.directory?.owners ?? []).filter(owner =>
      owner.role === 'CO_OWNER' && owner.status === 'ACTIVE' && owner.canReceivePrimary
    );
  }

  get selectedTransferOwner(): PropertyOwnerMembership | null {
    return this.directory?.owners.find(owner => owner.userId === this.transferTargetUserId) ?? null;
  }

  get pendingResponsibility(): OwnershipResponsibilitySummary | null {
    return this.directory?.pendingTransfer?.responsibility ?? null;
  }

  get canAcceptPendingTransfer(): boolean {
    const transfer = this.directory?.pendingTransfer;
    return Boolean(
      transfer &&
      this.actor?.userId === transfer.targetUserId &&
      transfer.status === 'PENDING' &&
      !this.isTransferExpired(transfer.expiresAt)
    );
  }

  get canCancelPendingTransfer(): boolean {
    const transfer = this.directory?.pendingTransfer;
    return Boolean(transfer && this.actor?.canTransferPrimary && transfer.status === 'PENDING');
  }

  get removalReasonValid(): boolean {
    return this.reasonValid(this.removalReason);
  }

  get leaveReasonValid(): boolean {
    return this.reasonValid(this.leaveReason);
  }

  loadOwners(): void {
    if (!this.propertyId || this.busyAction) return;
    this.loading = true;
    this.loadError = '';
    this.ownership.getOwners(this.propertyId).pipe(
      finalize(() => this.loading = false)
    ).subscribe({
      next: directory => this.directory = directory,
      error: () => this.loadError = 'Unable to load the owner directory. Refresh and try again.'
    });
  }

  inviteCoOwner(): void {
    if (!this.propertyId || !this.actor?.canInvite || this.busyAction) return;
    const email = this.invitationEmail.trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      this.actionError = 'Enter a valid invitation email address.';
      return;
    }
    this.beginAction('invite');
    this.ownership.inviteCoOwner(this.propertyId, email).pipe(
      finalize(() => this.busyAction = '')
    ).subscribe({
      next: invitation => {
        this.invitationResult = invitation;
        this.invitationEmail = '';
        this.actionMessage = `Invitation sent to ${invitation.email}. It expires in 7 days.`;
      },
      error: error => this.actionError = this.safeError(error)
    });
  }

  cancelInvitation(): void {
    const invitation = this.invitationResult;
    if (!this.propertyId || !invitation || !this.actor?.canInvite || this.busyAction) return;
    this.beginAction('cancel-invitation');
    this.ownership.cancelInvitation(this.propertyId, invitation.invitationId).pipe(
      finalize(() => this.busyAction = '')
    ).subscribe({
      next: () => {
        this.invitationResult = null;
        this.actionMessage = 'Owner invitation cancelled.';
      },
      error: error => this.actionError = this.safeError(error)
    });
  }

  initiateTransfer(): void {
    if (!this.propertyId || !this.actor?.canTransferPrimary || this.busyAction) return;
    const target = this.selectedTransferOwner;
    if (!target?.canReceivePrimary) {
      this.actionError = 'Choose an active co-owner who has completed the 7-day cooling period.';
      return;
    }
    if (!this.currentPassword) {
      this.actionError = 'Enter your current password to re-authenticate this transfer.';
      return;
    }
    const currentPassword = this.currentPassword;
    this.currentPassword = '';
    this.beginAction('transfer');
    this.ownership.initiatePrimaryTransfer(this.propertyId, target.userId, currentPassword).pipe(
      finalize(() => this.busyAction = '')
    ).subscribe({
      next: transfer => {
        if (this.directory) this.directory = { ...this.directory, pendingTransfer: transfer };
        this.actionMessage = 'Primary ownership transfer created. The recipient must review and accept within 7 days.';
      },
      error: error => this.actionError = this.safeError(error)
    });
  }

  acceptTransfer(): void {
    const transfer = this.directory?.pendingTransfer;
    if (!transfer || !this.canAcceptPendingTransfer || !this.responsibilityAccepted || this.busyAction) return;
    this.beginAction('accept-transfer');
    this.ownership.acceptPrimaryTransfer(transfer.transferId, true).pipe(
      finalize(() => this.busyAction = '')
    ).subscribe({
      next: directory => {
        this.directory = directory;
        this.responsibilityAccepted = false;
        this.actionMessage = 'Primary ownership transferred. Authorization and session permissions are being refreshed.';
      },
      error: error => this.actionError = this.safeError(error)
    });
  }

  cancelTransfer(): void {
    const transfer = this.directory?.pendingTransfer;
    if (!transfer || !this.canCancelPendingTransfer || this.busyAction) return;
    this.beginAction('cancel-transfer');
    this.ownership.cancelPrimaryTransfer(transfer.transferId).pipe(
      finalize(() => this.busyAction = '')
    ).subscribe({
      next: () => {
        if (this.directory) this.directory = { ...this.directory, pendingTransfer: null };
        this.responsibilityAccepted = false;
        this.actionMessage = 'Primary ownership transfer cancelled.';
      },
      error: error => this.actionError = this.safeError(error)
    });
  }

  openRemoval(owner: PropertyOwnerMembership): void {
    if (!this.actor?.canInvite || owner.role !== 'CO_OWNER' || this.busyAction) return;
    this.removeTarget = owner;
    this.removalReason = '';
    this.clearFeedback();
  }

  removeCoOwner(): void {
    if (!this.propertyId || !this.removeTarget || this.busyAction) return;
    const reason = this.validReason(this.removalReason);
    if (!reason) return;
    const targetUserId = this.removeTarget.userId;
    this.beginAction(`remove-${targetUserId}`);
    this.ownership.removeCoOwner(this.propertyId, targetUserId, reason).pipe(
      finalize(() => this.busyAction = '')
    ).subscribe({
      next: () => {
        this.removeTarget = null;
        this.removalReason = '';
        this.actionMessage = 'Co-owner access removed. Historical actions remain retained.';
        this.refreshAfterMutation();
      },
      error: error => this.actionError = this.safeError(error)
    });
  }

  leaveProperty(): void {
    if (!this.propertyId || !this.actor?.canLeave || this.busyAction) return;
    const reason = this.validReason(this.leaveReason);
    if (!reason) return;
    this.beginAction('leave');
    this.ownership.leaveProperty(this.propertyId, reason).pipe(
      finalize(() => this.busyAction = '')
    ).subscribe({
      next: () => void this.router.navigate(['/']),
      error: error => this.actionError = this.safeError(error)
    });
  }

  cancelEditor(): void {
    if (this.busyAction) return;
    this.removeTarget = null;
    this.removalReason = '';
    this.leaveEditorOpen = false;
    this.leaveReason = '';
  }

  coolingLabel(owner: PropertyOwnerMembership): string {
    if (owner.canReceivePrimary) return 'Transfer eligible';
    return owner.coolingEndsAt ? `Cooling until ${new Date(owner.coolingEndsAt).toLocaleDateString('vi-VN')}` : 'Not transfer eligible';
  }

  isTransferExpired(expiresAt: string): boolean {
    const expiresAtMs = Date.parse(expiresAt);
    return !Number.isFinite(expiresAtMs) || expiresAtMs <= Date.now();
  }

  private refreshAfterMutation(): void {
    if (!this.propertyId) return;
    this.ownership.getOwners(this.propertyId).subscribe({
      next: directory => this.directory = directory,
      error: () => this.actionError = 'The action succeeded, but the owner list could not be refreshed.'
    });
  }

  private validReason(value: string): string | null {
    const reason = value.trim();
    if (reason.length < 10 || reason.length > 500) {
      this.actionError = 'Enter a reason between 10 and 500 characters.';
      return null;
    }
    return reason;
  }

  private beginAction(action: string): void {
    this.busyAction = action;
    this.clearFeedback();
  }

  private clearFeedback(): void {
    this.actionError = '';
    this.actionMessage = '';
  }

  private safeError(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'The ownership action could not be completed. Please retry.';
    const body = error.error && typeof error.error === 'object' ? error.error as Record<string, unknown> : {};
    const code = typeof body['code'] === 'string' ? body['code'] : '';
    if (error.status === 403 || code === 'PRIMARY_OWNER_REQUIRED') return 'Only the current primary owner can perform this action.';
    if (error.status === 401 || code === 'OWNERSHIP_REAUTH_FAILED') return 'Current-password verification failed. No transfer was created.';
    if (code === 'OWNER_COOLING_PERIOD') return 'This owner is still in the 7-day cooling period.';
    if (code === 'OWNERSHIP_TRANSFER_BLOCKED') return 'Transfer is blocked by unresolved subscription responsibility. Review the summary and resolve the blockers.';
    if (code === 'OWNERSHIP_FINANCIAL_READINESS_UNAVAILABLE') return 'Financial responsibility readiness is unavailable. No transfer was created; retry after billing data is available.';
    if (code === 'OWNERSHIP_TRANSFER_EXPIRED') return 'This transfer request has expired. Refresh before creating a new request.';
    if (code === 'OWNER_INVITATION_CONFLICT') return 'An active membership or pending invitation already exists for this email.';
    if (code === 'OWNER_MEMBERSHIP_NOT_ACTIVE') return 'This owner membership is already inactive. Refresh to view the latest state.';
    if (code === 'OWNERSHIP_TRANSFER_CONFLICT') return 'A pending or completed ownership transfer prevents this action. Refresh to view the latest state.';
    if (error.status === 409) return 'The ownership state changed in another session. Refresh before trying again.';
    if (error.status === 404) return 'The ownership record no longer exists. Refresh before trying again.';
    if (error.status === 400) return 'Review the entered information and required confirmations.';
    return 'The ownership action could not be completed. Please retry.';
  }

  private reasonValid(value: string): boolean {
    const length = value.trim().length;
    return length >= 10 && length <= 500;
  }
}
