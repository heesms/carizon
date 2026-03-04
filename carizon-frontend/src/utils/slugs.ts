// Maker slug mapping: slug ↔ code ↔ display name
export type MakerSlugEntry = { slug: string; code: string; displayName: string }

export const MAKER_SLUG_MAP: MakerSlugEntry[] = [
  { slug: 'hyundai',       code: '101', displayName: 'HYUNDAI' },
  { slug: 'kia',           code: '102', displayName: 'KIA' },
  { slug: 'chevrolet',     code: '103', displayName: 'CHEVROLET' },
  { slug: 'kg-mobility',   code: '104', displayName: 'KG Mobility' },
  { slug: 'renault',       code: '105', displayName: 'RENAULT' },
  { slug: 'genesis',       code: '189', displayName: 'GENESIS' },
  { slug: 'bmw',           code: '107', displayName: 'BMW' },
  { slug: 'mercedes-benz', code: '108', displayName: 'Mercedes-Benz' },
  { slug: 'audi',          code: '109', displayName: 'AUDI' },
  { slug: 'volkswagen',    code: '110', displayName: 'Volkswagen' },
  { slug: 'volvo',         code: '111', displayName: 'Volvo' },
  { slug: 'ford',          code: '112', displayName: 'Ford' },
  { slug: 'lexus',         code: '113', displayName: 'LEXUS' },
  { slug: 'toyota',        code: '114', displayName: 'Toyota' },
  { slug: 'honda',         code: '115', displayName: 'Honda' },
  { slug: 'nissan',        code: '116', displayName: 'Nissan' },
  { slug: 'porsche',       code: '117', displayName: 'Porsche' },
  { slug: 'mini',          code: '120', displayName: 'MINI' },
  { slug: 'land-rover',    code: '118', displayName: 'Land Rover' },
  { slug: 'jeep',          code: '121', displayName: 'JEEP' },
  { slug: 'peugeot',       code: '122', displayName: 'Peugeot' },
  { slug: 'citroen',       code: '123', displayName: 'Citroën' },
  { slug: 'fiat',          code: '124', displayName: 'FIAT' },
  { slug: 'alfa-romeo',    code: '125', displayName: 'Alfa Romeo' },
  { slug: 'maserati',      code: '126', displayName: 'Maserati' },
  { slug: 'ferrari',       code: '127', displayName: 'Ferrari' },
  { slug: 'lamborghini',   code: '128', displayName: 'Lamborghini' },
  { slug: 'bentley',       code: '129', displayName: 'Bentley' },
  { slug: 'rolls-royce',   code: '130', displayName: 'Rolls-Royce' },
  { slug: 'cadillac',      code: '131', displayName: 'Cadillac' },
  { slug: 'lincoln',       code: '132', displayName: 'Lincoln' },
  { slug: 'infiniti',      code: '133', displayName: 'Infiniti' },
  { slug: 'acura',         code: '134', displayName: 'Acura' },
  { slug: 'tesla',         code: '135', displayName: 'Tesla' },
  { slug: 'subaru',        code: '136', displayName: 'Subaru' },
  { slug: 'mitsubishi',    code: '137', displayName: 'Mitsubishi' },
  { slug: 'mazda',         code: '138', displayName: 'Mazda' },
]

// code → entry lookup
const CODE_TO_MAKER = new Map(MAKER_SLUG_MAP.map(e => [e.code, e]))
// slug → entry lookup
const SLUG_TO_MAKER = new Map(MAKER_SLUG_MAP.map(e => [e.slug, e]))

export const makerCodeToSlug = (code: string): string =>
  CODE_TO_MAKER.get(code)?.slug ?? code

export const makerSlugToCode = (slug: string): string | undefined =>
  SLUG_TO_MAKER.get(slug)?.code

export const makerSlugToDisplayName = (slug: string): string | undefined =>
  SLUG_TO_MAKER.get(slug)?.displayName

export const makerCodeToDisplayName = (code: string): string | undefined =>
  CODE_TO_MAKER.get(code)?.displayName

// Body type slug mapping: English slug ↔ Korean value
export type BodyTypeSlugEntry = { slug: string; kr: string; icon: string; desc: string }

export const BODY_TYPE_SLUG_MAP: BodyTypeSlugEntry[] = [
  { slug: 'suv',         kr: 'SUV',    icon: '🚙', desc: '넉넉한 공간과 높은 시야로 패밀리카로 인기 있는 SUV 중고차를 비교해 보세요.' },
  { slug: 'sedan',       kr: '세단',   icon: '🚗', desc: '안정적인 주행 성능과 우아한 디자인의 세단 중고차를 한눈에 비교하세요.' },
  { slug: 'rv',          kr: 'RV',     icon: '🚐', desc: '다목적 공간 활용이 뛰어난 RV 중고차 매물을 통합 비교하세요.' },
  { slug: 'hatchback',   kr: '해치백', icon: '🚗', desc: '도심 주행에 최적화된 실용적인 해치백 중고차를 확인하세요.' },
  { slug: 'coupe',       kr: '쿠페',   icon: '🏎️', desc: '스포티한 디자인과 역동적인 주행의 쿠페 중고차를 비교하세요.' },
  { slug: 'convertible', kr: '컨버터블', icon: '🏎️', desc: '오픈 에어 드라이빙을 즐길 수 있는 컨버터블 중고차입니다.' },
  { slug: 'pickup',      kr: '픽업트럭', icon: '🚚', desc: '작업성과 실용성을 갖춘 픽업트럭 중고차를 비교하세요.' },
  { slug: 'truck',       kr: '트럭',   icon: '🚚', desc: '다양한 용도의 트럭 중고차 매물을 확인하세요.' },
  { slug: 'minivan',     kr: '미니밴', icon: '🚐', desc: '대가족을 위한 넓은 공간의 미니밴 중고차를 비교해 보세요.' },
]

const KR_TO_BODY = new Map(BODY_TYPE_SLUG_MAP.map(e => [e.kr, e]))
const SLUG_TO_BODY = new Map(BODY_TYPE_SLUG_MAP.map(e => [e.slug, e]))

export const bodyTypeKrToSlug = (kr: string): string =>
  KR_TO_BODY.get(kr)?.slug ?? kr.toLowerCase()

export const bodyTypeSlugToKr = (slug: string): string | undefined =>
  SLUG_TO_BODY.get(slug)?.kr

export const bodyTypeSlugToEntry = (slug: string): BodyTypeSlugEntry | undefined =>
  SLUG_TO_BODY.get(slug)
