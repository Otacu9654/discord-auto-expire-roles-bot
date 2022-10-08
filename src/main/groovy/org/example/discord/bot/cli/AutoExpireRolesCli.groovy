package org.example.discord.bot.cli

import groovy.cli.picocli.CliBuilder
import groovy.json.JsonSlurper

import java.util.concurrent.TimeUnit

/**
 * Command Line Interface
 */
final class AutoExpireRolesCli {

    private AutoExpireRolesCli() {
        throw new AssertionError('helper')
    }

    static def parseArgs(String[] args) {
        def cli = new CliBuilder(usage: 'java -jar AutoExpireRoles<version>.jar -t <token>')

        cli.d(longOpt:'config-directory', args:1, argName:'directory', 'Path to the directory with the config.json')
        cli.t(longOpt:'token', args:1, argName:'token', required:true, 'Set the bot token to work with')

        if (args.contains('-h') || args.contains('--help')) {
            cli.usage()
            return null
        }
        return cli.parse(args)
    }

    private static final String CONFIG_EXAMPLE = '{\n' +
            '  "taskLoop" : {\n' +
            '    "initDelay": 0,\n' +
            '    "period": 5,\n' +
            '    "timeUnit": "MINUTES"\n' +
            '  },\n' +
            '  "defaultDurations": {\n' +
            '    "TestRole1": [\n' +
            '      1,\n' +
            '      "MINUTES"\n' +
            '    ],\n' +
            '    "TestRole2": [\n' +
            '      5,\n' +
            '      "MINUTES"\n' +
            '    ],\n' +
            '    "TestRole3": [\n' +
            '      2,\n' +
            '      "DAYS"\n' +
            '    ],\n' +
            '    "TestRole4": [\n' +
            '      23446,\n' +
            '      "SECONDS"\n' +
            '    ]\n' +
            '  }\n' +
            '}'

    static def readConfig(String directoryPath, Map<String, Object> defaultDurationsMap) {
        def directory = new File(directoryPath?directoryPath:'.')
        def directoryAbsolutePath = directory.absolutePath
        if (!directory.exists()) {
            System.err.println("config-directory $directoryAbsolutePath does not exists")
            return [null, null, null]
        }
        if (!directory.canRead()) {
            System.err.println("can't read in config-directory $directoryAbsolutePath")
            return [null, null, null]
        }
        def configFile = new File("$directoryAbsolutePath/config.json")
        if (!configFile.exists()) {
            if (directory.canWrite()) {
                configFile.withWriter { bw ->
                    bw.println(CONFIG_EXAMPLE)
                }
            } else {
                System.err.println("can't create config.json $directoryAbsolutePath/config.json directory ist not writeable")
                return [null, null, null]
            }
        }
        if (!configFile.canRead()) {
            System.err.println("can't read config.json $directoryAbsolutePath/config.json")
            return [null, null, null]
        }
        configFile.withReader {br ->
            def lines = br.readLines()
            if (!lines) {
                System.err.println("config file $directoryAbsolutePath/config.json is empty")
                System.err.println("you can use the following as example")
                System.err.println(CONFIG_EXAMPLE)
                return [null, null, null]
            }
            def configJson = new JsonSlurper().parseText(lines.join('\r\n'))
            configJson.defaultDurations.each { mapEntry ->
                defaultDurationsMap.put(mapEntry.key, mapEntry.value)
            }

            return [configJson.taskLoop.initDelay, configJson.taskLoop.period, TimeUnit."$configJson.taskLoop.timeUnit"]
        }
    }
}
