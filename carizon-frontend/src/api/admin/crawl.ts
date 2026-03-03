/**
 * 크롤링 관리 API
 */

import apiClient from './client';

export interface CrawlRun {
  run_id: string;
  source: string;
  status: string;
  total_items: number;
  started_at: string;
  ended_at?: string;
  message?: string;
}

export const crawlApi = {
  runAll: () => apiClient.post('/admin/crawl/runAll'),
  runPlatform: (platform: string) => apiClient.post(`/admin/crawl/${platform}`),

  getRuns: (limit = 20, source?: string) =>
    apiClient.get<{ success: boolean; data: CrawlRun[] }>('/admin/crawl/runs', {
      params: { limit, source },
    }),

  runMerge: (bizDate?: string) =>
    apiClient.post('/admin/crawl/merge', null, {
      params: bizDate ? { bizDate } : {},
    }),

  runMergePlatform: (platform: string, bizDate?: string) =>
    apiClient.post(`/admin/crawl/merge/${platform}`, null, {
      params: bizDate ? { bizDate } : {},
    }),
};
