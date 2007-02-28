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
package com.ibm.wala.util.intset;

import java.util.NoSuchElementException;


/** 
 * An object that implements a bijection between whole numbers and
 * objects.
 *
 * @author Stephen Fink
 */
public interface OrdinalSetMapping<T> extends Iterable<T> {
  /**
   * @return the object numbered n.  
   */
  public T getMappedObject(int n) throws NoSuchElementException;

  /**
   * @return the number of a given object, or -1 if the object is not
   * currently in the range.
   */
  public int getMappedIndex(T o);

  /**
   * @return whether the given object is mapped by this mapping
   */
  public boolean hasMappedIndex(T o);

  /**
   * @return the size of the domain of the bijection. 
   */
  public int getMappingSize();

  /**
   * @param i
   * @return an ordinal set which conains only getMappedObject(i)
   */
  public OrdinalSet<T> makeSingleton(int i);
  
  /**
   * Add an Object to the set of mapped objects.
   * @return the integer to which the object is mapped.
   */
  public int add(T o);
}
