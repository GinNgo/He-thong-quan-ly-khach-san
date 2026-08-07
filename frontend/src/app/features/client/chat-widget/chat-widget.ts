import { CommonModule } from '@angular/common';
import {
  AfterViewChecked,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import {
  ChatConnectionState,
  ChatMessage,
  ChatService
} from '../../../core/services/chat.service';
import { AuthService } from '../../../core/services/auth';
import { PublicI18nService } from '../../../core/i18n/public-i18n.service';

const SEND_ACK_TIMEOUT_MS = 10_000;

@Component({
  selector: 'app-chat-widget',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './chat-widget.html',
  styleUrl: './chat-widget.css'
})
export class ChatWidgetComponent implements OnInit, OnDestroy, AfterViewChecked {
  private readonly chatService = inject(ChatService);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  readonly i18n = inject(PublicI18nService);

  @ViewChild('scrollMe') private scrollContainer?: ElementRef<HTMLElement>;
  @ViewChild('triggerButton') private triggerButton?: ElementRef<HTMLButtonElement>;
  @ViewChild('messageInput') private messageInput?: ElementRef<HTMLInputElement>;

  readonly isOpen = signal(false);
  readonly isLoggedIn = signal(false);
  readonly currentUserId = signal<number | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly historyState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly historyError = signal('');
  readonly connectionState = signal<ChatConnectionState>('idle');
  readonly connectionError = signal('');
  readonly isSending = signal(false);
  readonly sendError = signal('');

  newMessage = '';
  private renderedMessageCount = 0;
  private sendTimeoutId?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    const userId = this.authService.getCurrentUserId();
    const loggedIn = this.authService.isLoggedIn() && userId !== null;
    this.currentUserId.set(userId);
    this.isLoggedIn.set(loggedIn);

    if (!loggedIn) return;

    this.chatService.message$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((message) => this.handleIncomingMessage(message));
    this.chatService.connectionState$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((state) => this.connectionState.set(state));
    this.chatService.connectionError$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((error) => this.connectionError.set(error));

    this.chatService.connect('customer');
    this.loadHistory();
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

  loadHistory(): void {
    if (!this.isLoggedIn() || this.historyState() === 'loading') return;
    this.historyState.set('loading');
    this.historyError.set('');

    this.chatService.getMyHistory().subscribe({
      next: (messages) => {
        this.messages.set(messages);
        this.historyState.set('ready');
      },
      error: () => {
        this.historyState.set('error');
        this.historyError.set(this.i18n.text('PUBLIC.SUPPORT.LOADING_HISTORY'));
      }
    });
  }

  toggleChat(): void {
    if (this.isOpen()) {
      this.closeChat();
      return;
    }

    this.isOpen.set(true);
    queueMicrotask(() => this.messageInput?.nativeElement.focus());
  }

  @HostListener('document:keydown.escape')
  closeChat(): void {
    if (!this.isOpen()) return;
    this.isOpen.set(false);
    queueMicrotask(() => this.triggerButton?.nativeElement.focus());
  }

  retryConnection(): void {
    this.sendError.set('');
    this.chatService.connect('customer');
  }

  sendMessage(): void {
    const content = this.newMessage.trim();
    if (!content || this.isSending()) return;

    if (!this.chatService.isConnected()) {
      this.sendError.set(this.i18n.text('PUBLIC.SUPPORT.OFFLINE'));
      return;
    }

    this.sendError.set('');
    this.isSending.set(true);
    if (!this.chatService.sendCustomerMessage(content)) {
      this.isSending.set(false);
      this.sendError.set(this.i18n.text('PUBLIC.SUPPORT.SEND_ERROR'));
      return;
    }

    this.newMessage = '';
    this.clearSendTimeout();
    this.sendTimeoutId = setTimeout(() => {
      if (!this.isSending()) return;
      this.isSending.set(false);
      this.sendError.set(this.i18n.text('PUBLIC.SUPPORT.ACK_TIMEOUT'));
    }, SEND_ACK_TIMEOUT_MS);
  }

  connectionLabel(): string {
    switch (this.connectionState()) {
      case 'connected': return this.i18n.text('PUBLIC.SUPPORT.CONNECTED');
      case 'connecting': return this.i18n.text('PUBLIC.SUPPORT.CONNECTING');
      case 'reconnecting': return this.i18n.text('PUBLIC.SUPPORT.RECONNECTING');
      case 'error': return this.i18n.text('PUBLIC.SUPPORT.DISCONNECTED');
      default: return this.i18n.text('PUBLIC.SUPPORT.NOT_CONNECTED');
    }
  }

  isOwnMessage(message: ChatMessage): boolean {
    return message.senderId === this.currentUserId();
  }

  private handleIncomingMessage(message: ChatMessage | null): void {
    if (!message) return;
    const userId = this.currentUserId();
    if (message.senderId !== userId && message.receiverId !== userId) return;

    this.messages.update((messages) => {
      if (message.id && messages.some((item) => item.id === message.id)) return messages;
      return [...messages, message];
    });

    if (message.senderId === userId) {
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
