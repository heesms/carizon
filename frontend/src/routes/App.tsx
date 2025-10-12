import React from 'react'
import { Outlet, Link, useLocation } from 'react-router-dom'
import AdSlot from '@/components/AdSlot'

export default function App(){
    const loc = useLocation()
    return (
        <div className="min-h-screen flex flex-col">
            <header className="bg-white border-b">
                <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between gap-6">
                    <Link to="/search" className="font-bold text-xl">Carizon</Link>
                    <nav className="text-sm text-gray-600 flex gap-4">
                        <Link to="/search" className={loc.pathname.startsWith('/search')?'font-semibold':''}>검색</Link>
                    </nav>
                </div>
            </header>
            <main className="flex-1">
                <div className="max-w-6xl mx-auto p-4">
                    <AdSlot id="global_top" className="mb-4 h-20" />
                    <Outlet />
                    <AdSlot id="global_bottom" className="mt-6 h-20" />
                </div>
            </main>
            <footer className="border-t bg-white">
                <div className="max-w-6xl mx-auto px-4 py-6 text-xs text-gray-500">© Carizon</div>
            </footer>
        </div>
    )
}
