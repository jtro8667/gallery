// src/components/EmailDisplay.jsx

import React from 'react';
import { CONFIG } from '../config';

export default function EmailDisplay({ email }) {
    if (!email) return null;

    const parts = email.split('@');
    if (parts.length !== 2) return <span>{email}</span>;

    const [localPart, domain] = parts;
    const isDark = CONFIG.THEME === 'dark';
    const fillColor = isDark ? '%23d1d5db' : '%231f2937';

    return (
        <span>
            {localPart}
            <img
                src={'data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' viewBox=\'0 0 24 24\' fill=\'' + fillColor + '\'%3E%3Cpath d=\'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm0-14c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm0 10c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4z\'/%3E%3C/svg%3E'}
                alt="@"
                className="inline-block w-4 h-4 mx-0.1"
                style={{ verticalAlign: 'text-bottom' }}
            />
            {domain}
        </span>
    );
}
