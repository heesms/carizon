import React, { useEffect, useState } from "react";
import { fetchCars, CarListItem, Page } from "../api/cars";
import { Link } from "react-router-dom";

function fmt(n?: number) {
    if (n == null) return "-";
    return n.toLocaleString("ko-KR");
}

export default function CarsPage() {
    const [form, setForm] = useState({
        maker: "",
        modelGroup: "",
        model: "",
        trim: "",
        grade: "",
        q: "",
    });
    const [pageIdx, setPageIdx] = useState(0);
    const [data, setData] = useState<Page<CarListItem> | null>(null);
    const [loading, setLoading] = useState(false);

    const load = async (p = 0) => {
        setLoading(true);
        try {
            const res = await fetchCars({ ...form, page: p, size: 20 });
            setData(res);
            setPageIdx(p);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { load(0); /* 최초 로드 */ }, []);

    const onChange = (e: React.ChangeEvent<HTMLInputElement>) =>
        setForm((f) => ({ ...f, [e.target.name]: e.target.value }));

    return (
        <div style={{ padding: 24 }}>
            <h1 style={{ marginBottom: 16 }}>차량 검색</h1>

            <div style={{ display: "grid", gridTemplateColumns: "280px 1fr", gap: 24 }}>
                <aside style={{ border: "1px solid #e5e7eb", borderRadius: 12, padding: 16 }}>
                    {[
                        ["maker", "제조사"],
                        ["modelGroup", "대표차종"],
                        ["model", "차종"],
                        ["trim", "트림"],
                        ["grade", "등급"],
                        ["q", "키워드 (번호판/이름)"],
                    ].map(([name, label]) => (
                        <div key={name} style={{ marginBottom: 10 }}>
                            <label style={{ fontSize: 12, color: "#6b7280" }}>{label}</label>
                            <input
                                name={name}
                                value={(form as any)[name]}
                                onChange={onChange}
                                style={{ width: "100%", padding: 8, borderRadius: 8, border: "1px solid #e5e7eb" }}
                            />
                        </div>
                    ))}
                    <button onClick={() => load(0)} disabled={loading} style={{ width:"100%", padding:10, borderRadius:8 }}>
                        {loading ? "검색중..." : "검색"}
                    </button>
                </aside>

                <main>
                    {!data && loading && <div>로딩중…</div>}
                    {data && (
                        <>
                            <div style={{ marginBottom: 12, color: "#6b7280" }}>
                                총 {data.totalElements.toLocaleString()}건 / {pageIdx + 1} / {data.totalPages || 1}
                            </div>

                            {data.content.map((it) => {
                                const cheapest = it.cheapestPlatform?.split(":")[0] ?? "-";
                                return (
                                    <div
                                        key={it.myCarKey}
                                        style={{
                                            border: "1px solid #e5e7eb",
                                            borderRadius: 12,
                                            padding: 16,
                                            marginBottom: 12,
                                        }}
                                    >
                                        <Link to={`/cars/${encodeURIComponent(it.myCarKey)}`} style={{ textDecoration: "none", color: "inherit" }}>
                                            <div style={{ marginBottom: 6 }}>
                        <span
                            style={{
                                fontSize: 12,
                                padding: "2px 8px",
                                borderRadius: 999,
                                background: "#f3f4f6",
                                marginRight: 8,
                            }}
                        >
                          {it.advertStatus}
                        </span>
                                                <span style={{ color: "#6b7280" }}>번호판: {it.numberPlate}</span>
                                            </div>
                                            <h3 style={{ margin: 0 }}>
                                                {it.makerName} {it.modelGroupName} {it.modelName} {it.trimName ?? ""} {it.gradeName ?? ""}
                                            </h3>
                                            <div style={{ marginTop: 6 }}>
                                                <b>{fmt(it.minPrice)} ~ {fmt(it.maxPrice)} 만원</b> · 최저가 플랫폼: <b>{cheapest}</b>
                                            </div>
                                        </Link>
                                    </div>
                                );
                            })}

                            <div style={{ display: "flex", gap: 8, alignItems: "center", marginTop: 16 }}>
                                <button onClick={() => load(pageIdx - 1)} disabled={data.first || loading}>이전</button>
                                <span>{pageIdx + 1} / {data.totalPages || 1}</span>
                                <button onClick={() => load(pageIdx + 1)} disabled={data.last || loading}>다음</button>
                            </div>
                        </>
                    )}
                </main>
            </div>
        </div>
    );
}
