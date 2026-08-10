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
  readonly currentUserId = signal<number | null>(null);
  readonly connectionState = signal<ChatConnectionState>('idle');
  readonly connectionError = signal('');
  readonly conversationState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly conversationError = signal('');
  readonly messagesState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly messagesError = signal('');
  readonly isSending = signal(false);
  readonly sendError = signal('');

  newMessage = '';
  private renderedMessageCount = 0;
  private sendTimeoutId?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.currentUserId.set(this.authService.getCurrentUserId());
    this.chatService.message$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((message) => this.handleIncomingMessage(message));
    this.chatService.connectionState$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((state) => this.connectionState.set(state));
    this.chatService.connectionError$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((error) => this.connectionError.set(error));

    this.chatService.connect('support');
    this.loadConversations();
  }

  ngOnDestroy(): void {
    this.clearSendTimeout();
    this.chatService.disconnect();
  }

  ngAfterViewChecked(): void {
    if (this.messages().length !== this.renderedMessageCount) {
      this.renderedMessageCount = this.messages().length;
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
    this.chatService.getSupportConversations().subscribe({
      next: (conversations) => {
        this.conversations.set(conversations);
        this.conversationState.set('ready');
      },
      error: () => {
        this.conversationState.set('error');
        this.conversationError.set('Không thể tải danh sách hội thoại hỗ trợ.');
      }
    });
  }

  retryConnection(): void {
    this.chatService.connect('support');
    this.loadConversations();
  }

  selectConversation(conversation: ChatConversation): void {
    this.selectedConversationId.set(conversation.conversationId);
    this.messages.set([]);
    this.messagesState.set('loading');
    this.messagesError.set('');
    this.chatService.getSupportHistory(conversation.conversationId).subscribe({
      next: (messages) => {
        this.messages.set(messages);
        this.messagesState.set('ready');
      },
      error: () => {
        this.messagesState.set('error');
        this.messagesError.set('Không thể tải lịch sử hội thoại này.');
      }
    });
  }

  sendMessage(): void {
    const conversationId = this.selectedConversationId();
    const content = this.newMessage.trim();
    if (!conversationId || !content || this.isSending()) return;

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

    this.newMessage = '';
    this.clearSendTimeout();
    this.sendTimeoutId = setTimeout(() => {
      if (!this.isSending()) return;
      this.isSending.set(false);
      this.sendError.set('Chưa nhận được xác nhận gửi. Bạn có thể thử lại.');
    }, SEND_ACK_TIMEOUT_MS);
  }

  isOwnMessage(message: ChatMessage): boolean {
    return message.senderId === this.currentUserId();
  }

  connectionLabel(): string {
    switch (this.connectionState()) {
      case 'connected': return 'Đã kết nối';
      case 'connecting': return 'Đang kết nối…';
      case 'reconnecting': return 'Đang kết nối lại…';
      case 'error': return 'Mất kết nối';
      default: return 'Chưa kết nối';
    }
  }

  conversationInitials(conversation: ChatConversation): string {
    return conversation.customerName
      .split(/\s+/)
      .filter(Boolean)
      .slice(-2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('') || '?';
  }

  getConversation(conversationId: number): ChatConversation | undefined {
    return this.conversations().find((conversation) => conversation.conversationId === conversationId);
  }

  conversationTypeLabel(conversation?: ChatConversation): string {
    return conversation?.channel === 'TENANT_ADMIN' ? 'ĐỐI TÁC → QUẢN TRỊ HỆ THỐNG' : 'KHÁCH HÀNG → CƠ SỞ';
  }

  participantLabel(conversation?: ChatConversation): string {
    return conversation?.channel === 'TENANT_ADMIN' ? 'Đối tác' : 'Khách hàng';
  }

  private handleIncomingMessage(message: ChatMessage | null): void {
    if (!message) return;
    const selectedConversationId = this.selectedConversationId();
    if (!this.conversations().some((item) => item.conversationId === message.conversationId)) {
      this.loadConversations();
    }

    if (message.conversationId !== selectedConversationId) return;
    this.messages.update((messages) => {
      if (message.id && messages.some((item) => item.id === message.id)) return messages;
      return [...messages, message];
    });

    if (message.senderId === this.currentUserId()) {
      this.isSending.set(false);
      this.clearSendTimeout();
    }
  }

  private clearSendTimeout(): void {
    if (this.sendTimeoutId !== undefined) {
      clearTimeout(this.sendTimeoutId);
      this.sendTimeoutId = undefined;
    }
  }
}
