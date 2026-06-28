// src/components/GalleryView.jsx

import React, { useState, useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { CONFIG, resolveDataPath } from '../config';

export default function GalleryView() {
    const params = useParams();
    const galleryPath = params['*'] || '';

    const [gallery, setGallery] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        // Fetch individual index.json inside the configured data folder directory
        fetch(resolveDataPath(`${galleryPath}/index.json`))
            .then((res) => {
                if (!res.ok) throw new Error(`Failed to load index.json for ${galleryPath}`);
                return res.json();
            })
            .then((data) => {
                setGallery(data);
                document.title = data.name || CONFIG.PAGE_TITLE;
                setLoading(false);
            })
            .catch((err) => {
                setError(err.message);
                setLoading(false);
            });
    }, [galleryPath]);

    if (loading) return <div className="text-center p-10 font-semibold">Loading gallery...</div>;
    if (error) return <div className="text-center p-10 text-red-600 font-semibold">Error: {error}</div>;
    if (!gallery) return null;

    const images = gallery.images || [];
    const hasSomeDescriptions = images.some(img => img.description && img.description.trim() !== '');

    let unlabelledImages = [];
    let labelledImages = [];

    if (hasSomeDescriptions) {
        let startIndex = 0;
        while (startIndex < images.length && (!images[startIndex].description || images[startIndex].description.trim() === '')) {
            unlabelledImages.push(images[startIndex]);
            startIndex++;
        }
        labelledImages = images.slice(startIndex);
    } else {
        unlabelledImages = [];
        labelledImages = images;
    }

    const galleryGridStyle = {
        gridTemplateColumns: `repeat(${CONFIG.GALLERY_COLUMNS}, minmax(0, 1fr))`
    };

    const renderImageGrid = (items) => (
        <div
            className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6 mb-12"
            style={window.innerWidth >= 1024 ? galleryGridStyle : undefined}
        >
            {items.map((img, idx) => (
                <Link
                    key={idx}
                    to={`/gallery/${galleryPath}/${img.image}.htm`}
                    className="bg-white rounded shadow hover:shadow-md transition-shadow duration-200 flex flex-col"
                >

                <div className="w-full aspect-[4/3] bg-gray-100 overflow-hidden flex items-center justify-center">
                        <img
                            src={resolveDataPath(`${galleryPath}/${img.preview}`)}
                            alt={img.description || img.image}
                            className="w-full h-full object-contain"
                            loading="lazy"
                        />
                    </div>

                    <div className="p-3 flex-grow min-h-[4rem]">
                        {img.description && (
                            <p className="text-sm text-gray-600 line-clamp-3">{img.description}</p>
                        )}
                    </div>
                </Link>
            ))}
        </div>
    );

    return (
        <div className="max-w-7xl mx-auto px-4 py-8">
            <Link to="/" className="text-blue-600 hover:underline inline-block mb-6 font-medium">
                {CONFIG.BACK_TO_ROOT}
            </Link>

            <header className="mb-10 border-b border-gray-200 pb-6">
                <div className="flex flex-wrap items-baseline gap-4">
                    <h1 className="text-3xl font-extrabold text-gray-900">{gallery.name}</h1>
                    {gallery.date && <span className="text-gray-500 text-lg">({gallery.date})</span>}
                </div>
                {gallery.event && (
                    <h2 className="text-xl text-gray-600 mt-2 italic">{CONFIG.EVENT_LABEL}: {gallery.event}</h2>
                )}
                {gallery.note && (
                    <p className="mt-4 text-gray-700 bg-gray-100 p-4 rounded border-l-4 border-gray-400 whitespace-pre-line">
                        {gallery.note}
                    </p>
                )}
            </header>

            <main>
                {unlabelledImages.length > 0 && (
                    <div>
                        <h3 className="text-xl font-bold text-gray-800 mb-4">{CONFIG.UNLABELLED_BLOCK_TITLE}</h3>
                        {renderImageGrid(unlabelledImages)}
                    </div>
                )}

                {labelledImages.length > 0 && (
                    <div>
                        {unlabelledImages.length > 0 && (
                            <h3 className="text-xl font-bold text-gray-800 mb-4">{CONFIG.LABELLED_BLOCK_TITLE}</h3>
                        )}
                        {renderImageGrid(labelledImages)}
                    </div>
                )}

                {gallery.subdirectories && gallery.subdirectories.length > 0 && (
                    <section className="mt-16 border-t border-gray-200 pt-8">
                        <h3 className="text-xl font-bold text-gray-800 mb-4">{CONFIG.SUBDIRECTORIES_TITLE}</h3>
                        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
                            {gallery.subdirectories.map((subdir, index) => (
                                <Link
                                    key={index}
                                    to={`/gallery/${galleryPath}/${subdir.directory}`}
                                    className="group bg-white rounded-lg overflow-hidden shadow-md hover:shadow-xl transition-shadow duration-300 flex flex-col"
                                >
                                    <div className="w-full aspect-[4/3] bg-gray-200 overflow-hidden">
                                        {subdir.preview_path ? (
                                            <img
                                                src={resolveDataPath(`${galleryPath}/${subdir.directory}/${subdir.preview_path}`)}
                                                alt={subdir.directory}
                                                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                                                loading="lazy"
                                            />
                                        ) : (
                                            <div className="w-full h-full flex items-center justify-center">
                                                <svg className="w-16 h-16 text-gray-400" fill="currentColor" viewBox="0 0 20 20">
                                                    <path d="M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z" />
                                                </svg>
                                            </div>
                                        )}
                                    </div>
                                    <div className="p-4">
                                        <span className="font-bold text-gray-800 group-hover:text-blue-600 transition-colors">
                                            {subdir.directory}
                                        </span>
                                    </div>
                                </Link>
                            ))}
                        </div>
                    </section>
                )}
            </main>
        </div>
    );
}