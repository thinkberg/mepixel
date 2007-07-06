/*
 * Copyright (c) 2007, Matthias L. Jugel. All Rights Reserved.
 * See http://thinkberg.com/ for details and instructions.
 */
package com.thinkberg.launcher;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Policy;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.jar.JarEntry;

/**
 * Launcher for Java Applications. Creates the classpath and then starts the application.
 * The launcher extracts all available jar files found in the Class-Path of the launcher jar
 * file and adds them to the system class path before executing the real code.
 *
 * @author Matthias L. Jugel
 */
public class Launcher {
  public final static String CLASSPATH = "launcher.classpath";

  protected static boolean debug = false;

  private final static URL location = Launcher.class.getProtectionDomain().getCodeSource().getLocation();

  /**
   * Invokes the main() method of the class to be launched with the arguments provided.
   * This is a wrapper to configure class path and other settings before launching the actual code.
   *
   * @param mainClassName the class to be launched
   * @param args standard command line arguments
   * @throws ClassNotFoundException
   * @throws NoSuchMethodException
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   */
  public static void invokeMain(String mainClassName, final String args[])
          throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    // get the parent class loader
    ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
    if (null == parentClassLoader) {
      parentClassLoader = Launcher.class.getClassLoader();
    }
    if (null == parentClassLoader) {
      parentClassLoader = ClassLoader.getSystemClassLoader();
    }
    URLClassLoader classLoader = new URLClassLoader(initClassPath(System.getProperty(CLASSPATH)),
                                                    parentClassLoader);
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

    // load and start main class
    Class mainClass = classLoader.loadClass(mainClassName);
    final Method main = mainClass.getDeclaredMethod("main", new Class[]{String[].class});
    main.invoke(null, new Object[]{args});
  }

  /**
   * Initialize the class path dynamically from information available in the manifest file.
   *
   * @param extraClassPath extra class path elements
   * @return the URL array with the new class path
   */
  protected static URL[] initClassPath(String extraClassPath) {
    List urlArray = new ArrayList();
    InputStream manifestIn = null;
    InputStream jarIn = null;
    try {
      manifestIn = location.openStream();
      JarInputStream launcherJarIs = new JarInputStream(manifestIn);
      Manifest launcherManifest = launcherJarIs.getManifest();
      Attributes launcherAttribs = launcherManifest.getMainAttributes();
      String mainJarAttr = launcherAttribs.getValue("Launcher-Main-Jar");
      if (System.getProperty("launcher.main.jar") != null) {
        mainJarAttr = System.getProperty("launcher.main.jar");
      }
      URL mainJarUrl = getResourceUrl(mainJarAttr);
      jarIn = mainJarUrl.openStream();
      Manifest mainManifest = new JarInputStream(jarIn).getManifest();
      Attributes mainAttributes = mainManifest.getMainAttributes();
      String manifestClassPath = mainAttributes.getValue("Class-Path");

      urlArray.add(mainJarUrl);
      // append extra class path to manifest class path (after replacing separatorchar)
      if (extraClassPath != null && extraClassPath.length() > 0) {
        manifestClassPath += " " + extraClassPath.replace(File.pathSeparatorChar, ' ');
      }

      List extractedFiles = new ArrayList();
      StringBuffer classPath = new StringBuffer(location.getFile());
      if(manifestClassPath != null && manifestClassPath.length() != 0) {
        StringTokenizer tokenizer = new StringTokenizer(manifestClassPath, " \t" + File.pathSeparatorChar, false);
        while (tokenizer.hasMoreTokens()) {
          String entry = tokenizer.nextToken();
          try {
            URL classPathEntry = getResourceUrl(entry);
            extractedFiles.add(entry);
            urlArray.add(classPathEntry);
            classPath.append(File.pathSeparatorChar);
            classPath.append(classPathEntry.getFile());
          } catch (IOException e) {
            System.err.println("Error: Missing resource ("+entry+") ignored, expect errors ...");
          }
        }
      }

      // ensure we extract all jar files from the launcher package and add them to the class path
      // we do not rely on the Class-Path entry 
      List classpathList = new ArrayList(urlArray);
      JarEntry jarEntry = null;
      while(null != (jarEntry = launcherJarIs.getNextJarEntry())) {
        if(!jarEntry.isDirectory() &&
                (jarEntry.getName().endsWith(".jar") || jarEntry.getName().endsWith(".zip")) && 
                !extractedFiles.contains(jarEntry.getName())) {
          try {
            URL classPathEntry = getResourceUrl(jarEntry.getName());
            if(!classpathList.contains(classPathEntry)) {
              urlArray.add(classPathEntry);
              classPath.append(File.pathSeparatorChar);
              classPath.append(classPathEntry.getFile());
            }
          } catch (IOException e) {
            System.err.println("Error: Missing or corrupted resource ("+jarEntry.getName()+") ignored.");
          }
        }
      }

      System.setProperty("java.class.path", classPath.toString());
    } catch (IOException e) {
      System.err.println("Error: Set the system property launcher.main.jar to specify the jar file to start.");
      e.printStackTrace();
    } finally {
      try { manifestIn.close(); } catch (Throwable ignore) { };
      try { jarIn.close(); } catch (Throwable ignore) { };
    }
    return (URL[]) urlArray.toArray(new URL[0]);
  }

  /**
   * Make a URL from a resource name. Necessary for creating a URL class loader.
   * @param resource resource name/path
   * @return the url pointing to the resource
   * @throws IOException
   */
  private static URL getResourceUrl(String resource) throws IOException {
    File directoryBase = new File(location.getFile()).getParentFile();
    File file = new File(resource);
    // see if this  is an absolute URL
    if (file.isAbsolute() && file.exists()) {
      return file.toURL();
    }
    // handle non-absolute URLs
    file = new File(directoryBase, resource);
    if (file.exists()) {
      return file.toURL();
    }

    URL resourceURL = Launcher.class.getResource("/" + resource);
    if (null != resourceURL) {
      return extract(resourceURL);
    }

    throw new MalformedURLException("missing resource: " + resource);
  }

  /**
   * Extract file from launcher jar to be able to access is via classpath.
   *
   * @param resource the jar resource to be extracted
   * @return a url pointing to the new file
   * @throws IOException if the extraction was not possible
   */
  private static URL extract(URL resource) throws IOException {
    if(debug) {
      System.err.println("Launcher: extracting '" + resource.getFile() + "' ...");
    }
    File f = File.createTempFile("launcher_", ".jar");
    f.deleteOnExit();
    if (f.getParentFile() != null) {
      f.getParentFile().mkdirs();
    }
    InputStream is = new BufferedInputStream(resource.openStream());
    FileOutputStream os = new FileOutputStream(f);
    byte[] arr = new byte[8192];
    for (int i = 0; i >= 0; i = is.read(arr)) {
      os.write(arr, 0, i);
    }
    is.close();
    os.close();
    return f.toURL();
  }
}