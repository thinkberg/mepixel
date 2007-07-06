/*
 * Copyright (c) 2007, Matthias L. Jugel. All Rights Reserved.
 * See http://thinkberg.com/ for details and instructions.
 */

package com.thinkberg.mepixel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

/**
 * @author Matthias L. Jugel
 */
public class CharDisplay extends JFrame {
  private Image backingStore;
  private int xOffset;
  private int yOffset;

  public CharDisplay() {
    super();

    setUndecorated(true);
    setExtendedState(Frame.MAXIMIZED_BOTH);
    setVisible(true);
    setBackground(Color.BLACK);

    getGraphicsConfiguration().getDevice().setFullScreenWindow(this);

  }

  public void setBackingStore(Image image) {
    backingStore = image;
    xOffset = (getWidth() - backingStore.getWidth(this)) / 2;
    yOffset = (getHeight() - backingStore.getHeight(this)) / 2;
  }

  /**
   * Paint the current screen using the backing store image.
   */
  public void paint(Graphics g) {
    if (backingStore != null) {
      g.drawImage(backingStore, xOffset, yOffset, this);
    }
  }
}
