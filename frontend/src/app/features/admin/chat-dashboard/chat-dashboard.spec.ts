import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChatDashboardComponent } from './chat-dashboard';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
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
        { provide: ChatService, useValue: { connect: () => undefined, disconnect: () => undefined, message$: of(null) } },
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
});
