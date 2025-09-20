// App.tsx
import { Routes, Route, Navigate } from "react-router-dom";
import CarsPage from "./pages/CarsPage";

export default function App() {
    return (
        <Routes>
            <Route path="/" element={<Navigate to="/cars" replace />} />
            <Route path="/cars" element={<CarsPage />} />
            <Route path="/list" element={<CarsPage />} />  {/* 🔹 추가 */}
            <Route path="*" element={<div>Not Found</div>} />
        </Routes>
    );
}
