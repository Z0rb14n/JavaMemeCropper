/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package memecropper;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.List;
import java.util.Arrays;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.imageio.ImageIO;

/**
 *
 * @author adminasaurus
 */
public class MemeCropper {
    final static String FILE_DIR_TO_CROP = "/Users/adminasaurus/Desktop/le memes/";
    final static String FILE_DIR_CROPPED = "/Users/adminasaurus/Desktop/le memes/cropped/";
    final static boolean DELETE_ORIGINAL = true;
    final static short SENSITIVITY = 10; // how sensitive B&W detection is, higher is lower
    final static short SPEED = 4; // how many columns to skip: 1 to skip none, 2 to skip 1, etc.
    final static Color[] CROPPED_COLOURS = {
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
    String[] fileNamesToCrop;
    BufferedImage[] photos;
    volatile short highestLoadedImageIndex = -1;
    Thread setupThread = null;

    MemeCropper() {
        final File file = new File(FILE_DIR_TO_CROP);
        List<String> tempList = new ArrayList<>(Arrays.asList(file.list()));
        tempList.removeIf((String t) -> isInvalidImageExtension(t));
        fileNamesToCrop = tempList.toArray(new String[tempList.size()]);
        photos = new BufferedImage[fileNamesToCrop.length];
        System.gc();
        setupThread = new Thread() {
            @Override
            public void run() {
                try {
                    while (highestLoadedImageIndex + 1 < fileNamesToCrop.length) {
                        photos[highestLoadedImageIndex + 1] = ImageIO.read(new File(FILE_DIR_TO_CROP + fileNamesToCrop[highestLoadedImageIndex + 1]));
                        highestLoadedImageIndex++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        setupThread.start();
    }

    public static boolean isInvalidImageExtension(String file) {
        if (file == null) return true;
        if (file.endsWith(".gif")) return false;
        if (file.endsWith(".png")) return false;
        if (file.endsWith(".jpg")) return false;
        if (file.endsWith(".jpeg")) return false;
        return !file.endsWith(".bmp");
    }
    
    public static boolean compare(Color c, int b) {
        int temp = c.getRGB();
        boolean[] test = new boolean[4];
        test[0] = Math.abs((temp >> 24) - (b >> 24)) < SENSITIVITY;
        test[1] = Math.abs(((temp >> 16) & 0xFF) - ((b >> 16) & 0xFF)) < SENSITIVITY;
        test[2] = Math.abs(((temp >> 8) & 0xFF) - ((b >> 8) & 0xFF)) < SENSITIVITY;
        test[3] = Math.abs((temp & 0xFF) - (b & 0xFF)) < SENSITIVITY;
        return test[0] && test[1] && test[2] && test[3];
    }
    
    public static boolean pixelMatchesCroppedColours(int c) {
        for (int i = 0; i < CROPPED_COLOURS.length; i++) {
            if (compare(CROPPED_COLOURS[i],c)) return true;
        }
        return false;
    }

    public static int[][] convertTo2D(BufferedImage image) {
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
     * @param photo photo to save
     * @param string file path to save to
     */
    public void save(BufferedImage photo, String string) {
        try {
            if (string.endsWith(".gif")) {
                ImageIO.write(photo, "gif", new File(string));
            } else if (string.endsWith(".jpeg")) {
                ImageIO.write(photo, "jpeg", new File(string));
            } else if (string.endsWith(".jpg")) {
                ImageIO.write(photo, "jpg", new File(string));
            } else if (string.endsWith(".png")) {
                ImageIO.write(photo, "png", new File(string));
            } else if (string.endsWith(".bmp")) {
                ImageIO.write(photo, "bmp", new File(string));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        MemeCropper instance = new MemeCropper();
        instance.run();
    }
    volatile short drawX = 0;
    volatile short drawY = 0;
    volatile short shrinkX = 0;
    volatile short shrinkY = 0;
    
    volatile BufferedImage photo;
    volatile int[][] photoValues;

    /**
     * Like the draw() loop but better
     */
    void run() {
        for (int imgIndex = 0; imgIndex < fileNamesToCrop.length; imgIndex++) {
            drawX = 0;
            drawY = 0;
            shrinkX = 0;
            shrinkY = 0;
            // prevent range out of bounds errors
            while (imgIndex > highestLoadedImageIndex) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
            photo = photos[imgIndex];
            photoValues = convertTo2D(photo);
            CheckTB checkTB = new CheckTB();
            checkTB.start();
            CheckLR checkLR = new CheckLR();
            checkLR.start();
            try {
                checkTB.join();
                checkLR.join();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            final BufferedImage savedPhoto = photos[imgIndex].getSubimage(drawX, drawY, photos[imgIndex].getWidth() - shrinkX - drawX, photos[imgIndex].getHeight() - shrinkY - drawY);
            save(savedPhoto, FILE_DIR_CROPPED + fileNamesToCrop[imgIndex]);
            if (DELETE_ORIGINAL) {
                if (new File(FILE_DIR_TO_CROP + fileNamesToCrop[imgIndex]).delete()) {
                } else {
                    System.err.println("Could not delete file " + FILE_DIR_TO_CROP + fileNamesToCrop[imgIndex]);
                }
            }
        }
    }

    private class CheckTB extends Thread {

        @Override
        public void run() {
            OuterLoop:
            // For each row from 0 - height/2, check if all columns have matching colours
            for (short row = 0; row < Math.floor(photo.getHeight() / 2.0); row++) {
                for (short column = 0; column < photo.getWidth(); column += SPEED) {
                    boolean colorMatches = pixelMatchesCroppedColours(photoValues[row][column]);
                    if (!colorMatches) {
                        break OuterLoop;
                    }
                }
                drawY++;
            }
            SecondOuterLoop:
            // For each row from height - height/2, check if all columns have matching colours
            for (short row = (short) (photo.getHeight() - 1); row > Math.floor(photo.getHeight() / 2.0); row--) {
                for (short column = 0; column < photo.getWidth(); column += SPEED) {
                    boolean colorMatches = pixelMatchesCroppedColours(photoValues[row][column]);
                    if (!colorMatches) {
                        break SecondOuterLoop;
                    }
                }
                shrinkY++;
            }
        }
    }

    private class CheckLR extends Thread {

        @Override
        public void run() {
            OuterLoop:
            // Compare every row in all columns from 0 - half width
            for (short column = 0; column < Math.floor(photo.getWidth() / 2.0); column++) {
                for (short row = 0; row < photo.getHeight(); row += SPEED) {
                    boolean colorMatches = pixelMatchesCroppedColours(photoValues[row][column]);
                    if (!colorMatches) {
                        break OuterLoop;
                    }
                }
                drawX++;
            }
            SecondOuterLoop:
            // Compare every row in all columns from width to half width
            for (short column = (short) (photo.getWidth() - 1); column > Math.floor(photo.getWidth() / 2.0); column--) {
                for (short row = 0; row < photo.getHeight(); row += SPEED) {
                    boolean colorMatches = pixelMatchesCroppedColours(photoValues[row][column]);
                    if (!colorMatches) {
                        break SecondOuterLoop;
                    }
                }
                shrinkX++;
            }
        }
    }
}
