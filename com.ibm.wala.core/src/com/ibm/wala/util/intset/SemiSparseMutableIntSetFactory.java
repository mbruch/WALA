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

import java.util.Iterator;
import java.util.TreeSet;

/**
 * @author Julian Dolby (dolby@us.ibm.com)
 */
public class SemiSparseMutableIntSetFactory implements MutableIntSetFactory {

  /**
   * @param set
   */
  public MutableIntSet make(int[] set) {
    if (set.length == 0) {
      return new BitVectorIntSet();
    } else {
      // XXX not very efficient.
      TreeSet<Integer> T = new TreeSet<Integer>();
      for (int i = 0; i < set.length; i++) {
        T.add(new Integer(set[i]));
      }
      MutableIntSet result = new SemiSparseMutableIntSet();
      for (Iterator<Integer> it = T.iterator(); it.hasNext();) {
        Integer I = it.next();
        result.add(I.intValue());
      }
      return result;
    }
  }

  /**
   * @param string
   */
  public MutableIntSet parse(String string) {
    int[] data = SparseIntSet.parseIntArray(string);
    MutableIntSet result = new SemiSparseMutableIntSet();
    for (int i = 0; i < data.length; i++)
      result.add(data[i]);
    return result;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.wala.util.intset.MutableIntSetFactory#make(com.ibm.wala.util.intset.IntSet)
   */
  public MutableIntSet makeCopy(IntSet x) {
    MutableIntSet y = new SemiSparseMutableIntSet();
    y.copySet(x);
    return y;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.wala.util.intset.MutableIntSetFactory#make()
   */
  public MutableIntSet make() {
    return new SemiSparseMutableIntSet();
  }

}
