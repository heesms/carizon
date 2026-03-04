// Maker slug mapping: slug ↔ code ↔ display name
export type MakerSlugEntry = { slug: string; code: string; displayName: string }

export const MAKER_SLUG_MAP: MakerSlugEntry[] = [
  { slug: 'hyundai',       code: '101', displayName: 'HYUNDAI' },
  { slug: 'kia',           code: '102', displayName: 'KIA' },
  { slug: 'chevrolet',     code: '103', displayName: '쉐보레' },
  { slug: 'kg-mobility',   code: '104', displayName: 'KG모빌리티' },
  { slug: 'renault',       code: '105', displayName: '르노' },
  { slug: 'genesis',       code: '189', displayName: 'GENESIS' },
  { slug: 'bmw',           code: '107', displayName: 'BMW' },
  { slug: 'mercedes-benz', code: '108', displayName: '벤츠' },
  { slug: 'audi',          code: '109', displayName: '아우디' },
  { slug: 'volkswagen',    code: '110', displayName: '폭스바겐' },
  { slug: 'volvo',         code: '111', displayName: '볼보' },
  { slug: 'ford',          code: '112', displayName: '포드' },
  { slug: 'lexus',         code: '113', displayName: '렉서스' },
  { slug: 'toyota',        code: '114', displayName: '토요타' },
  { slug: 'honda',         code: '115', displayName: '혼다' },
  { slug: 'nissan',        code: '116', displayName: '닛산' },
  { slug: 'porsche',       code: '117', displayName: '포르쉐' },
  { slug: 'mini',          code: '120', displayName: 'MINI' },
  { slug: 'land-rover',    code: '118', displayName: '랜드로버' },
  { slug: 'jeep',          code: '121', displayName: 'JEEP' },
  { slug: 'peugeot',       code: '122', displayName: '푸조' },
  { slug: 'citroen',       code: '123', displayName: '시트로엥' },
  { slug: 'fiat',          code: '124', displayName: 'FIAT' },
  { slug: 'alfa-romeo',    code: '125', displayName: '알파로메오' },
  { slug: 'maserati',      code: '126', displayName: '마세라티' },
  { slug: 'ferrari',       code: '127', displayName: '페라리' },
  { slug: 'lamborghini',   code: '128', displayName: '람보르기니' },
  { slug: 'bentley',       code: '129', displayName: '벤틀리' },
  { slug: 'rolls-royce',   code: '130', displayName: '롤스로이스' },
  { slug: 'cadillac',      code: '131', displayName: '캐딜락' },
  { slug: 'lincoln',       code: '132', displayName: '링컨' },
  { slug: 'infiniti',      code: '133', displayName: '인피니티' },
  { slug: 'acura',         code: '134', displayName: '아큐라' },
  { slug: 'tesla',         code: '135', displayName: 'Tesla' },
  { slug: 'subaru',        code: '136', displayName: '스바루' },
  { slug: 'mitsubishi',    code: '137', displayName: '미쓰비시' },
  { slug: 'mazda',         code: '138', displayName: '마쓰다' },
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
