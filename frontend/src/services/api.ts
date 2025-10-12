const j = async (r: Response) => { if (!r.ok) throw new Error(`${r.status}`); return r.json() }

export type CodeItem = { code: string, name: string }
export const getMakers      = () => fetch(`/api/codes/makers`).then(j)
export const getModelGroups = (makerCode:string) => fetch(`/api/codes/model-groups?makerCode=${encodeURIComponent(makerCode)}`).then(j)
export const getModels      = (makerCode:string, modelGroupCode:string) =>
    fetch(`/api/codes/models?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}`).then(j)
export const getTrims       = (makerCode:string, modelGroupCode:string, modelCode:string) =>
    fetch(`/api/codes/trims?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}&modelCode=${encodeURIComponent(modelCode)}`).then(j)
export const getGrades      = (makerCode:string, modelGroupCode:string, modelCode:string, trimCode:string) =>
    fetch(`/api/codes/grades?makerCode=${encodeURIComponent(makerCode)}&modelGroupCode=${encodeURIComponent(modelGroupCode)}&modelCode=${encodeURIComponent(modelCode)}&trimCode=${encodeURIComponent(trimCode)}`).then(j)

export type CarListItem = {
  carId:number; maker:string; model:string; trim?:string; year?:number; km?:number;
  priceMin?:number; priceMax?:number; priceUpdatedAt?:string; representativeImageUrl?:string;  modelCode?: string; // ★ 추가
}
export type CarListResponse = { content:CarListItem[]; page:number; size:number; totalElements:number; totalPages:number; }
export const searchCars = (params:Record<string,any>)=>{
  const usp=new URLSearchParams()
  Object.entries(params).forEach(([k,v])=>{ if(v!==undefined&&v!==null&&v!=='') usp.append(k,String(v)) })
  return fetch(`/api/cars?`+usp.toString()).then(j)
}

export type CarDetail = {
  carId:number; specs:Record<string,any>;
  platforms:{platformCarId:number; platform:string; price:number; status:string; pcUrl?:string; mUrl?:string; lastSeenDate?:string}[];
  recommended:number[];
}
export const getCarDetail = (id:string|number)=> fetch(`/api/cars/${id}`).then(j)

export type PricePoint = { ts: string; price: number }   // ← PriceChart가 요구하는 타입
export const getPriceHistory = (id:string|number, platformCarId?:number)=>{
  const u = new URL(`/api/cars/${id}/price-history`, window.location.origin)
  if(platformCarId) u.searchParams.set('platformCarId', String(platformCarId))
  return fetch(u).then(j) as Promise<{ points: PricePoint[] }>
}
