export type LikeInfo = {
    count: number;
    liked: boolean;
};

export type LikeResponse = {
    liked: boolean;
    count: number;
};

export type ApiResponse<T> = {
    success: boolean;
    data: T;
    message?: string;
};

/**
 * 차량 추천에 좋아요 토글
 */
export async function toggleLike(carId: number): Promise<LikeResponse> {
    const res = await fetch(`/api/likes/${carId}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
    });
    
    if (!res.ok) {
        const error = await res.json().catch(() => ({ message: 'Unknown error' }));
        throw new Error(error.message || `HTTP ${res.status}`);
    }
    
    const apiResponse: ApiResponse<LikeResponse> = await res.json();
    return apiResponse.data;
}

/**
 * 차량 추천의 좋아요 정보 조회
 */
export async function getLikeInfo(carId: number): Promise<LikeInfo> {
    const res = await fetch(`/api/likes/${carId}`);
    
    if (!res.ok) {
        const error = await res.json().catch(() => ({ message: 'Unknown error' }));
        throw new Error(error.message || `HTTP ${res.status}`);
    }
    
    const apiResponse: ApiResponse<LikeInfo> = await res.json();
    return apiResponse.data;
}

/**
 * 여러 차량의 좋아요 개수 일괄 조회
 */
export async function getLikeCounts(carIds: number[]): Promise<Record<number, number>> {
    const res = await fetch('/api/likes/batch', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ carIds }),
    });
    
    if (!res.ok) {
        const error = await res.json().catch(() => ({ message: 'Unknown error' }));
        throw new Error(error.message || `HTTP ${res.status}`);
    }
    
    const apiResponse: ApiResponse<Record<number, number>> = await res.json();
    return apiResponse.data;
}
