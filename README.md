# Advent of Code (Kotlin)

Contains years:

- [2023](https://github.com/mtib/aoc23) (originally done stand-alone, now integrated here)
- 2024

## How to run

```bash
./gradlew fatJar
ln -s build/libs/aoc-kt-0.24.0-all.jar /build/libs/aoc-kt.jar
SESSION="<your session cookie>" java \
  -Dorg.slf4j.simpleLogger.log.dev.mtib.aoc24.AocRunner=INFO \
  -Dorg.slf4j.simpleLogger.showThreadName=false \
  -Djava.awt.headless=true \
  -jar /build/libs/aoc-kt.jar all
```

The jar file is also available [here](https://aoc24.fra1.cdn.digitaloceanspaces.com/aoc-kt-latest-all.jar).

Alternatively, to run a specific day or set of days:

```bash
# run day 5 of most recent year
java -jar /build/libs/aoc-kt.jar 5

# run day 1, 3, 24 of most recent year
java -jar /build/libs/aoc-kt.jar 1 3 24

# run all days
java -jar /build/libs/aoc-kt.jar all

# run the latest day available
java -jar /build/libs/aoc-kt.jar latest

# run day 1 of 2024
java -jar /build/libs/aoc-kt.jar 2024:1

# run all days of of 2024
java -jar /build/libs/aoc-kt.jar 2024:all
```

## How to use

1. Create a file like [Day1.kt](src/main/kotlin/dev/mtib/aoc/aoc24/days/Day1.kt)
   extending [AocDay](src/main/kotlin/dev/mtib/aoc/day/AocDay.kt).
2. Overwrite the `part1` and `part2` methods with your implementation, returning the solution as a String.
3. Run the application with the command above, replacing `<day>` with the day number, or just running with `latest`, to
   run the most recent day.

If provided with the `SESSION` in your environment variables (matching the cookie value from the AoC website), the
application will automatically fetch the input for the given day.
This will run the parts of the day you implemented and print the results.
Then run 10s worth of benchmarks for both parts and print their results.
Inside `src/main/resources` you will find the input files for each day you've run, as well as plots for the benchmarks,
as well as a results.json file with all results ever recorded.
(You may want to mark a particular run as `"verified": true` in the results file to detect regressions (different
results) in future runs. Or use the [VerifyTool](src/main/kotlin/dev/mtib/aoc/VerifyTool.kt) CLI to do this for you.)

## Tools & technologies

### Foundation

- Kotlin
    - Kotlin.coroutines
    - Kotlin.serialisation
- arrow-kt: Functional programming and stdlib extensions
- lets-plot: Data visualisation
- GraphJ: Graph representations and algorithms
- oj!Algo: MIP solver and fast math

### Build tools

- Gradle

### Misc

- SLF4J
- KotlinLogging
- JUnit 5
- Kotest

## Results

Benchmark results run on GitHub Actions can be
found [here](https://aoc24.fra1.cdn.digitaloceanspaces.com/results_cleaned.json). As well as plots for the performance
of each part:

![Day 1](https://aoc24.fra1.cdn.digitaloceanspaces.com/benchmark_2024_01_1.png)

(These are updated after each push to the main branch. Available
at `https://aoc24.fra1.cdn.digitaloceanspaces.com/benchmark_<year>_<day>_<part>.png`, with `<day>` and `<part>` being
the
zero-padded day and single-digit part number respectively.)
