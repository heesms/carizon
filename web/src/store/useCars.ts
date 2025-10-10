import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../services/apiClient';

export interface CarSummary {
  id: number;
  name: string;
  year: number | null;
  mileage: number | null;
  price: number | null;
  fuel: string | null;
  transmission: string | null;
  platforms: string[];
}

export function useCars() {
  const { data, isLoading } = useQuery({
    queryKey: ['cars'],
    queryFn: async () => {
      const response = await apiClient.get('/cars');
      const items: CarSummary[] = (response.data.data?.content || []).map((car: any) => ({
        ...car,
        platforms: Array.isArray(car.platforms) ? car.platforms : [],
      }));
      return items;
    },
  });

  return {
    cars: data ?? [],
    isLoading,
  };
}
