import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/services/api.service';

@Component({
  selector: 'app-coordinator-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="row g-3" *ngIf="data">
      <div class="col-md-4"><div class="card p-3"><div class="text-muted">Applications</div><div class="fs-3">{{ data.applicationsCount }}</div></div></div>
      <div class="col-md-4"><div class="card p-3"><div class="text-muted">Sponsors</div><div class="fs-3">{{ data.sponsorsCount }}</div></div></div>
      <div class="col-md-4"><div class="card p-3"><div class="text-muted">Grantees</div><div class="fs-3">{{ data.granteesCount }}</div></div></div>
    </div>
  `
})
export class CoordinatorDashboardComponent implements OnInit {
  data: any;
  constructor(private api: ApiService) {}
  ngOnInit(): void { this.api.get('/coordinator/dashboard').subscribe((d) => (this.data = d)); }
}
