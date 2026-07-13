import { Component, OnInit, OnDestroy, inject, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ChatService, ChatMessage } from '../../../core/services/chat.service';
import { environment } from '../../../../environments/environment';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-chat-widget',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-widget.html',
  styleUrls: ['./chat-widget.css']
})
export class ChatWidgetComponent implements OnInit, OnDestroy, AfterViewChecked {
  private chatService = inject(ChatService);
  private http = inject(HttpClient);
  
  @ViewChild('scrollMe') private myScrollContainer!: ElementRef;

  isOpen = false;
  isLoggedIn = false;
  currentUserId: number | null = null;
  adminId = 1; // Default admin ID for CSKH

  messages: ChatMessage[] = [];
  newMessage = '';
  private msgSub!: Subscription;

  ngOnInit() {
    this.checkLoginStatus();
  }

  ngOnDestroy() {
    if (this.msgSub) this.msgSub.unsubscribe();
    this.chatService.disconnect();
  }

  ngAfterViewChecked() {        
    this.scrollToBottom();        
  } 

  scrollToBottom(): void {
    try {
      if (this.myScrollContainer) {
        this.myScrollContainer.nativeElement.scrollTop = this.myScrollContainer.nativeElement.scrollHeight;
      }
    } catch(err) { }                 
  }

  checkLoginStatus() {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      try {
        const user = JSON.parse(userStr);
        this.currentUserId = user.id;
        this.isLoggedIn = true;
        
        // Connect WS
        this.chatService.connect(this.currentUserId!);
        
        // Listen for incoming messages
        this.msgSub = this.chatService.message$.subscribe(msg => {
          if (msg) {
            this.messages.push(msg);
          }
        });

        // Fetch history
        this.loadHistory();
      } catch (e) {
        this.isLoggedIn = false;
      }
    }
  }

  loadHistory() {
    if (!this.currentUserId) return;
    this.http.get<ChatMessage[]>(`${environment.apiUrl}/chat/history/${this.currentUserId}/${this.adminId}`)
      .subscribe({
        next: (res) => {
          this.messages = res;
        },
        error: (err) => console.error('Failed to load chat history', err)
      });
  }

  toggleChat() {
    this.isOpen = !this.isOpen;
    if (this.isOpen && this.isLoggedIn && this.messages.length === 0) {
      this.loadHistory();
    }
  }

  sendMessage() {
    if (!this.newMessage.trim() || !this.currentUserId) return;
    
    // Add locally to UI immediately
    const msg: ChatMessage = {
      senderId: this.currentUserId,
      receiverId: this.adminId,
      content: this.newMessage,
      timestamp: new Date().toISOString()
    };
    
    // Send to WS
    this.chatService.sendMessage(this.adminId, this.newMessage);
    
    // Optimistic UI update
    this.messages.push(msg);
    this.newMessage = '';
  }
}
