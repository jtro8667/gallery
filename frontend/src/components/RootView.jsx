// src/components/RootView.jsx

import React, { useState, useEffect, useRef, useLayoutEffect } from 'react';
import { Link } from 'react-router-dom';
import { CONFIG, resolveDataPath } from '../config';
import TextWithEmails from './TextWithEmails';
import EmailDisplay from './EmailDisplay';

// Helper function to measure text width in pixels using a 2D Canvas
const measureTextWidth = (text, fontStyle) => {
    const canvas = measureTextWidth.canvas || (measureTextWidth.canvas = document.createElement('canvas'));
    const context = canvas.getContext('2d');
    context.font = fontStyle;
    return context.measureText(text).width;
};

// Word-boundary pixel-based truncation engine
const truncateDateToWidth = (dateStr, maxWidth, fontStyle) => {
    if (!dateStr) return '';

    // Check if the full text fits immediately
    if (measureTextWidth(dateStr, fontStyle) <= maxWidth) {
        return dateStr;
    }

    const suffix = " ...";
    const words = dateStr.split(/(\s+)/); // Keep spaces as tokens to protect word structures
    let currentText = '';
    let result = suffix;

    for (let i = 0; i < words.length; i++) {
        // Skip leading whitespace tokens
        if (i === 0 && words[i].trim() === '') continue;

        const nextChunk = currentText + words[i];
        // Clean up trailing spaces/slashes for testing the width accurately
        const cleanedChunk = nextChunk.replace(/[ /]+$/, '');
        const testText = cleanedChunk + suffix;

        if (measureTextWidth(testText, fontStyle) > maxWidth) {
            break;
        }

        currentText = nextChunk;
        result = cleanedChunk + suffix;
    }

    return result;
};

export default function RootView() {
    const [galleries, setGalleries] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [containerWidths, setContainerWidths] = useState({});

    const letterRefs = useRef({});
    const gridContainerRef = useRef(null);

    useEffect(() => {
        document.title = CONFIG.PAGE_TITLE;
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

    // Monitor container sizes whenever galleries load, windows resize, or layout recalculates
    useLayoutEffect(() => {
        if (loading || !gridContainerRef.current) return;

        const handleResize = () => {
            const gridItems = gridContainerRef.current.children;
            const widths = {};

            // Calculate available width inside card minus padding
            // Tailwinds px-4 padding = 16px left + 16px right = 32px
            for (let i = 0; i < gridItems.length; i++) {
                const card = gridItems[i];
                if (card) {
                    const cardWidth = card.getBoundingClientRect().width;
                    widths[i] = Math.max(0, cardWidth - 32);
                }
            }
            setContainerWidths(widths);
        };

        // Create ResizeObserver to keep widths perfectly in sync
        const resizeObserver = new ResizeObserver(handleResize);
        resizeObserver.observe(gridContainerRef.current);

        // Initial measurement
        handleResize();

        return () => resizeObserver.disconnect();
    }, [loading, galleries]);

    if (loading) return <div className="text-center p-10 font-semibold">Loading galleries...</div>;
    if (error) return <div className="text-center p-10 text-red-600 font-semibold">Error: {error}</div>;

    const gridStyle = {
        gridTemplateColumns: `repeat(${CONFIG.ROOT_COLUMNS}, minmax(0, 1fr))`
    };

    const isDark = CONFIG.THEME === 'dark';
    const bgClass = isDark ? 'bg-gray-900 text-gray-100' : 'bg-white text-gray-900';

    const getCzechStartingLetter = (name) => {
        if (!name) return '';
        const upperName = name.trim().toUpperCase();
        if (upperName.startsWith('CH')) return 'CH';
        return upperName.charAt(0);
    };

    const rawLetters = [...new Set(galleries.map(g => getCzechStartingLetter(g.name)))].filter(Boolean);
    const alphabet = rawLetters.sort((a, b) => a.localeCompare(b, 'cs', { sensitivity: 'base' }));

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
            const elementPosition = element.getBoundingClientRect().top + window.scrollY;
            const offsetPosition = elementPosition - 20;
            window.scrollTo({ top: offsetPosition, behavior: 'smooth' });
        }
    };

    // Target typography layout to measure exact font pixels (Tailwind text-xs = 12px)
    const dateFontStyle = '12px sans-serif';

    return (
        <div className={`max-w-7xl mx-auto px-4 py-8 ${bgClass}`}>
            <header className="mb-10 text-center">
                <h1 className={`text-4xl font-extrabold tracking-tight sm:text-5xl ${isDark ? 'text-gray-100' : 'text-gray-900'}`}>
                    {CONFIG.PAGE_TITLE}
                </h1>
                <div className={`mt-4 max-w-2xl mx-auto rounded-lg p-4 text-sm ${isDark ? 'bg-gray-800 border border-gray-700 text-gray-300' : 'bg-blue-50 border border-blue-200 text-gray-700'}`}>
                    <TextWithEmails text={CONFIG.ROOT_INTRO_TEXT} />
                </div>

                {alphabet.length > 0 && (
                    <div className="mt-6 flex flex-wrap justify-center gap-2 max-w-2xl mx-auto">
                        {alphabet.map((letter) => (
                            <button
                                key={letter}
                                onClick={() => scrollToLetter(letter)}
                                className={`px-3 py-1 text-sm font-semibold rounded shadow-sm hover:scale-105 transition-transform duration-200 ${
                                    isDark ? 'bg-gray-800 hover:bg-gray-700 text-blue-400 border border-gray-700' : 'bg-blue-50 hover:bg-blue-100 text-blue-600 border border-blue-200'
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
                    ref={gridContainerRef}
                    className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6"
                    style={window.innerWidth >= 1024 ? gridStyle : undefined}
                >
                    {galleries.map((gallery, index) => {
                        const letter = getCzechStartingLetter(gallery.name);
                        const isFirstOfLetter = letterToFirstIndexMap[letter] === index;

                        const availableWidth = containerWidths[index] || 200; // Fallback estimate before observer renders
                        const displayDate = truncateDateToWidth(gallery.date, availableWidth, dateFontStyle);

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
                                        <p
                                            title={gallery.date} // Full date visible natively on hover
                                            className={`text-xs mt-1 truncate-render ${isDark ? 'text-gray-400' : 'text-gray-500'}`}
                                        >
                                            {displayDate}
                                        </p>
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
