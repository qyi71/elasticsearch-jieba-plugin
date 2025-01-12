package com.huaban.analysis.jieba;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;


public class WordDictionary {
    private static final String MAIN_DICT = "/dict.txt";
    private static WordDictionary singleton;
    private static String USER_DICT_SUFFIX = ".dict";

    public final Map<String, Double> freqs = new HashMap<String, Double>();
    public final Set<String> loadedPath = new HashSet<String>();
    private Double minFreq = Double.MAX_VALUE;
    private Double total = 0.0;
    private DictSegment _dict;
    // 用户dict备份目录
    private static final String USER_BACKUP_PATH = "backup";

    private WordDictionary() {
        this.loadDict();
    }

    public static WordDictionary getInstance() {
        if (singleton == null) {
            synchronized (WordDictionary.class) {
                if (singleton == null) {
                    singleton = new WordDictionary();
                    return singleton;
                }
            }
        }
        return singleton;
    }

    public String getUserBackupPath() {
        return USER_BACKUP_PATH;
    }

    /**
     * for ES to initialize the user dictionary.
     * You can call this method periodly for dynamic load new word
     *
     * @param configFile
     */
    public void init(File configFile) {
        String path = configFile.getAbsolutePath();
        System.out.println("initialize user dictionary:" + path);
        synchronized (WordDictionary.class) {
            for (File userDict : configFile.listFiles()) {
                if (loadedPath.contains(userDict.getAbsolutePath())) {
                    System.out.println("already loaded: " + userDict.getAbsolutePath());
                    continue;
                }
                if (userDict.getPath().endsWith(USER_DICT_SUFFIX)) {
                    Path targetPath = null;
                    try {
                        File file = new File(singleton.getUserBackupPath() + "/" + userDict.getName());
                        targetPath = Paths.get(file.getAbsolutePath());

//                        targetPath = Paths.get(singleton.getClass().getResourceAsStream(singleton.getUserBackupPath() + "/" + userDict.getName()).toURI());
                    } catch (Exception e) {
                        System.err.println(" copy user dict backup path fail: " + userDict.getAbsolutePath());
                        continue;
                    }

                    FileUtil.copyFile(new File(userDict.getAbsolutePath()).toPath(), targetPath);
                    singleton.loadUserDict(userDict);
                    loadedPath.add(userDict.getAbsolutePath());
                }
            }
            removeDict(configFile);
        }
    }

    public void removeDict(File configFile) {
        String path = configFile.getAbsolutePath();
        System.out.println("remove user dictionary:" + path);
        synchronized (WordDictionary.class) {
            HashSet<String> files = new HashSet<>();
            for (File file : configFile.listFiles()) {
                files.add(file.getAbsolutePath());
            }

            Set<String> unloadFiles = loadedPath.stream().filter(e -> !files.contains(e)).collect(Collectors.toSet());
            for (String unloadFile : unloadFiles) {
                if (unloadFile.endsWith(USER_DICT_SUFFIX)) {
                    Path fileName = Paths.get(unloadFile).getFileName();
                    singleton.unloadUserDict(new File(USER_BACKUP_PATH + "/" + fileName), Charset.forName("UTF-8"));
                }
            }

        }
    }


    public void loadDict() {
        _dict = new DictSegment((char) 0);
        InputStream is = this.getClass().getResourceAsStream(MAIN_DICT);
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

            long s = System.currentTimeMillis();
            while (br.ready()) {
                String line = br.readLine();
                String[] tokens = line.split("[\t ]+");

                if (tokens.length < 2) continue;

                String word = tokens[0];
                double freq = Double.valueOf(tokens[1]);
                total += freq;
                word = addWord(word);
                freqs.put(word, freq);
            }
            // normalize
            for (Entry<String, Double> entry : freqs.entrySet()) {
                entry.setValue((Math.log(entry.getValue() / total)));
                minFreq = Math.min(entry.getValue(), minFreq);
            }
            System.out.println(String.format("main dict load finished, time elapsed %d ms", System.currentTimeMillis() - s));
        } catch (IOException e) {
            System.err.println(String.format("%s load failure!", MAIN_DICT));
        } finally {
            try {
                if (null != is) is.close();
            } catch (IOException e) {
                System.err.println(String.format("%s close failure!", MAIN_DICT));
            }
        }
    }


    private String addWord(String word) {
        if (null != word && !"".equals(word.trim())) {
            String key = word.trim().toLowerCase();
            _dict.fillSegment(key.toCharArray());
            return key;
        } else return null;
    }


    public void loadUserDict(File userDict) {
        loadUserDict(userDict, Charset.forName("UTF-8"));
    }


    public void loadUserDict(File userDict, Charset charset) {
        InputStream is;
        try {
            is = new FileInputStream(userDict);
        } catch (FileNotFoundException e) {
            System.err.println(String.format("could not find %s", userDict.getAbsolutePath()));
            return;
        }
        try {
            @SuppressWarnings("resource") BufferedReader br = new BufferedReader(new InputStreamReader(is, charset));
            long s = System.currentTimeMillis();
            int count = 0;
            while (br.ready()) {
                String line = br.readLine();
                String[] tokens = line.split("[\t ]+");

                if (tokens.length < 2) continue;

                String word = tokens[0];
                double freq = Double.valueOf(tokens[1]);
                word = addWord(word);
                freqs.put(word, Math.log(freq / total));
                count++;
            }
            System.out.println(String.format("user dict %s load finished, tot words:%d, time elapsed:%dms", userDict.getAbsolutePath(), count, System.currentTimeMillis() - s));
        } catch (IOException e) {
            System.err.println(String.format("%s: load user dict failure!", userDict.getAbsolutePath()));
        } finally {
            try {
                if (null != is) is.close();
            } catch (IOException e) {
                System.err.println(String.format("%s close failure!", userDict.getAbsolutePath()));
            }
        }
    }

    public void unloadUserDict(File userDict) {
        unloadUserDict(userDict, Charset.forName("UTF-8"));
    }


    public void unloadUserDict(File userDict, Charset charset) {
        InputStream is;
        try {
            is = new FileInputStream(userDict);
        } catch (FileNotFoundException e) {
            System.err.println(String.format("could not find %s", userDict.getAbsolutePath()));
            return;
        }
        unloadUserDict(is, charset, userDict.getAbsolutePath());

    }

    public void unloadUserDict(InputStream is, Charset charset, String path) {
        try {
            @SuppressWarnings("resource") BufferedReader br = new BufferedReader(new InputStreamReader(is, charset));
            long s = System.currentTimeMillis();
            int count = 0;
            while (br.ready()) {
                String line = br.readLine();
                String[] tokens = line.split("[\t ]+");

                if (tokens.length < 2) continue;

                String word = tokens[0];
                _dict.unload(word.toCharArray());
                count++;
            }
            System.out.println(String.format("user dict %s unload finished, tot words:%d, time elapsed:%dms", path, count, System.currentTimeMillis() - s));
        } catch (IOException e) {
            System.err.println(String.format("%s: unload user dict failure!", path));
        } finally {
            try {
                if (null != is) is.close();
            } catch (IOException e) {
                System.err.println(String.format("%s close failure!", path));
            }
        }
    }

    public void delWord(String word) {
        if (null != word && !"".equals(word.trim())) {
            String key = word.trim().toLowerCase(Locale.getDefault());
            this._dict.unload(key.toCharArray());
        }

    }

    public DictSegment getTrie() {
        return this._dict;
    }


    public boolean containsWord(String word) {
        return freqs.containsKey(word);
    }


    public Double getFreq(String key) {
        if (containsWord(key)) return freqs.get(key);
        else return minFreq;
    }
}
