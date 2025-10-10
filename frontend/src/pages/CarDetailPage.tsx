import React from "react";
import { useParams, Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { carsApi } from "../api/carsApi";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

function fmt(n) {
    if (n == null) return "-";
    return n.toLocaleString("ko-KR");
}

export default function CarDetailPage() {
    const { id } = useParams();

    const { data: car, isLoading: carLoading } = useQuery({
        queryKey: ['car', id],
        queryFn: () => carsApi.getCarDetail(id),
    });

    const { data: priceHistory, isLoading: historyLoading } = useQuery({
        queryKey: ['priceHistory', id],
        queryFn: () => carsApi.getPriceHistory(id),
    });

    if (carLoading || historyLoading) {
        return <div className="wrap">로딩중…</div>;
    }

    if (!car) {
        return <div className="wrap">차량을 찾을 수 없습니다.</div>;
    }

    // Prepare chart data
    const chartData = priceHistory?.map(point => ({
        date: new Date(point.checkedAt).toLocaleDateString('ko-KR'),
        price: point.price / 10000, // Convert to 만원
        platform: point.platformName
    })) || [];

    // Find cheapest platform listing
    const cheapest = car.platformListings?.slice().sort((a, b) => (a.price ?? 9e9) - (b.price ?? 9e9))[0];

    return (
        <div className="wrap">
            <Link to="/cars" className="text-blue-600 hover:underline">← 목록으로</Link>
            
            <h1 className="text-3xl font-bold mt-4 mb-2">
                {car.makerName} {car.modelGroupName} {car.modelName} {car.trimName || ""} 
            </h1>
            
            <div className="mb-6 text-gray-600">
                <span className="inline-block bg-blue-100 text-blue-800 px-3 py-1 rounded-full text-sm mr-2">
                    {car.advStatus}
                </span>
                번호판 <b>{car.carNo}</b> · {car.year}년 · {fmt(car.mileage)}km · {car.fuel} · {car.transmission}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
                <div className="card">
                    <h3 className="text-lg font-bold mb-3">차량 정보</h3>
                    <div className="space-y-2 text-sm">
                        <div className="flex justify-between">
                            <span className="text-gray-600">제조사:</span>
                            <span className="font-medium">{car.makerName}</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-gray-600">모델:</span>
                            <span className="font-medium">{car.modelName}</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-gray-600">색상:</span>
                            <span className="font-medium">{car.color}</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-gray-600">배기량:</span>
                            <span className="font-medium">{car.displacement}cc</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-gray-600">지역:</span>
                            <span className="font-medium">{car.region}</span>
                        </div>
                    </div>
                </div>

                <div className="card">
                    <img 
                        src={car.representativeImageUrl} 
                        alt={car.modelName}
                        className="w-full h-48 object-contain"
                    />
                </div>
            </div>

            <h3 className="text-2xl font-bold mb-4">플랫폼별 가격</h3>
            <div className="space-y-3 mb-8">
                {car.platformListings?.map((p) => (
                    <div 
                        key={p.platformCarId}
                        className={`card flex justify-between items-center ${
                            cheapest && cheapest.platformCarId === p.platformCarId 
                                ? 'border-blue-500 border-2 bg-blue-50' 
                                : ''
                        }`}
                    >
                        <div>
                            <div className="font-bold">{p.platformName}</div>
                            <div className="text-sm text-gray-600">
                                {p.km && `${fmt(p.km)}km · `}
                                {p.lastSeenDate && `최종 확인: ${p.lastSeenDate}`}
                            </div>
                        </div>
                        <div className="text-right">
                            <div className="text-2xl font-bold text-blue-600">
                                {fmt(p.price)} 만원
                            </div>
                            {p.pcUrl && (
                                <a 
                                    href={p.pcUrl} 
                                    target="_blank" 
                                    rel="noreferrer"
                                    className="text-sm text-blue-500 hover:underline"
                                >
                                    상세 보기 →
                                </a>
                            )}
                        </div>
                    </div>
                ))}
            </div>

            {chartData.length > 0 && (
                <div className="card">
                    <h3 className="text-2xl font-bold mb-4">가격 히스토리</h3>
                    <ResponsiveContainer width="100%" height={300}>
                        <LineChart data={chartData}>
                            <CartesianGrid strokeDasharray="3 3" />
                            <XAxis dataKey="date" />
                            <YAxis label={{ value: '가격 (만원)', angle: -90, position: 'insideLeft' }} />
                            <Tooltip />
                            <Legend />
                            <Line 
                                type="monotone" 
                                dataKey="price" 
                                stroke="#2563eb" 
                                strokeWidth={2}
                                name="가격"
                            />
                        </LineChart>
                    </ResponsiveContainer>
                </div>
            )}
        </div>
    );
}
