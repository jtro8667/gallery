// src/App.jsx

import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import RootView from './components/RootView';
import GalleryView from './components/GalleryView';
import PhotoView from './components/PhotoView';

function App() {
    return (
        <BrowserRouter>
            <div className="min-h-screen bg-gray-50 text-gray-900 font-sans antialiased">
                <Routes>
                    {/* 1. Root page displaying all top-level galleries */}
                    <Route path="/" element={<RootView />} />

                    {/* 2. Direct /gallery link goes back to root */}
                    <Route path="/gallery" element={<RootView />} />

                    {/* 3. Photo View - Specific route for individual photos with .htm suffix */}
                    {/* This will match paths like /gallery/Beistein/DSC_56984.jpg.htm */}
                    <Route path="/gallery/:galleryPath/:imageName.htm" element={<PhotoView />} />

                    {/* 4. General Gallery View - Catches all other /gallery/* paths */}
                    <Route path="/gallery/*" element={<GalleryView />} />

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