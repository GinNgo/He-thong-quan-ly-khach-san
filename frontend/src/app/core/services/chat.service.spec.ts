import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { Subject, of } from 'rxjs';

import { AuthService } from './auth';
import { ChatService } from './chat.service';

describe('ChatService', () => {
  let service: ChatService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            getAccessToken: () => 'test-token',
            getCurrentUserId: () => 42,
            isLoggedIn: () => true,
            logout$: new Subject<void>(),
          }
        }
      ]
    });
    service = TestBed.inject(ChatService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('does not publish a message while the chat socket is offline', () => {
    expect(service.sendCustomerMessage('hello')).toBe(false);
  });

  it('uses the principal-scoped history endpoint', () => {
    service.getMyHistory().subscribe();

    const request = http.expectOne('http://localhost:8080/api/chat/me/history');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('adds a fresh correlation id to each published STOMP message', () => {
    const publish = vi.fn();
    const mutableService = service as unknown as {
      connected: boolean;
      stompClient: { publish: typeof publish };
    };
    mutableService.connected = true;
    mutableService.stompClient = { publish };

    expect(service.sendCustomerMessage('hello', 91)).toBe(true);
    expect(publish).toHaveBeenCalledWith(expect.objectContaining({
      destination: '/app/chat.support.send',
      body: JSON.stringify({ content: 'hello', conversationId: 91 }),
      headers: {
        'X-Correlation-ID': expect.stringMatching(/^chat-message-/),
      },
    }));
  });

  it('uses principal-scoped conversation and paginated message endpoints', () => {
    service.getMyConversations(1, 10).subscribe();
    const conversations = http.expectOne(request => request.url.endsWith('/api/chat/me/conversations'));
    expect(conversations.request.params.get('page')).toBe('1');
    expect(conversations.request.params.get('size')).toBe('10');
    conversations.flush({ content: [], totalElements: 0, totalPages: 0, number: 1, size: 10,
      first: false, last: true, retentionDays: 365 });

    service.getMyConversationMessages(91, 2, 25).subscribe();
    const messages = http.expectOne(request => request.url.endsWith('/api/chat/me/conversations/91/messages'));
    expect(messages.request.params.get('page')).toBe('2');
    expect(messages.request.params.get('size')).toBe('25');
    messages.flush({ content: [], totalElements: 0, totalPages: 0, number: 2, size: 25,
      first: false, last: true, retentionDays: 365 });
  });

  it('uses the conversation id for support message pagination', () => {
    service.getSupportConversationMessages(33, 1, 50).subscribe();

    const request = http.expectOne(item => item.url.endsWith('/api/chat/support/conversations/33/messages'));
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('1');
    request.flush({ content: [], totalElements: 0, totalPages: 0, number: 1, size: 50,
      first: false, last: true, retentionDays: 365 });
  });

  it('persists support replies through the selected conversation endpoint', () => {
    service.sendSupportConversationMessage(33, 'Da tiep nhan').subscribe();

    const request = http.expectOne('http://localhost:8080/api/chat/support/conversations/33/messages');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ content: 'Da tiep nhan' });
    request.flush({ id: 1, conversationId: 33, senderId: 7, receiverId: 42, content: 'Da tiep nhan' });
  });
});
