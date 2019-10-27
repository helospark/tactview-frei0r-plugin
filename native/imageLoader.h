#ifndef IMAGE_LOADER_H
#define IMAGE_LOADER_H

#ifdef DEBUG

    struct Image {
        int width, height;
        char* data;

        Image(int width2, int height2, char* data2) : width(width2), height(height2), data(data2) {}

        ~Image() {
        // delete[] data;
        }
    };

    Image* loadImage(const char* imageFileName);

    void writeImage(const char* imageFileName, Image* image);

#endif

#endif