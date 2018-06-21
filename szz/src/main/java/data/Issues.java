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

package data;

import java.util.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * A container of changes that are considered as fixes for issues.
 *
 * @author Oscar Svensson
 */
public class Issues {

  public Set<RevCommit> revisions;
  public Map<String, Map<String, String>> dates;

  /**
   * Return the three dates for a change. The dates are the commit date, the resolution date and the
   * creation date.
   *
   * @param commit the referencing commit.
   * @return a map containing all dates.
   */
  public Map<String, String> get(RevCommit commit) {
    String rev = ObjectId.toString(commit.toObjectId());

    try {
      return dates.get(rev);
    } catch (Exception e) {
      return new HashMap<>();
    }
  }

  /**
   * Return the three dates for a change. The dates are the commit date, the resolution date and the
   * creation date.
   *
   * @param commit the referencing commit.
   * @return a map containing all dates.
   */
  public Map<String, String> get(String commit) {
    if (dates.containsKey(commit)) {
      return dates.get(commit);
    }
    return new HashMap<>();
  }
}
