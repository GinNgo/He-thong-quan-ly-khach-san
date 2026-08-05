import { CommonModule } from '@angular/common';
import { AfterViewChecked, Component, ElementRef, HostListener, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { timeout } from 'rxjs';
import { FocusTrapModule } from 'primeng/focustrap';

import { AiService } from '../../core/services/ai';

interface ChatMessage {
  text: string;
  sender: 'user' | 'ai';
  time: Date;
  retryText?: string;
}

const AI_REQUEST_TIMEOUT_MS = 15_000;

@Component({
  selector: 'app-ai-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule, FocusTrapModule],
  templateUrl: './ai-assistant.html',
  styleUrl: './ai-assistant.css'
})
export class AiAssistant implements AfterViewChecked {
  @ViewChild('scrollMe') private myScrollContainer?: ElementRef<HTMLElement>;
  @ViewChild('triggerButton') private triggerButton?: ElementRef<HTMLButtonElement>;

  isOpen = false;
  messages: ChatMessage[] = [];
  newMessage = '';
  isTyping = false;

  constructor(private aiService: AiService) {}

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  scrollToBottom(): void {
    const container = this.myScrollContainer?.nativeElement;
    if (!container) return;

    container.scrollTop = container.scrollHeight;
  }

  toggleChat(): void {
    if (this.isOpen) {
      this.isOpen = false;
      setTimeout(() => this.triggerButton?.nativeElement.focus());
      return;
    }

    this.isOpen = !this.isOpen;
    if (this.isOpen && this.messages.length === 0) {
      this.messages.push({
        text: 'Xin chào 👋! Tôi là Trợ lý AI của LuxeStay. Tôi có thể giúp gì cho bạn hôm nay?',
        sender: 'ai',
        time: new Date()
      });
    }
  }

  @HostListener('document:keydown.escape')
  closeChat(): void {
    if (!this.isOpen) return;

    this.isOpen = false;
    setTimeout(() => this.triggerButton?.nativeElement.focus());
  }

  sendMessage(): void {
    const userText = this.newMessage.trim();
    if (!userText || this.isTyping) return;

    this.newMessage = '';
    this.send(userText, true);
  }

  retryMessage(userText: string): void {
    if (!userText.trim() || this.isTyping) return;

    const failedMessage = [...this.messages]
      .reverse()
      .find((message) => message.retryText === userText);
    if (failedMessage) failedMessage.retryText = undefined;

    this.send(userText, false);
  }

  private send(userText: string, appendUserMessage: boolean): void {
    if (appendUserMessage) {
      this.messages.push({ text: userText, sender: 'user', time: new Date() });
    }
    this.isTyping = true;

    this.aiService.chat(userText).pipe(timeout(AI_REQUEST_TIMEOUT_MS)).subscribe({
      next: (res) => {
        this.isTyping = false;
        const reply = res.reply?.trim();
        if (!reply) {
          this.addFailureMessage(userText);
          return;
        }

        this.messages.push({ text: reply, sender: 'ai', time: new Date() });
      },
      error: () => {
        this.isTyping = false;
        this.addFailureMessage(userText);
      }
    });
  }

  private addFailureMessage(userText: string): void {
    this.messages.push({
      text: 'Xin lỗi, tôi chưa thể kết nối lúc này. Bạn có thể thử gửi lại tin nhắn.',
      sender: 'ai',
      time: new Date(),
      retryText: userText,
    });
  }
}
