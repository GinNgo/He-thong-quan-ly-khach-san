import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PaymentSimulator } from './payment-simulator';

describe('PaymentSimulator', () => {
  let component: PaymentSimulator;
  let fixture: ComponentFixture<PaymentSimulator>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentSimulator],
    }).compileComponents();

    fixture = TestBed.createComponent(PaymentSimulator);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
