/*
 * MIT License
 *
 * Copyright (c) 2018 Axis Communications AB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package util;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.*;

/**
 * Combinator for revisions.
 */
public class RevisionCombinationGenerator {
  private int[] a;
  private int n;
  private int r;
  private BigInteger numLeft;
  private BigInteger total;

  private List<String> sa;
  private List<String> sb;

  /*
  * Temporary holder for the iterator.
  */
  private Set<String> revs;
  private Set<String> issues;
  private List<String> all_commits;

  /**
   * Constructor
   */
  public RevisionCombinationGenerator(List<String> sa, List<String> sb, int r) {
    this.n = sa.size() + sb.size();
    if (r > this.n) {
      throw new IllegalArgumentException();
    }
    if (n < 1) {
      throw new IllegalArgumentException();
    }
    this.r = r;

    this.sa = sa;
    this.sb = sb;

    a = new int[r];
    BigInteger nFact = getFactorial(n);
    BigInteger rFact = getFactorial(r);
    BigInteger nminusrFact = getFactorial(n - r);
    total = nFact.divide(rFact.multiply(nminusrFact));
    reset();
  }

  public RevisionCombinationGenerator(int n, int r) {
    if (r > n) {
      throw new IllegalArgumentException();
    }
    if (n < 1) {
      throw new IllegalArgumentException();
    }
    this.n = n;
    this.r = r;
    a = new int[r];
    BigInteger nFact = getFactorial(n);
    BigInteger rFact = getFactorial(r);
    BigInteger nminusrFact = getFactorial(n - r);
    total = nFact.divide(rFact.multiply(nminusrFact));
    reset();
  }

  public void reset() {
    for (int i = 0; i < a.length; i++) {
      a[i] = i;
    }
    numLeft = new BigInteger(total.toString());
  }

  /**
   * Return number of combinations not yet generated.
   */
  public BigInteger getNumLeft() {
    return numLeft;
  }

  /**
   * Are there more combinations?
   */
  public boolean hasMore() {
    return numLeft.compareTo(BigInteger.ZERO) == 1;
  }

  /**
   * Returns the total number of combinations,
   */
  public BigInteger getTotal() {
    return total;
  }

  /**
   * Computes factorial of n.
   */
  private static BigInteger getFactorial(int n) {
    BigInteger fact = BigInteger.ONE;
    for (int i = n; i > 1; i--) {
      fact = fact.multiply(new BigInteger(Integer.toString(i)));
    }
    return fact;
  }

  /**
   * Generates next combination (algorithm from Rosen p. 286)
   */
  public int[] getNext() {

    if (numLeft.equals(total)) {
      numLeft = numLeft.subtract(BigInteger.ONE);
      return a;
    }

    int i = r - 1;
    while (a[i] == n - r + i) {
      i--;
    }
    a[i] = a[i] + 1;
    for (int j = i + 1; j < r; j++) {
      a[j] = a[i] + j - i;
    }

    numLeft = numLeft.subtract(BigInteger.ONE);
    return a;
  }

  public RevisionCombinationGenerator iterator() {
    this.revs = new HashSet<>(this.sa);
    this.issues = new HashSet<>(this.sb);
    this.all_commits = Stream.concat(this.sa.stream(), this.sb.stream()).collect(Collectors.toList());

    return this;
  }

  public boolean hasNext() {
    if (r != 2) return false;
    return hasMore();
  }

  public String[] getNextIndic() {
    int[] indices;
    indices = getNext();

    String c1 = this.all_commits.get(indices[0]);
    String c2 = this.all_commits.get(indices[1]);

    if (revs.contains(c1) && issues.contains(c2)) {
      return new String[]{c1, c2};
    } else if (revs.contains(c2) && issues.contains(c1)) {
      return new String[]{c2, c1};
    }

    return new String[]{"", ""};
  }

  public List<String[]> generateRevIssuePairs() {
    if (r != 2) return Collections.emptyList();
    List<String[]> combinations = new LinkedList<>();
    int[] indices;

    Set<String> revs = new HashSet<>(sa);
    Set<String> issues = new HashSet<>(sb);

    List<String> all_commits = Stream.concat(sa.stream(), sb.stream()).collect(Collectors.toList());

    boolean inRevs = false;
    while (hasMore()) {
      indices = getNext();

      String c1 = all_commits.get(indices[0]);
      String c2 = all_commits.get(indices[1]);

      inRevs = revs.contains(c1);
      if (revs.contains(c1) && issues.contains(c2)) {
        combinations.add(new String[] {c1, c2});
      } else if (revs.contains(c2) && issues.contains(c1)) {
        combinations.add(new String[] {c2, c1});
      }
    }

    return combinations;
  }
}
