import { Pipe, PipeTransform } from '@angular/core';
import { marked } from 'marked';

/**
 * Previews website Markdown as the website shows it. Bound with [innerHTML], so Angular's sanitizer still strips
 * scripts, event handlers and javascript: URLs.
 */
@Pipe({ name: 'websiteMarkdown', standalone: true })
export class WebsiteMarkdownPipe implements PipeTransform {
  transform(value: unknown): string {
    return value ? (marked.parse(String(value), { async: false, gfm: true, breaks: true }) as string) : '';
  }
}
