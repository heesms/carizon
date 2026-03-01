/**
 * 대시보드 API
 */

import apiClient from './client';

export interface DashboardStats {
  carMasterCount: number;
  platformCarCount: number;
  platformStats: Array<{ platform_name: string; count: number }>;
  recentCrawls: Array<any>;
  meilisearchCount: number;
  embeddingCount: number;
}

export const dashboardApi = {
  getStats: () =>
    apiClient.get<{ success: boolean; data: DashboardStats }>('/admin/dashboard/stats'),
};
