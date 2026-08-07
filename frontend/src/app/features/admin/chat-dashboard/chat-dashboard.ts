import { CommonModule } from '@angular/common';
import {
  AfterViewChecked,
  Component,
  DestroyRef,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  ChatConnectionState,
  ChatConversation,
  ChatMessage,
  ChatService,
  SupportAttachment,
  SupportConversationAuditPolicy,
  SupportConversationEvent
} from '../../../core/services/chat.service';
import { AuthService } from '../../../core/services/auth';

const SEND_ACK_TIMEOUT_MS = 10_000;

@Component({
  selector: 'app-chat-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-dashboard.html',
  styleUrl: './chat-dashboard.css'
})
export class ChatDashboardComponent implements OnInit, OnDestroy, AfterViewChecked {
  private readonly chatService = inject(ChatService);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('scrollMe') private scrollContainer?: ElementRef<HTMLElement>;

  readonly conversations = signal<ChatConversation[]>([]);
  readonly selectedConversationId = signal<number | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly hasOlderMessages = signal(false);
  readonly isLoadingOlder = signal(false);
  readonly currentUserId = signal<number | null>(null);
  readonly connectionState = signal<ChatConnectionState>('idle');
  readonly connectionError = signal('');
  readonly conversationState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly conversationError = signal('');
  readonly messagesState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly messagesError = signal('');
  readonly isSending = signal(false);
  readonly sendError = signal('');
  readonly isMutating = signal(false);
  readonly mutationError = signal('');
  readonly conflictRecovery = signal('');
  readonly attachments = signal<SupportAttachment[]>([]);
  readonly attachmentsState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly attachmentError = signal('');
  readonly isUploadingAttachment = signal(false);
  readonly auditEvents = signal<SupportConversationEvent[]>([]);
  readonly auditPolicy = signal<SupportConversationAuditPolicy | null>(null);
  readonly auditState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly auditError = signal('');
  readonly auditPage = signal(0);
  readonly auditTotalPages = signal(0);

  newMessage = '';
  searchQuery = '';
  lifecycleReason = '';
  statusFilter: 'ALL' | 'OPEN' | 'ASSIGNED' | 'ESCALATED' | 'CLOSED' = 'ALL';
  assignmentFilter: 'ALL' | 'UNASSIGNED' | 'MINE' = 'ALL';
  slaFilter: 'ALL' | 'BREACHED' | 'AT_RISK' | 'ON_TRACK' | 'NO_PENDING_RESPONSE' = 'ALL';
  private messagePage = 0;
  private renderedMessageCount = 0;
  private skipAutoScrollOnce = false;
  private sendTimeoutId?: ReturnType<typeof setTimeout>;
  private pendingClientMessageId?: string;
  private pendingContent?: string;

  ngOnInit(): void {
    this.currentUserId.set(this.authService.getCurrentUserId());
    this.chatService.message$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(message => this.handleIncomingMessage(message));
    this.chatService.connectionState$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(state => this.connectionState.set(state));
    this.chatService.connectionError$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(error => this.connectionError.set(error));

    this.chatService.connect('support');
    this.loadAuditPolicy();
    this.loadConversations();
  }

  ngOnDestroy(): void {
    this.clearSendTimeout();
    this.chatService.disconnect();
  }

  ngAfterViewChecked(): void {
    if (this.messages().length === this.renderedMessageCount) return;
    this.renderedMessageCount = this.messages().length;
    if (this.skipAutoScrollOnce) {
      this.skipAutoScrollOnce = false;
    } else {
      this.scrollToBottom();
    }
  }

  scrollToBottom(): void {
    const container = this.scrollContainer?.nativeElement;
    if (!container) return;
    container.scrollTop = container.scrollHeight;
  }

  loadConversations(): void {
    this.conversationState.set('loading');
    this.conversationError.set('');
    this.chatService.getSupportConversations({
      status: this.statusFilter,
      assignment: this.assignmentFilter,
      sla: this.slaFilter,
      query: this.searchQuery,
    }).subscribe({
      next: conversations => {
        this.conversations.set(conversations);
        this.conversationState.set('ready');
        if (this.selectedConversationId()
            && !conversations.some(item => item.conversationId === this.selectedConversationId())) {
          this.selectedConversationId.set(null);
          this.messages.set([]);
        }
      },
      error: () => {
        this.conversationState.set('error');
        this.conversationError.set('Khong the tai danh sach hoi thoai ho tro.');
      }
    });
  }

  retryConnection(): void {
    this.chatService.connect('support');
    this.loadConversations();
  }

  applyFilters(): void {
    this.loadConversations();
  }

  selectConversation(conversation: ChatConversation): void {
    this.selectedConversationId.set(conversation.conversationId);
<<<<<<< HEAD
    this.messagePage = 0;
=======
>>>>>>> codex/ui-functional-audit-polish
    this.messages.set([]);
    this.hasOlderMessages.set(false);
    this.messagesState.set('loading');
    this.messagesError.set('');
<<<<<<< HEAD
    this.chatService.getSupportConversationMessages(conversation.conversationId, 0, 50).subscribe({
      next: page => {
        this.messages.set(page.content);
        this.acknowledgeVisibleMessages(page.content);
        this.hasOlderMessages.set(!page.last);
=======
    this.chatService.getSupportHistory(conversation.conversationId).subscribe({
      next: (messages) => {
        this.messages.set(messages);
>>>>>>> codex/ui-functional-audit-polish
        this.messagesState.set('ready');
      },
      error: () => {
        this.messagesState.set('error');
        this.messagesError.set('Khong the tai lich su hoi thoai nay.');
      }
    });
    this.loadAttachments(conversation.conversationId);
    this.loadAuditEvents(conversation.conversationId, 0);
  }

  loadOlderMessages(): void {
    const conversationId = this.selectedConversationId();
    if (!conversationId || !this.hasOlderMessages() || this.isLoadingOlder()) return;
    const nextPage = this.messagePage + 1;
    const container = this.scrollContainer?.nativeElement;
    const previousHeight = container?.scrollHeight ?? 0;
    this.isLoadingOlder.set(true);
    this.chatService.getSupportConversationMessages(conversationId, nextPage, 50).subscribe({
      next: page => {
        this.messagePage = nextPage;
        this.skipAutoScrollOnce = true;
        this.messages.update(items => [...page.content, ...items]);
        this.hasOlderMessages.set(!page.last);
        this.isLoadingOlder.set(false);
        queueMicrotask(() => {
          if (container) container.scrollTop += container.scrollHeight - previousHeight;
        });
      },
      error: () => {
        this.isLoadingOlder.set(false);
        this.messagesError.set('Khong the tai them tin nhan cu.');
      }
    });
  }

  sendMessage(): void {
    const conversationId = this.selectedConversationId();
    const content = this.newMessage.trim();
    if (!conversationId || !content || this.isSending()) return;
<<<<<<< HEAD
    this.sendError.set('');
    this.isSending.set(true);
=======

    if (!this.chatService.isConnected()) {
      this.sendError.set('Chat đang offline. Hãy kết nối lại trước khi gửi.');
      return;
    }

    this.sendError.set('');
    this.isSending.set(true);
    if (!this.chatService.sendSupportReply(conversationId, content)) {
      this.isSending.set(false);
      this.sendError.set('Không thể gửi phản hồi. Hãy thử lại.');
      return;
    }

>>>>>>> codex/ui-functional-audit-polish
    this.newMessage = '';
    const clientMessageId = this.pendingContent === content && this.pendingClientMessageId
      ? this.pendingClientMessageId
      : this.chatService.createClientMessageId();
    this.pendingClientMessageId = clientMessageId;
    this.pendingContent = content;
    this.clearSendTimeout();
    this.sendTimeoutId = setTimeout(() => {
      if (!this.isSending()) return;
      this.isSending.set(false);
      if (!this.newMessage) this.newMessage = content;
      this.sendError.set('Chua nhan duoc xac nhan gui. Ban co the thu lai.');
    }, SEND_ACK_TIMEOUT_MS);
    const conversation = this.getConversation(conversationId);
    this.chatService.sendSupportConversationMessage(
      conversationId, content, conversation?.version, clientMessageId
    ).subscribe({
      next: message => {
        this.handleIncomingMessage(message);
        this.loadConversations();
      },
      error: error => {
        this.clearSendTimeout();
        this.isSending.set(false);
        this.newMessage = content;
        if (error instanceof HttpErrorResponse && error.status === 409) {
          this.sendError.set('Hoi thoai da thay doi. Danh sach da duoc tai lai; hay kiem tra truoc khi gui lai.');
          this.loadConversations();
        } else {
          this.sendError.set('Khong the gui phan hoi. Hay thu lai.');
        }
      }
    });
  }

  claimConversation(): void {
    this.runMutation(conversation => this.chatService.claimSupportConversation(
      conversation.conversationId, conversation.version));
  }

  unassignConversation(): void {
    this.runMutation(conversation => this.chatService.unassignSupportConversation(
      conversation.conversationId, conversation.version));
  }

  escalateConversation(): void {
    this.runMutation(conversation => this.chatService.escalateSupportConversation(
      conversation.conversationId, conversation.version));
  }

  reopenConversation(): void {
    const reason = this.lifecycleReason.trim();
    if (!reason) {
      this.mutationError.set('Nhap ly do mo lai hoi thoai.');
      return;
    }
    this.runMutation(conversation => this.chatService.reopenSupportConversation(
      conversation.conversationId, reason, conversation.version));
  }

  closeConversation(): void {
    const reason = this.lifecycleReason.trim();
    if (!reason) {
      this.mutationError.set('Nhap ly do dong hoi thoai.');
      return;
    }
    this.runMutation(conversation => this.chatService.closeSupportConversation(
      conversation.conversationId, reason, conversation.version));
  }

  loadAuditEvents(conversationId: number, page: number): void {
    this.auditState.set('loading');
    this.auditError.set('');
    this.chatService.getSupportConversationEvents(conversationId, page, 20).subscribe({
      next: result => {
        if (this.selectedConversationId() !== conversationId) return;
        this.auditEvents.set(result.content);
        this.auditPage.set(result.number);
        this.auditTotalPages.set(result.totalPages);
        this.auditState.set('ready');
      },
      error: () => {
        if (this.selectedConversationId() !== conversationId) return;
        this.auditState.set('error');
        this.auditError.set('Khong the tai lich su su kien bat bien.');
      }
    });
  }

  changeAuditPage(delta: number): void {
    const conversationId = this.selectedConversationId();
    const nextPage = this.auditPage() + delta;
    if (!conversationId || nextPage < 0 || nextPage >= this.auditTotalPages()) return;
    this.loadAuditEvents(conversationId, nextPage);
  }

  auditEventLabel(eventType: string): string {
    switch (eventType) {
      case 'CLOSED': return 'Da dong hoi thoai';
      case 'REOPENED': return 'Da mo lai hoi thoai';
      case 'MESSAGE_READ': return 'Tin nhan da doc';
      case 'MESSAGE_DELIVERED': return 'Tin nhan da nhan';
      case 'ASSIGNED': return 'Da nhan xu ly';
      case 'UNASSIGNED': return 'Da tra lai hang doi';
      case 'ESCALATED': return 'Da chuyen cap';
      case 'CREATED': return 'Da tao hoi thoai';
      case 'REPLIED': return 'Nhan vien da phan hoi';
      case 'CUSTOMER_MESSAGE': return 'Khach hang da gui tin';
      default: return eventType.replaceAll('_', ' ');
    }
  }

  uploadAttachment(event: Event): void {
    const conversationId = this.selectedConversationId();
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!conversationId || !file || this.isUploadingAttachment()) return;
    this.isUploadingAttachment.set(true);
    this.attachmentError.set('');
    this.chatService.uploadSupportAttachment(conversationId, file).subscribe({
      next: attachment => {
        this.attachments.update(items => [...items, attachment]);
        this.attachmentsState.set('ready');
        this.isUploadingAttachment.set(false);
      },
      error: error => {
        this.isUploadingAttachment.set(false);
        if (error instanceof HttpErrorResponse && error.status === 413) {
          this.attachmentError.set('Tep vuot qua gioi han kich thuoc.');
        } else if (error instanceof HttpErrorResponse && error.status === 415) {
          this.attachmentError.set('Chi chap nhan PDF, PNG, JPEG hoac tep van ban UTF-8.');
        } else {
          this.attachmentError.set('Khong the tai tep dinh kem. Hay thu lai.');
        }
      }
    });
  }

  downloadAttachment(attachment: SupportAttachment): void {
    this.attachmentError.set('');
    this.chatService.downloadAttachment(attachment.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = attachment.filename;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.attachmentError.set('Khong the tai tep dinh kem.')
    });
  }

  formatBytes(size: number): string {
    if (size < 1024) return `${size} B`;
    return `${(size / 1024).toFixed(size < 10240 ? 1 : 0)} KB`;
  }

  isOwnMessage(message: ChatMessage): boolean {
    return message.senderId === this.currentUserId();
  }

  messageStateLabel(message: ChatMessage): string {
    if (message.deliveryStatus === 'READ') return 'Da doc';
    if (message.deliveryStatus === 'DELIVERED') return 'Da nhan';
    return 'Da gui';
  }

  connectionLabel(): string {
    switch (this.connectionState()) {
      case 'connected': return 'Da ket noi';
      case 'connecting': return 'Dang ket noi...';
      case 'reconnecting': return 'Dang ket noi lai...';
      case 'error': return 'Mat ket noi';
      default: return 'Chua ket noi';
    }
  }

  conversationInitials(conversation: ChatConversation): string {
    return conversation.customerName
      .split(/\s+/)
      .filter(Boolean)
      .slice(-2)
      .map(part => part.charAt(0).toUpperCase())
      .join('') || '?';
  }

  getConversation(conversationId: number): ChatConversation | undefined {
<<<<<<< HEAD
    return this.conversations().find(conversation => conversation.conversationId === conversationId);
  }

  statusLabel(conversation: ChatConversation): string {
    switch (conversation.status) {
      case 'ASSIGNED': return 'Dang xu ly';
      case 'ESCALATED': return 'Da chuyen cap';
      case 'CLOSED': return 'Da dong';
      default: return 'Dang cho';
    }
  }

  slaLabel(conversation: ChatConversation): string {
    switch (conversation.slaState) {
      case 'BREACHED': return 'Qua SLA';
      case 'AT_RISK': return 'Sap qua SLA';
      case 'ON_TRACK': return 'Trong SLA';
      default: return 'Khong cho phan hoi';
    }
=======
    return this.conversations().find((conversation) => conversation.conversationId === conversationId);
>>>>>>> codex/ui-functional-audit-polish
  }

  private handleIncomingMessage(message: ChatMessage | null): void {
    if (!message) return;
<<<<<<< HEAD
    if (message.conversationId
        && !this.conversations().some(item => item.conversationId === message.conversationId)) {
      if (message.senderId !== this.currentUserId()) this.acknowledgeIncoming(message, 'DELIVERED');
=======
    const selectedConversationId = this.selectedConversationId();
    if (!this.conversations().some((item) => item.conversationId === message.conversationId)) {
>>>>>>> codex/ui-functional-audit-polish
      this.loadConversations();
    }
    if (message.conversationId !== this.selectedConversationId()) return;

<<<<<<< HEAD
    this.mergeMessage(message);
    this.conversations.update(items => items.map(item => item.conversationId === message.conversationId
      ? { ...item, lastMessage: message.content, lastMessageAt: message.timestamp }
      : item));
=======
    if (message.conversationId !== selectedConversationId) return;
    this.messages.update((messages) => {
      if (message.id && messages.some((item) => item.id === message.id)) return messages;
      return [...messages, message];
    });
>>>>>>> codex/ui-functional-audit-polish

    if (message.senderId === this.currentUserId()) {
      this.isSending.set(false);
      this.clearSendTimeout();
      if (!message.clientMessageId || message.clientMessageId === this.pendingClientMessageId) {
        if (this.newMessage === this.pendingContent) this.newMessage = '';
        this.pendingClientMessageId = undefined;
        this.pendingContent = undefined;
      }
    } else {
      this.acknowledgeIncoming(message, 'READ');
      this.loadConversations();
    }
  }

  private acknowledgeVisibleMessages(messages: ChatMessage[]): void {
    const userId = this.currentUserId();
    messages
      .filter(message => message.senderId !== userId && message.deliveryStatus !== 'READ')
      .forEach(message => this.acknowledgeIncoming(message, 'READ'));
  }

  private acknowledgeIncoming(message: ChatMessage, state: 'DELIVERED' | 'READ'): void {
    if (!message.id || message.deliveryStatus === 'READ'
        || (state === 'DELIVERED' && message.deliveryStatus === 'DELIVERED')) return;
    this.chatService.acknowledgeMessage(message.id, state).subscribe({
      next: updated => {
        if (message.conversationId === this.selectedConversationId()
            && updated.conversationId === message.conversationId) {
          this.mergeMessage(updated);
          this.loadAuditEvents(message.conversationId, 0);
        }
      },
      error: () => undefined,
    });
  }

  private mergeMessage(message: ChatMessage): void {
    this.messages.update(messages => {
      const index = message.id ? messages.findIndex(item => item.id === message.id) : -1;
      if (index < 0) return [...messages, message];
      return messages.map((item, itemIndex) => itemIndex === index ? { ...item, ...message } : item);
    });
  }

  private runMutation(
    operation: (conversation: ChatConversation) => Observable<ChatConversation>
  ): void {
    const conversationId = this.selectedConversationId();
    const conversation = conversationId ? this.getConversation(conversationId) : undefined;
    if (!conversation || this.isMutating()) return;
    this.isMutating.set(true);
    this.mutationError.set('');
    this.conflictRecovery.set('');
    operation(conversation).subscribe({
      next: updated => {
        this.conversations.update(items => items.map(item =>
          item.conversationId === updated.conversationId ? updated : item));
        this.isMutating.set(false);
        this.lifecycleReason = '';
        this.loadAuditEvents(updated.conversationId, 0);
      },
      error: error => {
        this.isMutating.set(false);
        if (error instanceof HttpErrorResponse && error.status === 409) {
          this.conflictRecovery.set('Hang doi da thay doi boi mot nhan vien khac. Da tai lai trang thai moi nhat.');
          this.loadConversations();
        } else if (error instanceof HttpErrorResponse && error.status === 403) {
          this.mutationError.set('Hoi thoai dang thuoc nhan vien khac. Ban khong the thay doi phan cong.');
        } else {
          this.mutationError.set('Khong the cap nhat hoi thoai. Hay thu lai.');
        }
      }
    });
  }

  private clearSendTimeout(): void {
    if (this.sendTimeoutId === undefined) return;
    clearTimeout(this.sendTimeoutId);
    this.sendTimeoutId = undefined;
  }

  private loadAttachments(conversationId: number): void {
    this.attachmentsState.set('loading');
    this.attachmentError.set('');
    this.attachments.set([]);
    this.chatService.getSupportAttachments(conversationId).subscribe({
      next: attachments => {
        if (this.selectedConversationId() !== conversationId) return;
        this.attachments.set(attachments);
        this.attachmentsState.set('ready');
      },
      error: () => {
        if (this.selectedConversationId() !== conversationId) return;
        this.attachmentsState.set('error');
        this.attachmentError.set('Khong the tai danh sach tep dinh kem.');
      }
    });
  }

  private loadAuditPolicy(): void {
    this.chatService.getSupportAuditPolicy().subscribe({
      next: policy => this.auditPolicy.set(policy),
      error: () => this.auditPolicy.set(null),
    });
  }
}
