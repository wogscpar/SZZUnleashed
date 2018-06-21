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

package diff;

import java.io.*;
import java.util.*;
import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * A class which partitions the commits into evenly partitions.
 *
 * @author Oscar Svensson
 */
public class SimplePartition {

  private static String ANNOTATIONPATH = "annotations.json",
      COMMITSPATH = "commits.json",
      FIXINTRODUCERSPATH = "fix_and_introducers_pairs.json",
      SUBFIXINTRODUCERSPATH = "fix_and_introducers_pairs_%d.json";

  private static List<String> splitJSON(int partitions, String path, String resPath) {

    List<String> paths = new ArrayList<>();
    JSONParser parser = new JSONParser();
    try {
      JSONObject issues = (JSONObject) parser.parse(new FileReader(path));

      int size = issues.keySet().size();

      int div = size / partitions;
      int mod = size % partitions;

      List<Integer[]> chunks = new ArrayList<>();

      /*
       * Divide into evenly sized chunks.
       */
      for (int i = 0; i < partitions; i++) {
        Integer[] chunk = new Integer[2];
        chunk[0] = i * div + Math.min(i, mod);
        chunk[1] = (i + 1) * div + Math.min(i + 1, mod);
        if (i < partitions - 1) chunk[1] -= 1;

        chunks.add(chunk);
      }

      List<String> keys = new ArrayList<>(issues.keySet());

      for (int i = 0; i < partitions; i++) {
        JSONObject chunkObject = new JSONObject();
        Integer[] chunk = chunks.get(i);

        int limit = i < partitions - 1 ? chunk[1] : chunk[1] - 1;
        for (int start = chunk[0]; start <= limit; start++) {
          String key = keys.get(start);
          chunkObject.put(key, issues.get(key));
        }

        String chunkPath = resPath + "/" + String.format(SUBFIXINTRODUCERSPATH, i);

        FileWriter writer = new FileWriter(chunkPath);
        writer.write(chunkObject.toJSONString());
        writer.flush();

        paths.add(chunkPath);
      }

    } catch (IOException | ParseException e) {
      e.printStackTrace();
      return new LinkedList<>();
    }

    return paths;
  }

  public static List<String> splitFile(int partitions, String path, String resPath) {
    List<String> paths = new LinkedList<>();

    File f = new File(path);
    if (!f.isFile()) {
      System.err.println("Path doesn't exists...");
      return new LinkedList<>();
    }

    File resDir = new File(resPath);
    if (resDir.exists()) {
      System.err.println(String.format("%s already exists!", resPath));
      return new LinkedList<>();
    }

    resDir.mkdirs();

    String extension = FilenameUtils.getExtension(path);
    if (extension.equals("json")) {
      paths = SimplePartition.splitJSON(partitions, path, resPath);
    } else {
      System.err.println("Unknown filetype...");
      return new LinkedList<>();
    }

    return paths;
  }

  public static void mergeFiles(List<String> resPaths, String resPath) {
    List<JSONObject> commits = new LinkedList<>();
    List<JSONObject> annotations = new LinkedList<>();
    List<JSONArray> fix_and_introducers_pairs = new LinkedList<>();

    JSONParser parser = new JSONParser();
    for (String path : resPaths) {
      File dir = new File(path);
      if (dir.exists() && dir.isDirectory()) {
        try {
          JSONObject commit = (JSONObject) parser.parse(new FileReader(path + "/" + COMMITSPATH));
          commits.add(commit);
          JSONObject annotation =
              (JSONObject) parser.parse(new FileReader(path + "/" + ANNOTATIONPATH));
          annotations.add(annotation);
          JSONArray pair =
              (JSONArray) parser.parse(new FileReader(path + "/" + FIXINTRODUCERSPATH));
          fix_and_introducers_pairs.add(pair);
        } catch (IOException | ParseException e) {
          e.printStackTrace();
        }
      } else {
        System.err.println(path + " doesn't exist! Omitting..");
      }
    }

    /*
     * Merge all commits into a single object.
     */
    JSONObject commitObject = new JSONObject();
    for (JSONObject commit : commits) {
      for (Object c : commit.keySet()) {
        commitObject.put(c, commit.get(c));
      }
    }

    /*
     * Write all merged files into a single file.
     */
    try (FileWriter writer = new FileWriter(resPath + "/" + COMMITSPATH)) {
      writer.write(commitObject.toJSONString());
      writer.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }

    JSONObject annotationsObject = new JSONObject();
    for (JSONObject annotation : annotations) {
      for (Object a : annotation.keySet()) {
        annotationsObject.put(a, annotation.get(a));
      }
    }

    try (FileWriter writer = new FileWriter(resPath + "/" + ANNOTATIONPATH)) {
      writer.write(annotationsObject.toJSONString());
      writer.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }

    JSONArray pairObject = new JSONArray();
    for (JSONArray pair : fix_and_introducers_pairs) {
      pairObject.addAll(pair);
    }

    try (FileWriter writer = new FileWriter(resPath + "/" + FIXINTRODUCERSPATH)) {
      writer.write(pairObject.toJSONString());
      writer.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
