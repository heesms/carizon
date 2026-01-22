import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import axios from 'axios'

export default function CarDetail() {
  const { id } = useParams()
  const [data, setData] = useState<any>(null)
  const [history, setHistory] = useState<any>(null)

  useEffect(() => {
    axios.get(`/api/cars/${id}`, { baseURL: 'http://localhost:8080' }).then(r => {
      setData(r.data)
      axios.get(`/api/cars/${id}/price-history`, { baseURL: 'http://localhost:8080' }).then(h => setHistory(h.data))
    })
  }, [id])

  if (!data) return <div className="p-6">Loading...</div>

  return (
    <div className="p-6 space-y-4">
      <h2 className="text-xl font-semibold">#{data.carId} 상세</h2>
      <pre className="bg-gray-50 p-3 rounded">{JSON.stringify(data.specs, null, 2)}</pre>
      <div>
        <h3 className="font-semibold mb-2">플랫폼별 매물</h3>
        <ul className="list-disc ml-6">
          {data.platforms?.map((p:any) => (
            <li key={p.platformCarId}>
              {p.platform} · {p.price?.toLocaleString()}원 · <a className="text-blue-600 underline" href={p.pcUrl} target="_blank">원문</a>
            </li>
          ))}
        </ul>
      </div>
      <div>
        <h3 className="font-semibold mb-2">가격 히스토리(샘플 데이터)</h3>
        <pre className="bg-gray-50 p-3 rounded">{JSON.stringify(history, null, 2)}</pre>
      </div>
    </div>
  )
}
