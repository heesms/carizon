import { apiClient } from './client'

export interface BlogPostResponse {
  postId?: number
  title: string
  content: string
  status?: string
  carCount?: number
  message?: string
}

export const blogPostApi = {
  /**
   * 모델코드로 WordPress 포스팅 생성
   */
  postToWordPress: async (
    modelCode: string,
    limit: number = 10,
    status: string = 'draft'
  ): Promise<BlogPostResponse> => {
    const response = await apiClient.post(
      `/admin/recommendation/weekly-best/model/${modelCode}/post-to-wordpress`,
      null,
      {
        params: { limit, status },
      }
    )
    return response.data.data
  },

  /**
   * 포스팅 내용 미리보기 생성 (WordPress 포스팅 없이)
   */
  generatePostContent: async (
    modelCode: string,
    limit: number = 10
  ): Promise<BlogPostResponse> => {
    const response = await apiClient.post(
      `/admin/recommendation/weekly-best/model/${modelCode}/generate`,
      null,
      {
        params: { limit },
      }
    )
    return response.data.data
  },
}
