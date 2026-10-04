import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/services/api.service';

@Component({
  selector: 'app-convenor-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="data">
      <h6>Applications in your region</h6>
      <table class="table table-sm">
        <thead><tr><th>Applicant</th><th>RCC</th></tr></thead>
        <tbody>
          <tr *ngFor="let a of data.applications"><td>{{ a.applicant_name }}</td><td>{{ a.rcc_name }}</td></tr>
        </tbody>
      </table>
    </div>
  `
})
export class ConvenorDashboardComponent implements OnInit {
  data: any;
  constructor(private api: ApiService) {}
  ngOnInit(): void { this.api.get('/convenor/dashboard').subscribe((d) => (this.data = d)); }
}
