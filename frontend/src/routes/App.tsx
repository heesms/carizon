import React from 'react'
import { Outlet } from 'react-router-dom'
import Header from '@/components/Header'

export default function App(){
    return (
        <div className="min-h-screen flex flex-col bg-gradient-to-br from-gray-50 via-white to-gray-50">
            <Header />
            <main className="flex-1">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 lg:py-8">
                    <Outlet />
                </div>
            </main>
            <footer className="border-t border-gray-200 bg-white/80 backdrop-blur-sm mt-auto">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                    <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
                        <div className="text-sm text-gray-600">
                            © 2024 Carizon. All rights reserved.
                        </div>
                        <div className="flex gap-6 text-sm text-gray-500">
                            <a href="#" className="hover:text-gray-900 transition">이용약관</a>
                            <a href="#" className="hover:text-gray-900 transition">개인정보처리방침</a>
                            <a href="#" className="hover:text-gray-900 transition">문의하기</a>
                        </div>
                    </div>
                </div>
            </footer>
        </div>
    )
}
