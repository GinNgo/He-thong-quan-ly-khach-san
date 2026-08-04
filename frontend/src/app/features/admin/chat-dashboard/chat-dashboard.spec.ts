import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';

import { ChatDashboardComponent } from './chat-dashboard';
import { ChatConversation, ChatMessage, ChatService } from '../../../core/services/chat.service';
import { AuthService } from '../../../core/services/auth';

describe('ChatDashboard', () => {
  let component: ChatDashboardComponent;
  let fixture: ComponentFixture<ChatDashboardComponent>;
  let incoming: Subject<ChatMessage | null>;
  let getMessages: ReturnType<typeof vi.fn>;
  let sendSupportMessage: ReturnType<typeof vi.fn>;

  const conversation: ChatConversation = {
    conversationId: 21,
    customerId: 42,
    customerName: 'Customer',
    subject: 'Hoa don',
    lastMessage: 'Can ho tro',
  };

  beforeEach(async () => {
    incoming = new Subject<ChatMessage | null>();
    getMessages = vi.fn(() => of(page([])));
    sendSupportMessage = vi.fn(() => of({
      id: 10, conversationId: 21, senderId: 7, receiverId: 42, content: 'Da tiep nhan'
    }));
    await TestBed.configureTestingModule({
      imports: [ChatDashboardComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            getCurrentUserId: () => 7,
            getAccessToken: () => 'test-token',
          }
        },
        {
          provide: ChatService,
          useValue: {
            connect: vi.fn(),
            disconnect: vi.fn(),
            message$: incoming,
            connectionState$: of('connected'),
            connectionError$: of(''),
            getSupportConversations: () => of([conversation]),
            getSupportConversationMessages: getMessages,
            sendSupportConversationMessage: sendSupportMessage,
            isConnected: () => true,
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ChatDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads history by conversation id instead of customer id', () => {
    component.selectConversation(conversation);

    expect(component.selectedConversationId()).toBe(21);
    expect(getMessages).toHaveBeenCalledWith(21, 0, 50);
  });

  it('sends a support reply to the selected conversation', () => {
    component.selectConversation(conversation);
    component.newMessage = 'Da tiep nhan';

    component.sendMessage();

    expect(sendSupportMessage).toHaveBeenCalledWith(21, 'Da tiep nhan');
  });

  it('ignores realtime messages from a different conversation', () => {
    component.selectConversation(conversation);

    incoming.next({ id: 5, conversationId: 99, senderId: 44, receiverId: 0, content: 'other' });

    expect(component.messages()).toEqual([]);
  });

  function page<T>(content: T[]) {
    return { content, totalElements: content.length, totalPages: content.length ? 1 : 0,
      number: 0, size: 50, first: true, last: true, retentionDays: 365 };
  }
});
