import React from 'react'
import { Outlet } from 'react-router-dom'
import Header from '@/components/Header'

export default function App(){
    return (
        <div className="min-h-screen flex flex-col bg-gray-50">
            <Header />
            <main className="flex-1">
                <div className="max-w-6xl mx-auto p-4">
                    <Outlet />
                </div>
            </main>
            <footer className="border-t bg-white">
                <div className="max-w-6xl mx-auto px-4 py-6 text-xs text-gray-500">© Carizon</div>
            </footer>
        </div>
    )
}
