import { apiClient } from './client'

export interface CodeMapping {
  platform_name: string
  p_maker_code?: string
  p_model_group_code?: string
  p_model_code?: string
  p_trim_code?: string
  p_grade_code?: string
  p_maker_name_norm?: string
  p_model_group_name_norm?: string
  p_model_name_norm?: string
  p_trim_name_norm?: string
  p_grade_name_norm?: string
  maker_code?: string
  model_group_code?: string
  model_code?: string
  trim_code?: string
  grade_code?: string
  confidence_score: number
  match_reason: 'PLATE_EQUAL' | 'HIER_TEXT' | 'MANUAL'
  status: 'AUTO' | 'REVIEW' | 'LOCKED'
  first_seen: string
  last_seen: string
}

export interface CodeMappingStats {
  statusStats: Array<{ status: string; count: number }>
  platformReview: Array<{ platform_name: string; count: number }>
  scoreStats: Array<{ score_range: string; count: number }>
}

export const codeMappingApi = {
  getReviewMappings: (params: {
    platformName?: string
    page?: number
    size?: number
  }) => {
    const queryParams = new URLSearchParams()
    if (params.platformName) queryParams.append('platformName', params.platformName)
    queryParams.append('page', String(params.page || 0))
    queryParams.append('size', String(params.size || 50))
    return apiClient.get<{
      items: CodeMapping[]
      total: number
      page: number
      size: number
    }>(`/admin/code-mapping/review?${queryParams}`)
  },

  getStats: () => {
    return apiClient.get<CodeMappingStats>('/admin/code-mapping/stats')
  },

  searchMappings: (params: {
    status?: string
    platform?: string
    keyword?: string
    page?: number
    size?: number
  }) => {
    const q = new URLSearchParams()
    if (params.status) q.append('status', params.status)
    if (params.platform) q.append('platformName', params.platform)
    if (params.keyword) q.append('keyword', params.keyword)
    q.append('page', String(params.page || 0))
    q.append('size', String(params.size || 50))
    return apiClient.get<{
      items: CodeMapping[]
      total: number
      page: number
      size: number
    }>(`/admin/code-mapping/search?${q}`)
  },

  updateMapping: (platformName: string, data: {
    p_maker_code?: string
    p_model_group_code?: string
    p_model_code?: string
    p_trim_code?: string
    p_grade_code?: string
    maker_code?: string
    model_group_code?: string
    model_code?: string
    trim_code?: string
    grade_code?: string
    status: 'AUTO' | 'LOCKED'
  }) => {
    return apiClient.put<string>(`/admin/code-mapping/review/${platformName}/update`, data)
  },

  runAutoMapping: (platformName: string, scope: 'TODAY' | 'FULL' = 'TODAY') => {
    return apiClient.post<{
      platform: string
      scope: string
      mappedCount: number
    }>(`/admin/code-mapping/auto-mapping/${platformName}?scope=${scope}`)
  },
}
