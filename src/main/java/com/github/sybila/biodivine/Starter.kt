package com.github.sybila.biodivine

import java.io.File
import java.util.logging.Level
import java.util.logging.Logger


val rootPackage = "com.github.sybila"

/**
 * This is "The Main" main. The purpose of this is to load the config file, parse it,
 * prepare experiment environment and then call the model checker, or otherwise initiate the
 * computation (in case of mpj, we will probably have to start a new process)
 **/

fun main(args: Array<String>) {
    if (args.size != 1) {
        error("Expecting one argument that is a readable configuration file. ${args.size} arguments given.")
    }
    val configFile = File(args[0])
    if (!configFile.isFile) {
        error("Expecting one argument that is a readable configuration file. ${args[0]} is not a file.")
    }
    if (!configFile.canRead()) {
        error("Expecting one argument that is a readable configuration file. ${args[0]} is not readable.")
    }

    // Setup Experiment

    val yamlConfig = configFile.toYamlMap()
    val root = setupExperiment(configFile.name.trimExtension(), yamlConfig)

    //copy config file into experiment root as backup
    configFile.copyTo(File(root, configFile.name), overwrite = true)

    val logger = Logger.getLogger(rootPackage)
    logger.info("Experiment is prepared. Executing tasks...")

    // Execute tasks

    val tasks = yamlConfig.getMapList("tasks")

    var success = 0
    val globalLogLevel = yamlConfig.getLogLevel("consoleLogLevel", Level.INFO)
    when (tasks.size) {
        0 -> logger.warning("No tasks specified!")
        1 -> if (executeTask(tasks.first(), null, root, globalLogLevel)) {
            success += 1
        }
        else -> tasks.forEachIndexed { i, task ->
            if (executeTask(task, "task-$i", root, globalLogLevel)) {
                success += 1
            }
        }
    }

    logger.info("$success/${tasks.size} tasks finished successfully. Exiting.")
}