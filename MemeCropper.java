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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 *
 * @author adminasaurus
 */
public class MemeCropper {
    final String FILE_DIR_TO_CROP = "/Users/adminasaurus/Desktop/le memes/";
    final String FILE_DIR_CROPPED = "/Users/adminasaurus/Desktop/le memes/cropped/";
    
    String[] TO_CROP;
    final short SENSITIVITY = 10; // how sensitive B&W detection is, higher is lower
    final short SPEED = 4; // how many columns to skip: 1 to skip none, 2 to skip 1, etc.
    BufferedImage[] Photos;
    short LOADED = -1;
    Thread setupThread = null;
    Color[] CROPPED_COLOURS= {
        new Color(22,28,44,255), // twitter dark screen
        new Color(33,33,33,255), // some dark grey
        new Color(0,0,0,255), //black
        new Color(18,18,18,255), //reddit dark theme
        new Color(20,20,20,255), // born pub
        new Color(54,57,66,255), //discord grey
        new Color(19,31,45,255), //some dark blue, twitter
        new Color(23,40,50,255), //some dark blue, twitter
        new Color(255,255,255,255) //white
    };
    MemeCropper(){
        File file = new File(FILE_DIR_TO_CROP);
        List<String> tempList = Arrays.asList(file.list());
        ArrayList<String> contents = new ArrayList<>(tempList);
        for (int i = 0; i < contents.size(); i++) {
            String temp = contents.get(i);
            if (temp == null || !(temp.endsWith(".gif")||temp.endsWith(".png")||temp.endsWith(".jpg")||temp.endsWith(".jpeg")||temp.endsWith(".bmp"))) {
                contents.remove(i);
                i--;
            }
        }
        TO_CROP = contents.toArray(new String[0]);
        Photos = new BufferedImage[TO_CROP.length];
        System.gc();
        setupThread = new Thread() {
            @Override
            public void run() {
                try {
                    while (LOADED + 1 < TO_CROP.length) {
                        Photos[LOADED+1] = ImageIO.read(new File(FILE_DIR_TO_CROP + TO_CROP[LOADED+1]));
                        LOADED++;
                    }
                } catch (IOException e){
                    e.printStackTrace();
                }
            }  
        };
        setupThread.start();
    }
    boolean compare(Color c, int b){
        int temp = c.getRGB();
        boolean[] test = new boolean[4];
        test[0] = Math.abs((temp >> 24) - (b >> 24)) < SENSITIVITY;
        test[1] = Math.abs(((temp >> 16) & 0xFF) - ((b >> 16) & 0xFF)) < SENSITIVITY;
        test[2] = Math.abs(((temp >> 8) & 0xFF) - ((b >> 8) & 0xFF)) < SENSITIVITY;
        test[3] = Math.abs((temp & 0xFF) - (b & 0xFF)) < SENSITIVITY;
        return test[0] && test[1] && test[2] && test[3];
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
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        MemeCropper instance = new MemeCropper();
        instance.run();
    }
    boolean fn1Done = false; 
    boolean fn2Done = false; 
    final boolean deleteOriginal = true;
    short drawX = 0; 
    short drawY = 0; 
    short shrinkX = 0; 
    short shrinkY = 0;
    short imgIndex = 0;
    /**
     * Like the draw() loop but better
     */
    void run(){
        while (imgIndex < TO_CROP.length) {
            drawX = 0;
            drawY = 0;
            shrinkX = 0;
            shrinkY = 0;
            fn1Done = false;
            fn2Done = false;
            // prevent range out of bounds errors
            while (imgIndex > LOADED) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Logger.getLogger(MemeCropper.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            Thread checkTB = new Thread(){
                @Override
                public void run(){
                    BufferedImage photo = Photos[imgIndex];
                    //for each row, compare each column
                    boolean broken = false;
                    int[][] lol = convertTo2D(photo);
                    for (short row = 0; row < Math.floor(photo.getHeight()/2.0); row++) {
                        //for each column
                        for (short column = 0; column < photo.getWidth(); column += SPEED) {
                            //for each color
                            boolean COLOR_MATCH = false;
                            int pixel = lol[row][column];
                            for (short choice = 0; choice < CROPPED_COLOURS.length; choice++) {
                                if (compare(CROPPED_COLOURS[choice],pixel)) {
                                  COLOR_MATCH = true;
                                  break;
                                }
                            }
                            if (!COLOR_MATCH) {
                                broken = true;
                                break;
                            }
                        }
                        if (!broken) drawY++;
                        else break;
                    }
                    //do thing in reverse
                    broken = false;
                    for (short row = (short) (photo.getHeight() - 1); row > Math.floor(photo.getHeight()/2.0); row--) {
                        //for each column
                        for (short column = 0; column < photo.getWidth(); column += SPEED) {
                            boolean overCount= false;
                            if (column >= photo.getWidth()) {
                              column = (short) (photo.getWidth() - 1);
                              overCount = true;
                            }
                            //for each color
                            boolean COLOR_MATCH = false;
                            int pixel = lol[row][column];
                            for (short choice = 0; choice < CROPPED_COLOURS.length; choice++) {
                                if (compare(CROPPED_COLOURS[choice],pixel)) {
                                  COLOR_MATCH = true;
                                  break;
                                }
                            }
                            if (!COLOR_MATCH) {
                              broken = true;
                              break;
                            }
                            if (overCount) break;
                        }
                        if (!broken) shrinkY++;
                        else break;
                    }
                    fn1Done = true;
                }
            };
            checkTB.start();
            Thread checkLR = new Thread(){
                @Override
                public void run(){
                    BufferedImage photo = Photos[imgIndex];
                    int[][] lol = convertTo2D(photo);
                    //for each row, compare each column
                    boolean broken = false;
                    for (short column = 0; column < Math.floor(photo.getWidth()/2.0); column++) {
                        //for each column
                        for (short row = 0; row < photo.getHeight(); row += SPEED) {
                            //for each color
                            boolean COLOR_MATCH = false;
                            int pixel = lol[row][column];
                            for (short choice = 0; choice < CROPPED_COLOURS.length; choice++) {
                                if (compare(CROPPED_COLOURS[choice],pixel)) {
                                  COLOR_MATCH = true;
                                  break;
                                }
                            }
                            if (!COLOR_MATCH) {
                                broken = true;
                                break;
                            }
                        }
                        if (!broken) drawX++;
                        else break;
                    }
                    broken = false;
                    for (short column = (short) (photo.getWidth()-1); column > Math.floor(photo.getWidth()/2.0); column--) {
                        //for each column
                        for (short row = 0; row < photo.getHeight(); row += SPEED) {
                            //for each color
                            boolean COLOR_MATCH = false;
                            int pixel = lol[row][column];
                            for (short choice = 0; choice < CROPPED_COLOURS.length; choice++) {
                                if (compare(CROPPED_COLOURS[choice],pixel)) {
                                    COLOR_MATCH = true;
                                    break;
                                }
                            }
                            if (!COLOR_MATCH) {
                                broken = true;
                                break;
                            }
                        }
                        if (!broken) shrinkX++;
                        else break;
                    }
                    fn2Done = true;
                }
            };
            checkLR.start();
            while (!(fn1Done && fn2Done)) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Logger.getLogger(MemeCropper.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            BufferedImage photo = Photos[imgIndex].getSubimage(drawX, drawY, Photos[imgIndex].getWidth() - shrinkX-drawX, Photos[imgIndex].getHeight() - shrinkY-drawY);
            save(photo, FILE_DIR_CROPPED + TO_CROP[imgIndex]);
            if (deleteOriginal) {
                if (new File(FILE_DIR_TO_CROP + TO_CROP[imgIndex]).delete()) {
                } else {
                    System.out.println("Could not delete file " + FILE_DIR_TO_CROP + TO_CROP[imgIndex]);
                }
            }
            imgIndex++;
        }
        System.exit(0);
    }
    /**
     * Saves photo file to location
     * @param photo photo to save
     * @param string file path to save to
     */
    public void save(BufferedImage photo, String string) {
        try{
            if (string.endsWith(".gif")) ImageIO.write(photo,"gif",new File(string));
            else if (string.endsWith(".jpeg")) ImageIO.write(photo,"jpeg",new File(string));
            else if (string.endsWith(".jpg")) ImageIO.write(photo,"jpg",new File(string));
            else if (string.endsWith(".png")) ImageIO.write(photo,"png",new File(string));
            else if (string.endsWith(".bmp")) ImageIO.write(photo,"bmp",new File(string));
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}
