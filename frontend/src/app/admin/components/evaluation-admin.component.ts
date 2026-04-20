import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-evaluation-admin',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="evaluation-admin">
      <h2>Evaluation Admin</h2>
      <p>Manage evaluations here.</p>
    </div>
  `
})
export class EvaluationAdminComponent {
}