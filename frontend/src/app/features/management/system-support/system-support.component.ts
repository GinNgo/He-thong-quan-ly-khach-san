import { CommonModule } from '@angular/common';
import { Component, DestroyRef, ElementRef, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import { AuthService } from '../../../core/services/auth';
import { ChatConnectionState, ChatMessage, ChatService } from '../../../core/services/chat.service';

const SEND_ACK_TIMEOUT_MS = 10_000;

@Component({
  selector: 'app-system-support',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './system-support.component.html',
  styleUrl: './system-support.component.css',
})
export class SystemSupportComponent implements OnInit, OnDestroy {
  private readonly chatService = inject(ChatService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('messageStream') private messageStream?: ElementRef<HTMLElement>;

  readonly messages = signal<ChatMessage[]>([]);
  readonly historyState = signal<'loading' | 'ready' | 'error'>('loading');
  readonly connectionState = signal<ChatConnectionState>('idle');
  readonly errorMessage = signal('');
  readonly isSending = signal(false);

  propertyId: number | null = null;
  currentUserId: number | null = null;
  newMessage = '';
  private sendTimeoutId?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.currentUserId = this.authService.getCurrentUserId();
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const propertyId = Number(params.get('propertyId'));
        this.propertyId = Number.isInteger(propertyId) && propertyId > 0 ? propertyId : null;
      });
    this.chatService.message$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((message) => this.handleIncomingMessage(message));
    this.chatService.connectionState$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((state) => this.connectionState.set(state));
    this.chatService.connectionError$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((message) => this.errorMessage.set(message));

    this.chatService.connect('customer');
    this.loadHistory();
  }

  ngOnDestroy(): void {
    this.clearSendTimeout();
    this.chatService.disconnect();
  }

  loadHistory(): void {
    this.historyState.set('loading');
    this.chatService.getMyTenantSupportHistory().subscribe({
      next: (messages) => {
        this.messages.set(messages);
        this.historyState.set('ready');
        queueMicrotask(() => this.scrollToBottom());
      },
      error: () => {
        this.historyState.set('error');
        this.errorMessage.set('Không thể tải lịch sử hỗ trợ.');
      },
    });
  }

  sendMessage(): void {
    const content = this.newMessage.trim();
    if (!content || !this.propertyId || this.isSending()) return;
    if (!this.chatService.isConnected()) {
      this.errorMessage.set('Kết nối hỗ trợ đang gián đoạn. Vui lòng thử kết nối lại.');
      return;
    }

    this.errorMessage.set('');
    this.isSending.set(true);
    if (!this.chatService.sendTenantMessage(content, this.propertyId)) {
      this.isSending.set(false);
      this.errorMessage.set('Không thể gửi yêu cầu hỗ trợ.');
      return;
    }
    this.newMessage = '';
    this.clearSendTimeout();
    this.sendTimeoutId = setTimeout(() => {
      if (!this.isSending()) return;
      this.isSending.set(false);
      this.errorMessage.set('Chưa nhận được xác nhận gửi. Vui lòng kiểm tra lịch sử hoặc thử lại.');
    }, SEND_ACK_TIMEOUT_MS);
  }

  retry(): void {
    this.errorMessage.set('');
    this.chatService.connect('customer');
    this.loadHistory();
  }

  isOwnMessage(message: ChatMessage): boolean {
    return message.senderId === this.currentUserId;
  }

  private handleIncomingMessage(message: ChatMessage | null): void {
    if (!message || (message.senderId !== this.currentUserId && message.receiverId !== this.currentUserId)) return;
    this.messages.update((messages) => {
      if (message.id && messages.some((item) => item.id === message.id)) return messages;
      return [...messages, message];
    });
    if (message.senderId === this.currentUserId) {
      this.isSending.set(false);
      this.clearSendTimeout();
    }
    queueMicrotask(() => this.scrollToBottom());
  }

  private clearSendTimeout(): void {
    if (this.sendTimeoutId !== undefined) {
      clearTimeout(this.sendTimeoutId);
      this.sendTimeoutId = undefined;
    }
  }

  private scrollToBottom(): void {
    const element = this.messageStream?.nativeElement;
    if (element) element.scrollTop = element.scrollHeight;
  }
}
