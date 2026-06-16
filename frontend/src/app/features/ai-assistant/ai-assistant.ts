import { Component, ElementRef, ViewChild, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AiService } from '../../core/services/ai';
import { ButtonModule } from 'primeng/button';

interface ChatMessage {
  text: string;
  sender: 'user' | 'ai';
  time: Date;
}

@Component({
  selector: 'app-ai-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule],
  templateUrl: './ai-assistant.html',
  styleUrl: './ai-assistant.css'
})
export class AiAssistant implements AfterViewChecked {
  @ViewChild('scrollMe') private myScrollContainer!: ElementRef;

  isOpen = false;
  messages: ChatMessage[] = [];
  newMessage = '';
  isTyping = false;

  constructor(private aiService: AiService) {}

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  scrollToBottom(): void {
    try {
      this.myScrollContainer.nativeElement.scrollTop = this.myScrollContainer.nativeElement.scrollHeight;
    } catch(err) { }
  }

  toggleChat() {
    this.isOpen = !this.isOpen;
    if (this.isOpen && this.messages.length === 0) {
      this.messages.push({
        text: 'Xin chào 👋! Tôi là Trợ lý AI của Hotel. Tôi có thể giúp gì cho bạn hôm nay?',
        sender: 'ai',
        time: new Date()
      });
    }
  }

  sendMessage() {
    if (!this.newMessage.trim()) return;

    const userText = this.newMessage;
    this.messages.push({ text: userText, sender: 'user', time: new Date() });
    this.newMessage = '';
    this.isTyping = true;

    this.aiService.chat(userText).subscribe({
      next: (res) => {
        this.isTyping = false;
        this.messages.push({ text: res.reply, sender: 'ai', time: new Date() });
      },
      error: () => {
        this.isTyping = false;
        this.messages.push({ text: 'Xin lỗi, tôi đang gặp sự cố kết nối. Vui lòng thử lại sau!', sender: 'ai', time: new Date() });
      }
    });
  }
}
