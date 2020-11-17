# A Benchmark Starter for Scala Project

## Example 1: The Magic `-opt:l:inline` and `-opt-inline-from`

see https://github.com/scala/scala-dev/issues/81

```
sbt clean 'project benchmark' 'set scalaVersion := "2.11.12"' 'jmh:run -f 1 -i 20 -wi 20 -t 1 Richards'

sbt clean 'project benchmark' 'set scalaVersion := "2.12.12"' 'jmh:run -f 1 -i 20 -wi 20 -t 1 Richards'

sbt clean 'project benchmark' 'set scalaVersion := "2.12.12"' 'set scalacOptions in ThisBuild ++= Seq("-opt:l:inline", "-opt-inline-from:**")' 'jmh:run -f 1 -i 20 -wi 20 -t 1 Richards'

sbt clean 'project benchmark' 'set scalaVersion := "2.12.12"' 'set scalacOptions in ThisBuild ++= Seq("-opt:l:method", "-opt:-closure-invocations")' 'jmh:run -f 1 -i 20 -wi 20 -t 1 Richards'
```

## Example 2: Flame Graph
```
## Prepare the submodule
git submodule init
git submodule update

## Prepare the jfr-flame-graph
cd thirdparty/jfr-flame-graph
./gradlew installDist
cp build/install/jfr-flame-graph/bin/jfr-flame-graph build/install/jfr-flame-graph/bin/flamegraph-output.sh
```

```
bin/flame_bench.sh Richards
```
