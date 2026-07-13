import { Component, OnInit, OnDestroy, inject, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ChatService, ChatMessage } from '../../../core/services/chat.service';
import { UserService, User } from '../../../core/services/user.service';
import { environment } from '../../../../environments/environment';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-chat-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-dashboard.html',
  styleUrls: ['./chat-dashboard.css']
})
export class ChatDashboardComponent implements OnInit, OnDestroy, AfterViewChecked {
  private chatService = inject(ChatService);
  private userService = inject(UserService);
  private http = inject(HttpClient);
  
  @ViewChild('scrollMe') private myScrollContainer!: ElementRef;

  adminId = 1; // Default admin ID
  activeUserIds: number[] = [];
  usersMap = new Map<number, User>();
  
  selectedUserId: number | null = null;
  messages: ChatMessage[] = [];
  newMessage = '';
  
  private msgSub!: Subscription;

  ngOnInit() {
    this.chatService.connect(this.adminId);
    
    // Load all users to get names
    this.userService.getAllUsers().subscribe(users => {
      users.forEach((u: User) => {
        if (u.id) {
          this.usersMap.set(u.id, u);
        }
      });
      this.loadActiveChats();
    });

    this.msgSub = this.chatService.message$.subscribe(msg => {
      if (msg) {
        // If message is from currently selected user, add it to view
        if (msg.senderId === this.selectedUserId || msg.receiverId === this.selectedUserId) {
          this.messages.push(msg);
        }
        
        // Update active users list if it's a new sender
        if (msg.senderId !== this.adminId && !this.activeUserIds.includes(msg.senderId)) {
          this.activeUserIds.unshift(msg.senderId);
        }
      }
    });
  }

  ngOnDestroy() {
    if (this.msgSub) this.msgSub.unsubscribe();
    // Chat widget and dashboard share same service, so be careful disconnecting
    // this.chatService.disconnect();
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

  loadActiveChats() {
    this.http.get<number[]>(`${environment.apiUrl}/chat/active-users/${this.adminId}`).subscribe(ids => {
      // Exclude self
      this.activeUserIds = ids.filter(id => id !== this.adminId);
    });
  }

  selectUser(userId: number) {
    this.selectedUserId = userId;
    this.messages = [];
    this.http.get<ChatMessage[]>(`${environment.apiUrl}/chat/history/${userId}/${this.adminId}`)
      .subscribe(res => {
        this.messages = res;
      });
  }

  getUserName(userId: number): string {
    const user = this.usersMap.get(userId);
    return user ? (user.fullName || user.username) : `Khách hàng #${userId}`;
  }

  sendMessage() {
    if (!this.newMessage.trim() || !this.selectedUserId) return;
    
    const msg: ChatMessage = {
      senderId: this.adminId,
      receiverId: this.selectedUserId,
      content: this.newMessage,
      timestamp: new Date().toISOString()
    };
    
    this.chatService.sendMessage(this.selectedUserId, this.newMessage);
    this.messages.push(msg);
    this.newMessage = '';
  }
}
