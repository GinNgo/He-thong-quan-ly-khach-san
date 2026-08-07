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
import { FocusTrapModule } from 'primeng/focustrap';

import {
  ChatConnectionState,
  ChatConversation,
  ChatMessage,
  ChatService,
  SupportAttachment
} from '../../../core/services/chat.service';
import { AuthService } from '../../../core/services/auth';
<<<<<<< HEAD
import { FocusOnErrorDirective } from '../../../shared/directives/focus-management.directive';
=======
import { PublicI18nService } from '../../../core/i18n/public-i18n.service';
>>>>>>> codex/ui-functional-audit-polish

const SEND_ACK_TIMEOUT_MS = 10_000;

@Component({
  selector: 'app-chat-widget',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, FocusTrapModule, FocusOnErrorDirective],
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
  readonly attachments = signal<SupportAttachment[]>([]);
  readonly isUploadingAttachment = signal(false);
  readonly attachmentError = signal('');

  newMessage = '';
  newSubject = 'Ho tro chung';
  private messagePage = 0;
  private renderedMessageCount = 0;
  private skipAutoScrollOnce = false;
  private sendTimeoutId?: ReturnType<typeof setTimeout>;
  private pendingClientMessageId?: string;
  private pendingContent?: string;

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
        this.acknowledgeVisibleMessages(page.content);
        this.hasOlderMessages.set(!page.last);
        this.historyState.set('ready');
      },
      error: () => {
        this.historyState.set('error');
<<<<<<< HEAD
        this.historyError.set('Khong the tai lich su cuoc tro chuyen.');
=======
        this.historyError.set(this.i18n.text('PUBLIC.SUPPORT.LOADING_HISTORY'));
>>>>>>> codex/ui-functional-audit-polish
      }
    });
    this.loadAttachments(conversation.conversationId);
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
    this.acknowledgeVisibleMessages(this.messages());
    setTimeout(() => this.messageInput?.nativeElement.focus());
  }

  @HostListener('document:keydown.escape')
  closeChat(): void {
    if (!this.isOpen()) return;
    this.isOpen.set(false);
    setTimeout(() => this.triggerButton?.nativeElement.focus());
  }

  retryConnection(): void {
    this.sendError.set('');
    this.chatService.connect('customer');
  }

  sendMessage(): void {
    const content = this.newMessage.trim();
    const conversationId = this.selectedConversationId();
    if (!content || this.isSending()) return;
<<<<<<< HEAD
    if (!conversationId) {
      this.sendError.set('Hay tao hoac chon mot cuoc tro chuyen truoc khi gui.');
=======

    if (!this.chatService.isConnected()) {
      this.sendError.set(this.i18n.text('PUBLIC.SUPPORT.OFFLINE'));
>>>>>>> codex/ui-functional-audit-polish
      return;
    }
    this.sendError.set('');
    this.isSending.set(true);
<<<<<<< HEAD
=======
    if (!this.chatService.sendCustomerMessage(content)) {
      this.isSending.set(false);
      this.sendError.set(this.i18n.text('PUBLIC.SUPPORT.SEND_ERROR'));
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
<<<<<<< HEAD
      if (!this.newMessage) this.newMessage = content;
      this.sendError.set('Chua nhan duoc xac nhan gui. Ban co the thu lai.');
=======
      this.sendError.set(this.i18n.text('PUBLIC.SUPPORT.ACK_TIMEOUT'));
>>>>>>> codex/ui-functional-audit-polish
    }, SEND_ACK_TIMEOUT_MS);
    this.chatService.sendMyConversationMessage(conversationId, content, clientMessageId).subscribe({
      next: message => this.handleIncomingMessage(message),
      error: () => {
        this.clearSendTimeout();
        this.isSending.set(false);
        this.newMessage = content;
        this.sendError.set('Khong the gui tin nhan. Hay thu lai.');
      }
    });
  }

  uploadAttachment(event: Event): void {
    const conversationId = this.selectedConversationId();
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!conversationId || !file || this.isUploadingAttachment()) return;
    this.isUploadingAttachment.set(true);
    this.attachmentError.set('');
    this.chatService.uploadMyAttachment(conversationId, file).subscribe({
      next: attachment => {
        this.attachments.update(items => [...items, attachment]);
        this.isUploadingAttachment.set(false);
      },
      error: () => {
        this.isUploadingAttachment.set(false);
        this.attachmentError.set('Khong the tai tep. Chi dung PDF, PNG, JPEG hoac TXT toi da 5 MB.');
      }
    });
  }

  downloadAttachment(attachment: SupportAttachment): void {
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

  connectionLabel(): string {
    switch (this.connectionState()) {
<<<<<<< HEAD
      case 'connected': return 'Da ket noi';
      case 'connecting': return 'Dang ket noi...';
      case 'reconnecting': return 'Dang ket noi lai...';
      case 'error': return 'Mat ket noi';
      default: return 'Chua ket noi';
=======
      case 'connected': return this.i18n.text('PUBLIC.SUPPORT.CONNECTED');
      case 'connecting': return this.i18n.text('PUBLIC.SUPPORT.CONNECTING');
      case 'reconnecting': return this.i18n.text('PUBLIC.SUPPORT.RECONNECTING');
      case 'error': return this.i18n.text('PUBLIC.SUPPORT.DISCONNECTED');
      default: return this.i18n.text('PUBLIC.SUPPORT.NOT_CONNECTED');
>>>>>>> codex/ui-functional-audit-polish
    }
  }

  isOwnMessage(message: ChatMessage): boolean {
    return message.senderId === this.currentUserId();
  }

  messageStateLabel(message: ChatMessage): string {
    if (message.deliveryStatus === 'READ') return 'Da doc';
    if (message.deliveryStatus === 'DELIVERED') return 'Da nhan';
    return 'Da gui';
  }

  isSelectedConversationClosed(): boolean {
    return this.conversations().some(item =>
      item.conversationId === this.selectedConversationId() && item.status === 'CLOSED');
  }

  private handleIncomingMessage(message: ChatMessage | null): void {
    if (!message) return;
    const userId = this.currentUserId();
    if (message.senderId !== userId && message.receiverId !== userId) return;
    if (message.conversationId !== this.selectedConversationId()) {
      if (message.senderId !== userId) this.acknowledgeIncoming(message, 'DELIVERED');
      this.loadConversations();
      return;
    }

    this.mergeMessage(message);
    this.conversations.update(items => items.map(item => item.conversationId === message.conversationId
      ? { ...item, lastMessage: message.content, lastMessageAt: message.timestamp }
      : item));

    if (message.senderId === userId) {
      this.isSending.set(false);
      this.clearSendTimeout();
      if (!message.clientMessageId || message.clientMessageId === this.pendingClientMessageId) {
        if (this.newMessage === this.pendingContent) this.newMessage = '';
        this.pendingClientMessageId = undefined;
        this.pendingContent = undefined;
      }
    } else {
      this.acknowledgeIncoming(message, 'READ');
    }
  }

  private acknowledgeVisibleMessages(messages: ChatMessage[]): void {
    const userId = this.currentUserId();
    const state = this.isOpen() ? 'READ' : 'DELIVERED';
    messages
      .filter(message => message.senderId !== userId && message.deliveryStatus !== 'READ')
      .forEach(message => this.acknowledgeIncoming(message, state));
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

  private clearSendTimeout(): void {
    if (this.sendTimeoutId === undefined) return;
    clearTimeout(this.sendTimeoutId);
    this.sendTimeoutId = undefined;
  }

  private loadAttachments(conversationId: number): void {
    this.attachments.set([]);
    this.attachmentError.set('');
    this.chatService.getMyAttachments(conversationId).subscribe({
      next: attachments => {
        if (this.selectedConversationId() === conversationId) this.attachments.set(attachments);
      },
      error: () => {
        if (this.selectedConversationId() === conversationId) {
          this.attachmentError.set('Khong the tai tep dinh kem.');
        }
      }
    });
  }
}
