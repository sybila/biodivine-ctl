package com.github.sybila.biodivine

/*
val globalStart = System.nanoTime()

fun String.trimExtension(): String {
    return this.substring(0, this.lastIndexOf("."))
}

fun String.toLogLevel(): Level {
    return when (this) {
        "off" -> Level.OFF
        "error", "severe" -> Level.SEVERE
        "warning" -> Level.WARNING
        "info" -> Level.INFO
        "fine" -> Level.FINE
        "finer" -> Level.FINER
        "finest" -> Level.FINEST
        "all" -> Level.ALL
        else -> error("Unknown log level: $this")
    }
}

fun getJavaLocation(): String {
    return System.getProperties().getProperty("java.home") + File.separator + "bin" + File.separator +
            if (System.getProperty("os.name").startsWith("Win")) {
                "java.exe"
            } else "java"
}

fun <C: Colors<C>> Nodes<IDNode, C>.prettyPrint(model: Model, encoder: NodeEncoder): String {
    return this.entries.map {
        "\n${it.key.prettyPrint(model, encoder)} - ${it.value}"
    }.joinToString()
}

fun IDNode.prettyPrint(model: Model, encoder: NodeEncoder): String {
    val coordinates = encoder.decodeNode(this)
    return coordinates.mapIndexed { i, c ->
        val t = model.variables[i].thresholds
        "[${t[c]},${t[c+1]}]($c)"
    }.joinToString()+"{${this.id}}"
}

fun IDNode.legacyPrint(model: Model, encoder: NodeEncoder): String {
    val coordinates = encoder.decodeNode(this)
    return coordinates.mapIndexed { i, c ->
        val t = model.variables[i].thresholds
        "[${t[c]},${t[c+1]}]"
    }.joinToString()
}

fun <N: Node, C: Colors<C>> clearStats(modelChecker: ModelChecker<N, C>, smt: Boolean) {
    modelChecker.timeInGenerator = 0
    modelChecker.verificationTime = 0
    modelChecker.queueStats.clear()

    /*if (smt) {
        timeInSimplify = 0
        simplifyCacheHit = 0
        simplifyCalls = 0
        solverCalls = 0
        timeInSolver = 0
        solverCacheHit = 0
        timeInOrdering = 0
        solverCallsInOrdering = 0
    }*/
}

fun Long.toMillis() = (this / (1000 * 1000))

fun <N: Node, C: Colors<C>> printStats(logger: Logger, modelChecker: ModelChecker<N, C>) {
    logger.info("Time in generator: ${modelChecker.timeInGenerator.toMillis()}ms")
    logger.info("Verification time: ${modelChecker.verificationTime.toMillis()}ms")
    for ((name, value) in modelChecker.queueStats) {
        logger.info("$name: $value")
    }
    /*if (smt) {  //don't load z3 if not needed
        logger.info("Time in simplify: ${timeInSimplify.toMillis()}ms")
        logger.info("Time in solver: ${timeInSolver.toMillis()}ms")
        logger.info("Time in ordering: ${timeInOrdering.toMillis()}ms")
        logger.info("Solver calls in ordering: ${solverCallsInOrdering}")
        logger.info("Simplify cache hit: $simplifyCacheHit/$simplifyCalls")
        logger.info("Solver cache hit: $solverCacheHit vs. $solverCalls")
    }*/
}

fun <C: Colors<C>> processResults(
        id: Int,
        taskRoot: File,
        queryName: String,
        results: Nodes<IDNode, C>,
        checker: ModelChecker<IDNode, C>,
        encoder: NodeEncoder,
        model: Model,
        printConfig: Set<String>,
        logger: Logger
) {
    for (printType in printConfig) {
        when (printType) {
            c.size -> logger.info("Results size: ${results.entries.count()}")
            c.stats -> {
                printStats(logger, checker)
            }
            c.human -> {
                File(taskRoot, "$queryName.human.$id.txt").bufferedWriter().use {
                    for ((key, value) in results.entries) {
                        it.write("${key.prettyPrint(model, encoder)} - ${value}\n")
                    }
                }
            }
            c.legacy -> {
                val file = File(taskRoot, "$queryName.legacy.txt")
                if (!file.exists()) {
                    try {
                        file.createNewFile()
                    } catch (e: Exception) {} //Ok
                }
                var simplify: Tactic? = null
                val rString = results.entries.map {
                    val (node, colors) = it
                    if (colors is SMTColors) {
                        //stay lazy!
                        if (simplify == null) simplify = z3.mkTactic("ctx-solver-simplify")
                        "${node.legacyPrint(model, encoder)} ${simplify!!}"
                    } else {
                        "${node.legacyPrint(model, encoder)} $colors"
                    }
                }.toMutableList()
                rString.add("$id Total duration: ${(System.nanoTime() - globalStart).toMillis()}ms")
                if (simplify != null) {
                    //rString.add("$id Solver used x-times: ${solverCalls + solverCallsInOrdering}")
                    //rString.add("$id Time in solver: ${(timeInSolver + timeInOrdering).toMillis()}ms")
                }
                Files.write(Paths.get(file.toURI()), rString, Charset.defaultCharset(), StandardOpenOption.APPEND)
            }
            else -> error("Unknown print type: $printType")
        }
    }
}

fun guardedRemoteProcess(
        host: String,
        args: Array<String>,
        vars: Array<String>?,
        logger: Logger, timeout: Int = -1, shouldDie: () -> Boolean): Int {
    val command = "cd ${System.getProperty("user.dir")}; " +    //move to the right directory
            (vars?.map { " export $it; " }?.joinToString(separator = " ") ?: "") + //add environmental variables
            args.map { "\"$it\"" }.joinToString(separator = " ")    //escape arguments
    //println("Execute command: $command")
    val sshProcess = Runtime.getRuntime().exec(arrayOf("ssh", host, command))
    val timeoutThread = if (timeout > 0) {
        thread {
            val start = System.currentTimeMillis()
            try {
                while (System.currentTimeMillis() - start < timeout * 1000L || !shouldDie()) {
                    Thread.sleep(1000L)
                }
                try {
                    sshProcess.exitValue()
                    //Process terminated fine
                } catch (e: IllegalThreadStateException) {
                    //Process is still alive!
                    sshProcess.destroy()
                    if (shouldDie()) {
                        logger.info("Task was killed due to an error in other process")
                    } else {
                        logger.info("Task was killed after exceeding timeout ${timeout}s")
                    }
                }
            } catch (e: InterruptedException) {
                //it's ok
            }
        }
    } else null
    val stdReader = thread {
        sshProcess.inputStream.bufferedReader().use { std ->
            var line = std.readLine()
            while (line != null) {
                println(line)   //standard output is not logged, only printed
                line = std.readLine()
            }
        }
    }
    val errReader = thread {
        sshProcess.errorStream.bufferedReader().use { err ->
            var line = err.readLine()
            while (line != null) {
                logger.severe(line)
                line = err.readLine()
            }
        }
    }
    sshProcess.waitFor()
    timeoutThread?.interrupt()
    timeoutThread?.join()
    stdReader.join()
    errReader.join()
    return sshProcess.exitValue()
}

fun guardedProcess(args: Array<String>, vars: Array<String>?, logger: Logger, timeout: Int = -1): Int {
    val process = Runtime.getRuntime().exec(args, vars)
    val timeoutThread = if (timeout > 0) {
        thread {
            try {
                Thread.sleep(timeout * 1000L)
                try {
                    process.exitValue()
                    //Process terminated fine
                } catch (e: IllegalThreadStateException) {
                    //Process is still alive!
                    process.destroy()
                    logger.info("Task was killed after exceeding timeout ${timeout}s")
                }
            } catch (e: InterruptedException) {
                //it's ok
            }
        }
    } else null
    val stdReader = thread {
        process.inputStream.bufferedReader().use { std ->
            var line = std.readLine()
            while (line != null) {
                println(line)   //standard output is not logged, only printed
                line = std.readLine()
            }
        }
    }
    val errReader = thread {
        process.errorStream.bufferedReader().use { err ->
            var line = err.readLine()
            while (line != null) {
                logger.severe(line)
                line = err.readLine()
            }
        }
    }
    process.waitFor()
    timeoutThread?.interrupt()
    timeoutThread?.join()
    stdReader.join()
    errReader.join()
    return process.exitValue()
}*/