import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/services/api.service';

@Component({
  selector: 'app-sponsor-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="data">
      <h6>Your sponsored students</h6>
      <table class="table table-sm">
        <thead><tr><th>Name</th><th>Status</th></tr></thead>
        <tbody>
          <tr *ngFor="let g of data.grantees"><td>{{ g.user?.name }}</td><td>{{ g.paymentStatus }}</td></tr>
        </tbody>
      </table>
    </div>
  `
})
export class SponsorDashboardComponent implements OnInit {
  data: any;
  constructor(private api: ApiService) {}
  ngOnInit(): void { this.api.get('/sponsor/dashboard').subscribe((d) => (this.data = d)); }
}
