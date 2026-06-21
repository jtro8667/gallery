// src/components/RootView.jsx

import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { CONFIG, resolveDataPath } from '../config';

export default function RootView() {
    const [galleries, setGalleries] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        // Dynamically resolves path based on config, e.g., /data/root.json
        fetch(resolveDataPath('root.json'))
            .then((res) => {
                if (!res.ok) throw new Error('Failed to load root.json');
                return res.json();
            })
            .then((data) => {
                setGalleries(data);
                setLoading(false);
            })
            .catch((err) => {
                setError(err.message);
                setLoading(false);
            });
    }, []);

    if (loading) return <div className="text-center p-10 font-semibold">Loading galleries...</div>;
    if (error) return <div className="text-center p-10 text-red-600 font-semibold">Error: {error}</div>;

    const gridStyle = {
        gridTemplateColumns: `repeat(${CONFIG.ROOT_COLUMNS}, minmax(0, 1fr))`
    };

    return (
        <div className="max-w-7xl mx-auto px-4 py-8">
            <header className="mb-10 text-center">
                <h1 className="text-4xl font-extrabold tracking-tight text-gray-900 sm:text-5xl">
                    Foto Galerie
                </h1>
            </header>

            <main>
                <div
                    className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6"
                    style={window.innerWidth >= 1024 ? gridStyle : undefined}
                >
                    {galleries.map((gallery, index) => (
                        <Link
                            key={index}
                            to={`/gallery/${gallery.directory}`}
                            className="group bg-white rounded-lg overflow-hidden shadow-md hover:shadow-xl transition-shadow duration-300 flex flex-col"
                        >
                            <div className="w-full aspect-[4/3] bg-gray-200 overflow-hidden">
                                <img
                                    src={resolveDataPath(gallery.preview_path)}
                                    alt={gallery.name}
                                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                                    loading="lazy"
                                />
                            </div>

                            <div className="p-4 flex-grow flex flex-col justify-between">
                                <h2 className="font-bold text-lg text-gray-800 group-hover:text-blue-600 transition-colors">
                                    {gallery.name}
                                </h2>
                                {gallery.date && (
                                    <p className="text-xs text-gray-500 mt-1">{gallery.date}</p>
                                )}
                            </div>
                        </Link>
                    ))}
                </div>
            </main>
        </div>
    );
}
