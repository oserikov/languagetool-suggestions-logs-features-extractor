# languagetool-suggestions-logs-features-extractor
The tool to extract features from the suggestions choices data collected by the languagetool.

* Configuration possible via `features-extractor.properties`, an example provided.
* Run gradle FatJar task `gradle FatJar` to build from sources or get the released version. 

## ngrams and word2vec directories

**These directories are not required for tool to work.**

The output of the `tree` command for the ngrams directory I was working with:
```bash
oleg@DESKTOP-UFQCH1N:~$ tree -d -L 2 /mnt/c/Users/olegs/Documents/ngram/
/mnt/c/Users/olegs/Documents/ngram/
└── en
    ├── 1grams
    ├── 2grams
    └── 3grams

4 directories
oleg@DESKTOP-UFQCH1N:~$
```

The output of the `tree` command for the word2vec directory I was working with:
```bash
oleg@DESKTOP-UFQCH1N:~$ tree -d -L 2 /mnt/c/Users/olegs/Documents/word2vec/
/mnt/c/Users/olegs/Documents/word2vec/
└── en
    └── neuralnetwork

2 directories
oleg@DESKTOP-UFQCH1N:~$
```
Both directories were downloaded from [the LanguageTool download page](https://languagetool.org/download/).