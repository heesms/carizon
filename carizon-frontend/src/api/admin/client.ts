/**
 * Admin API 클라이언트 설정
 */

import axios from 'axios';

export const apiClient = axios.create({
  baseURL: '',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

// 401 응답 시 로그인 페이지로 리다이렉트
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const currentPath = window.location.pathname;
      if (!currentPath.startsWith('/admin/login')) {
        window.location.href = '/admin/login';
      }
    }
    console.error('Admin API Error:', error);
    return Promise.reject(error);
  }
);

export default apiClient;
