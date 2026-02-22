import { useSearchParams } from 'react-router-dom'

type Tab = 'contact' | 'terms' | 'privacy'

const TABS: { key: Tab; label: string }[] = [
  { key: 'contact', label: '문의하기' },
  { key: 'terms',   label: '이용약관' },
  { key: 'privacy', label: '개인정보처리방침' },
]

export default function Info() {
  const [sp, setSp] = useSearchParams()
  const activeTab = (sp.get('tab') ?? 'contact') as Tab

  return (
    <div className="max-w-3xl mx-auto animate-fade-in">
      {/* 탭 네비 */}
      <div className="flex border-b border-gray-200 mb-6">
        {TABS.map(({ key, label }) => (
          <button
            key={key}
            onClick={() => setSp({ tab: key })}
            className={`px-4 py-2.5 text-sm font-semibold border-b-2 transition-colors -mb-px whitespace-nowrap
              ${activeTab === key
                ? 'border-brand-600 text-brand-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'}`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* 탭 콘텐츠 */}
      <div className="card p-6 sm:p-8">
        {activeTab === 'contact' && <ContactContent />}
        {activeTab === 'terms'   && <TermsContent />}
        {activeTab === 'privacy' && <PrivacyContent />}
      </div>
    </div>
  )
}

// ── 문의하기 ──────────────────────────────────────────────────────────────────
function ContactContent() {
  return (
    <div className="space-y-5">
      <h1 className="text-xl font-black text-gray-900">문의하기</h1>
      <p className="text-sm text-gray-600">
        Carizon에 대한 문의사항이 있으시면 아래 방법으로 연락해 주세요.
      </p>

      <div className="flex items-start gap-3 p-4 bg-brand-50 rounded-xl border border-brand-100">
        <span className="text-2xl leading-none mt-0.5">📧</span>
        <div>
          <p className="text-sm font-bold text-gray-800 mb-0.5">이메일</p>
          <a
            href="mailto:carizon.info@gmail.com"
            className="text-sm text-brand-600 hover:underline font-semibold"
          >
            carizon.info@gmail.com
          </a>
        </div>
      </div>

      <p className="text-sm text-gray-500 leading-relaxed">
        콘텐츠 오류 제보, 제휴 문의, 기타 의견을 환영합니다.<br />
        확인 후 최대한 빠르게 답변드리겠습니다.
      </p>
    </div>
  )
}

// ── 이용약관 ──────────────────────────────────────────────────────────────────
function TermsContent() {
  return (
    <div className="space-y-5">
      <h1 className="text-xl font-black text-gray-900">이용약관</h1>
      <p className="text-sm text-gray-600 leading-relaxed">
        본 약관은 Carizon(이하 "본 사이트")의 이용과 관련하여
        이용자와 사이트 간의 권리 및 의무를 규정함을 목적으로 합니다.
      </p>

      <ol className="space-y-5 text-sm text-gray-600 list-decimal list-outside pl-5">
        <li>
          <p className="font-bold text-gray-800 mb-1">서비스 내용</p>
          <p className="leading-relaxed">
            본 사이트는 중고차 및 자동차 관련 정보를 제공하는 정보성 플랫폼입니다.<br />
            모든 콘텐츠는 참고용이며, 특정 거래를 보장하지 않습니다.
          </p>
        </li>
        <li>
          <p className="font-bold text-gray-800 mb-1">책임의 한계</p>
          <p className="leading-relaxed">
            본 사이트에서 제공하는 정보는 신뢰성을 높이기 위해 노력하고 있으나,
            정보의 정확성, 완전성에 대해 법적 책임을 지지 않습니다.<br />
            이용자는 본 사이트의 정보를 참고하여 스스로 판단해야 합니다.
          </p>
        </li>
        <li>
          <p className="font-bold text-gray-800 mb-1">저작권</p>
          <p className="leading-relaxed">
            본 사이트에 게시된 모든 콘텐츠의 저작권은 Carizon에 있으며,
            무단 복제, 배포, 재가공을 금지합니다.
          </p>
        </li>
        <li>
          <p className="font-bold text-gray-800 mb-1">약관의 변경</p>
          <p className="leading-relaxed">
            본 사이트는 필요 시 약관을 변경할 수 있으며,
            변경된 약관은 사이트에 게시함으로써 효력이 발생합니다.
          </p>
        </li>
      </ol>
    </div>
  )
}

// ── 개인정보처리방침 ───────────────────────────────────────────────────────────
function PrivacyContent() {
  return (
    <div className="space-y-5">
      <h1 className="text-xl font-black text-gray-900">개인정보처리방침</h1>
      <p className="text-sm text-gray-600 leading-relaxed">
        Carizon(이하 "본 사이트")는 이용자의 개인정보를 중요하게 생각하며,
        「개인정보 보호법」을 준수합니다.
      </p>

      <ol className="space-y-5 text-sm text-gray-600 list-decimal list-outside pl-5">
        <li>
          <p className="font-bold text-gray-800 mb-1">개인정보의 수집 항목 및 목적</p>
          <p className="leading-relaxed mb-2">본 사이트는 다음과 같은 정보를 수집할 수 있습니다.</p>
          <ul className="list-disc list-outside pl-4 space-y-1">
            <li>쿠키(Cookie): 방문 기록, 페이지 이용 정보 분석</li>
            <li>IP 주소, 브라우저 정보: 서비스 품질 개선 및 통계 분석</li>
          </ul>
          <p className="mt-2 leading-relaxed">수집된 정보는 사이트 운영 및 서비스 개선 목적으로만 사용됩니다.</p>
        </li>
        <li>
          <p className="font-bold text-gray-800 mb-1">개인정보의 보유 및 이용 기간</p>
          <p className="leading-relaxed">
            수집된 정보는 목적 달성 후 지체 없이 파기됩니다.<br />
            단, 관련 법령에 따라 보존이 필요한 경우 해당 기간 동안 보관할 수 있습니다.
          </p>
        </li>
        <li>
          <p className="font-bold text-gray-800 mb-1">개인정보의 제3자 제공</p>
          <p className="leading-relaxed">
            본 사이트는 이용자의 개인정보를 외부에 제공하지 않습니다.<br />
            다만, Google AdSense 등 광고 서비스 제공 과정에서 쿠키가 사용될 수 있습니다.
          </p>
        </li>
        <li>
          <p className="font-bold text-gray-800 mb-1">쿠키(Cookie)의 사용</p>
          <p className="leading-relaxed">
            본 사이트는 맞춤형 광고 제공을 위해 쿠키를 사용할 수 있으며,
            이용자는 브라우저 설정을 통해 쿠키 저장을 거부할 수 있습니다.
          </p>
        </li>
        <li>
          <p className="font-bold text-gray-800 mb-1">외부 광고 서비스</p>
          <p className="leading-relaxed">
            본 사이트는 Google AdSense를 사용하며, Google은 DoubleClick 쿠키를 사용하여
            이용자에게 맞춤 광고를 제공할 수 있습니다.
          </p>
          <a
            href="https://policies.google.com/privacy"
            target="_blank"
            rel="noopener noreferrer"
            className="text-xs text-brand-600 hover:underline mt-1 inline-block"
          >
            Google 개인정보 정책 보기 →
          </a>
        </li>
        <li>
          <p className="font-bold text-gray-800 mb-1">개인정보 보호 문의</p>
          <p className="leading-relaxed">
            개인정보 관련 문의는 아래 이메일로 연락 주시기 바랍니다.<br />
            이메일:{' '}
            <a href="mailto:carizon.info@gmail.com" className="text-brand-600 hover:underline">
              carizon.info@gmail.com
            </a>
          </p>
        </li>
      </ol>
    </div>
  )
}

