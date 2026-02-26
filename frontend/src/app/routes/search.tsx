import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import axios from 'axios'

function NumberInput({label, name, value, onChange}:{label:string, name:string, value:any, onChange:(e:any)=>void}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-sm text-gray-600">{label}</span>
      <input type="number" name={name} value={value ?? ''} onChange={onChange}
             className="border rounded px-2 py-1" />
    </label>
  )
}

export default function Search() {
  const [sp, setSp] = useSearchParams()
  const navigate = useNavigate()
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(false)

  const qs = useMemo(() => Object.fromEntries(sp.entries()), [sp])

  function updateQS(k:string, v:any) {
    const next = new URLSearchParams(sp)
    if (v === '' || v == null) next.delete(k); else next.set(k, String(v))
    navigate({ search: next.toString() }, { replace: true })
  }

  useEffect(() => {
    setLoading(true)
    axios.get('/api/search', { params: qs, baseURL: 'http://localhost:8080' })
      .then(res => setData(res.data))
      .finally(() => setLoading(false))
  }, [qs.q, qs.maker, qs.model, qs.priceMin, qs.priceMax, qs.yearMin, qs.yearMax, qs.kmMax, qs.sort, qs.page, qs.size])

  return (
    <div className="p-6 grid grid-cols-1 md:grid-cols-[280px_1fr] gap-6">
      <aside className="space-y-4">
        <div className="p-4 border rounded">
          <h3 className="font-semibold mb-2">검색</h3>
          <input className="w-full border rounded px-2 py-1" placeholder="모델/키워드"
                 defaultValue={qs.q ?? ''}
                 onKeyDown={(e:any) => { if (e.key === 'Enter') updateQS('q', e.target.value) }} />
          <button className="mt-2 px-3 py-1 border rounded" onClick={() => updateQS('q', (document.querySelector('input[placeholder="모델/키워드"]') as HTMLInputElement)?.value)}>적용</button>
        </div>
        <div className="p-4 border rounded space-y-3">
          <h3 className="font-semibold">필터</h3>
          <label className="flex flex-col gap-1">
            <span className="text-sm text-gray-600">제조사</span>
            <input className="border rounded px-2 py-1" value={qs.maker ?? ''} onChange={e=>updateQS('maker', e.target.value)} placeholder="Kia / Hyundai ..." />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-sm text-gray-600">모델</span>
            <input className="border rounded px-2 py-1" value={qs.model ?? ''} onChange={e=>updateQS('model', e.target.value)} placeholder="Sportage / Avante ..." />
          </label>
          <div className="grid grid-cols-2 gap-3">
            <NumberInput label="최소가" name="priceMin" value={qs.priceMin ?? ''} onChange={(e)=>updateQS('priceMin', e.target.value)} />
            <NumberInput label="최대가" name="priceMax" value={qs.priceMax ?? ''} onChange={(e)=>updateQS('priceMax', e.target.value)} />
            <NumberInput label="최소연식" name="yearMin" value={qs.yearMin ?? ''} onChange={(e)=>updateQS('yearMin', e.target.value)} />
            <NumberInput label="최대연식" name="yearMax" value={qs.yearMax ?? ''} onChange={(e)=>updateQS('yearMax', e.target.value)} />
            <NumberInput label="주행거리 ≤" name="kmMax" value={qs.kmMax ?? ''} onChange={(e)=>updateQS('kmMax', e.target.value)} />
          </div>
          <div>
            <span className="text-sm text-gray-600">정렬</span>
            <select className="w-full border rounded px-2 py-1 mt-1" value={qs.sort ?? 'RECENT'} onChange={e=>updateQS('sort', e.target.value)}>
              <option value="RECENT">최신순</option>
              <option value="LOW_PRICE">낮은가격순</option>
              <option value="LOW_KM">주행거리낮은순</option>
              <option value="NEW_YEAR">연식최신순</option>
            </select>
          </div>
          <button className="px-3 py-1 border rounded" onClick={()=>navigate('/cars?'+sp.toString())}>DB검색으로 보기</button>
        </div>
      </aside>

      <main>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-xl font-semibold">검색 결과</h2>
          <div className="text-sm text-gray-600">{data?.totalElements ?? 0}건</div>
        </div>
        {loading && <div>Loading...</div>}
        <div className="grid md:grid-cols-3 gap-4">
          {data?.content?.map((hit:any) => (
            <Link to={`/cars/${hit.id}`} key={hit.id} className="border rounded p-3 hover:shadow">
              {hit.imageUrl && <img className="w-full h-40 object-cover mb-2" src={`http://localhost:8088${hit.imageUrl}`} />}
              <div className="font-medium">{hit.maker} {hit.model} {hit.trim ?? ''}</div>
              <div className="text-sm text-gray-600">{hit.year ?? ''} · {hit.km ?? ''}km</div>
              <div className="text-blue-600 font-semibold">{hit.priceMin ? hit.priceMin.toLocaleString()+'원~' : '-'}</div>
            </Link>
          ))}
        </div>
      </main>
    </div>
  )
}
