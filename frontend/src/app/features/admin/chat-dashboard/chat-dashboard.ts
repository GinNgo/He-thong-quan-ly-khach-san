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
  ChatService
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

  newMessage = '';
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
    this.messagePage = 0;
    this.messages.set([]);
    this.hasOlderMessages.set(false);
    this.messagesState.set('loading');
    this.messagesError.set('');
    this.chatService.getSupportConversationMessages(conversation.conversationId, 0, 50).subscribe({
      next: page => {
        this.messages.set(page.content);
        this.acknowledgeVisibleMessages(page.content);
        this.hasOlderMessages.set(!page.last);
        this.messagesState.set('ready');
      },
      error: () => {
        this.messagesState.set('error');
        this.messagesError.set('Khong the tai lich su hoi thoai nay.');
      }
    });
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
    this.sendError.set('');
    this.isSending.set(true);
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
    this.runMutation(conversation => this.chatService.reopenSupportConversation(
      conversation.conversationId, conversation.version));
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
  }

  private handleIncomingMessage(message: ChatMessage | null): void {
    if (!message) return;
    if (message.conversationId
        && !this.conversations().some(item => item.conversationId === message.conversationId)) {
      if (message.senderId !== this.currentUserId()) this.acknowledgeIncoming(message, 'DELIVERED');
      this.loadConversations();
    }
    if (message.conversationId !== this.selectedConversationId()) return;

    this.mergeMessage(message);
    this.conversations.update(items => items.map(item => item.conversationId === message.conversationId
      ? { ...item, lastMessage: message.content, lastMessageAt: message.timestamp }
      : item));

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
}
