/*
 * Copyright (c) 2007, Matthias L. Jugel. All Rights Reserved.
 * See http://thinkberg.com/ for details and instructions.
 */

package com.thinkberg.mepixel;

import quicktime.QTException;
import quicktime.QTRuntimeException;
import quicktime.QTRuntimeHandler;
import quicktime.QTSession;
import quicktime.io.QTFile;
import quicktime.io.OpenMovieFile;
import quicktime.qd.PixMap;
import quicktime.qd.QDGraphics;
import quicktime.qd.QDRect;
import quicktime.qd.QDConstants;
import quicktime.std.StdQTConstants;
import quicktime.std.movies.Movie;
import quicktime.std.sg.SGVideoChannel;
import quicktime.std.sg.SequenceGrabber;
import quicktime.util.RawEncodedImage;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;

/**
 * @author Matthias L. Jugel
 */
public class CameraGrabberThread extends Thread {
  private boolean running = false;

  private SequenceGrabber grabber;
  private SGVideoChannel channel;
  private RawEncodedImage rowEncodedImage;
  private int[] pixelData;
  private int width, height;

  public CameraGrabberThread() {
    super();
  }

  public void init() throws QTException {
    QTSession.open();

    grabber = new SequenceGrabber();
    channel = new SGVideoChannel(grabber);

    width = channel.getSrcVideoBounds().getWidth();
    height = channel.getSrcVideoBounds().getHeight();
    QDRect bounds = new QDRect(width, height);
    QDGraphics graphics;
    if (quicktime.util.EndianOrder.isNativeLittleEndian()) {
      graphics = new QDGraphics(QDConstants.k32BGRAPixelFormat, bounds);
    } else {
      graphics = new QDGraphics(QDGraphics.kDefaultPixelFormat, bounds);
    }
    grabber.setGWorld(graphics, null);
    channel.setBounds(bounds);
    channel.setUsage(StdQTConstants.seqGrabPreview);
    grabber.prepare(true, false);

		grabber.setDataOutput( null, StdQTConstants.seqGrabDontMakeMovie);
		grabber.prepare(true, true);
		grabber.startRecord();

    PixMap pixMap = graphics.getPixMap();
    rowEncodedImage = pixMap.getPixelData();
    pixelData = new int[width * height];

    QTRuntimeException.registerHandler(new QTRuntimeHandler() {
      public void exceptionOccurred(
              QTRuntimeException e, Object eGenerator,
              String methodNameIfKnown, boolean unrecoverableFlag) {
        System.out.println("what should i do?");
      }
    });
  }

  public int getCameraWidth() {
    return width;
  }

  public int getCameraHeight() {
    return height;
  }
  
  public void enable() {
    running = true;
    start();
  }

  public void disable() {
    running = true;
  }

  public void run() {
    try {
      while (running) {
        grabber.idle();
        rowEncodedImage.copyToArray(0, pixelData, 0, pixelData.length);
      }
    } catch (QTException e) {
      e.printStackTrace();
    }
    QTSession.close();
  }

  public int[] getPixelData() {
    return pixelData;
  }

  public void dispose() {
    try {
      grabber.stop();
      grabber.release();
      grabber.disposeChannel(channel);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      QTSession.close();
    }
  }
}
