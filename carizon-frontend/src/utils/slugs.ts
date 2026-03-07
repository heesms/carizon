// Maker slug mapping: slug ↔ code ↔ display name
export type MakerSlugEntry = { slug: string; code: string; displayName: string }

export const MAKER_SLUG_MAP: MakerSlugEntry[] = [
  { slug: 'hyundai',       code: '101', displayName: 'HYUNDAI' },
  { slug: 'kia',           code: '102', displayName: 'KIA' },
  { slug: 'gm-korea',      code: '103', displayName: 'GM Korea' },
  { slug: 'kg-mobility',   code: '104', displayName: 'KG Mobility' },
  { slug: 'renault-korea', code: '105', displayName: 'RENAULT' },
  { slug: 'bmw',           code: '107', displayName: 'BMW' },
  { slug: 'mercedes-benz', code: '108', displayName: 'Mercedes-Benz' },
  { slug: 'audi',          code: '109', displayName: 'AUDI' },
  { slug: 'peugeot',       code: '110', displayName: 'Peugeot' },
  { slug: 'saab',          code: '111', displayName: 'Saab' },
  { slug: 'volkswagen',    code: '112', displayName: 'Volkswagen' },
  { slug: 'fiat',          code: '113', displayName: 'FIAT' },
  { slug: 'porsche',       code: '114', displayName: 'Porsche' },
  { slug: 'jaguar',        code: '115', displayName: 'Jaguar' },
  { slug: 'land-rover',    code: '116', displayName: 'Land Rover' },
  { slug: 'volvo',         code: '117', displayName: 'Volvo' },
  { slug: 'citroen',       code: '118', displayName: 'Citroën' },
  { slug: 'rolls-royce',   code: '119', displayName: 'Rolls-Royce' },
  { slug: 'chrysler',      code: '121', displayName: 'Chrysler' },
  { slug: 'ford',          code: '122', displayName: 'Ford' },
  { slug: 'honda',         code: '123', displayName: 'Honda' },
  { slug: 'toyota',        code: '124', displayName: 'Toyota' },
  { slug: 'mitsubishi',    code: '125', displayName: 'Mitsubishi' },
  { slug: 'mazda',         code: '126', displayName: 'Mazda' },
  { slug: 'isuzu',         code: '127', displayName: 'Isuzu' },
  { slug: 'nissan',        code: '128', displayName: 'Nissan' },
  { slug: 'daihatsu',      code: '129', displayName: 'Daihatsu' },
  { slug: 'dodge',         code: '130', displayName: 'Dodge' },
  { slug: 'lancia',        code: '131', displayName: 'Lancia' },
  { slug: 'lamborghini',   code: '132', displayName: 'Lamborghini' },
  { slug: 'lexus',         code: '133', displayName: 'LEXUS' },
  { slug: 'rover',         code: '134', displayName: 'Rover' },
  { slug: 'lotus',         code: '135', displayName: 'Lotus' },
  { slug: 'lincoln',       code: '136', displayName: 'Lincoln' },
  { slug: 'maserati',      code: '137', displayName: 'Maserati' },
  { slug: 'bentley',       code: '138', displayName: 'Bentley' },
  { slug: 'buick',         code: '139', displayName: 'Buick' },
  { slug: 'subaru',        code: '140', displayName: 'Subaru' },
  { slug: 'suzuki',        code: '141', displayName: 'Suzuki' },
  { slug: 'chevrolet',     code: '142', displayName: 'CHEVROLET' },
  { slug: 'alfa-romeo',    code: '143', displayName: 'Alfa Romeo' },
  { slug: 'opel',          code: '144', displayName: 'Opel' },
  { slug: 'cadillac',      code: '146', displayName: 'Cadillac' },
  { slug: 'ferrari',       code: '148', displayName: 'Ferrari' },
  { slug: 'pontiac',       code: '149', displayName: 'Pontiac' },
  { slug: 'hummer',        code: '150', displayName: 'Hummer' },
  { slug: 'renault',       code: '151', displayName: 'RENAULT' },
  { slug: 'gmc',           code: '152', displayName: 'GMC' },
  { slug: 'infiniti',      code: '153', displayName: 'Infiniti' },
  { slug: 'aston-martin',  code: '156', displayName: 'Aston Martin' },
  { slug: 'polestar',      code: '158', displayName: 'Polestar' },
  { slug: 'mini',          code: '160', displayName: 'MINI' },
  { slug: 'bugatti',       code: '163', displayName: 'Bugatti' },
  { slug: 'acura',         code: '167', displayName: 'Acura' },
  { slug: 'jeep',          code: '170', displayName: 'JEEP' },
  { slug: 'mclaren',       code: '173', displayName: 'McLaren' },
  { slug: 'byd',           code: '176', displayName: 'BYD' },
  { slug: 'genesis',       code: '189', displayName: 'GENESIS' },
  { slug: 'tesla',         code: '190', displayName: 'Tesla' },
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
  { slug: 'micro',       kr: '경차',   icon: '🚗', desc: '연비 좋고 주차 편한 경차 중고차를 비교해 보세요.' },
  { slug: 'small',       kr: '소형',   icon: '🚘', desc: '도심 주행에 최적화된 소형 중고차를 확인하세요.' },
  { slug: 'compact',     kr: '준중형', icon: '🚙', desc: '실용성과 경제성을 갖춘 준중형 중고차를 비교하세요.' },
  { slug: 'midsize',     kr: '중형',   icon: '🚗', desc: '균형 잡힌 성능의 중형 중고차를 한눈에 비교하세요.' },
  { slug: 'fullsize',    kr: '대형',   icon: '🚐', desc: '넉넉한 공간과 고급스러운 승차감의 대형 중고차입니다.' },
  { slug: 'rv',          kr: 'RV',     icon: '🚐', desc: '다목적 공간 활용이 뛰어난 RV 중고차 매물을 통합 비교하세요.' },
  { slug: 'suv',         kr: 'SUV',    icon: '🚙', desc: '넉넉한 공간과 높은 시야로 패밀리카로 인기 있는 SUV 중고차를 비교해 보세요.' },
  { slug: 'sports',      kr: '스포츠카', icon: '🏎️', desc: '역동적인 주행 성능의 스포츠카 중고차를 비교하세요.' },
  { slug: 'cargo',       kr: '화물',   icon: '🚚', desc: '다양한 용도의 화물차 중고차 매물을 확인하세요.' },
]

const KR_TO_BODY = new Map(BODY_TYPE_SLUG_MAP.map(e => [e.kr, e]))
const SLUG_TO_BODY = new Map(BODY_TYPE_SLUG_MAP.map(e => [e.slug, e]))

export const bodyTypeKrToSlug = (kr: string): string =>
  KR_TO_BODY.get(kr)?.slug ?? kr.toLowerCase()

export const bodyTypeSlugToKr = (slug: string): string | undefined =>
  SLUG_TO_BODY.get(slug)?.kr

export const bodyTypeSlugToEntry = (slug: string): BodyTypeSlugEntry | undefined =>
  SLUG_TO_BODY.get(slug)
