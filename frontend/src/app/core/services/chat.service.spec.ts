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

  it('loads paginated immutable support events and their retention policy', () => {
    service.getSupportConversationEvents(33, 2, 20).subscribe();
    const events = http.expectOne(item => item.url.endsWith('/api/chat/support/conversations/33/events'));
    expect(events.request.method).toBe('GET');
    expect(events.request.params.get('page')).toBe('2');
    expect(events.request.params.get('size')).toBe('20');
    events.flush({ content: [], totalElements: 0, totalPages: 0, number: 2, size: 20,
      first: false, last: true });

    service.getSupportAuditPolicy().subscribe();
    const policy = http.expectOne('http://localhost:8080/api/chat/support/audit-policy');
    expect(policy.request.method).toBe('GET');
    policy.flush({ appendOnly: true, retentionDays: 730, pageMaxRows: 100, events: [] });
  });

  it('persists support replies through the selected conversation endpoint', () => {
    service.sendSupportConversationMessage(33, 'Da tiep nhan', 4, 'support-1').subscribe();

    const request = http.expectOne('http://localhost:8080/api/chat/support/conversations/33/messages');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      conversationId: 33, content: 'Da tiep nhan', expectedVersion: 4, clientMessageId: 'support-1'
    });
    request.flush({ id: 1, conversationId: 33, senderId: 7, receiverId: 42, content: 'Da tiep nhan' });
  });

  it('sends client message ids and records delivery acknowledgements', () => {
    service.sendMyConversationMessage(91, 'Xin chao', 'customer-1').subscribe();
    const send = http.expectOne('http://localhost:8080/api/chat/me/conversations/91/messages');
    expect(send.request.body).toEqual({ content: 'Xin chao', clientMessageId: 'customer-1' });
    send.flush({ id: 5, conversationId: 91, senderId: 42, receiverId: 0, content: 'Xin chao' });

    service.acknowledgeMessage(5, 'READ').subscribe();
    const state = http.expectOne(request => request.url.endsWith('/api/chat/messages/5/state'));
    expect(state.request.method).toBe('POST');
    expect(state.request.params.get('state')).toBe('READ');
    state.flush({ id: 5, deliveryStatus: 'READ' });
  });

  it('sends tenant queue filters without redundant ALL values', () => {
    service.getSupportConversations({
      status: 'OPEN', assignment: 'ALL', sla: 'BREACHED', hotelId: 9, query: 'hoa don'
    })
      .subscribe();

    const request = http.expectOne(item => item.url.endsWith('/api/chat/support/conversations'));
    expect(request.request.params.get('status')).toBe('OPEN');
    expect(request.request.params.has('assignment')).toBe(false);
    expect(request.request.params.get('sla')).toBe('BREACHED');
    expect(request.request.params.get('hotelId')).toBe('9');
    expect(request.request.params.get('query')).toBe('hoa don');
    request.flush([]);
  });

  it('includes the optimistic version for queue lifecycle mutations', () => {
    service.unassignSupportConversation(33, 5).subscribe();

    const request = http.expectOne(item => item.url.endsWith('/api/chat/support/conversations/33/unassign'));
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('expectedVersion')).toBe('5');
    request.flush({ conversationId: 33, version: 6, status: 'OPEN' });
  });

  it('sends reasoned close/reopen requests and tenant attachment calls', () => {
    service.closeSupportConversation(33, 'Da xu ly xong', 6).subscribe();
    const close = http.expectOne(item => item.url.endsWith('/api/chat/support/conversations/33/close'));
    expect(close.request.params.get('expectedVersion')).toBe('6');
    expect(close.request.body).toEqual({ reason: 'Da xu ly xong' });
    close.flush({ conversationId: 33, version: 7, status: 'CLOSED' });

    service.reopenSupportConversation(33, 'Khach phan hoi them', 7).subscribe();
    const reopen = http.expectOne(item => item.url.endsWith('/api/chat/support/conversations/33/reopen'));
    expect(reopen.request.body).toEqual({ reason: 'Khach phan hoi them' });
    reopen.flush({ conversationId: 33, version: 8, status: 'OPEN' });

    service.getSupportAttachments(33).subscribe();
    const list = http.expectOne('http://localhost:8080/api/chat/support/conversations/33/attachments');
    expect(list.request.method).toBe('GET');
    list.flush([]);

    service.getMyAttachments(91).subscribe();
    const customerList = http.expectOne('http://localhost:8080/api/chat/me/conversations/91/attachments');
    expect(customerList.request.method).toBe('GET');
    customerList.flush([]);
  });
});
