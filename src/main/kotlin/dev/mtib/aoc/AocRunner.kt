package dev.mtib.aoc

import arrow.core.getOrElse
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import dev.mtib.aoc.benchmark.BenchmarkProgressPlotter
import dev.mtib.aoc.benchmark.BenchmarkWindowPlotter
import dev.mtib.aoc.day.AocDay
import dev.mtib.aoc.day.PuzzleExecutor
import dev.mtib.aoc.util.AocLogger
import dev.mtib.aoc.util.Day
import dev.mtib.aoc.util.PuzzleIdentity
import dev.mtib.aoc.util.Results
import dev.mtib.aoc.util.Results.BenchmarkResult
import dev.mtib.aoc.util.Results.RunResult
import dev.mtib.aoc.util.Year
import dev.mtib.aoc.util.ciTimeout
import dev.mtib.aoc.util.styleResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private val logger = AocLogger.new {}
private val cleanupScope = CoroutineScope(Dispatchers.IO)

const val BENCHMARK_WINDOW = 100
const val BENCHMARK_TIMEOUT_SECONDS = 10
suspend fun main(args: Array<String>) {
    logger.log(AocLogger.Main) { "starting advent of code" }
    AocDay.load()

    val days = buildSet<Day> {
        val years = AocDay.years()
        val mostRecentYear = years.maxByOrNull { it.toInt() } ?: run {
            logger.error { "no years found" }
            return@main
        }
        val yearIdentifiedRegex = Regex("""(\d{4}):(.+)""")
        fun List<AocDay>.days() = map { it.identity }

        args.forEach { arg ->
            val day = arg.toIntOrNull()
            if (day != null) {
                add(mostRecentYear.Day(day))
                return@forEach
            }
            when (arg) {
                "all" -> addAll(AocDay.getAll().days())
                "latest" -> AocDay.getAll(mostRecentYear).days().maxOrNull()?.also { add(it) }
                else -> {
                    if (arg.matches(yearIdentifiedRegex)) {
                        val (_, yearString, selection) = yearIdentifiedRegex.matchEntire(arg)!!.groupValues
                        when (selection) {
                            "all" -> addAll(AocDay.getAll(Year(yearString)).days())
                            "latest" -> AocDay.getAll(Year(yearString)).days().maxOrNull()?.also { add(it) }
                            else -> {
                                val day = selection.toIntOrNull()
                                if (day != null) {
                                    add(Year(yearString).Day(day))
                                } else {
                                    logger.error { "unknown argument: $arg" }
                                    return@main
                                }
                            }
                        }
                    } else {
                        logger.error { "unknown argument: $arg" }
                        return@main
                    }
                }
            }
        }
    }

    val sortedDays = days.toList().sorted()

    if (days.isEmpty()) {
        logger.error { "no day provided" }
        return
    } else {
        logger.log(AocLogger.Main) {
            "running day${if (days.size > 1) "s" else ""}: ${
                sortedDays.joinToString(", ") {
                    "${it.toInt()}.${it.year}."
                }
            }"
        }
    }

    days.toList().sorted().forEach { day ->
        runDay(day)
    }

    Results.collect()

    cleanupScope.coroutineContext[Job]?.also {
        logger.log(AocLogger.Main) { "post-processing" }
        it.start()
        joinAll(*it.children.toList().toTypedArray())
    }

    logger.log(AocLogger.Main) { "done" }
}

suspend fun runDay(day: Day) {
    val aocDay = AocDay.get(day).getOrElse {
        logger.error(year = day.year, day = day.toInt()) { "not found" }
        return
    }

    mapOf(
        ::runResults to "solving puzzles",
        ::benchmark to "running benchmarks",
    ).forEach { (func, message) ->
        aocDay.benchmarking = func == ::benchmark
        logger.log(day) { message }
        mapOf(
            1 to PuzzleExecutor::part1,
            2 to PuzzleExecutor::part2,
        ).forEach { (part, block) ->
            aocDay.partMode = part
            val puzzle = day.PuzzleIdentity(part)
            ciTimeout(puzzle, 10.seconds) {
                withContext(aocDay.pool) {
                    func(day.PuzzleIdentity(part)) {
                        block(aocDay)
                    }
                }
            }
            aocDay.partMode = null
        }
    }

    cleanupScope.launch(start = CoroutineStart.LAZY) {
        BenchmarkProgressPlotter(day).plot()
    }
}

suspend fun runResults(puzzle: PuzzleIdentity, block: suspend () -> String) {
    try {
        val result = measureTimedValue { block() }
        val knownResult = Results.findVerifiedOrNull(puzzle)

        val styledResult = styleResult(result.value)
        if (knownResult != null) {
            if (knownResult.result != result.value) {
                logger.error(
                    e = null,
                    puzzle = puzzle,
                ) { "found conflicting solution $styledResult (expected ${styleResult(knownResult.result ?: "???")}) in ${result.duration}" }
            } else {
                logger.log(
                    puzzle,
                ) { "found solution $styledResult in ${result.duration} " + TextColors.brightGreen("(verified)") }
            }
        } else {
            logger.log(puzzle) { "found solution $styledResult in ${result.duration}" }
        }
        Results.send(RunResult(result = result.value, puzzle = puzzle))
    } catch (e: NotImplementedError) {
        logger.error(e = null, puzzle) { "not implemented" }
    } catch (e: AocDay.DayNotReleasedException) {
        logger.error(e = null, puzzle) { "not released yet, releasing in ${e.waitDuration}" }
    }
}

suspend fun benchmark(
    puzzle: PuzzleIdentity,
    block: suspend () -> String,
) {
    try {
        val durations = mutableListOf<Duration>()
        val timeout = BENCHMARK_TIMEOUT_SECONDS.seconds
        val startTime = System.currentTimeMillis()
        val benchmarkDuration = measureTime {
            while (System.currentTimeMillis() - startTime < timeout.inWholeMilliseconds && (durations.size < BENCHMARK_WINDOW * 20 || System.currentTimeMillis() - startTime < 1.seconds.inWholeMilliseconds)) {
                measureTime { block() }.also {
                    durations.add(it)
                }
                yield()
            }
        }
        logger.log(puzzle) { "ran ${durations.size} iterations in $benchmarkDuration" }
        val average = durations
            .takeLast(BENCHMARK_WINDOW)
            .let { tail ->
                tail.sumOf { sample -> sample.inWholeMicroseconds.toBigDecimal() } / tail.size.toBigDecimal()
            }
            .toDouble()
            .microseconds
        Results.send(BenchmarkResult(average, durations.size.toLong(), puzzle))
        cleanupScope.launch(start = CoroutineStart.LAZY) {
            BenchmarkWindowPlotter(puzzle, BENCHMARK_WINDOW, durations).plot()
        }
        val styledCommand =
            (TextStyles.italic + TextColors.blue)("/aoc_benchmark day:${puzzle.day} part:${puzzle.part} time_milliseconds:${average.inWholeMicroseconds / 1000.0}")
        logger.log(
            puzzle
        ) { "averaged at ${TextColors.brightWhite(average.toString())}, report with: $styledCommand" }
    } catch (e: NotImplementedError) {
        logger.error(e = null, puzzle) { "not implemented" }
    } catch (e: AocDay.DayNotReleasedException) {
        logger.error(e = null, puzzle) { "not released yet, releasing in ${e.waitDuration}" }
    }
}