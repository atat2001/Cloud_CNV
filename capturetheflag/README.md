## Capture the Flag

This project contains Capture the Flag functionality.

### How to build

1. Make sure your `JAVA_HOME` environment variable is set to Java 11+ distribution
2. Run `mvn clean package`

### How to run locally

To run Capture the Flag locally in CLI, execute this command:

```
java -cp target/capturetheflag-1.0.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.capturetheflag.CaptureTheFlagHandler <grid-size> <num-blue-agents> <num-red-agents> <flag-placement-type>
```
