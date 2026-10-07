import { Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { ApiService } from './api.service';
import { Chapter } from '../../admin/admin-chapters.component';

/** The chapter list behind every Chapter dropdown, loaded once per session. */
@Injectable({ providedIn: 'root' })
export class ChapterService {
  private cache$?: Observable<Chapter[]>;

  constructor(private api: ApiService) {}

  list(): Observable<Chapter[]> {
    this.cache$ ??= this.api.get<Chapter[]>('/admin/chapters').pipe(shareReplay(1));
    return this.cache$;
  }

  /** Chapters to offer in a dropdown: the active ones, plus the currently chosen one even if it is inactive. */
  static options(chapters: Chapter[], currentId: number | null | undefined): Chapter[] {
    return chapters.filter((c) => c.active !== false || (currentId != null && c.chapterId === currentId));
  }

  /** Call after chapters are added, renamed or deleted so dropdowns reload. */
  refresh(): void {
    this.cache$ = undefined;
  }
}
