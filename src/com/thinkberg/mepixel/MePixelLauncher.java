/*
 * Copyright (c) 2007, Matthias L. Jugel. All Rights Reserved.
 * See http://thinkberg.com/ for details and instructions.
 */

package com.thinkberg.mepixel;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Policy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Matthias L. Jugel
 */
public class MePixelLauncher {
  private static List<String> qtClassPath = Arrays.asList(
          "/System/Library/Java/Extensions/QTJava.zip",
          "C:\\Program Files\\Quicktime\\QTSystem\\QTJava.zip",
          "C:\\Programme\\Quicktime\\QTSystem\\QTJava.zip"
  );

  public static void main(String args[]) {

    System.err.println("MePixelLauncher (c) 2007 Matthias L. Jugel. All Rights Reserved.");
    System.err.println("++ Checking Quicktime installation ...");
    ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
    if (null == parentClassLoader) {
      parentClassLoader = MePixel.class.getClassLoader();
    }
    if (null == parentClassLoader) {
      parentClassLoader = ClassLoader.getSystemClassLoader();
    }

    List<URL> classPath = new ArrayList<URL>();
    classPath.add(MePixel.class.getProtectionDomain().getCodeSource().getLocation());
    for (String fileName : qtClassPath) {
      File file = new File(fileName);
      if (file.exists()) {
        try {
          classPath.add(file.toURL());
          System.err.println("++ Added " + file + " to class path.");
        } catch (MalformedURLException e) {
          // ignore
        }
      }
    }
    URLClassLoader classLoader = new URLClassLoader(classPath.toArray(new URL[0]),
                                                    null);
    Thread.currentThread().setContextClassLoader(classLoader);

    // for the sake of Java Web Start it is necessary to uninstall the security manager
    if (System.getSecurityManager() != null) {
      System.err.println("Launcher: uninstalling security manager ...");
      System.setSecurityManager(null);
    }

    try {
      Policy.getPolicy().refresh();
    } catch (Exception e) {
      e.printStackTrace();
    }

    System.err.println();
    // load and start main class
    try {
      Class mainClass = classLoader.loadClass("com.thinkberg.mepixel.MePixel");
      final Constructor mainContructor = mainClass.getConstructor(String[].class);
      mainContructor.newInstance(new Object[]{args});
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
