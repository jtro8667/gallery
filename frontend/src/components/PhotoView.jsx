// src/components/PhotoView.jsx

import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { CONFIG, resolveDataPath } from '../config';
import EmailDisplay from './EmailDisplay';

export default function PhotoView({ galleryPath: propGalleryPath, imageName: propImageName }) {
    const params = useParams();
    const galleryPath = propGalleryPath || params.galleryPath;
    const imageName = propImageName || params.imageName;

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
                document.title = `${data.name || CONFIG.PAGE_TITLE} - ${imageName}`;
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
    const isDark = CONFIG.THEME === 'dark';
    const bgClass = isDark ? 'bg-gray-900 text-gray-100' : 'bg-white text-gray-900';

    return (
        <div className={`min-h-screen flex flex-col px-4 py-6 select-none ${bgClass}`}>
            <header className="max-w-7xl w-full mx-auto mb-4 flex justify-between items-center">
                <Link
                    to={`/gallery/${galleryPath}`}
                    className={`hover:transition-colors font-medium ${isDark ? 'text-gray-400 hover:text-white' : 'text-gray-600 hover:text-gray-900'}`}
                >
                    {CONFIG.BACK_TO_GALLERY}
                </Link>
                <div className={`text-sm font-mono ${isDark ? 'text-gray-400' : 'text-gray-600'}`}>
                    {currentIndex + 1} / {images.length}
                </div>
            </header>

            <main className="flex-grow flex flex-col items-center justify-center max-w-7xl w-full mx-auto relative gap-4 my-auto">
                {gallery && (
                    <div className="text-center mb-4">
                        <h1 className={`text-2xl font-bold ${isDark ? 'text-gray-100' : 'text-gray-900'}`}>{gallery.name}</h1>
                        {gallery.date && <p className={`text-sm ${isDark ? 'text-gray-400' : 'text-gray-600'}`}>{gallery.date}</p>}
                        {gallery.event && <p className={`text-sm italic ${isDark ? 'text-gray-400' : 'text-gray-600'}`}>{CONFIG.EVENT_LABEL}: {gallery.event}</p>}
                    </div>
                )}

                <div className="flex items-center justify-between w-full">
                    <button
                        onClick={handlePrevious}
                        disabled={!hasPrevious}
                        className={`p-3 rounded-full transition-all focus:outline-none flex-shrink-0 z-10 ${
                            isDark
                                ? 'bg-gray-800 hover:bg-gray-700 text-white'
                                : 'bg-gray-200 hover:bg-gray-300 text-gray-900'
                        } ${
                            !hasPrevious ? 'opacity-20 cursor-not-allowed' : 'opacity-80 hover:opacity-100'
                        }`}
                        aria-label={CONFIG.PREVIOUS_BUTTON}
                    >
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
                        </svg>
                    </button>

                    <div className="w-full h-[70vh] flex items-center justify-center">
                        <a
                            href={resolveDataPath(`${galleryPath}/${currentImageData.image}`)}
                            rel="noopener noreferrer"
                            className="flex items-center justify-center w-full h-full"
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
                        className={`p-3 rounded-full transition-all focus:outline-none flex-shrink-0 z-10 ${
                            isDark
                                ? 'bg-gray-800 hover:bg-gray-700 text-white'
                                : 'bg-gray-200 hover:bg-gray-300 text-gray-900'
                        } ${
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

            <footer className={`max-w-4xl w-full mx-auto mt-6 rounded-lg p-5 border shadow-md ${
                isDark
                    ? 'bg-gray-800 border-gray-700'
                    : 'bg-gray-100 border-gray-300'
            }`}>
                {currentImageData.description ? (
                    <h2 className={`text-lg md:text-xl font-medium mb-3 ${isDark ? 'text-gray-100' : 'text-gray-900'}`}>
                        {currentImageData.description}
                    </h2>
                ) : (
                    <p className={`italic mb-2 ${isDark ? 'text-gray-500' : 'text-gray-600'}`}>Bez popisku</p>
                )}

                <div className={`grid grid-cols-1 sm:grid-cols-2 text-xs font-mono gap-2 border-t pt-3 ${isDark ? 'text-gray-400 border-gray-700' : 'text-gray-600 border-gray-300'}`}>
                    <div><span className={isDark ? 'text-gray-500' : 'text-gray-600'}>File:</span> {currentImageData.image}</div>
                    {/* Removed Path display */}
                </div>
            </footer>

            <footer className={`max-w-7xl w-full mx-auto mt-6 text-center text-sm ${isDark ? 'text-gray-400' : 'text-gray-600'}`}>
                <p>{CONFIG.FOOTER_COPYRIGHT}</p>
                <p><EmailDisplay email={CONFIG.FOOTER_EMAIL} /></p>
            </footer>
        </div>
    );
}