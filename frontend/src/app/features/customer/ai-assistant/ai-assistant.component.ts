import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';

@Component({
  standalone: true,
  imports: [SharedModule],
  selector: 'app-ai-assistant',
  templateUrl: './ai-assistant.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./ai-assistant.component.css'],
})
export class AiAssistantComponent implements OnInit {
  constructor() {}

  ngOnInit(): void {}
}
