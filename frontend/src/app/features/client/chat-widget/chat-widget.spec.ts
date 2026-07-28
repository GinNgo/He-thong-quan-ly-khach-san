import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';

import { ChatWidgetComponent } from './chat-widget';
import { ChatService } from '../../../core/services/chat.service';
import { AuthService } from '../../../core/services/auth';

describe('ChatWidget', () => {
  let component: ChatWidgetComponent;
  let fixture: ComponentFixture<ChatWidgetComponent>;
  let chatService: { sendCustomerMessage: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    chatService = {
      sendCustomerMessage: vi.fn(() => false),
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
            message$: new Subject(),
            connectionState$: of('idle'),
            connectionError$: of(''),
            getMyHistory: () => of([]),
            isConnected: () => false,
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ChatWidgetComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('does not fail when the chat body is not mounted', () => {
    expect(() => component.scrollToBottom()).not.toThrow();
  });

  it('exposes dialog semantics and avoids optimistic send while offline', () => {
    component.toggleChat();
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('[role="dialog"]');
    expect(panel?.getAttribute('aria-labelledby')).toBe('support-chat-title');
    expect(fixture.nativeElement.querySelector('.close-button')?.getAttribute('aria-label')).toContain('Đóng');

    component.newMessage = 'hello';
    component.sendMessage();

    expect(chatService.sendCustomerMessage).not.toHaveBeenCalled();
    expect(component.messages()).toHaveLength(0);
    expect(component.sendError()).toContain('offline');
  });
});
