// App.jsx
import { Routes, Route, Navigate } from "react-router-dom";
import CarsPage from "./pages/CarsPage";
import CarDetailPage from "./pages/CarDetailPage";
import Header from "./components/Header";
import Footer from "./components/Footer";

export default function App() {
    return (
        <div className="app">
            <Header />
            <main className="min-h-screen">
                <Routes>
                    <Route path="/" element={<Navigate to="/cars" replace />} />
                    <Route path="/cars" element={<CarsPage />} />
                    <Route path="/cars/:id" element={<CarDetailPage />} />
                    <Route path="/list" element={<CarsPage />} />
                    <Route path="*" element={<div className="wrap"><h1>Not Found</h1></div>} />
                </Routes>
            </main>
            <Footer />
        </div>
    );
}
