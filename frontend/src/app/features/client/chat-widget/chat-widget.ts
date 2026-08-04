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
  ChatConversation,
  ChatMessage,
  ChatService
} from '../../../core/services/chat.service';
import { AuthService } from '../../../core/services/auth';

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

  @ViewChild('scrollMe') private scrollContainer?: ElementRef<HTMLElement>;
  @ViewChild('triggerButton') private triggerButton?: ElementRef<HTMLButtonElement>;
  @ViewChild('messageInput') private messageInput?: ElementRef<HTMLInputElement>;

  readonly isOpen = signal(false);
  readonly isLoggedIn = signal(false);
  readonly currentUserId = signal<number | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly conversations = signal<ChatConversation[]>([]);
  readonly selectedConversationId = signal<number | null>(null);
  readonly conversationState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly hasOlderMessages = signal(false);
  readonly isLoadingOlder = signal(false);
  readonly isCreatingConversation = signal(false);
  readonly historyState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly historyError = signal('');
  readonly connectionState = signal<ChatConnectionState>('idle');
  readonly connectionError = signal('');
  readonly isSending = signal(false);
  readonly sendError = signal('');

  newMessage = '';
  newSubject = 'Ho tro chung';
  private messagePage = 0;
  private renderedMessageCount = 0;
  private skipAutoScrollOnce = false;
  private sendTimeoutId?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    const userId = this.authService.getCurrentUserId();
    const loggedIn = this.authService.isLoggedIn() && userId !== null;
    this.currentUserId.set(userId);
    this.isLoggedIn.set(loggedIn);

    if (!loggedIn) return;

    this.chatService.message$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(message => this.handleIncomingMessage(message));
    this.chatService.connectionState$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(state => this.connectionState.set(state));
    this.chatService.connectionError$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(error => this.connectionError.set(error));

    this.chatService.connect('customer');
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
    if (!this.isLoggedIn() || this.conversationState() === 'loading') return;
    this.conversationState.set('loading');
    this.historyError.set('');
    this.chatService.getMyConversations().subscribe({
      next: page => {
        this.conversations.set(page.content);
        this.conversationState.set('ready');
        const selected = page.content.find(item => item.conversationId === this.selectedConversationId());
        if (selected) return;
        if (page.content.length) {
          this.selectConversation(page.content[0]);
        } else {
          this.selectedConversationId.set(null);
          this.messages.set([]);
          this.historyState.set('ready');
        }
      },
      error: () => {
        this.conversationState.set('error');
        this.historyState.set('error');
        this.historyError.set('Khong the tai danh sach hoi thoai ho tro.');
      }
    });
  }

  createConversation(): void {
    if (this.isCreatingConversation()) return;
    const subject = this.newSubject.trim() || 'Ho tro chung';
    this.isCreatingConversation.set(true);
    this.historyError.set('');
    this.chatService.createMyConversation(subject).subscribe({
      next: conversation => {
        this.conversations.update(items => [conversation, ...items]);
        this.newSubject = 'Ho tro chung';
        this.isCreatingConversation.set(false);
        this.selectConversation(conversation);
      },
      error: () => {
        this.isCreatingConversation.set(false);
        this.historyError.set('Khong the tao cuoc tro chuyen moi.');
      }
    });
  }

  selectConversationById(conversationId: number | null): void {
    const conversation = this.conversations().find(item => item.conversationId === Number(conversationId));
    if (conversation) this.selectConversation(conversation);
  }

  selectConversation(conversation: ChatConversation): void {
    this.selectedConversationId.set(conversation.conversationId);
    this.messagePage = 0;
    this.messages.set([]);
    this.hasOlderMessages.set(false);
    this.historyState.set('loading');
    this.historyError.set('');
    this.chatService.getMyConversationMessages(conversation.conversationId, 0, 50).subscribe({
      next: page => {
        this.messages.set(page.content);
        this.hasOlderMessages.set(!page.last);
        this.historyState.set('ready');
      },
      error: () => {
        this.historyState.set('error');
        this.historyError.set('Khong the tai lich su cuoc tro chuyen.');
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
    this.chatService.getMyConversationMessages(conversationId, nextPage, 50).subscribe({
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
        this.historyError.set('Khong the tai them tin nhan cu.');
      }
    });
  }

  retryHistory(): void {
    const conversation = this.conversations()
      .find(item => item.conversationId === this.selectedConversationId());
    if (conversation) {
      this.selectConversation(conversation);
    } else {
      this.loadConversations();
    }
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
    const conversationId = this.selectedConversationId();
    if (!content || this.isSending()) return;
    if (!conversationId) {
      this.sendError.set('Hay tao hoac chon mot cuoc tro chuyen truoc khi gui.');
      return;
    }
    this.sendError.set('');
    this.isSending.set(true);
    this.newMessage = '';
    this.clearSendTimeout();
    this.sendTimeoutId = setTimeout(() => {
      if (!this.isSending()) return;
      this.isSending.set(false);
      this.sendError.set('Chua nhan duoc xac nhan gui. Ban co the thu lai.');
    }, SEND_ACK_TIMEOUT_MS);
    this.chatService.sendMyConversationMessage(conversationId, content).subscribe({
      next: message => this.handleIncomingMessage(message),
      error: () => {
        this.clearSendTimeout();
        this.isSending.set(false);
        this.newMessage = content;
        this.sendError.set('Khong the gui tin nhan. Hay thu lai.');
      }
    });
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

  isOwnMessage(message: ChatMessage): boolean {
    return message.senderId === this.currentUserId();
  }

  private handleIncomingMessage(message: ChatMessage | null): void {
    if (!message) return;
    const userId = this.currentUserId();
    if (message.senderId !== userId && message.receiverId !== userId) return;
    if (message.conversationId !== this.selectedConversationId()) {
      this.loadConversations();
      return;
    }

    this.messages.update(messages => {
      if (message.id && messages.some(item => item.id === message.id)) return messages;
      return [...messages, message];
    });
    this.conversations.update(items => items.map(item => item.conversationId === message.conversationId
      ? { ...item, lastMessage: message.content, lastMessageAt: message.timestamp }
      : item));

    if (message.senderId === userId) {
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
