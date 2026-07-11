// src/components/GalleryView.jsx

import React, { useState, useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { CONFIG, resolveDataPath } from '../config';
import EmailDisplay from './EmailDisplay';

export default function GalleryView() {
    const params = useParams();
    const galleryPath = params['*'] || '';

    const [gallery, setGallery] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        fetch(resolveDataPath(galleryPath + '/index.json'))
            .then((res) => {
                if (!res.ok) throw new Error('Failed to load index.json for ' + galleryPath);
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

    const isDark = CONFIG.THEME === 'dark';
    const bgClass = isDark ? 'bg-gray-900 text-gray-100' : 'bg-white text-gray-900';

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
        gridTemplateColumns: 'repeat(' + CONFIG.GALLERY_COLUMNS + ', minmax(0, 1fr))'
    };

    const renderImageGrid = (items) => (
        <div
            className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6 mb-12"
            style={window.innerWidth >= 1024 ? galleryGridStyle : undefined}
        >
            {items.map((img, idx) => (
                <Link
                    key={idx}
                    to={'/gallery/' + galleryPath + '/' + img.image + '.htm'}
                    className={'rounded shadow hover:shadow-md transition-shadow duration-200 flex flex-col ' + (isDark ? 'bg-gray-800' : 'bg-white')}
                >
                <div className={'w-full aspect-[4/3] overflow-hidden flex items-center justify-center ' + (isDark ? 'bg-gray-700' : 'bg-gray-100')}>
                        <img
                            src={resolveDataPath(galleryPath + '/' + img.preview)}
                            alt={img.description || img.image}
                            className="w-full h-full object-contain"
                            loading="lazy"
                        />
                    </div>

                    <div className="p-3 flex-grow min-h-[4rem]">
                        {img.description && (
                            <p className={'text-sm line-clamp-3 ' + (isDark ? 'text-gray-300' : 'text-gray-600')}>{img.description}</p>
                        )}
                    </div>
                </Link>
            ))}
        </div>
    );

    return (
        <div className={'max-w-7xl mx-auto px-4 py-8 ' + bgClass}>
            <Link to="/" className={'hover:underline inline-block mb-6 font-medium ' + (isDark ? 'text-blue-400' : 'text-blue-600')}>
                {CONFIG.BACK_TO_ROOT}
            </Link>

            <header className={'mb-10 border-b pb-6 ' + (isDark ? 'border-gray-700' : 'border-gray-200')}>
                <div className="flex flex-wrap items-baseline gap-4">
                    <h1 className={'text-3xl font-extrabold ' + (isDark ? 'text-gray-100' : 'text-gray-900')}>{gallery.name}</h1>
                    {gallery.date && <span className={'text-lg ' + (isDark ? 'text-gray-400' : 'text-gray-500')}>({gallery.date})</span>}
                </div>
                {gallery.event && (
                    <h2 className={'text-xl mt-2 italic ' + (isDark ? 'text-gray-400' : 'text-gray-600')}>{CONFIG.EVENT_LABEL}: {gallery.event}</h2>
                )}
                {gallery.note && (
                    <p className={'mt-4 p-4 rounded border-l-4 whitespace-pre-line ' + (isDark ? 'text-gray-300 bg-gray-800 border-gray-600' : 'text-gray-700 bg-gray-100 border-gray-400')}>
                        {gallery.note}
                    </p>
                )}
            </header>

            <main>
                {unlabelledImages.length > 0 && (
                    <div>
                        <h3 className={'text-xl font-bold mb-4 ' + (isDark ? 'text-gray-100' : 'text-gray-800')}>{CONFIG.UNLABELLED_BLOCK_TITLE}</h3>
                        {renderImageGrid(unlabelledImages)}
                    </div>
                )}

                {labelledImages.length > 0 && (
                    <div>
                        {unlabelledImages.length > 0 && (
                            <h3 className={'text-xl font-bold mb-4 ' + (isDark ? 'text-gray-100' : 'text-gray-800')}>{CONFIG.LABELLED_BLOCK_TITLE}</h3>
                        )}
                        {renderImageGrid(labelledImages)}
                    </div>
                )}

                {gallery.subdirectories && gallery.subdirectories.length > 0 && (
                    <section className={'mt-16 border-t pt-8 ' + (isDark ? 'border-gray-700' : 'border-gray-200')}>
                        <h3 className={'text-xl font-bold mb-4 ' + (isDark ? 'text-gray-100' : 'text-gray-800')}>{CONFIG.SUBDIRECTORIES_TITLE}</h3>
                        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
                            {gallery.subdirectories.map((subdir, index) => (
                                <Link
                                    key={index}
                                    to={'/gallery/' + galleryPath + '/' + subdir.directory}
                                    className={'group rounded-lg overflow-hidden shadow-md hover:shadow-xl transition-shadow duration-300 flex flex-col ' + (isDark ? 'bg-gray-800' : 'bg-white')}
                                >
                                    <div className={'w-full aspect-[4/3] overflow-hidden ' + (isDark ? 'bg-gray-700' : 'bg-gray-200')}>
                                        {subdir.preview_path ? (
                                            <img
                                                src={resolveDataPath(galleryPath + '/' + subdir.directory + '/' + subdir.preview_path)}
                                                alt={subdir.directory}
                                                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                                                loading="lazy"
                                            />
                                        ) : (
                                            <div className="w-full h-full flex items-center justify-center">
                                                <svg className={'w-16 h-16 ' + (isDark ? 'text-gray-500' : 'text-gray-400')} fill="currentColor" viewBox="0 0 20 20">
                                                    <path d="M2 6a2 2 0 012-2h5l2 2h5a2 2 0 012 2v6a2 2 0 01-2 2H4a2 2 0 01-2-2V6z" />
                                                </svg>
                                            </div>
                                        )}
                                    </div>
                                    <div className="p-4">
                                        <span className={'font-bold group-hover:text-blue-600 transition-colors ' + (isDark ? 'text-gray-100' : 'text-gray-800')}>
                                            {subdir.directory}
                                        </span>
                                    </div>
                                </Link>
                            ))}
                        </div>
                    </section>
                )}
            </main>

            <footer className={'mt-10 text-center text-sm ' + (isDark ? 'text-gray-400' : 'text-gray-600')}>
                <p>{CONFIG.FOOTER_COPYRIGHT}</p>
                <p><EmailDisplay email={CONFIG.FOOTER_EMAIL} /></p>
            </footer>
        </div>
    );
}
