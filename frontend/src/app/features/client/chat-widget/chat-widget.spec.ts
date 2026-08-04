import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { ChatWidgetComponent } from './chat-widget';
import { ChatConversation, ChatMessage, ChatService } from '../../../core/services/chat.service';
import { AuthService } from '../../../core/services/auth';

describe('ChatWidget', () => {
  let component: ChatWidgetComponent;
  let fixture: ComponentFixture<ChatWidgetComponent>;
  let incoming: Subject<ChatMessage | null>;
  let chatService: {
    sendMyConversationMessage: ReturnType<typeof vi.fn>;
    getMyConversationMessages: ReturnType<typeof vi.fn>;
  };

  const conversations: ChatConversation[] = [
    { conversationId: 11, customerId: 42, customerName: 'Customer', subject: 'Dat phong', lastMessage: '' },
    { conversationId: 12, customerId: 42, customerName: 'Customer', subject: 'Hoa don', lastMessage: '' },
  ];

  beforeEach(async () => {
    incoming = new Subject<ChatMessage | null>();
    chatService = {
      sendMyConversationMessage: vi.fn(() => of({
        id: 50, conversationId: 11, senderId: 42, receiverId: 0, content: 'hello'
      })),
      getMyConversationMessages: vi.fn(() => of(page([]))),
    };
    await TestBed.configureTestingModule({
      imports: [ChatWidgetComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            isLoggedIn: () => true,
            getCurrentUserId: () => 42,
            getAccessToken: () => 'test-token',
          }
        },
        {
          provide: ChatService,
          useValue: {
            ...chatService,
            connect: vi.fn(),
            disconnect: vi.fn(),
            message$: incoming,
            connectionState$: of('idle'),
            connectionError$: of(''),
            getMyConversations: () => of(page(conversations)),
            createMyConversation: (subject: string) => of({ ...conversations[0], conversationId: 13, subject }),
            createClientMessageId: () => 'customer-client-1',
            acknowledgeMessage: vi.fn(messageId => of({
              id: messageId, conversationId: 11, senderId: 7, receiverId: 42,
              content: 'reply', deliveryStatus: 'READ' as const
            })),
            isConnected: () => false,
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ChatWidgetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('selects and loads the latest own conversation', () => {
    expect(component.selectedConversationId()).toBe(11);
    expect(chatService.getMyConversationMessages).toHaveBeenCalledWith(11, 0, 50);
  });

  it('does not fail when the chat body is not mounted', () => {
    expect(() => component.scrollToBottom()).not.toThrow();
  });

  it('exposes dialog semantics and does not add an optimistic message', () => {
    component.toggleChat();
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('[role="dialog"]');
    expect(panel?.getAttribute('aria-labelledby')).toBe('support-chat-title');
    expect(fixture.nativeElement.querySelector('.close-button')?.getAttribute('aria-label')).toContain('Dong');

    component.newMessage = 'hello';
    component.sendMessage();

    expect(chatService.sendMyConversationMessage).toHaveBeenCalledWith(11, 'hello', 'customer-client-1');
    expect(component.messages()).toEqual([expect.objectContaining({ id: 50, conversationId: 11 })]);
  });

  it('sends with the selected conversation and ignores realtime messages for another conversation', () => {
    component.newMessage = 'Can ho tro';

    component.sendMessage();
    incoming.next({ id: 99, conversationId: 12, senderId: 7, receiverId: 42, content: 'other' });

    expect(chatService.sendMyConversationMessage).toHaveBeenCalledWith(11, 'Can ho tro', 'customer-client-1');
    expect(component.messages()).toEqual([expect.objectContaining({ conversationId: 11 })]);
  });

  it('reuses the client message id after a retryable send failure', () => {
    chatService.sendMyConversationMessage
      .mockReturnValueOnce(throwError(() => new Error('offline')))
      .mockReturnValueOnce(of({
        id: 51, conversationId: 11, senderId: 42, receiverId: 0,
        content: 'Thu lai', clientMessageId: 'customer-client-1', deliveryStatus: 'PERSISTED'
      }));
    component.newMessage = 'Thu lai';

    component.sendMessage();
    component.sendMessage();

    expect(chatService.sendMyConversationMessage).toHaveBeenNthCalledWith(
      1, 11, 'Thu lai', 'customer-client-1');
    expect(chatService.sendMyConversationMessage).toHaveBeenNthCalledWith(
      2, 11, 'Thu lai', 'customer-client-1');
    expect(component.messages()).toEqual([expect.objectContaining({ id: 51 })]);
  });

  function page<T>(content: T[]) {
    return { content, totalElements: content.length, totalPages: content.length ? 1 : 0,
      number: 0, size: 50, first: true, last: true, retentionDays: 365 };
  }
});
