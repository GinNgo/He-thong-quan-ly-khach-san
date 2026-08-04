import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';

import { ChatDashboardComponent } from './chat-dashboard';
import { ChatConversation, ChatMessage, ChatService } from '../../../core/services/chat.service';
import { AuthService } from '../../../core/services/auth';

describe('ChatDashboard', () => {
  let component: ChatDashboardComponent;
  let fixture: ComponentFixture<ChatDashboardComponent>;
  let incoming: Subject<ChatMessage | null>;
  let getMessages: ReturnType<typeof vi.fn>;
  let sendSupportMessage: ReturnType<typeof vi.fn>;
  let getConversations: ReturnType<typeof vi.fn>;
  let claimConversation: ReturnType<typeof vi.fn>;
  let closeConversation: ReturnType<typeof vi.fn>;

  const conversation: ChatConversation = {
    conversationId: 21,
    customerId: 42,
    customerName: 'Customer',
    subject: 'Hoa don',
    hotelId: 5,
    hotelName: 'LuxeStay Sai Gon',
    status: 'OPEN',
    version: 3,
    slaState: 'AT_RISK',
    lastMessage: 'Can ho tro',
  };

  beforeEach(async () => {
    incoming = new Subject<ChatMessage | null>();
    getMessages = vi.fn(() => of(page([])));
    getConversations = vi.fn(() => of([conversation]));
    claimConversation = vi.fn(() => of({
      ...conversation, status: 'ASSIGNED' as const, assignedAgentId: 7, version: 4
    }));
    closeConversation = vi.fn(() => of({
      ...conversation, status: 'CLOSED' as const, closedReason: 'Da xu ly', version: 4
    }));
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
            getSupportConversations: getConversations,
            getSupportConversationMessages: getMessages,
            sendSupportConversationMessage: sendSupportMessage,
            createClientMessageId: () => 'support-client-1',
            acknowledgeMessage: vi.fn(messageId => of({
              id: messageId, conversationId: 21, senderId: 42, receiverId: 0,
              content: 'Can ho tro', deliveryStatus: 'READ' as const
            })),
            claimSupportConversation: claimConversation,
            unassignSupportConversation: vi.fn(),
            escalateSupportConversation: vi.fn(),
            reopenSupportConversation: vi.fn(),
            closeSupportConversation: closeConversation,
            getSupportAttachments: vi.fn(() => of([])),
            uploadSupportAttachment: vi.fn(),
            downloadAttachment: vi.fn(),
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

    expect(sendSupportMessage).toHaveBeenCalledWith(21, 'Da tiep nhan', 3, 'support-client-1');
  });

  it('ignores realtime messages from a different conversation', () => {
    component.selectConversation(conversation);

    incoming.next({ id: 5, conversationId: 99, senderId: 44, receiverId: 0, content: 'other' });

    expect(component.messages()).toEqual([]);
  });

  it('applies assignment and SLA filters to the tenant queue', () => {
    component.assignmentFilter = 'MINE';
    component.slaFilter = 'BREACHED';

    component.applyFilters();

    expect(getConversations).toHaveBeenLastCalledWith({
      status: 'ALL', assignment: 'MINE', sla: 'BREACHED', query: ''
    });
  });

  it('claims the selected conversation using its optimistic version', () => {
    component.selectConversation(conversation);

    component.claimConversation();

    expect(claimConversation).toHaveBeenCalledWith(21, 3);
    expect(component.getConversation(21)?.status).toBe('ASSIGNED');
    expect(component.getConversation(21)?.version).toBe(4);
  });

  it('reloads the queue and explains recovery after a version conflict', () => {
    claimConversation.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.selectConversation(conversation);

    component.claimConversation();
    fixture.detectChanges();

    expect(component.conflictRecovery()).toContain('tai lai');
    expect(getConversations).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('tai lai');
  });

  it('requires and sends an audited reason when closing a conversation', () => {
    component.selectConversation(conversation);
    component.lifecycleReason = 'Da xu ly';

    component.closeConversation();

    expect(closeConversation).toHaveBeenCalledWith(21, 'Da xu ly', 3);
    expect(component.getConversation(21)?.status).toBe('CLOSED');
    expect(component.lifecycleReason).toBe('');
  });

  it('sends the search query with existing queue filters', () => {
    component.searchQuery = 'hoa don';

    component.applyFilters();

    expect(getConversations).toHaveBeenLastCalledWith(expect.objectContaining({ query: 'hoa don' }));
  });

  function page<T>(content: T[]) {
    return { content, totalElements: content.length, totalPages: content.length ? 1 : 0,
      number: 0, size: 50, first: true, last: true, retentionDays: 365 };
  }
});
