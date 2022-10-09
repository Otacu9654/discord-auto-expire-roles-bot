package org.example.discord.bot.cli

import spock.lang.Shared
import spock.lang.Specification

import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.TimeUnit

class AutoExpireRolesCliTest extends Specification {

    @Shared
    def origSystemErr = System.err
    
    def cleanup() {
        System.err = origSystemErr
    }

    def "TestConstructorPrivate"() {
        when:
            new AutoExpireRolesCli()
        then:
            def error = thrown(AssertionError)
            assert error.message == 'helper'
    }

    def "ParseArgs"() {
        when:
            def sw = new ByteArrayOutputStream()
            def err = new PrintStream(sw)
            System.err = err
            def cli = AutoExpireRolesCli.parseArgs((String[])args)
        then:
            def message = new String(sw.toByteArray())
            assert message == expecetedMessage
            if (!message && cli) {
                if (cli.d) {
                    assert cli.'config-directory' == './target'
                }
            }
        where:
            args                                | expecetedMessage
            []                                  | ''
            ['token']                           | ''
            ['-t', 'token']                     | ''
            ['-h']                              | ''
            ['--help']                          | ''
            ['-t', 'token', '-blubb']           | ''
            ['-d', './target', '-t', 'token']   | ''
            ['-t', 'token', '-d', './target']   | ''
            ['-t', 'token', '-d', './target', '-h']   | ''
            ['--token', 'token', '--config-directory', './target']   | ''
            ['--token', 'token', '--config-directory', './target']   | ''
    }

    def "ReadConfig"() {
        when:
            Map<String, Object> map = [:]
            if (deleteConfig) {
                def f = new File("$input/config.json")
                if (f.exists()) {
                    f.delete()
                }
            } else {
                if (emptyConfig) {
                    FileChannel.open(Paths.get("$input/config.json"), StandardOpenOption.WRITE).truncate(0).close()
                }
            }
            def (initDelayRes, periodRes, timeUnitRes) = AutoExpireRolesCli.readConfig(input, map)
        then:
            assert initDelay == initDelayRes
            assert period == periodRes
            assert timeUnit == timeUnitRes
            assert map == mapResult
        where:
            deleteConfig | emptyConfig |  input       | initDelay | period | timeUnit         | mapResult
            true         | false       |  './target'  | 0         | 5      | TimeUnit.MINUTES | [TestRole1:[1, "MINUTES"], TestRole2:[5, "MINUTES"], TestRole3:[2, "DAYS"], TestRole4:[23446, "SECONDS"]]
            false        | false       |  './target'  | 0         | 5      | TimeUnit.MINUTES | [TestRole1:[1, "MINUTES"], TestRole2:[5, "MINUTES"], TestRole3:[2, "DAYS"], TestRole4:[23446, "SECONDS"]]
            false        | false       |  './xyz'     | null      | null   | null             | [:]
            false        | true        |  './target'  | null      | null   | null             | [:]
    }

    def "toJsonFormat"() {
        when:
            def result = AutoExpireRolesCli.toJsonFormat(input != null?new TreeMap<String, Object>(input):input as Map<String, Object>)
        then:
            assert result == expected
        where:
            input                                                                                 | expected
            [xxxxx: "2022-10-09T07:55:31.804621500Z"]                                             | '{"xxxxx" : "2022-10-09T07:55:31.804621500Z"}'
            [xxxxx: "2022-10-09T07:55:31.804621500Z", yyyyy: "2022-10-10T07:55:31.804621500Z"]    | '{"xxxxx" : "2022-10-09T07:55:31.804621500Z",' + System.lineSeparator() + '"yyyyy" : "2022-10-10T07:55:31.804621500Z"}'
            [:]    | '{}'
            null  | '{}'

    }

    def "readWroteExpireState"() {
        when:
            AutoExpireRolesCli.writeExpireState('./target', input)
            def result = [:]
            AutoExpireRolesCli.readExpireState('./target', result as Map<String, Object>)
        then:
            assert result == expected
        where:
            input                                                                                 | expected
            [:]                                                                                   | [:]
            null                                                                                  | [:]
            [xxxxx: "2022-10-09T07:55:31.804621500Z"]                                             | [xxxxx: Instant.parse("2022-10-09T07:55:31.804621500Z")]
            [xxxxx: "2022-10-09T07:55:31.804621500Z", yyyyy: "2022-10-10T07:55:31.804621500Z"]    | [xxxxx: Instant.parse("2022-10-09T07:55:31.804621500Z"), yyyyy: Instant.parse("2022-10-10T07:55:31.804621500Z")]

    }
}
