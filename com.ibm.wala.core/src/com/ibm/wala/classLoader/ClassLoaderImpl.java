/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.classLoader;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.wala.ipa.callgraph.impl.SetOfClasses;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.Atom;
import com.ibm.wala.util.ShrikeClassReaderHandle;
import com.ibm.wala.util.collections.HashCodeComparator;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Collection;
import com.ibm.wala.util.collections.ToStringComparator;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.debug.Trace;
import com.ibm.wala.util.warnings.Warning;
import com.ibm.wala.util.warnings.WarningSet;

/**
 *
 * A class loader that reads class definitions from a set of Modules.
 * 
 * @author sfink
 */
public class ClassLoaderImpl implements IClassLoader {
  private static final int DEBUG_LEVEL = 0;

  /**
   * classes to ignore
   */
  private SetOfClasses exclusions;

  /**
   * Identity for this class loader
   */
  private ClassLoaderReference loader;

  /**
   * A mapping from class name (TypeName) to IClass
   */
  protected final Map<TypeName, IClass> loadedClasses = HashMapFactory.make();

  /**
   * A mapping from class name (TypeName) to String (source file name)
   */
  private final Map<TypeName, String> sourceMap = HashMapFactory.make();

  /**
   * An object to track warnings
   */
  protected final WarningSet warnings;

  /**
   * Parent classloader
   */
  private IClassLoader parent;

  /**
   * Governing class hierarchy
   */
  protected final IClassHierarchy cha;

  /**
   * an object to delegate to for loading of array classes
   */
  private final ArrayClassLoader arrayClassLoader;

  /**
   * @param loader
   *          class loader reference identifying this loader
   * @param parent
   *          parent loader for delegation
   * @param exclusions
   *          set of classes to exclude from loading
   */
  public ClassLoaderImpl(ClassLoaderReference loader, ArrayClassLoader arrayClassLoader, IClassLoader parent,
      SetOfClasses exclusions, IClassHierarchy cha, WarningSet warnings) {

    this.arrayClassLoader = arrayClassLoader;
    this.parent = parent;
    this.loader = loader;
    this.exclusions = exclusions;
    this.cha = cha;
    this.warnings = warnings;

    if (DEBUG_LEVEL > 0) {
      Trace.println("Creating class loader for " + loader);
    }
  }

  /**
   * Return the Set of (ModuleEntry) source files found in a module.
   * 
   * @param M
   *          the module
   * @return the Set of source files in the module
   * @throws IOException
   */
  private Set<ModuleEntry> getSourceFiles(Module M) throws IOException {
    if (DEBUG_LEVEL > 0) {
      Trace.println("Get source files for " + M);
    }
    TreeSet<ModuleEntry> sortedEntries = new TreeSet<ModuleEntry>(HashCodeComparator.instance());
    sortedEntries.addAll(new Iterator2Collection<ModuleEntry>(M.getEntries()));

    HashSet<ModuleEntry> result = HashSetFactory.make();
    for (Iterator it = sortedEntries.iterator(); it.hasNext();) {
      ModuleEntry entry = (ModuleEntry) it.next();
      if (DEBUG_LEVEL > 0) {
        Trace.println("consider entry for source information: " + entry);
      }
      if (entry.isSourceFile()) {
        if (DEBUG_LEVEL > 0) {
          Trace.println("found source file: " + entry);
        }
        result.add(entry);
      } else if (entry.isModuleFile()) {
        result.addAll(getSourceFiles(entry.asModule()));
      }
    }
    return result;
  }

  /**
   * Return the Set of (ModuleEntry) class files found in a module.
   * 
   * @param M
   *          the module
   * @return the Set of class Files in the module
   * @throws IOException
   */
  private Set<ModuleEntry> getClassFiles(Module M) throws IOException {
    if (DEBUG_LEVEL > 0) {
      Trace.println("Get class files for " + M);
    }
    TreeSet<ModuleEntry> sortedEntries = new TreeSet<ModuleEntry>(HashCodeComparator.instance());
    sortedEntries.addAll(new Iterator2Collection<ModuleEntry>(M.getEntries()));

    HashSet<ModuleEntry> result = HashSetFactory.make();
    for (Iterator it = sortedEntries.iterator(); it.hasNext();) {
      ModuleEntry entry = (ModuleEntry) it.next();
      if (DEBUG_LEVEL > 0) {
        Trace.println("ClassLoaderImpl.getClassFiles:Got entry: " + entry);
      }
      if (entry.isClassFile()) {
        if (DEBUG_LEVEL > 0) {
          Trace.println("result contains: " + entry);
        }
        result.add(entry);
      } else if (entry.isModuleFile()) {
        Set<ModuleEntry> s = getClassFiles(entry.asModule());
        removeClassFiles(s, result);
        result.addAll(s);
      } else {
        if (DEBUG_LEVEL > 0) {
          Trace.println("Ignoring entry: " + entry);
        }
      }
    }
    return result;
  }

  /**
   * Remove from s any class file module entries which already are in t
   * 
   * @param s
   * @param t
   */
  private void removeClassFiles(Set<ModuleEntry> s, Set<ModuleEntry> t) {
    Set<String> old = new HashSet<String>();
    for (Iterator<ModuleEntry> it = t.iterator(); it.hasNext();) {
      ModuleEntry m = it.next();
      old.add(m.getClassName());
    }
    HashSet<ModuleEntry> toRemove = new HashSet<ModuleEntry>();
    for (Iterator<ModuleEntry> it = s.iterator(); it.hasNext();) {
      ModuleEntry m = it.next();
      if (old.contains(m.getClassName())) {
        toRemove.add(m);
      }
    }
    s.removeAll(toRemove);
  }

  /**
   * Return a Set of IClasses, which represents all classes this class loader
   * can load.
   */
  private Collection<IClass> getAllClasses() {
    if (Assertions.verifyAssertions) {
      Assertions._assert(loadedClasses != null);
    }

    return loadedClasses.values();
  }

  /**
   * Set up the set of classes loaded by this object.
   */
  private void loadAllClasses(Collection<ModuleEntry> moduleEntries) {
    for (Iterator<ModuleEntry> it = moduleEntries.iterator(); it.hasNext();) {
      ModuleEntry entry = it.next();
      if (!entry.isClassFile()) {
        continue;
      }

      String className = entry.getClassName().replace('.', '/');

      if (DEBUG_LEVEL > 0) {
        Trace.println("Consider " + className);
      }

      if (exclusions != null && exclusions.contains(className)) {
        if (DEBUG_LEVEL > 0) {
          Trace.println("Excluding " + className);
        }
        continue;
      }

      ShrikeClassReaderHandle reader = new ShrikeClassReaderHandle(entry);

      className = "L" + className;
      if (DEBUG_LEVEL > 0) {
        Trace.println("Load class " + className);
      }
      try {
        TypeName T = TypeName.string2TypeName(className);
        if (loadedClasses.get(T) != null) {
          warnings.add(MultipleImplementationsWarning.create(className));
        } else {
          ShrikeClass klass = new ShrikeClass(reader, this, cha, warnings);
          if (klass.getReference().getName().equals(T)) {
            loadedClasses.put(T, new ShrikeClass(reader, this, cha, warnings));
            if (DEBUG_LEVEL > 1) {
              Trace.println("put " + T + " ");
            }
          } else {
            warnings.add(InvalidClassFile.create(className));
          }
        }
      } catch (InvalidClassFileException e) {
        if (DEBUG_LEVEL > 0) {
          Trace.println("Ignoring class " + className + " due to InvalidClassFileException");
        }
        warnings.add(InvalidClassFile.create(className));
      }
    }
  }

  /**
   * @author sfink
   * 
   * A waring when we find more than one implementation of a given class name
   */
  private static class MultipleImplementationsWarning extends Warning {

    final String className;

    MultipleImplementationsWarning(String className) {
      super(Warning.SEVERE);
      this.className = className;
    }

    @Override
    public String getMsg() {
      return getClass().toString() + " : " + className;
    }

    public static MultipleImplementationsWarning create(String className) {
      return new MultipleImplementationsWarning(className);
    }
  }

  /**
   * @author sfink
   * 
   * A waring when we encounter InvalidClassFileException
   */
  private static class InvalidClassFile extends Warning {

    final String className;

    InvalidClassFile(String className) {
      super(Warning.SEVERE);
      this.className = className;
    }

    @Override
    public String getMsg() {
      return getClass().toString() + " : " + className;
    }

    public static InvalidClassFile create(String className) {
      return new InvalidClassFile(className);
    }
  }

  /**
   * Set up mapping from type name to Module Entry
   */
  protected void loadAllSources(Set<ModuleEntry> sourceModules) {

    for (Iterator<ModuleEntry> it = sourceModules.iterator(); it.hasNext();) {
      ModuleEntry entry = it.next();
      String className = entry.getClassName().replace('.', '/');
      className = className.replace(File.separatorChar, '/');
      className = "L" + ((className.startsWith("/")) ? className.substring(1) : className);
      TypeName T = TypeName.string2TypeName(className);
      if (DEBUG_LEVEL > 0) {
        Trace.println("adding to source map: " + T + " -> " + entry.getName());
      }
      sourceMap.put(T, entry.getName());
    }
  }

  /**
   * Initialize internal data structures
   * 
   * @throws IOException
   * @throws IllegalArgumentException  if modules is null
   */
  public void init(Set modules) throws IOException {

    if (modules == null) {
      throw new IllegalArgumentException("modules is null");
    }
    // use tree set to keep things sorted ... for deterministic class loading
    TreeSet<Module> archives = new TreeSet<Module>(ToStringComparator.instance());
    for (Iterator i = modules.iterator(); i.hasNext();) {
      Module M = (Module) i.next();
      if (DEBUG_LEVEL > 0) {
        Trace.println("add archive: " + M);
      }
      archives.add(M);
    }
    Set<ModuleEntry> classModuleEntries = new HashSet<ModuleEntry>();
    Set<ModuleEntry> sourceModuleEntries = new HashSet<ModuleEntry>();
    for (Iterator<Module> it = archives.iterator(); it.hasNext();) {
      Module archive = it.next();
      Set<ModuleEntry> classFiles = getClassFiles(archive);
      removeClassFiles(classFiles, classModuleEntries);
      for (Iterator<ModuleEntry> it2 = classFiles.iterator(); it2.hasNext();) {
        ModuleEntry file = it2.next();
        classModuleEntries.add(file);
      }
      Set<ModuleEntry> sourceFiles = getSourceFiles(archive);
      for (Iterator<ModuleEntry> it2 = sourceFiles.iterator(); it2.hasNext();) {
        ModuleEntry file = it2.next();
        sourceModuleEntries.add(file);
      }
    }
    loadAllClasses(classModuleEntries);
    loadAllSources(sourceModuleEntries);
  }

  public ClassLoaderReference getReference() {
    return loader;
  }

  public Iterator<IClass> iterateAllClasses() {
    return getAllClasses().iterator();
  }

  /**
   * This version returns null instead of throwing ClassNotFoundException.
   */
  private IClass lookupClassInternal(TypeName className) {
    if (DEBUG_LEVEL > 1) {
      Trace.println(this + ": lookupClassInternal " + className);
    }

    // try delegating first.
    ClassLoaderImpl parent = (ClassLoaderImpl) getParent();
    if (parent != null) {
      IClass result = parent.lookupClassInternal(className);
      if (result != null)
        return result;
    }
    // delegating failed. Try our own namespace.
    return loadedClasses.get(className);
  }

  public IClass lookupClass(TypeName className, IClassHierarchy cha) {
    if (className == null) {
      throw new IllegalArgumentException("className is null");
    }
    if (DEBUG_LEVEL > 1) {
      Trace.println(this + ": lookupClass " + className);
    }

    // treat arrays specially:
    if (className.isArrayType()) {
      return arrayClassLoader.lookupClass(className, this,cha);
    }

    // try delegating first.
    ClassLoaderImpl parent = (ClassLoaderImpl) getParent();
    if (parent != null) {
      IClass result = parent.lookupClassInternal(className);
      if (result != null) {
        if (DEBUG_LEVEL > 1) {
          Trace.println(this + ": returning class from parent: " + result);
        }
        return result;
      }
    }
    // delegating failed. Try our own namespace.
    IClass result = loadedClasses.get(className);
    return result;
  }

  /**
   * Method getParent.
   */
  public IClassLoader getParent() {
    return parent;
  }

  public Atom getName() {
    return loader.getName();
  }

  public Language getLanguage() {
    return Language.JAVA;
  }

  /**
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return getName().toString();
  }

  /*
   * @see com.ibm.wala.classLoader.IClassLoader#getNumberOfClasses()
   */
  public int getNumberOfClasses() {
    return getAllClasses().size();
  }

  /*
   * @see com.ibm.wala.classLoader.IClassLoader#getNumberOfMethods()
   */
  public int getNumberOfMethods() {
    int result = 0;
    for (Iterator<IClass> it = iterateAllClasses(); it.hasNext();) {
      IClass klass = it.next();
      result += klass.getDeclaredMethods().size();
    }
    return result;
  }

  /*
   * @see com.ibm.wala.classLoader.IClassLoader#getSourceFileName(com.ibm.wala.classLoader.IClass)
   */
  public String getSourceFileName(IClass klass) {
    if (klass == null) {
      throw new IllegalArgumentException("klass is null");
    }
    return sourceMap.get(klass.getName());
  }

  /*
   * @see com.ibm.wala.classLoader.IClassLoader#removeAll(java.util.Collection)
   */
  public void removeAll(Collection<IClass> toRemove) {
    if (toRemove == null) {
      throw new IllegalArgumentException("toRemove is null");
    }
    for (Iterator<IClass> it = toRemove.iterator(); it.hasNext();) {
      IClass klass = it.next();
      loadedClasses.remove(klass.getName());
      sourceMap.remove(klass.getName());
    }
  }
}
