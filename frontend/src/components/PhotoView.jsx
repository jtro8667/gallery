// src/components/PhotoView.jsx

import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { CONFIG, resolveDataPath } from '../config';

export default function PhotoView() {
    const { galleryPath, imageName } = useParams(); // Directly extract galleryPath and imageName

    const navigate = useNavigate();

    const [gallery, setGallery] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        fetch(resolveDataPath(`${galleryPath}/index.json`))
            .then((res) => {
                if (!res.ok) throw new Error(`Failed to load index metadata for ${galleryPath}`);
                return res.json();
            })
            .then((data) => {
                setGallery(data);
                setLoading(false);
            })
            .catch((err) => {
                setError(err.message);
                setLoading(false);
            });
    }, [galleryPath]);

    const images = gallery?.images || [];
    const currentIndex = images.findIndex((img) => img.image === imageName);

    const hasPrevious = currentIndex > 0;
    const hasNext = currentIndex !== -1 && currentIndex < images.length - 1;

    const handlePrevious = useCallback(() => {
        if (hasPrevious) {
            const prevImage = images[currentIndex - 1].image;
            navigate(`/gallery/${galleryPath}/${prevImage}.htm`); // Updated navigate call with .htm
        }
    }, [hasPrevious, currentIndex, images, galleryPath, navigate]);

    const handleNext = useCallback(() => {
        if (hasNext) {
            const nextImage = images[currentIndex + 1].image;
            navigate(`/gallery/${galleryPath}/${nextImage}.htm`); // Updated navigate call with .htm
        }
    }, [hasNext, currentIndex, images, galleryPath, navigate]);

    const handleBackToGallery = useCallback(() => {
        navigate(`/gallery/${galleryPath}`);
    }, [galleryPath, navigate]);

    useEffect(() => {
        const handleKeyDown = (e) => {
            if (e.key === 'ArrowLeft') handlePrevious();
            if (e.key === 'ArrowRight') handleNext();
            if (e.key === 'Escape') handleBackToGallery();
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handlePrevious, handleNext, handleBackToGallery]);

    if (loading) return <div className="text-center p-10 font-semibold">Loading image view...</div>;
    if (error) return <div className="text-center p-10 text-red-600 font-semibold">Error: {error}</div>;
    if (currentIndex === -1) {
        return (
            <div className="text-center p-10">
                <p className="text-red-600 font-bold mb-4">Error: Image not found in gallery listing.</p>
                <Link to={`/gallery/${galleryPath}`} className="text-blue-600 hover:underline">{CONFIG.BACK_TO_GALLERY}</Link>
            </div>
        );
    }

    const currentImageData = images[currentIndex];

    return (
        <div className="min-h-screen flex flex-col bg-gray-900 text-gray-100 px-4 py-6 select-none">
            <header className="max-w-7xl w-full mx-auto mb-4 flex justify-between items-center">
                <Link
                    to={`/gallery/${galleryPath}`}
                    className="text-gray-400 hover:text-white transition-colors font-medium"
                >
                    {CONFIG.BACK_TO_GALLERY}
                </Link>
                <div className="text-sm text-gray-400 font-mono">
                    {currentIndex + 1} / {images.length}
                </div>
            </header>

            <main className="flex-grow flex flex-col items-center justify-center max-w-7xl w-full mx-auto relative gap-4 my-auto">
                {gallery && (
                    <div className="text-center mb-4">
                        <h1 className="text-2xl font-bold text-gray-100">{gallery.name}</h1>
                        {gallery.date && <p className="text-gray-400 text-sm">{gallery.date}</p>}
                        {gallery.event && <p className="text-gray-400 text-sm italic">Event: {gallery.event}</p>}
                    </div>
                )}

                <div className="flex items-center justify-between w-full">
                    <button
                        onClick={handlePrevious}
                        disabled={!hasPrevious}
                        className={`p-3 rounded-full bg-gray-800 hover:bg-gray-700 text-white transition-all focus:outline-none flex-shrink-0 z-10 ${
                            !hasPrevious ? 'opacity-20 cursor-not-allowed' : 'opacity-80 hover:opacity-100'
                        }`}
                        aria-label={CONFIG.PREVIOUS_BUTTON}
                    >
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
                        </svg>
                    </button>

                    <div className="w-full h-[70vh] flex items-center justify-center overflow-hidden">
                        <a
                            href={resolveDataPath(`${galleryPath}/${currentImageData.image}`)}
                            rel="noopener noreferrer"
                        >
                            <img
                                src={resolveDataPath(`${galleryPath}/${currentImageData.image}`)}
                                alt={currentImageData.description || imageName}
                                className="max-w-full max-h-full object-contain shadow-2xl rounded cursor-pointer"
                            />
                        </a>
                    </div>

                    <button
                        onClick={handleNext}
                        disabled={!hasNext}
                        className={`p-3 rounded-full bg-gray-800 hover:bg-gray-700 text-white transition-all focus:outline-none flex-shrink-0 z-10 ${
                            !hasNext ? 'opacity-20 cursor-not-allowed' : 'opacity-80 hover:opacity-100'
                        }`}
                        aria-label={CONFIG.NEXT_BUTTON}
                    >
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" />
                        </svg>
                    </button>
                </div>
            </main>

            <footer className="max-w-4xl w-full mx-auto mt-6 bg-gray-800 rounded-lg p-5 border border-gray-700 shadow-md">
                {currentImageData.description ? (
                    <h2 className="text-lg md:text-xl font-medium text-gray-100 mb-3">
                        {currentImageData.description}
                    </h2>
                ) : (
                    <p className="text-gray-500 italic mb-2">Bez popisku</p>
                )}

                <div className="grid grid-cols-1 sm:grid-cols-2 text-xs font-mono text-gray-400 gap-2 border-t border-gray-700 pt-3">
                    <div><span className="text-gray-500">File:</span> {currentImageData.image}</div>
                    {/* Removed Path display */}
                </div>
            </footer>
        </div>
    );
}