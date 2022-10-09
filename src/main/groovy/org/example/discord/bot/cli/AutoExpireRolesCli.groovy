package org.example.discord.bot.cli

import groovy.cli.picocli.CliBuilder
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Command Line Interface
 */
final class AutoExpireRolesCli {

    private static Logger LOG = LoggerFactory.getLogger(AutoExpireRolesCli)

    private AutoExpireRolesCli() {
        throw new RuntimeException('helper')
    }

    static def parseArgs(String[] args) {
        def cli = new CliBuilder(usage: 'java -jar AutoExpireRoles<version>.jar')

        cli.d(longOpt:'config-directory', args:1, argName:'directory', 'Path to the directory with the config.json')

        if (args.contains('-h') || args.contains('--help')) {
            cli.usage()
            return null
        }
        return cli.parse(args)
    }

    private static final String CONFIG_EXAMPLE = '{\n' +
            '  "token" : "", \n' +
            '  "channelCommandWhiteList" : ["bot-fun"], \n' +
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
            '    ],\n' +
            '    "TestRole5": [\n' +
            '      3,\n' +
            '      "HALF_DAYS"\n' +
            '    ] \n' +
            '  }\n' +
            '}'

    static def readConfig(String directoryPath, Map<String, Object> defaultDurationsMap) {
        def directory = new File(directoryPath?directoryPath:'.')
        def directoryAbsolutePath = directory.absolutePath
        if (!directory.exists()) {
            LOG.error("config-directory $directoryAbsolutePath does not exists")
            return [null, null, null]
        }
        if (!directory.canRead()) {
            LOG.error("can't read in config-directory $directoryAbsolutePath")
            return [null, null, null]
        }
        def configFile = new File("$directoryAbsolutePath/config.json")
        if (!configFile.exists()) {
            if (directory.canWrite()) {
                configFile.withWriter(StandardCharsets.UTF_8 as String, { bw ->
                    bw.println(CONFIG_EXAMPLE)
                })
            } else {
                LOG.error("can't create config.json $directoryAbsolutePath/config.json directory ist not writeable")
                return [null, null, null]
            }
        }
        if (!configFile.canRead()) {
            LOG.error("can't read config.json $directoryAbsolutePath/config.json")
            return [null, null, null]
        }
        configFile.withReader(StandardCharsets.UTF_8 as String, {br ->
            def lines = br.readLines()
            if (!lines) {
                LOG.error("config file $directoryAbsolutePath/config.json is empty")
                LOG.error("you can use the following as example")
                LOG.error(CONFIG_EXAMPLE)
                return [null, null, null]
            }
            def configJson = new JsonSlurper().parseText(lines.join('\r\n'))
            configJson.defaultDurations.each { mapEntry ->
                defaultDurationsMap.put(mapEntry.key, mapEntry.value)
            }

            return [configJson.taskLoop.initDelay, configJson.taskLoop.period,
                    TimeUnit."$configJson.taskLoop.timeUnit", configJson.token,
                    configJson.channelCommandWhiteList]
        })
    }

    static boolean readExpireState(String directoryPath, Map<String, Object> expireStateMap) {
        def directory = new File(directoryPath?directoryPath:'.')
        def directoryAbsolutePath = directory.absolutePath
        if (!directory.exists()) {
            LOG.error("config-directory $directoryAbsolutePath does not exists")
            return false
        }
        if (!directory.canRead()) {
            LOG.error("can't read in config-directory $directoryAbsolutePath")
            return false
        }
        def stateFile = new File("$directoryAbsolutePath/expires-state.json")
        if (!stateFile.exists()) {
            LOG.info("expires-state $directoryAbsolutePath/expires-state.json file does not exist")
            return false
        }
        if (!stateFile.canRead()) {
            LOG.error("can't read expires-state $directoryAbsolutePath/expires-state.json")
            return false
        }
        stateFile.withReader(StandardCharsets.UTF_8 as String, { br ->
            def lines = br.readLines()
            if (!lines) {
                LOG.error("expires-state file $directoryAbsolutePath/expires-state.json is empty")
                return false
            }
            def stateJson = new JsonSlurper().parseText(lines.join('\r\n'))
            stateJson.each { mapEntry ->
                expireStateMap.put(mapEntry.key, Instant.parse(mapEntry.value))
            }
            return true
        })
    }

    static void writeExpireState(String directoryPath, Map<String, Object> expireStateMap) {
        def directory = new File(directoryPath?directoryPath:'.')
        def directoryAbsolutePath = directory.absolutePath
        if (!directory.exists()) {
            LOG.error("config-directory $directoryAbsolutePath does not exists")
            return
        }
        if (!directory.canRead()) {
            LOG.error("can't read in config-directory $directoryAbsolutePath")
            return
        }
        def stateFile = new File("$directoryAbsolutePath/expires-state.json")
        if (!directory.canWrite()) {
            LOG.error("can't create expires-state $directoryAbsolutePath/expires-state.json directory ist not writeable")
            return
        }
        if (stateFile.exists()) {
            try {
                def currentFile = stateFile.toPath()
                Files.move(currentFile, currentFile.resolveSibling("expires-state.json.bak"),
                        StandardCopyOption.REPLACE_EXISTING)
            } catch (IOException e) {
                LOG.warn("$e.message: could not backup state file $stateFile.absolutePath", e)
            }
        }
        def string = toJsonFormat(expireStateMap)
        stateFile.withWriter(StandardCharsets.UTF_8 as String, {bw ->
            bw.append(string)
        })
    }

    private static def toJsonFormat(Map<String, Object> expireStateMap) {
        if (!expireStateMap) {
            return '{}'
        }
        StringBuilder sb = new StringBuilder()
        sb.append('{')
        def entries = expireStateMap.entrySet()
        def max = entries.size() - 2
        int i = 0
        for (; i <= max; i++) {
            sb.append("\"${entries[i].key}\" : \"${entries[i].value}\",${System.lineSeparator()}")
        }
        sb.append("\"${entries[i].key}\" : \"${entries[i].value}\"")

        sb.append('}')
        return sb.toString()
    }
}
