## Game of Life

This project contains Conway's Game of Life functionality.

### How to build

1. Make sure your `JAVA_HOME` environment variable is set to Java 11+ distribution
2. Run `mvn clean package`

### How to run locally

To run Game of Life locally in CLI, execute this command:

```
java -cp target/gameoflife-1.0.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.gameoflife.GameOfLifeHandler <map-filename> <iterations>
```

The `map-filename` argument should be the filename of one of the JSON files from `src/main/resources`.
