/**
 * 설정 관리 API
 */

import apiClient from './client';

export interface SearchModeConfig {
  useMeilisearch: boolean;
  fallbackToDb: boolean;
}

export interface LlmPrompt {
  id: number;
  prompt_type: string;
  prompt_text: string;
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export interface LlmMatchingConfig {
  config_key: string;
  config_value: number;
  description: string;
  updated_at: string;
}

export const configApi = {
  getSearchMode: () =>
    apiClient.get<{ success: boolean; data: SearchModeConfig }>('/admin/config/search-mode'),
  setSearchMode: (config: SearchModeConfig) =>
    apiClient.post<{ success: boolean; data: string }>('/admin/config/search-mode', config),

  getCodes: (type?: string) =>
    apiClient.get<{ success: boolean; data: any }>('/admin/config/codes', {
      params: type ? { type } : {},
    }),

  getLlmPrompts: () =>
    apiClient.get<{ success: boolean; data: LlmPrompt[] }>('/admin/config/llm/prompts'),
  getLlmPrompt: (type: string) =>
    apiClient.get<{ success: boolean; data: LlmPrompt }>(`/admin/config/llm/prompts/${type}`),
  updateLlmPrompt: (type: string, data: { prompt_text: string; is_active: boolean }) =>
    apiClient.post<{ success: boolean; data: string }>(`/admin/config/llm/prompts/${type}`, data),

  getLlmMatching: () =>
    apiClient.get<{ success: boolean; data: LlmMatchingConfig[] }>('/admin/config/llm/matching'),
  updateLlmMatching: (key: string, data: { config_value: number; description?: string }) =>
    apiClient.post<{ success: boolean; data: string }>(`/admin/config/llm/matching/${key}`, data),
  updateLlmMatchingBatch: (configs: Record<string, number>) =>
    apiClient.post<{ success: boolean; data: string }>('/admin/config/llm/matching/batch', configs),
  getLlmMatchingPresets: () =>
    apiClient.get<{ success: boolean; data: string[] }>('/admin/config/llm/matching/preset'),
  applyLlmMatchingPreset: (presetName: string) =>
    apiClient.post<{ success: boolean; data: string }>(`/admin/config/llm/matching/preset/${presetName}`),
};
