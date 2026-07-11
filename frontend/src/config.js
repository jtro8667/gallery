// src/config.js

export const CONFIG = {
    // Base URL or path where all gallery data (root.json, images, sub-directories) is located.
    // Leave empty "" or "/" if data is directly in the public root.
    // Example: "/data" or "https://my-cdn.com"
    //DATA_BASE_URL: "http://localhost:8080",
    DATA_BASE_URL: "https://czech-castles-gallery.netlify.app",

    // Number of grid columns on desktop viewports (min-width: 1024px)
    ROOT_COLUMNS: 5,
    GALLERY_COLUMNS: 5,

    // Text headings and labels
    UNLABELLED_BLOCK_TITLE: "Plánky a přehledy",
    LABELLED_BLOCK_TITLE: "Fotografie",
    SUBDIRECTORIES_TITLE: "Galerie",
    EVENT_LABEL: "Akce",
    PAGE_TITLE: "Galerie českých hradů",
    ROOT_INTRO_TEXT: "Galerie zatím není kompletní - stále ve výstavbě. Fotografie jsou zmenšeniny, v případě zájmu o nezmenšenou verzi bez vodoznaku mne kontaktujte emailem: josef.troch@email.cz",

    // Navigation buttons
    BACK_TO_ROOT: "← Zpět na přehled galerií",
    BACK_TO_GALLERY: "← Zpět do galerie",
    PREVIOUS_BUTTON: "Předchozí",
    NEXT_BUTTON: "Následující",

    // Footer
    FOOTER_COPYRIGHT: "© Josef Troch",
    FOOTER_EMAIL: "josef.troch@email.cz",

    // Theme: 'white' or 'dark'
    THEME: 'dark'
};

/**
 * Helper function to safely join the base URL with relative data paths.
 * Prevents double slashes and ensures clean asset resolution.
 */
export function resolveDataPath(relativePath) {
    const base = CONFIG.DATA_BASE_URL.replace(/\/+$/, ""); // Remove trailing slashes
    const cleanRelative = relativePath.replace(/^\/+/, ""); // Remove leading slashes
    return base ? `${base}/${cleanRelative}` : `/${cleanRelative}`;
}
