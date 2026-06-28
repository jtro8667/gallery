// src/components/GalleryOrPhotoView.jsx

import React from 'react';
import { useParams } from 'react-router-dom';
import GalleryView from './GalleryView';
import PhotoView from './PhotoView';

export default function GalleryOrPhotoView() {
    const params = useParams();
    const wildcardPath = params['*'] || '';

    // Check if the path ends with .htm to determine if it's a photo view
    const isPhotoView = wildcardPath.endsWith('.htm');

    if (isPhotoView) {
        // Extract gallery path and image name from the wildcard path
        // Path format: galleryPath/imageName.htm
        const lastSlashIndex = wildcardPath.lastIndexOf('/');
        const galleryPath = wildcardPath.substring(0, lastSlashIndex);
        const imageNameWithExt = wildcardPath.substring(lastSlashIndex + 1);
        const imageName = imageNameWithExt.replace('.htm', '');

        return <PhotoView galleryPath={galleryPath} imageName={imageName} />;
    }

    return <GalleryView />;
}
