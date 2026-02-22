export type CarListItem = {
    myCarKey: string;
    numberPlate: string;
    makerName: string;
    modelGroupName: string;
    modelName: string;
    trimName?: string;
    gradeName?: string;
    advertStatus: string;
    minPrice?: number;
    maxPrice?: number;
    cheapestPlatform?: string; // "CHUTCHA:1500"
};

export type Page<T> = {
    content: T[];
    totalPages: number;
    totalElements: number;
    number: number; // page index
    size: number;
    first: boolean;
    last: boolean;
};

export type CarDetail = {
    myCarKey: string;
    numberPlate: string;
    makerName: string;
    modelGroupName: string;
    modelName: string;
    trimName?: string;
    gradeName?: string;
    advertStatus: string;
    minPrice?: number;
    maxPrice?: number;
    platforms: { source: string; priceNow?: number; detailUrl: string }[];
};

const q = (params: Record<string, any>) =>
    Object.entries(params)
        .filter(([, v]) => v !== undefined && v !== null && `${v}`.trim() !== "")
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
        .join("&");

export async function fetchCars(params: {
    maker?: string;
    modelGroup?: string;
    model?: string;
    trim?: string;
    grade?: string;
    q?: string;
    page?: number;
    size?: number;
}) {
    const res = await fetch(`/api/cars?${q({ size: 20, page: 0, ...params })}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return (await res.json()) as Page<CarListItem>;
}

export async function fetchCarDetail(myCarKey: string) {
    const res = await fetch(`/api/cars/${encodeURIComponent(myCarKey)}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return (await res.json()) as CarDetail;
}
