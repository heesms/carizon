import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../services/apiClient';
import { useAuthStore } from '../store/useAuthStore';

export function useAuthGuard() {
  const setProfile = useAuthStore((state) => state.setProfile);
  const profile = useAuthStore((state) => state.profile);

  useQuery({
    queryKey: ['me'],
    queryFn: () => apiClient.get('/me').then((res) => res.data.data),
    onSuccess: (data) => {
      const roles: string[] = Array.isArray(data?.roles) ? data.roles : Array.from(data?.roles || []);
      setProfile({
        email: data?.email,
        name: data?.name,
        roles,
      });
    },
    retry: false,
  });

  const roles = profile?.roles ?? [];
  const isAdmin = roles.includes('ROLE_ADMIN') || roles.includes('ADMIN');

  return {
    profile,
    isAdmin,
  };
}
