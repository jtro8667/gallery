// src/components/RootView.jsx

import React, { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { CONFIG, resolveDataPath } from '../config';
import TextWithEmails from './TextWithEmails';
import EmailDisplay from './EmailDisplay';

export default function RootView() {
    const [galleries, setGalleries] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const letterRefs = useRef({});

    useEffect(() => {
        document.title = CONFIG.PAGE_TITLE;
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

    const isDark = CONFIG.THEME === 'dark';
    const bgClass = isDark ? 'bg-gray-900 text-gray-100' : 'bg-white text-gray-900';

    // Helper to extract the starting letter respecting the Czech "CH" digraph
    const getCzechStartingLetter = (name) => {
        if (!name) return '';
        const upperName = name.trim().toUpperCase();
        if (upperName.startsWith('CH')) {
            return 'CH';
        }
        return upperName.charAt(0);
    };

    // Extract unique starting letters
    const rawLetters = [...new Set(galleries.map(g => getCzechStartingLetter(g.name)))].filter(Boolean);

    // Sort letters using the Czech locale rule (places CH correctly between H and I)
    const alphabet = rawLetters.sort((a, b) => a.localeCompare(b, 'cs', { sensitivity: 'base' }));

    // Map to find the first gallery index for each starting letter
    const letterToFirstIndexMap = {};
    galleries.forEach((gallery, index) => {
        const letter = getCzechStartingLetter(gallery.name);
        if (letter && letterToFirstIndexMap[letter] === undefined) {
            letterToFirstIndexMap[letter] = index;
        }
    });

    const scrollToLetter = (letter) => {
        const element = letterRefs.current[letter];
        if (element) {
            // Native scrollIntoView doesn't accept a custom pixel offset,
            // so we calculate the absolute position and subtract 20px for padding.
            const elementPosition = element.getBoundingClientRect().top + window.scrollY;
            const offsetPosition = elementPosition - 20;

            window.scrollTo({
                top: offsetPosition,
                behavior: 'smooth'
            });
        }
    };

    return (
        <div className={`max-w-7xl mx-auto px-4 py-8 ${bgClass}`}>
            <header className="mb-10 text-center">
                <h1 className={`text-4xl font-extrabold tracking-tight sm:text-5xl ${isDark ? 'text-gray-100' : 'text-gray-900'}`}>
                    {CONFIG.PAGE_TITLE}
                </h1>
                <div className={`mt-4 max-w-2xl mx-auto rounded-lg p-4 text-sm ${isDark ? 'bg-gray-800 border border-gray-700 text-gray-300' : 'bg-blue-50 border border-blue-200 text-gray-700'}`}>
                    <TextWithEmails text={CONFIG.ROOT_INTRO_TEXT} />
                </div>

                {/* Alphabet navigation menu */}
                {alphabet.length > 0 && (
                    <div className="mt-6 flex flex-wrap justify-center gap-2 max-w-2xl mx-auto">
                        {alphabet.map((letter) => (
                            <button
                                key={letter}
                                onClick={() => scrollToLetter(letter)}
                                className={`px-3 py-1 text-sm font-semibold rounded shadow-sm hover:scale-105 transition-transform duration-200 ${
                                    isDark
                                        ? 'bg-gray-800 hover:bg-gray-700 text-blue-400 border border-gray-700'
                                        : 'bg-blue-50 hover:bg-blue-100 text-blue-600 border border-blue-200'
                                }`}
                            >
                                {letter}
                            </button>
                        ))}
                    </div>
                )}
            </header>

            <main>
                <div
                    className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6"
                    style={window.innerWidth >= 1024 ? gridStyle : undefined}
                >
                    {galleries.map((gallery, index) => {
                        const letter = getCzechStartingLetter(gallery.name);
                        const isFirstOfLetter = letterToFirstIndexMap[letter] === index;

                        return (
                            <Link
                                key={index}
                                ref={el => { if (isFirstOfLetter) letterRefs.current[letter] = el; }}
                                to={`/gallery/${gallery.directory}`}
                                className={`group rounded-lg overflow-hidden shadow-md hover:shadow-xl transition-shadow duration-300 flex flex-col ${isDark ? 'bg-gray-800' : 'bg-white'}`}
                            >
                                <div className={`w-full aspect-[4/3] overflow-hidden ${isDark ? 'bg-gray-700' : 'bg-gray-200'}`}>
                                    <img
                                        src={resolveDataPath(gallery.preview_path)}
                                        alt={gallery.name}
                                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                                        loading="lazy"
                                    />
                                </div>

                                <div className="p-4 flex-grow flex flex-col justify-between">
                                    <h2 className={`font-bold text-lg group-hover:text-blue-600 transition-colors ${isDark ? 'text-gray-100' : 'text-gray-800'}`}>
                                        {gallery.name}
                                    </h2>
                                    {gallery.date && (
                                        <p className={`text-xs mt-1 ${isDark ? 'text-gray-400' : 'text-gray-500'}`}>{gallery.date}</p>
                                    )}
                                </div>
                            </Link>
                        );
                    })}
                </div>
            </main>

            <footer className={`mt-10 text-center text-sm ${isDark ? 'text-gray-400' : 'text-gray-600'}`}>
                <p>{CONFIG.FOOTER_COPYRIGHT}</p>
                <p><EmailDisplay email={CONFIG.FOOTER_EMAIL} /></p>
            </footer>
        </div>
    );
}
