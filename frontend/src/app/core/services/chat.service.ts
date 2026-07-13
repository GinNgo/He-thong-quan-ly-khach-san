import { Injectable, inject } from '@angular/core';
import { Client, Message } from '@stomp/stompjs';
import * as SockJS from 'sockjs-client';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ChatMessage {
  id?: number;
  senderId: number;
  receiverId: number;
  content: string;
  timestamp?: string;
  isRead?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private stompClient!: Client;
  private currentUserId: number | null = null;
  
  // Observable for new incoming messages
  private messageSubject = new BehaviorSubject<ChatMessage | null>(null);
  public message$ = this.messageSubject.asObservable();

  constructor() {}

  connect(userId: number) {
    this.currentUserId = userId;
    
    // For SockJS fallback if needed:
    // const socket = new SockJS('http://localhost:8080/ws');
    // this.stompClient = new Client({
    //   webSocketFactory: () => socket as any,
    //   ...
    // });
    
    this.stompClient = new Client({
      brokerURL: environment.apiUrl.replace('http', 'ws') + '/ws',
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    this.stompClient.onConnect = (frame) => {
      console.log('Connected to Chat WS: ' + frame);
      
      // Subscribe to private messages queue
      this.stompClient.subscribe(`/user/${userId}/queue/messages`, (message: Message) => {
        if (message.body) {
          const chatMsg: ChatMessage = JSON.parse(message.body);
          this.messageSubject.next(chatMsg);
        }
      });
    };

    this.stompClient.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
    };

    this.stompClient.activate();
  }

  disconnect() {
    if (this.stompClient && this.stompClient.active) {
      this.stompClient.deactivate();
    }
  }

  sendMessage(receiverId: number, content: string) {
    if (this.stompClient && this.stompClient.active && this.currentUserId) {
      const chatMessage: ChatMessage = {
        senderId: this.currentUserId,
        receiverId: receiverId,
        content: content
      };
      
      this.stompClient.publish({
        destination: '/app/chat.sendMessage',
        body: JSON.stringify(chatMessage)
      });
    } else {
      console.error('Cannot send message. STOMP client is not connected.');
    }
  }
}
