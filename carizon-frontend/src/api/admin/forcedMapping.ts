import { apiClient } from './client'

export interface ForcedMapping {
  platform_name: string
  depth: number
  p_maker_code?: string
  p_model_group_code?: string
  p_model_code?: string
  p_trim_code?: string
  p_grade_code?: string
  maker_code: string
  model_group_code?: string
  model_code: string
  trim_code?: string
  grade_code?: string
  created_at: string
  updated_at: string
}

export const forcedMappingApi = {
  getForcedMappings: (platformName?: string) => {
    const params = platformName ? `?platformName=${platformName}` : ''
    return apiClient.get<ForcedMapping[]>(`/admin/forced-mapping${params}`)
  },

  createForcedMapping: (data: Partial<ForcedMapping>) => {
    return apiClient.post<string>('/admin/forced-mapping', data)
  },

  updateForcedMapping: (platformName: string, data: Partial<ForcedMapping>) => {
    return apiClient.put<string>(`/admin/forced-mapping/${platformName}`, data)
  },

  deleteForcedMapping: (
    platformName: string,
    params?: {
      pMakerCode?: string
      pModelGroupCode?: string
      pModelCode?: string
      pTrimCode?: string
      pGradeCode?: string
    }
  ) => {
    const queryParams = new URLSearchParams()
    if (params?.pMakerCode) queryParams.append('pMakerCode', params.pMakerCode)
    if (params?.pModelGroupCode) queryParams.append('pModelGroupCode', params.pModelGroupCode)
    if (params?.pModelCode) queryParams.append('pModelCode', params.pModelCode)
    if (params?.pTrimCode) queryParams.append('pTrimCode', params.pTrimCode)
    if (params?.pGradeCode) queryParams.append('pGradeCode', params.pGradeCode)
    const query = queryParams.toString()
    return apiClient.delete<string>(`/admin/forced-mapping/${platformName}${query ? '?' + query : ''}`)
  },
}
