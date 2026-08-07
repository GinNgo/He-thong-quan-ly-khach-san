import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivationState, Client, IFrame } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import type { MockInstance } from 'vitest';

import { AuthService } from './auth';
import { ClientObservabilityService } from './client-observability.service';
import { NotificationHistoryPage, NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let http: HttpTestingController;
  let token: string | null;
  let logout$: Subject<void>;
  let activateSpy: MockInstance<() => void>;

  beforeEach(() => {
    token = 'notification-token';
    logout$ = new Subject<void>();
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(true);
    activateSpy = vi.spyOn(Client.prototype, 'activate').mockImplementation(() => undefined);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: { getAccessToken: () => token, logout$ }
        },
        {
          provide: ClientObservabilityService,
          useValue: {
            createCorrelationId: () => 'notification-correlation',
            recordStompFailure: vi.fn(),
          }
        }
      ]
    });
    service = TestBed.inject(NotificationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('adds the bearer token before activating realtime notifications', () => {
    service.connect();

    const client = currentClient(service);
    expect(client.connectHeaders).toEqual(expect.objectContaining({
      Authorization: 'Bearer notification-token',
      'X-Correlation-ID': 'notification-correlation',
    }));
    expect(activateSpy).toHaveBeenCalledOnce();
  });

  it('does not activate realtime notifications without a valid token', () => {
    token = null;
    const states: string[] = [];
    service.connectionState$.subscribe(state => states.push(state));

    service.connect();

    expect(activateSpy).not.toHaveBeenCalled();
    expect(states.at(-1)).toBe('error');
  });

  it('subscribes only to the protected admin topic and personal user queue', () => {
    service.connect();
    const client = currentClient(service);
    const subscribe = vi.spyOn(client, 'subscribe').mockReturnValue({
      id: 'notification-test',
      unsubscribe: vi.fn(),
    });

    client.onConnect({} as IFrame);

    expect(subscribe).toHaveBeenCalledWith('/topic/admin/notifications', expect.any(Function));
    expect(subscribe).toHaveBeenCalledWith('/user/queue/notifications', expect.any(Function));
    expect(subscribe).not.toHaveBeenCalledWith('/topic/notifications', expect.any(Function));
  });

  it('exposes connected, reconnecting and offline states for shell recovery', () => {
    const states: string[] = [];
    service.connectionState$.subscribe(state => states.push(state));
    service.connect();
    const client = currentClient(service);
    vi.spyOn(client, 'subscribe').mockReturnValue({
      id: 'notification-state-test',
      unsubscribe: vi.fn(),
    });

    client.onConnect({} as IFrame);
    client.state = ActivationState.ACTIVE;
    client.onWebSocketClose({ wasClean: false } as CloseEvent);
    window.dispatchEvent(new Event('offline'));

    expect(states).toContain('connected');
    expect(states).toContain('reconnecting');
    expect(states.at(-1)).toBe('offline');
  });

  it('stops automatic retries when the broker rejects the authenticated session', () => {
    const states: string[] = [];
    service.connectionState$.subscribe(state => states.push(state));
    service.connect();
    const client = currentClient(service);
    const deactivate = vi.spyOn(client, 'deactivate').mockResolvedValue();

    client.onStompError({ headers: {}, body: '' } as IFrame);

    expect(states.at(-1)).toBe('error');
    expect(deactivate).toHaveBeenCalledWith({ force: true });
  });

  it('loads a bounded page of retained notification history', () => {
    let result: NotificationHistoryPage | undefined;
    service.getAdminNotifications(2, 25).subscribe(page => result = page);

    const request = http.expectOne(req => req.url === '/api/notifications');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('25');
    request.flush({
      content: [], totalElements: 0, totalPages: 0, number: 2, size: 25,
      first: false, last: true, unreadCount: 0, retentionDays: 90,
    });

    expect(result?.retentionDays).toBe(90);
  });

  it('marks a notification through the ownership-protected REST endpoint', () => {
    service.markAsRead(17).subscribe();

    const request = http.expectOne('/api/notifications/17/read');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(null);
  });
});

function currentClient(service: NotificationService): Client {
  return (service as unknown as { stompClient: Client | null }).stompClient as Client;
}
