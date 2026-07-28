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
});
