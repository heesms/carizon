import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { carsApi } from "../api/carsApi";
import { Link } from "react-router-dom";

function fmt(n) {
    if (n == null) return "-";
    return n.toLocaleString("ko-KR");
}

export default function CarsPage() {
    const [filters, setFilters] = useState({
        maker: "",
        modelGroup: "",
        model: "",
        trim: "",
        grade: "",
        q: "",
        page: 0,
        size: 20,
    });

    const { data, isLoading, refetch } = useQuery({
        queryKey: ['cars', filters],
        queryFn: () => carsApi.getCars(filters),
    });

    const onChange = (e) => {
        setFilters(f => ({ ...f, [e.target.name]: e.target.value }));
    };

    const handleSearch = () => {
        setFilters(f => ({ ...f, page: 0 }));
        refetch();
    };

    const changePage = (newPage) => {
        setFilters(f => ({ ...f, page: newPage }));
    };

    return (
        <div className="wrap">
            <h1 className="text-3xl font-bold mb-6">차량 검색</h1>

            <div className="grid grid-cols-1 md:grid-cols-[280px_1fr] gap-6">
                <aside className="card">
                    {[
                        ["maker", "제조사"],
                        ["modelGroup", "대표차종"],
                        ["model", "차종"],
                        ["trim", "트림"],
                        ["grade", "등급"],
                        ["q", "키워드 (번호판/이름)"],
                    ].map(([name, label]) => (
                        <div key={name} className="mb-4">
                            <label className="block text-sm text-gray-600 mb-1">{label}</label>
                            <input
                                name={name}
                                value={filters[name]}
                                onChange={onChange}
                                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                        </div>
                    ))}
                    <button 
                        onClick={handleSearch}
                        disabled={isLoading}
                        className="w-full bg-blue-600 text-white py-2 px-4 rounded-lg hover:bg-blue-700 disabled:bg-gray-400"
                    >
                        {isLoading ? "검색중..." : "검색"}
                    </button>
                </aside>

                <main>
                    {isLoading && <div className="text-gray-500">로딩중…</div>}
                    
                    {data && (
                        <>
                            <div className="mb-4 text-gray-600">
                                총 {data.totalElements.toLocaleString()}건 / {filters.page + 1} / {data.totalPages || 1} 페이지
                            </div>

                            <div className="space-y-4">
                                {data.content.map((car) => (
                                    <div key={car.carId} className="card">
                                        <Link 
                                            to={`/cars/${car.carId}`}
                                            className="block hover:text-blue-600"
                                        >
                                            <div className="flex gap-4">
                                                <img 
                                                    src={car.representativeImageUrl}
                                                    alt={car.modelName}
                                                    className="w-24 h-16 object-contain bg-gray-100 rounded"
                                                />
                                                <div className="flex-1">
                                                    <div className="mb-2">
                                                        <span className="inline-block text-xs px-2 py-1 rounded-full bg-gray-100 text-gray-700 mr-2">
                                                            {car.advStatus}
                                                        </span>
                                                        <span className="text-sm text-gray-600">
                                                            번호판: {car.carNo}
                                                        </span>
                                                    </div>
                                                    <h3 className="font-bold text-lg mb-1">
                                                        {car.makerName} {car.modelGroupName} {car.modelName} {car.trimName || ""}
                                                    </h3>
                                                    <div className="text-sm text-gray-600">
                                                        {car.year}년 · {fmt(car.mileage)}km · {car.fuel} · {car.transmission}
                                                    </div>
                                                    <div className="mt-2">
                                                        <span className="text-xl font-bold text-blue-600">
                                                            {fmt(car.representativePrice)} 만원
                                                        </span>
                                                    </div>
                                                </div>
                                            </div>
                                        </Link>
                                    </div>
                                ))}
                            </div>

                            <div className="flex gap-3 items-center justify-center mt-8">
                                <button 
                                    onClick={() => changePage(filters.page - 1)}
                                    disabled={data.first || isLoading}
                                    className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    이전
                                </button>
                                <span className="text-gray-700">
                                    {filters.page + 1} / {data.totalPages || 1}
                                </span>
                                <button 
                                    onClick={() => changePage(filters.page + 1)}
                                    disabled={data.last || isLoading}
                                    className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    다음
                                </button>
                            </div>
                        </>
                    )}
                </main>
            </div>
        </div>
    );
}
