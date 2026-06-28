// src/App.jsx

import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import RootView from './components/RootView';
import GalleryOrPhotoView from './components/GalleryOrPhotoView';

function App() {
    return (
        <BrowserRouter>
            <div className="min-h-screen bg-gray-50 text-gray-900 font-sans antialiased">
                <Routes>
                    {/* 1. Root page displaying all top-level galleries */}
                    <Route path="/" element={<RootView />} />

                    {/* 2. Direct /gallery link goes back to root */}
                    <Route path="/gallery" element={<RootView />} />

                    {/* 3. Gallery and Photo View - Handles both gallery listings and individual photos */}
                    {/* The component will check if the path ends with .htm to determine which view to show */}
                    <Route path="/gallery/*" element={<GalleryOrPhotoView />} />

                    {/* Fallback for undefined routes */}
                    <Route path="*" element={
                        <div className="flex flex-col items-center justify-center min-h-screen">
                            <h1 className="text-2xl font-bold mb-4">404 - Page Not Found</h1>
                            <a href="/" className="text-blue-600 hover:underline">Return to Home</a>
                        </div>
                    } />
                </Routes>
            </div>
        </BrowserRouter>
    );
}

export default App;