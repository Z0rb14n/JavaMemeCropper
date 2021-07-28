package meme;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;

/**
 * Cropper of memes.
 */
public class MemeCropper {
    private final MemeCropperSettings settings = new MemeCropperSettings();
    private final CropThread[] cropThreads = new CropThread[settings.numThreads];
    private String[] fileNamesToCrop;

    /**
     * Main method
     *
     * @param args unused command line arguments.
     */
    public static void main(String[] args) {
        long time = System.nanoTime();
        MemeCropper instance = new MemeCropper();
        assert (instance.settings.isValid());
        instance.runCropper();
        System.out.printf("Time: %f seconds\n", (System.nanoTime() - time) / 1000000000f);
    }

    /**
     * Thread that crops a series of images sequentially
     */
    private class CropThread extends Thread {
        private final int indexOffset;

        /**
         * Constructor for CropThread with index of images array, and index of thread array.
         *
         * @param index index of images array.
         */
        CropThread(int index) {
            super();
            this.indexOffset = index;
        }

        /**
         * Body of thread - crops/saves an image.
         */
        @Override
        public void run() {
            int numThreads = settings.numThreads;
            for (int index = indexOffset; index < fileNamesToCrop.length; index += numThreads) {
                if (settings.debug) System.out.printf("Running crop thread %d on index %d\n", indexOffset, index);
                int drawX = 0, drawY = 0, shrinkX = 0, shrinkY = 0;
                int height;
                int width;
                BufferedImage image;
                int[][] photoAsInt;
                try {
                    image = ImageIO.read(new File(settings.fileDirToCrop + fileNamesToCrop[index]));
                    photoAsInt = convertTo2D(image);
                    height = image.getHeight();
                    width = image.getWidth();
                } catch (Exception e) {
                    System.err.println("Error in getting file image information for index " + index + "; skipping");
                    e.printStackTrace(System.err);
                    continue;
                }
                TopOuter:
                // deals with top area
                for (int row = 0; row < height; row++) {
                    for (int column = 0; column < width; column += settings.speed) {
                        if (pixelDoesNotMatchCroppedColours(photoAsInt[row][column])) break TopOuter;
                    }
                    drawY++;
                }
                BottomOuter:
                // deals with bottom area
                for (int row = height - 1; row >= 0; row--) {
                    for (int column = 0; column < width; column += settings.speed) {
                        if (pixelDoesNotMatchCroppedColours(photoAsInt[row][column])) break BottomOuter;
                    }
                    shrinkY++;
                }
                LeftOuter:
                // Compare every row in all columns from 0 - half width
                for (int column = 0; column < width; column++) {
                    for (int row = 0; row < height; row += settings.speed) {
                        if (pixelDoesNotMatchCroppedColours(photoAsInt[row][column])) break LeftOuter;
                    }
                    drawX++;
                }
                RightOuter:
                // Compare every row in all columns from width to half width
                for (int column = width - 1; column >= 0; column--) {
                    for (int row = 0; row < height; row += settings.speed) {
                        if (pixelDoesNotMatchCroppedColours(photoAsInt[row][column])) break RightOuter;
                    }
                    shrinkX++;
                }
                if (width - shrinkX - drawX > 0 && height - shrinkY - drawY > 0) {
                    final BufferedImage savedPhoto = image.getSubimage(drawX, drawY, width - shrinkX - drawX, height - shrinkY - drawY);
                    save(savedPhoto, settings.fileDirCropped + fileNamesToCrop[index]);
                }
                if (settings.deleteOriginal) {
                    if (!new File(settings.fileDirToCrop + fileNamesToCrop[index]).delete()) {
                        System.err.println("Could not delete file " + settings.fileDirToCrop + fileNamesToCrop[index]);
                    }
                }
                if (settings.debug) System.out.printf("Crop thread %d finished index %d\n", indexOffset, index);
            }
        }
    }

    /**
     * Initializes the file names to crop
     */
    private MemeCropper() {
        fileNamesToCrop = settings.dirToCrop.list((dir, name) -> isValidImageExtension(name));
        if (fileNamesToCrop == null) fileNamesToCrop = new String[0];
    }

    /**
     * Runs the cropper.
     */
    private void runCropper() {
        for (int i = 0; i < settings.numThreads; i++) {
            cropThreads[i] = new CropThread(i);
            cropThreads[i].start();
        }
        for (int i = 0; i < settings.numThreads; i++) {
            try {
                if (cropThreads[i] != null) cropThreads[i].join();
            } catch (Exception e) {
                System.err.println("Uncaught exception when joining threads: ");
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * Determines if a file name has a valid file extension (i.e. one of GIF,PNG,JPG,JPEG,BMP).
     *
     * @param file file name of file.
     * @return whether it has a supported file extension (GIF,PNG,JPG,JPEG,BMP).
     */
    private static boolean isValidImageExtension(String file) {
        return file != null && (file.endsWith(".gif") || file.endsWith(".png") || file.endsWith(".jpg") || file.endsWith(".jpeg") || file.endsWith(".bmp"));
    }

    /**
     * Determines if the two colors in RGB format is similar (i.e. difference between RGB components < settings.sensitivity)
     *
     * @param rgbone RGB value of first color
     * @param rgbtwo RGB value of second color
     * @return whether they are similar enough.
     */
    private boolean colorSimilar(int rgbone, int rgbtwo) {
        boolean[] test = new boolean[4];
        test[0] = Math.abs((rgbone >> 24) - (rgbtwo >> 24)) < settings.sensitivity;
        test[1] = Math.abs(((rgbone >> 16) & 0xFF) - ((rgbtwo >> 16) & 0xFF)) < settings.sensitivity;
        test[2] = Math.abs(((rgbone >> 8) & 0xFF) - ((rgbtwo >> 8) & 0xFF)) < settings.sensitivity;
        test[3] = Math.abs((rgbone & 0xFF) - (rgbtwo & 0xFF)) < settings.sensitivity;
        return test[0] && test[1] && test[2] && test[3];
    }

    /**
     * Determines if the pixel matches any of the cropped colors.
     *
     * @param c RGB value of color
     * @return whether the pixel is similar enough to any one of the pre-determined colors.
     */
    private boolean pixelDoesNotMatchCroppedColours(int c) {
        for (int color : settings.croppedColorsInt) {
            if (colorSimilar(color, c)) return false;
        }
        return true;
    }

    /**
     * Converts a bufferedimage into int[][].
     * @param image image to convert into integer array.
     * @return integer array containing RGB values.
     */
    private static int[][] convertTo2D(BufferedImage image) {
        final byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        final int width = image.getWidth();
        final int height = image.getHeight();
        final boolean hasAlphaChannel = image.getAlphaRaster() != null;
        int[][] result = new int[height][width];
        if (hasAlphaChannel) {
            final int pixelLength = 4;
            for (int pixel = 0, row = 0, col = 0; pixel + 3 < pixels.length; pixel += pixelLength) {
                int argb = (((int) pixels[pixel] & 0xff) << 24) | ((int) pixels[pixel + 1] & 0xff) | (((int) pixels[pixel + 2] & 0xff) << 8) | (((int) pixels[pixel + 3] & 0xff) << 16);
                result[row][col] = argb;
                col++;
                if (col == width) {
                    col = 0;
                    row++;
                }
            }
        } else {
            final int pixelLength = 3;
            for (int pixel = 0, row = 0, col = 0; pixel + 2 < pixels.length; pixel += pixelLength) {
                int argb = 0xff000000 | ((int) pixels[pixel] & 0xff) | (((int) pixels[pixel + 1] & 0xff) << 8) | (((int) pixels[pixel + 2] & 0xff) << 16);
                result[row][col] = argb;
                col++;
                if (col == width) {
                    col = 0;
                    row++;
                }
            }
        }
        return result;
    }

    /**
     * Saves photo file to location
     *
     * @param photo  photo to save
     * @param string file path to save to
     */
    private void save(BufferedImage photo, String string) {
        try {
            if (string.endsWith(".gif")) ImageIO.write(photo, "gif", new File(string));
            else if (string.endsWith(".jpeg")) ImageIO.write(photo, "jpeg", new File(string));
            else if (string.endsWith(".jpg")) ImageIO.write(photo, "jpg", new File(string));
            else if (string.endsWith(".png")) ImageIO.write(photo, "png", new File(string));
            else if (string.endsWith(".bmp")) ImageIO.write(photo, "bmp", new File(string));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
