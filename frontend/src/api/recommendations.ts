export type RecommendationRequest = {
    query: string;
    maxResults?: number;
    minPrice?: number;
    maxPrice?: number;
    maker?: string;
    fuel?: string;
};

export type RecommendedCar = {
    carId: number;
    maker: string;
    model: string;
    trim?: string;
    year?: number;
    mileage?: number;
    price?: number;
    fuel?: string;
    transmission?: string;
    color?: string;
    region?: string;
    url?: string;
    pcUrl?: string;
    mUrl?: string;
    /** 차량 이미지 URL (API는 imageUrl 또는 car_image_url 반환 가능) */
    imageUrl?: string;
    car_image_url?: string;
    relevanceScore?: number;
    reason?: string;
};

export type RecommendationResponse = {
    recommendation: string;
    cars: RecommendedCar[];
};

export type ApiResponse<T> = {
    success: boolean;
    data: T;
    message?: string;
};

export async function getRecommendations(request: RecommendationRequest): Promise<RecommendationResponse> {
    const res = await fetch('/api/recommendations', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            query: request.query,
            maxResults: request.maxResults || 5,
            minPrice: request.minPrice,
            maxPrice: request.maxPrice,
            maker: request.maker,
            fuel: request.fuel,
        }),
    });
    
    if (!res.ok) {
        const error = await res.json().catch(() => ({ message: 'Unknown error' }));
        throw new Error(error.message || `HTTP ${res.status}`);
    }
    
    const apiResponse: ApiResponse<RecommendationResponse> = await res.json();
    return apiResponse.data;
}
