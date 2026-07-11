// src/components/TextWithEmails.jsx

import React from 'react';
import EmailDisplay from './EmailDisplay';

const EMAIL_REGEX = /([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/g;

export default function TextWithEmails({ text }) {
    if (!text) return null;

    const parts = [];
    let lastIndex = 0;
    let match;

    while ((match = EMAIL_REGEX.exec(text)) !== null) {
        // Add text before the email
        if (match.index > lastIndex) {
            parts.push(text.slice(lastIndex, match.index));
        }

        // Add the email with @ as image
        const fullEmail = match[0];
        parts.push(<EmailDisplay key={`email-${match.index}`} email={fullEmail} />);

        lastIndex = match.index + match[0].length;
    }

    // Add remaining text after the last email
    if (lastIndex < text.length) {
        parts.push(text.slice(lastIndex));
    }

    return <span>{parts.length > 0 ? parts : text}</span>;
}
