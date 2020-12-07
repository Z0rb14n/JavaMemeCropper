import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cropper of memes.
 */
public class MemeCropper {
    private final static boolean DEBUG = false;
    private final static String FILE_DIR_TO_CROP = "/Users/adminasaurus/Desktop/le memes/";
    private final static File DIR_TO_CROP = new File(FILE_DIR_TO_CROP);
    private final static String FILE_DIR_CROPPED = "/Users/adminasaurus/Desktop/le memes/cropped/";
    private final static boolean DELETE_ORIGINAL = true;
    private final static short SENSITIVITY = 10; // how sensitive detection is, higher value is lower
    private final static short SPEED = 4; // how many columns to skip: 1 to skip none, 2 to skip 1, etc.
    private final static short NUM_THREADS = 3;
    private final static Color[] CROPPED_COLOURS = {
            new Color(22, 28, 44, 255), // twitter dark screen
            new Color(33, 33, 33, 255), // some dark grey
            new Color(0, 0, 0, 255), //black
            new Color(18, 18, 18, 255), //reddit dark theme
            new Color(20, 20, 20, 255), // born pub
            new Color(54, 57, 66, 255), //discord grey
            new Color(19, 31, 45, 255), //some dark blue, twitter
            new Color(23, 40, 50, 255), //some dark blue, twitter
            new Color(255, 255, 255, 255) //white
    };
    private final static int[] CROPPED_COLOURS_INT = new int[CROPPED_COLOURS.length];

    static {
        for (int i = 0; i < CROPPED_COLOURS.length; i++) CROPPED_COLOURS_INT[i] = CROPPED_COLOURS[i].getRGB();
    }

    private CropThread[] cropThreads = new CropThread[NUM_THREADS];
    private String[] fileNamesToCrop;
    private BufferedImage[] photos;
    private SetupThread setup = new SetupThread();
    private final Lock finishLock = new ReentrantLock();
    private volatile int numFinished = NUM_THREADS;
    private volatile boolean[] threadsFinished = new boolean[NUM_THREADS];
    private final Condition finishCondition = finishLock.newCondition();
    private final Lock loadedLock = new ReentrantLock();
    private final Condition loadedCondition = loadedLock.newCondition();
    private volatile boolean[] isLoaded;

    /**
     * Main method
     *
     * @param args unused command line arguments.
     */
    public static void main(String[] args) {
        long time = System.nanoTime();
        assert (NUM_THREADS > 0);
        MemeCropper instance = new MemeCropper();
        instance.runCropper();
        System.out.printf("Time: %f seconds\n", (System.nanoTime() - time) / 1000000000f);
    }

    /**
     * Thread that stores all photos in a buffered image object.
     */
    private class SetupThread extends Thread {
        /**
         * Loads all images in the folder as a BufferedImage.
         */
        @Override
        public void run() {
            try {
                for (int i = 0; i < fileNamesToCrop.length; i++) {
                    if (DEBUG) System.out.printf("Setup on iteration %d\n", i);
                    photos[i] = ImageIO.read(new File(FILE_DIR_TO_CROP + fileNamesToCrop[i]));
                    isLoaded[i] = true;
                    loadedLock.lock();
                    loadedCondition.signal();
                    loadedLock.unlock();
                }
            } catch (Exception e) {
                System.err.println("Exception on setup thread: ");
                e.printStackTrace(System.err);
                System.exit(-1);
            }
        }
    }

    /**
     * Thread that crops a single image.
     */
    private class CropThread extends Thread {
        private int drawX = 0;
        private int drawY = 0;
        private int shrinkX = 0;
        private int shrinkY = 0;
        private int index;
        private int threadIndex;

        /**
         * Constructor for CropThread with index of images array, and index of thread array.
         *
         * @param index       index of images array.
         * @param threadIndex index of threads array.
         */
        CropThread(int index, int threadIndex) {
            super();
            this.index = index;
            this.threadIndex = threadIndex;
        }

        /**
         * Body of thread - crops/saves an image.
         */
        @Override
        public void run() {
            if (DEBUG) System.out.println("Acquiring loadedLock...");
            loadedLock.lock();
            if (DEBUG) System.out.println("Loaded lock acquired.");
            while (!isLoaded[index]) {
                try {
                    loadedCondition.await();
                } catch (InterruptedException e) {
                    System.err.println("Interrupted on IsLoaded await.");
                }
            }
            loadedLock.unlock();
            if (DEBUG) System.out.printf("Running crop thread on index %d\n", index);
            final int height = photos[index].getHeight();
            final int width = photos[index].getWidth();
            final int[][] photoAsInt = convertTo2D(photos[index]);
            TopOuter:
            // deals with top area
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column += SPEED) {
                    if (pixelDoesNotMatchCroppedColours(photoAsInt[row][column])) break TopOuter;
                }
                this.drawY++;
            }
            BottomOuter:
            // deals with bottom area
            for (int row = height - 1; row >= 0; row--) {
                for (int column = 0; column < width; column += SPEED) {
                    if (pixelDoesNotMatchCroppedColours(photoAsInt[row][column])) break BottomOuter;
                }
                this.shrinkY++;
            }
            LeftOuter:
            // Compare every row in all columns from 0 - half width
            for (int column = 0; column < width; column++) {
                for (int row = 0; row < height; row += SPEED) {
                    if (pixelDoesNotMatchCroppedColours(photoAsInt[row][column])) break LeftOuter;
                }
                this.drawX++;
            }
            RightOuter:
            // Compare every row in all columns from width to half width
            for (int column = width - 1; column >= 0; column--) {
                for (int row = 0; row < height; row += SPEED) {
                    if (pixelDoesNotMatchCroppedColours(photoAsInt[row][column])) break RightOuter;
                }
                this.shrinkX++;
            }
            if (width - shrinkX - drawX > 0 && height - shrinkY - drawY > 0) {
                final BufferedImage savedPhoto = photos[index].getSubimage(drawX, drawY, width - shrinkX - drawX, height - shrinkY - drawY);
                save(savedPhoto, FILE_DIR_CROPPED + fileNamesToCrop[index]);
            }
            photos[index] = null;
            if (DELETE_ORIGINAL) {
                if (!new File(FILE_DIR_TO_CROP + fileNamesToCrop[index]).delete()) {
                    System.err.println("Could not delete file " + FILE_DIR_TO_CROP + fileNamesToCrop[index]);
                }
            }
            finishLock.lock();
            threadsFinished[threadIndex] = true;
            numFinished++;
            finishCondition.signalAll();
            finishLock.unlock();
        }
    }

    /**
     * Initializes all fields to be empty.
     */
    private MemeCropper() {
        fileNamesToCrop = DIR_TO_CROP.list((dir, name) -> isValidImageExtension(name));
        if (fileNamesToCrop == null) fileNamesToCrop = new String[0];
        photos = new BufferedImage[fileNamesToCrop.length];
        isLoaded = new boolean[fileNamesToCrop.length];
        for (int i = 0; i < threadsFinished.length; i++) {
            threadsFinished[i] = true;
        }
    }

    /**
     * Runs the cropper.
     */
    private void runCropper() {
        setup.start();
        for (int currIndex = 0; currIndex < photos.length; currIndex++) {
            if (DEBUG) System.out.printf("RunCropper on iteration %d\n", currIndex);
            assert (numFinished > 0);
            finishLock.lock();
            numFinished--;
            boolean didBreak = false;
            for (int i = 0; i < NUM_THREADS; i++) {
                if (threadsFinished[i]) {
                    cropThreads[i] = new CropThread(currIndex, i);
                    threadsFinished[i] = false;
                    cropThreads[i].start();
                    didBreak = true;
                    break;
                }
            }
            assert (didBreak);
            finishLock.unlock();
            try {
                finishLock.lock();
                while (numFinished <= 0) finishCondition.await();
                finishLock.unlock();
                for (int i = 0; i < NUM_THREADS; i++) {
                    if (threadsFinished[i] && cropThreads[i] != null) cropThreads[i].join();
                }
            } catch (InterruptedException ignored) {
                System.err.println("Finish condition await interruption ignored.");
            }
        }
        try {
            setup.join();
        } catch (Exception e) {
            System.err.println("Uncaught exception when joining setup thread: ");
            e.printStackTrace(System.err);
        }
        for (int i = 0; i < NUM_THREADS; i++) {
            try {
                if (!threadsFinished[i] && cropThreads[i] != null) cropThreads[i].join();
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
        if (file == null) return false;
        if (file.endsWith(".gif")) return true;
        if (file.endsWith(".png")) return true;
        if (file.endsWith(".jpg")) return true;
        if (file.endsWith(".jpeg")) return true;
        return file.endsWith(".bmp");
    }

    /**
     * Determines if the two colors in RGB format is similar (i.e. difference between RGB components < SENSITIVITY)
     *
     * @param rgbone RGB value of first color
     * @param rgbtwo RGB value of second color
     * @return whether they are similar enough.
     */
    private static boolean colorSimilar(int rgbone, int rgbtwo) {
        boolean[] test = new boolean[4];
        test[0] = Math.abs((rgbone >> 24) - (rgbtwo >> 24)) < SENSITIVITY;
        test[1] = Math.abs(((rgbone >> 16) & 0xFF) - ((rgbtwo >> 16) & 0xFF)) < SENSITIVITY;
        test[2] = Math.abs(((rgbone >> 8) & 0xFF) - ((rgbtwo >> 8) & 0xFF)) < SENSITIVITY;
        test[3] = Math.abs((rgbone & 0xFF) - (rgbtwo & 0xFF)) < SENSITIVITY;
        return test[0] && test[1] && test[2] && test[3];
    }

    /**
     * Determines if the pixel matches any of the cropped colors.
     *
     * @param c RGB value of color
     * @return whether the pixel is similar enough to any one of the pre-determined colors.
     */
    private static boolean pixelDoesNotMatchCroppedColours(int c) {
        for (int color : CROPPED_COLOURS_INT) {
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
