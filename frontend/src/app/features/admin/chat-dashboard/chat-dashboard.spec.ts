import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChatDashboardComponent } from './chat-dashboard';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, Subject } from 'rxjs';
import { AuthService } from '../../../core/services/auth';
import { ChatService } from '../../../core/services/chat.service';
import { UserService } from '../../../core/services/user.service';

describe('ChatDashboard', () => {
  let component: ChatDashboardComponent;
  let fixture: ComponentFixture<ChatDashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatDashboardComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
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
            connect: () => undefined,
            disconnect: () => undefined,
            message$: new Subject(),
            connectionState$: of('idle'),
            connectionError$: of(''),
            getSupportConversations: () => of([]),
            getSupportHistory: () => of([]),
            isConnected: () => false,
          }
        },
        { provide: UserService, useValue: { getAllUsers: () => of([]) } }
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatDashboardComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('does not fail before a conversation body is mounted', () => {
    expect(() => component.scrollToBottom()).not.toThrow();
  });
});
