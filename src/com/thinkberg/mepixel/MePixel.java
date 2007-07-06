/*
 * Copyright (c) 2007, Matthias L. Jugel. All Rights Reserved.
 * See http://thinkberg.com/ for details and instructions.
 */

package com.thinkberg.mepixel;

import quicktime.QTException;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * MePixel -- a fun application for your webcam. It can display the image using zeros and ones
 * or just as rectangles (filled or empty) depending on the brightness of a certain area on
 * screen.
 *
 * @author Matthias L. Jugel
 */
public class MePixel {
  private static int delay = 20;
  private static boolean fillSwap = true;
  private static boolean charSwap = false;
  private static double brightnessThreshold = 0.50;


  private static void showUsage() {
    System.out.println("MePixel (c) 2007 Matthias L. Jugel. All Rights Reserved.");
    System.out.println();
    System.out.println("** Use cursor 'UP' and 'DOWN' to increase/decrease speed");
    System.out.println("** Use cursor 'LEFT' and 'RIGHT' to change the brightness threshold");
    System.out.println("** Press 'C' to change between number and rectangle fill mode");
    System.out.println("** Press 'F' to toggle fill modes (toggle 1 and 0)");
    System.out.println("** To quit, press 'Cmd-Q' or 'Alt-F4'");
    System.out.println();
    System.out.println("Press return to continue ...");
    try {
      new BufferedReader(new InputStreamReader(System.in)).readLine();
    } catch (IOException e) {
      // ignore
    }
  }

  public MePixel(String[] args) {
    showUsage();

    int fontSize = 8;
    try {
      if (args.length > 0) {
        fontSize = Integer.parseInt(args[0]);
      }
    } catch (NumberFormatException e) {
      // ignore if the value is not an integer
    }

    CameraGrabberThread cameraGrabber = new CameraGrabberThread();
    try {
      cameraGrabber.init();
    } catch (QTException e) {
      System.err.println("Camera grabber failed: " + e.getMessage() + " (no camera found?)");
      System.err.println("Exiting ...");
      System.exit(-1);
    }
    cameraGrabber.enable();

    int cameraWidth = cameraGrabber.getCameraWidth();
    int cameraHeight = cameraGrabber.getCameraHeight();

    int[] pixelData = cameraGrabber.getPixelData();

    BufferedDisplay bufferedDisplay = new BufferedDisplay();
    bufferedDisplay.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent keyEvent) {
        switch (keyEvent.getKeyCode()) {
          case KeyEvent.VK_UP:
            delay += 20;
            break;
          case KeyEvent.VK_DOWN:
            if (delay >= 20) delay -= 20;
            break;
          case KeyEvent.VK_LEFT:
            if (brightnessThreshold > 0) brightnessThreshold -= 0.05;
            break;
          case KeyEvent.VK_RIGHT:
            if (brightnessThreshold < 1) brightnessThreshold += 0.05;
            break;
          case KeyEvent.VK_F:
            fillSwap = !fillSwap;
            break;
          case KeyEvent.VK_C:
            charSwap = !charSwap;
        }
      }
    });

    Font font = new Font("Bitstream Vera Sans Mono", Font.PLAIN, fontSize);
    bufferedDisplay.setFont(font);
    int charWidth = bufferedDisplay.getFontMetrics(font).charWidth('@');
    int charHeight = bufferedDisplay.getFontMetrics(font).getHeight();
    int adaptedScreenHeight = bufferedDisplay.getHeight() / charHeight;
    int adaptedScreenWidth = bufferedDisplay.getWidth() / charWidth;
    System.out.println("Using a character size of [" + charWidth + "x" + charHeight + "] pixels");
    System.out.println("The screen is divided into [" + adaptedScreenWidth + "x" + adaptedScreenHeight + "] squares");


    BufferedImage cachedImage = new BufferedImage(adaptedScreenWidth * charWidth,
                                                  adaptedScreenHeight * charHeight,
                                                  BufferedImage.TYPE_INT_RGB);
    Graphics2D cachedImageG2D = cachedImage.createGraphics();
    cachedImageG2D.setFont(font);
    bufferedDisplay.setBackingStore(cachedImage);
    float[] hsvValues = new float[3];

    int rectWidth = cameraWidth / adaptedScreenWidth;
    int rectHeight = cameraHeight / adaptedScreenHeight;

    while (true) {
      for (int row = 0; row < cameraHeight; row += rectHeight) {
        int y = (row / rectHeight) * charHeight;

        cachedImageG2D.setColor(bufferedDisplay.getBackground());
        cachedImageG2D.fillRect(0, y, cameraWidth * charWidth, charHeight);

        for (int column = 0; column < cameraWidth; column += rectWidth) {
          int offset = (row * cameraWidth) + column;
          int avgColor = getAveragedRectPixelColor(pixelData, offset, cameraWidth, rectWidth, rectHeight);

          int x = ((cameraWidth - column) / rectWidth) * charWidth;


          Color fg = new Color(avgColor);
          Color.RGBtoHSB(fg.getRed(), fg.getGreen(), fg.getBlue(), hsvValues);
          cachedImageG2D.setColor(fg);
          if ((fillSwap && (hsvValues[2] > brightnessThreshold)) ||
                  (!fillSwap && hsvValues[2] < brightnessThreshold)) {
            if (charSwap) {
              cachedImageG2D.drawRect(x, y, charWidth, charHeight);
            } else {
              cachedImageG2D.drawString("1", x, y - charHeight);
            }
          } else {
            if (charSwap) {
              cachedImageG2D.fillRect(x, y, charWidth, charHeight);
            } else {
              cachedImageG2D.drawString("0", x, y - charHeight);
            }
          }
        }
        bufferedDisplay.repaint();
        try {
          Thread.sleep(delay);
        } catch (InterruptedException e) {
          // ignore thread errors
        }
      }
    }
  }

  /**
   * Calculate the average color for a certain pixel rectangle
   *
   * @param image   the image to get the data from
   * @param offset  current offset (left, top) in the data array
   * @param rowSize the current row length in bytes
   * @param width   the width of the rectangle in pixels
   * @param height  the height of the rectange in pixels
   * @return a 24 bit color int
   */
  private int getAveragedRectPixelColor(int[] image, int offset, int rowSize, int width, int height) {
    int avgColor = 0;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int pixColor;
        try {
          pixColor = image[offset + (y * rowSize) + x];
        } catch (Exception e) {
          return avgColor;
        }
        // color averaging
        avgColor = (((avgColor ^ pixColor) & 0xfffefefe) >> 1) + (avgColor & pixColor);
      }
    }
    return avgColor;
  }
}
