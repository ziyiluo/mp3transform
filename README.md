Java MP3 Decoder / Player

This project is based on JavaZOOM (http://www.javazoom.net/javalayer/javalayer.html).
It is licensed under LGPL (see LICENSE.txt).

The credit for the mp3 decodor goes to the original author. The code base can be found at [mp3transform](https://code.google.com/archive/p/mp3transform/source/default/source).

I modernized the code base, simplified the code by adopting features in Java 8 and Gradle-5.0 and completely refactor the GUI.

The reason I folked the repository is to find a cross-platform MP3 player that supports fade-in and fade-out.
I did not expect it to be so hard but eventually I gave up searching and decided to implement the logic by my own based on this project ;)

Use Gradle to build the jar:
```
./gradlew build
```

Then run `play.sh` to initiate the GUI.