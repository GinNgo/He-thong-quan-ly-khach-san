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

  newMessage = '';
  private messagePage = 0;
  private renderedMessageCount = 0;
  private skipAutoScrollOnce = false;
  private sendTimeoutId?: ReturnType<typeof setTimeout>;

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
    this.chatService.getSupportConversations().subscribe({
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
    this.clearSendTimeout();
    this.sendTimeoutId = setTimeout(() => {
      if (!this.isSending()) return;
      this.isSending.set(false);
      this.sendError.set('Chua nhan duoc xac nhan gui. Ban co the thu lai.');
    }, SEND_ACK_TIMEOUT_MS);
    this.chatService.sendSupportConversationMessage(conversationId, content).subscribe({
      next: message => this.handleIncomingMessage(message),
      error: () => {
        this.clearSendTimeout();
        this.isSending.set(false);
        this.newMessage = content;
        this.sendError.set('Khong the gui phan hoi. Hay thu lai.');
      }
    });
  }

  isOwnMessage(message: ChatMessage): boolean {
    return message.senderId === this.currentUserId();
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

  private handleIncomingMessage(message: ChatMessage | null): void {
    if (!message) return;
    if (message.conversationId
        && !this.conversations().some(item => item.conversationId === message.conversationId)) {
      this.loadConversations();
    }
    if (message.conversationId !== this.selectedConversationId()) return;

    this.messages.update(messages => {
      if (message.id && messages.some(item => item.id === message.id)) return messages;
      return [...messages, message];
    });
    this.conversations.update(items => items.map(item => item.conversationId === message.conversationId
      ? { ...item, lastMessage: message.content, lastMessageAt: message.timestamp }
      : item));

    if (message.senderId === this.currentUserId()) {
      this.isSending.set(false);
      this.clearSendTimeout();
    }
  }

  private clearSendTimeout(): void {
    if (this.sendTimeoutId === undefined) return;
    clearTimeout(this.sendTimeoutId);
    this.sendTimeoutId = undefined;
  }
}
