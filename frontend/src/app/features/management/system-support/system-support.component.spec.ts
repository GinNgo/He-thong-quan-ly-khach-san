import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, Subject } from 'rxjs';

import { AuthService } from '../../../core/services/auth';
import { ChatService } from '../../../core/services/chat.service';
import { SystemSupportComponent } from './system-support.component';

describe('SystemSupportComponent', () => {
  let fixture: ComponentFixture<SystemSupportComponent>;
  let component: SystemSupportComponent;
  const sendTenantMessage = vi.fn(() => true);

  beforeEach(async () => {
    sendTenantMessage.mockClear();
    await TestBed.configureTestingModule({
      imports: [SystemSupportComponent],
      providers: [
        { provide: ActivatedRoute, useValue: { queryParamMap: of({ get: (key: string) => key === 'propertyId' ? '11' : null }) } },
        { provide: AuthService, useValue: { getCurrentUserId: () => 52 } },
        {
          provide: ChatService,
          useValue: {
            connect: vi.fn(),
            disconnect: vi.fn(),
            message$: new Subject(),
            connectionState$: of('connected'),
            connectionError$: of(''),
            getMyTenantSupportHistory: () => of([]),
            isConnected: () => true,
            sendTenantMessage,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SystemSupportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('sends the request with the active tenant property', () => {
    component.newMessage = 'Cần hỗ trợ';
    component.sendMessage();

    expect(sendTenantMessage).toHaveBeenCalledWith('Cần hỗ trợ', 11);
  });

  it('unlocks the composer when the server acknowledgement never arrives', () => {
    vi.useFakeTimers();
    component.newMessage = 'Cần hỗ trợ';

    component.sendMessage();
    expect(component.isSending()).toBe(true);

    vi.advanceTimersByTime(10_000);

    expect(component.isSending()).toBe(false);
    expect(component.errorMessage()).toContain('Chưa nhận được xác nhận gửi');
    vi.useRealTimers();
  });
});
