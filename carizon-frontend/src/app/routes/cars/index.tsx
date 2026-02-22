import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import axios from 'axios'

export default function Cars() {
  const [sp] = useSearchParams()
  const [data, setData] = useState<any>(null)

  useEffect(() => {
    const params = Object.fromEntries(sp.entries())
    axios.get('/api/cars', { params, baseURL: 'http://localhost:8080' })
      .then(res => setData(res.data))
  }, [sp])

  if (!data) return <div className="p-6">Loading...</div>

  return (
    <div className="p-6">
      <h2 className="text-xl font-semibold mb-4">Cars</h2>
      <div className="grid md:grid-cols-3 gap-4">
        {data.content.map((c:any) => (
          <Link to={`/cars/${c.carId}`} key={c.carId} className="border rounded-lg p-3 hover:shadow">
            {c.representativeImageUrl && (
              <img className="w-full h-40 object-cover mb-2" src={`http://localhost:8088${c.representativeImageUrl}`} />
            )}
            <div className="font-medium">{c.maker} {c.model} {c.trim ?? ''}</div>
            <div className="text-sm text-gray-600">{c.year ?? ''} · {c.km ?? ''}km</div>
            <div className="text-blue-600 font-semibold">{c.priceMin ? c.priceMin.toLocaleString()+'원~' : '-'}</div>
          </Link>
        ))}
      </div>
    </div>
  )
}
