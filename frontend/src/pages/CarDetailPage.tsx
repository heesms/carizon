import React, { useEffect, useState } from "react";
import { fetchCarDetail, CarDetail } from "../api/cars";
import { useParams, Link } from "react-router-dom";

function fmt(n?: number) {
    if (n == null) return "-";
    return n.toLocaleString("ko-KR");
}

export default function CarDetailPage() {
    const { myCarKey = "" } = useParams();
    const [data, setData] = useState<CarDetail | null>(null);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        (async () => {
            setLoading(true);
            try {
                const d = await fetchCarDetail(myCarKey);
                setData(d);
            } finally {
                setLoading(false);
            }
        })();
    }, [myCarKey]);

    if (loading || !data) return <div style={{ padding: 24 }}>로딩중…</div>;

    // 최저가 하이라이트
    const cheapest = data.platforms?.slice().sort((a, b) => (a.priceNow ?? 9e9) - (b.priceNow ?? 9e9))[0];

    return (
        <div style={{ padding: 24 }}>
            <Link to="/cars">← 목록</Link>
            <h1 style={{ marginTop: 12 }}>
                {data.makerName} {data.modelGroupName} {data.modelName} {data.trimName ?? ""} {data.gradeName ?? ""}
            </h1>
            <div style={{ marginBottom: 8, color: "#6b7280" }}>
        <span style={{ marginRight: 8, background: "#eef2ff", padding: "4px 10px", borderRadius: 999 }}>
          {data.advertStatus}
        </span>
                번호판 <b>{data.numberPlate}</b> · 가격범위 <b>{fmt(data.minPrice)} ~ {fmt(data.maxPrice)} 만원</b>
            </div>

            <h3>플랫폼별 가격</h3>
            <div>
                {data.platforms?.map((p) => (
                    <div key={p.source}
                         style={{
                             border: "1px solid #e5e7eb",
                             borderRadius: 10,
                             padding: 12,
                             margin: "8px 0",
                             display: "flex",
                             justifyContent: "space-between",
                             ...(cheapest && cheapest.source === p.source && cheapest.priceNow === p.priceNow
                                 ? { borderColor: "#2563eb", boxShadow: "0 0 0 2px #dbeafe inset" }
                                 : {}),
                         }}>
                        <div><b>{p.source}</b></div>
                        <div><b>{fmt(p.priceNow)} 만원</b></div>
                        <div><a href={p.detailUrl} target="_blank" rel="noreferrer">상세 보기 →</a></div>
                    </div>
                ))}
            </div>
        </div>
    );
}
